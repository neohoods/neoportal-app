package com.neohoods.portal.platform;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Singleton container PostgreSQL partagé pour tous les tests.
 * Chaque classe de test obtient sa propre base de données dans ce container.
 */
public class SharedPostgresContainer {

    private static volatile SharedPostgresContainer instance;
    private static final Lock lock = new ReentrantLock();

    private final PostgreSQLContainer<?> container;
    // Map thread-safe pour garantir une seule DB par classe de test
    private final ConcurrentHashMap<String, String> databasesByTestClass = new ConcurrentHashMap<>();

    private SharedPostgresContainer() {
        // Container partagé avec une base de données par défaut
        // Les scripts d'init seront exécutés sur chaque nouvelle base de données créée
        // withReuse(true) permet de réutiliser le container entre les tests et dans la
        // CI
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("postgres") // Base de données par défaut pour créer d'autres DBs
                .withUsername("test")
                .withPassword("test")
                .withStartupTimeoutSeconds(120)
                .withReuse(true); // Réutiliser le container entre les tests

        // Démarrer le container (Testcontainers le réutilisera s'il existe déjà)
        container.start();
        this.container = container; // Assigner après le start pour éviter le warning de resource leak
    }

    public static SharedPostgresContainer getInstance() {
        if (instance == null) {
            lock.lock();
            try {
                if (instance == null) {
                    instance = new SharedPostgresContainer();
                }
            } finally {
                lock.unlock();
            }
        }
        return instance;
    }

    public PostgreSQLContainer<?> getContainer() {
        return container;
    }

    /**
     * Crée une base de données unique pour une classe de test et exécute les
     * scripts d'initialisation.
     * Garantit qu'une seule DB est créée par classe de test, même en exécution
     * parallèle.
     * 
     * @param testClassName Le nom de la classe de test
     * @return Le nom de la base de données créée
     */
    public String createDatabaseForTest(String testClassName) {
        // Utiliser computeIfAbsent pour garantir qu'une seule DB est créée par classe
        // de test
        return databasesByTestClass.computeIfAbsent(testClassName, className -> {
            String databaseName = "neohoods_test_" + className.toLowerCase().replace(".", "_")
                    + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

            try {
                // Utiliser getJdbcUrl() de Testcontainers qui gère correctement les environnements
                // conteneurisés. Cette méthode fonctionnait avant avec un container par test.
                // Dans GitHub Actions, Testcontainers configure automatiquement le réseau Docker.
                String jdbcUrl = container.getJdbcUrl().replace("/" + container.getDatabaseName(), "/postgres");

                try (java.sql.Connection conn = java.sql.DriverManager.getConnection(jdbcUrl, container.getUsername(),
                        container.getPassword());
                        Statement stmt = conn.createStatement()) {

                    // Créer la base de données via JDBC (fonctionnait avant)
                    stmt.executeUpdate("CREATE DATABASE \"" + databaseName + "\"");

                    // Se connecter à la nouvelle base de données et exécuter les scripts via psql
                    // (psql est nécessaire pour les fonctions et triggers)
                    executeInitScriptViaPsql(databaseName, "init.sql");
                    executeInitScriptViaPsql(databaseName, "data.sql");
                }
            } catch (Exception e) {
                // Logger l'erreur complète pour debug
                System.err.println("ERROR: Failed to create database " + databaseName + " for test class " + className);
                e.printStackTrace();
                throw new RuntimeException("Failed to create database " + databaseName + " for test class " + className,
                        e);
            }

            return databaseName;
        });
    }

