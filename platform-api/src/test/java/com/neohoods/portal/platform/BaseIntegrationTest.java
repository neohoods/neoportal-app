package com.neohoods.portal.platform;

import java.io.File;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import com.neohoods.portal.platform.services.Auth0Service;
import com.neohoods.portal.platform.services.MailService;
import com.neohoods.portal.platform.services.NotificationsService;
import com.neohoods.portal.platform.spaces.services.DigitalLockService;
import com.neohoods.portal.platform.spaces.services.NukiRemoteAPIService;
import com.neohoods.portal.platform.spaces.services.StripeService;
import com.neohoods.portal.platform.spaces.services.TTlockRemoteAPIService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.show-sql=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    // Isolated container for each test class
    // Each test class gets its own PostgreSQL container with a unique database name
    // This ensures complete isolation between test classes
    private static final String uniqueDatabaseName = "neohoods-test-" + UUID.randomUUID().toString().replace("-", "");

    @Container
    protected static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName(uniqueDatabaseName)
            .withUsername("test")
            .withPassword("test")
            .withStartupTimeoutSeconds(120)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(new File("../db/postgres/init.sql").getAbsolutePath()),
                    "/docker-entrypoint-initdb.d/1-init.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(new File("../db/postgres/data.sql").getAbsolutePath()),
                    "/docker-entrypoint-initdb.d/2-data.sql");
    // Each test class gets its own unique database for complete isolation

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
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
