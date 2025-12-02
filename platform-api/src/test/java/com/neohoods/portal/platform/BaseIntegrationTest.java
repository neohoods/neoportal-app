package com.neohoods.portal.platform;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.neohoods.portal.platform.services.Auth0Service;
import com.neohoods.portal.platform.services.MailService;
import com.neohoods.portal.platform.services.MatrixAssistantService;
import com.neohoods.portal.platform.services.MatrixOAuth2Service;
import com.neohoods.portal.platform.services.MatrixAssistantInitializationService;
import com.neohoods.portal.platform.services.NotificationsService;
import com.neohoods.portal.platform.spaces.services.DigitalLockService;
import com.neohoods.portal.platform.spaces.services.NukiRemoteAPIService;
import com.neohoods.portal.platform.spaces.services.StripeService;
import com.neohoods.portal.platform.spaces.services.TTlockRemoteAPIService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.show-sql=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "neohoods.portal.matrix.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    // Container PostgreSQL partagé réutilisé pour tous les tests
    // Chaque classe de test obtient sa propre base de données dans ce container
    private static final SharedPostgresContainer sharedContainer = SharedPostgresContainer.getInstance();
    
    // Map thread-safe pour stocker le nom de la DB par classe de test
    // Permet l'exécution parallèle sans conflits
    private static final ConcurrentHashMap<String, String> databaseNamesByTestClass = new ConcurrentHashMap<>();
    
    /**
     * Initialise la base de données pour cette classe de test.
     * Cette méthode doit être appelée depuis chaque classe de test concrète.
     */
    protected static void initializeDatabase(Class<?> testClass) {
        String testClassName = testClass.getSimpleName();
        databaseNamesByTestClass.computeIfAbsent(testClassName, className -> {
            return sharedContainer.createDatabaseForTest(className);
        });
    }
    
    protected static PostgreSQLContainer getPostgresContainer() {
        return sharedContainer.getContainer();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Initialiser la base de données avec le nom de la classe concrète
        // Utiliser une approche avec reflection pour obtenir la classe appelante
        String testClassName = getTestClassName();
        String uniqueDatabaseName = databaseNamesByTestClass.computeIfAbsent(testClassName, className -> {
            return sharedContainer.createDatabaseForTest(className);
        });
        
        // Construire l'URL JDBC avec le nom de la base de données unique
        String jdbcUrl = sharedContainer.getJdbcUrl(uniqueDatabaseName);
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", sharedContainer::getUsername);
        registry.add("spring.datasource.password", sharedContainer::getPassword);
    }
    
    private static String getTestClassName() {
        // Obtenir le nom de la classe de test concrète via la stack trace
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            try {
                Class<?> clazz = Class.forName(className);
                // Vérifier si c'est une classe de test (hérite de BaseIntegrationTest mais n'est pas BaseIntegrationTest)
                if (BaseIntegrationTest.class.isAssignableFrom(clazz) && 
                    !clazz.equals(BaseIntegrationTest.class) &&
                    !clazz.isInterface() &&
                    !className.contains("$")) {
                    return clazz.getSimpleName();
                }
            } catch (ClassNotFoundException e) {
                // Ignorer
            }
        }
        // Fallback: utiliser un UUID
        return "test-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    @MockBean
    protected StripeService stripeService;

    @MockBean
    protected TTlockRemoteAPIService ttlockService;

    @MockBean
    protected NukiRemoteAPIService nukiService;

    @MockBean
    protected DigitalLockService digitalLockService;

    @MockBean
    protected Auth0Service auth0Service;

    @MockBean
    protected MailService mailService;

    @MockBean
    protected NotificationsService notificationsService;

    @MockBean
    protected MatrixAssistantService matrixAssistantService;

    @MockBean
    protected MatrixOAuth2Service matrixOAuth2Service;

    @MockBean
    protected MatrixAssistantInitializationService matrixBotInitializationService;

    @BeforeEach
    public void baseSetUp() {
        // Configure DigitalLockService mock to return a random access code
        org.mockito.Mockito.when(digitalLockService.generateAccessCode(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    // Generate a random 6-character code
                    String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
                    StringBuilder code = new StringBuilder(6);
                    java.util.Random random = new java.util.Random();
                    for (int i = 0; i < 6; i++) {
                        code.append(chars.charAt(random.nextInt(chars.length())));
                    }
                    return code.toString();
                });

        // Configure MailService mock to do nothing (don't send emails in tests)
        org.mockito.Mockito.doNothing().when(mailService).sendReservationConfirmationEmail(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doNothing().when(mailService).sendReservationReminderEmail(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());

        // Configure NotificationsService mock to do nothing
        org.mockito.Mockito.doNothing().when(notificationsService).createNotification(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doNothing().when(notificationsService).notifyReservationConfirmed(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

}