    /**
     * Crée une base de données via psql (plus fiable dans les environnements
     * conteneurisés).
     */
    private void createDatabaseViaPsql(String databaseName) throws Exception {
        // Utiliser psql pour créer la base de données
        // Dans les environnements conteneurisés, utiliser le nom du container
        // PostgreSQL
        // créé par Testcontainers comme hostname, car ils sont sur le même réseau
        // Docker
        String host = container.getHost();
        int port = container.getFirstMappedPort();

        // Si on est dans un container Docker (host commence par 172. ou est localhost
        // avec port mappé),
        // utiliser le nom court du container (12 premiers caractères) comme hostname
        // car Testcontainers peut créer des alias réseau avec ce nom
        boolean isContainerized = host.startsWith("172.") ||
                (host.equals("localhost") && port != 5432);

        if (isContainerized) {
            try {
                String containerId = container.getContainerId();
                if (containerId != null && !containerId.isEmpty()) {
                    // Utiliser les 12 premiers caractères de l'ID comme hostname
                    // C'est le format utilisé par Docker pour les noms de containers
                    host = containerId.substring(0, Math.min(12, containerId.length()));
                    // Utiliser le port interne PostgreSQL (5432) car on est sur le même réseau
                    // Docker
                    port = 5432;
                }
                // Si containerId est null, on garde localhost avec le port mappé (ne devrait
                // pas arriver)
            } catch (Exception e) {
                // En cas d'erreur, garder localhost avec le port mappé
            }
        }

        String username = container.getUsername();
        String password = container.getPassword();

        // Utiliser psql pour créer la base de données
        ProcessBuilder pb = new ProcessBuilder(
                "psql",
                "-h", host,
                "-p", String.valueOf(port),
                "-U", username,
                "-d", "postgres", // Se connecter à la DB par défaut
                "-c", "CREATE DATABASE \"" + databaseName + "\"");

        pb.environment().put("PGPASSWORD", password);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            String errorOutput = new String(process.getInputStream().readAllBytes());
            // Ignorer l'erreur si la base existe déjà
            if (!errorOutput.contains("already exists")) {
                throw new RuntimeException("Failed to create database " + databaseName + " using host=" + host
                        + " port=" + port + ": " + errorOutput);
            }
        }
    }

    private void executeInitScriptViaPsql(String databaseName, String scriptName) throws Exception {
        File scriptFile = findScriptFile(scriptName);

        if (scriptFile == null || !scriptFile.exists()) {
            String userDir = System.getProperty("user.dir");
            System.err.println("ERROR: SQL script not found: " + scriptName);
            System.err.println("Current working directory: " + userDir);
            System.err.println("Searched paths relative to: " + (userDir != null ? userDir : "unknown"));
            throw new RuntimeException("SQL script not found: " + scriptName +
                    ". Current directory: " + userDir +
                    ". Please ensure the script exists in db/postgres/ directory.");
        }

        // Utiliser psql pour exécuter le script (plus fiable pour les fonctions et
        // triggers)
        // Dans les environnements conteneurisés, utiliser localhost si nécessaire
        String host = container.getHost();
        if ("172.17.0.1".equals(host) || host.startsWith("172.")) {
            host = "localhost";
        }
        int port = container.getFirstMappedPort();
        String username = container.getUsername();
        String password = container.getPassword();

        // Construire la commande psql
        ProcessBuilder pb = new ProcessBuilder(
                "psql",
                "-h", host,
                "-p", String.valueOf(port),
                "-U", username,
                "-d", databaseName,
                "-f", scriptFile.getAbsolutePath(),
                "-q" // Mode quiet pour réduire la sortie
        );

        // Définir le mot de passe via variable d'environnement
        pb.environment().put("PGPASSWORD", password);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            // Lire la sortie d'erreur
            String errorOutput = new String(process.getInputStream().readAllBytes());
            System.err.println("Warning: Error executing SQL script " + scriptName + ": " + errorOutput);
            // Ne pas échouer si c'est juste des warnings (comme "already exists")
            if (!errorOutput.contains("already exists") && !errorOutput.contains("does not exist")) {
                throw new RuntimeException("Failed to execute SQL script " + scriptName + ": " + errorOutput);
            }
        }
    }

    @SuppressWarnings("unused")
    private void executeInitScript(Statement stmt, String scriptPath) throws Exception {
        // Essayer plusieurs chemins possibles
        File scriptFile = findScriptFile(scriptPath);

        if (scriptFile == null || !scriptFile.exists()) {
            System.err.println("Warning: SQL script not found: " + scriptPath);
            return;
        }

        // Lire le contenu du fichier SQL
        String sql = new String(java.nio.file.Files.readAllBytes(scriptFile.toPath()));

        // Nettoyer le SQL: remplacer les retours à la ligne multiples et normaliser
        sql = sql.replaceAll("--.*", ""); // Supprimer les commentaires sur une ligne
        sql = sql.replaceAll("/\\*.*?\\*/", ""); // Supprimer les commentaires multi-lignes

        // Exécuter les commandes SQL (séparées par ;)
        // Utiliser un split plus intelligent qui gère les cas spéciaux
        String[] commands = sql.split(";(?=(?:[^']*'[^']*')*[^']*$)");
        for (String command : commands) {
            String trimmed = command.trim();
            if (!trimmed.isEmpty() && trimmed.length() > 3) {
                try {
                    stmt.execute(trimmed);
                } catch (Exception e) {
                    String errorMsg = e.getMessage();
                    // Ignorer certaines erreurs communes (déjà existant, etc.)
                    if (errorMsg != null &&
                            (errorMsg.contains("already exists") ||
                                    errorMsg.contains("does not exist") ||
                                    errorMsg.contains("duplicate key") ||
                                    errorMsg.contains("relation") && errorMsg.contains("already exists"))) {
                        // Erreur attendue, ignorer
                    } else {
                        System.err.println("Warning executing SQL command: " + e.getMessage());
                        System.err.println("Command: " + trimmed.substring(0, Math.min(100, trimmed.length())));
                    }
                }
            }
        }
    }

    private File findScriptFile(String scriptName) {
        // Essayer plusieurs chemins possibles depuis le répertoire de test
        File[] candidates = {
                new File("../db/postgres/" + scriptName),
                new File("../../db/postgres/" + scriptName),
                new File("../../../db/postgres/" + scriptName),
                new File("platform-api/../db/postgres/" + scriptName),
                new File("neoportal-app/db/postgres/" + scriptName),
                new File("db/postgres/" + scriptName),
        };

        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isFile()) {
                return candidate;
            }
        }

        // Si aucun fichier trouvé, essayer le chemin absolu depuis le répertoire de
        // travail
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            File[] absoluteCandidates = {
                    new File(userDir + "/db/postgres/" + scriptName),
                    new File(userDir + "/../db/postgres/" + scriptName),
                    new File(userDir + "/../../db/postgres/" + scriptName),
                    new File(userDir + "/neoportal-app/db/postgres/" + scriptName),
            };
            for (File candidate : absoluteCandidates) {
                if (candidate.exists() && candidate.isFile()) {
                    return candidate;
                }
            }

            // Pour GitHub Actions CI: le répertoire de travail peut être dans platform-api
            // Chercher depuis le répertoire parent
            if (userDir.contains("platform-api")) {
                File parentDir = new File(userDir).getParentFile();
                if (parentDir != null) {
                    File ciCandidate = new File(parentDir, "db/postgres/" + scriptName);
                    if (ciCandidate.exists() && ciCandidate.isFile()) {
                        return ciCandidate;
                    }
                }
            }
        }

        // Dernière tentative: chercher depuis le répertoire de la classe
        try {
            String classPath = SharedPostgresContainer.class.getProtectionDomain()
                    .getCodeSource().getLocation().getPath();
            File classDir = new File(classPath);
            if (classDir.exists()) {
                // Remonter depuis target/test-classes vers db/postgres
                File testDbDir = new File(classDir.getParentFile().getParentFile().getParentFile(),
                        "../db/postgres/" + scriptName);
                if (testDbDir.exists() && testDbDir.isFile()) {
                    return testDbDir;
                }
            }
        } catch (Exception e) {
            // Ignorer les erreurs de reflection
        }

        return null;
    }

    public void stop() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }
}
