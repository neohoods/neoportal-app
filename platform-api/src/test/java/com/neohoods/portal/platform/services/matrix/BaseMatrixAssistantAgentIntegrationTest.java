package com.neohoods.portal.platform.services.matrix;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.BaseIntegrationTest;
import com.neohoods.portal.platform.entities.InfoEntity;
import com.neohoods.portal.platform.entities.UnitEntity;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserStatus;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.repositories.InfoRepository;
import com.neohoods.portal.platform.repositories.UnitRepository;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAdminCommandService;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.mcp.MatrixAssistantMCPAdapter;
import com.neohoods.portal.platform.assistant.mcp.MatrixAssistantMCPServer;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceStatusForEntity;

import lombok.extern.slf4j.Slf4j;

/**
 * Base class for Matrix Assistant agent integration tests.
 * Provides common setup and test data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.show-sql=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "neohoods.portal.matrix.enabled=false", // Disable Matrix API calls
        "neohoods.portal.matrix.assistant.ai.enabled=true",
        "neohoods.portal.matrix.assistant.ai.api-key=${MISTRAL_AI_TOKEN:}",
        "neohoods.portal.matrix.assistant.ai.provider=mistral",
        "neohoods.portal.matrix.assistant.ai.model=mistral-small",
        "neohoods.portal.matrix.assistant.mcp.enabled=true",
        "neohoods.portal.matrix.assistant.rag.enabled=false", // Disable RAG for focused tests
        "neohoods.portal.matrix.assistant.conversation.enabled=true",
        "neohoods.portal.matrix.assistant.llm-judge.enabled=false" // Disable LLM-as-a-Judge for integration tests
})
@Transactional
@Slf4j
public abstract class BaseMatrixAssistantAgentIntegrationTest extends BaseIntegrationTest {

    @Autowired
    protected UsersRepository usersRepository;

    @Autowired
    protected UnitRepository unitRepository;

    @Autowired
    protected InfoRepository infoRepository;

    @Autowired
    protected SpaceRepository spaceRepository;

    @Autowired
    protected com.neohoods.portal.platform.services.UnitsService unitsService;

    // Spy on MCP adapter to verify tool calls
    @SpyBean
    protected MatrixAssistantMCPAdapter mcpAdapter;

    // Spy on MCP server to verify tool calls
    @SpyBean
    protected MatrixAssistantMCPServer mcpServer;

    @MockBean
    protected MatrixAssistantAdminCommandService adminCommandService;

    protected UserEntity testUser;
    protected UnitEntity apartment808;
    protected InfoEntity infoEntity;
    protected SpaceEntity commonRoomSpace;
    protected SpaceEntity parkingSpace1;
    protected SpaceEntity parkingSpace2;

    @BeforeEach
    public void setUp() {
        // Skip tests if MISTRAL_AI_TOKEN is not set (e.g., in CI without token)
        String apiKey = System.getenv("MISTRAL_AI_TOKEN");
        if (apiKey == null || apiKey.isEmpty()) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "MISTRAL_AI_TOKEN not set - skipping integration test");
        }
        // Create test user
        // IMPORTANT: Username must match the Matrix user ID format
        // (@username:server.com)
        // The getUser() method extracts "testuser" from "@testuser:chat.neohoods.com"
        testUser = new UserEntity();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test-user@neohoods.com");
        testUser.setUsername("testuser"); // Must match the username extracted from Matrix user ID
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setType(UserType.OWNER);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setPreferredLanguage("fr");
        testUser.setPassword("$2a$10$dummyPasswordHashForTesting"); // Required for database constraint
        testUser = usersRepository.save(testUser);

        // Create apartment 808
        apartment808 = new UnitEntity();
        apartment808.setId(UUID.randomUUID());
        apartment808.setName("808");
        apartment808.setType(com.neohoods.portal.platform.entities.UnitTypeForEntity.FLAT);
        apartment808 = unitRepository.save(apartment808);

        // Create a unit for testUser and set it as primary unit (required for
        // COMMON_ROOM reservations)
        // For PARKING, it's optional but helps avoid errors
        // createUnit with adminId automatically adds the user as ADMIN member and sets
        // it as primary if it's the first unit
        if (unitsService.getUserUnits(testUser.getId()).count().block() == 0) {
            unitsService.createUnit("Test Unit " + testUser.getId(), null, testUser.getId()).block();
        }

        // Refresh testUser to get the updated primaryUnit (createUnit should have set
        // it automatically)
        testUser = usersRepository.findById(testUser.getId()).orElse(testUser);

        // Verify that primaryUnit is set
        if (testUser.getPrimaryUnit() == null) {
            log.warn("Primary unit is null after createUnit, trying to set it manually");
            var units = unitsService.getUserUnits(testUser.getId()).collectList().block();
            if (units != null && !units.isEmpty() && units.get(0).getId() != null) {
                // Add user as member first if not already a member
                try {
                    unitsService.addMemberToUnit(units.get(0).getId(), testUser.getId(), null).block();
                } catch (Exception e) {
                    // User might already be a member, ignore
                    log.debug("User might already be a member: {}", e.getMessage());
                }
                // Then set it as primary unit
                unitsService.setPrimaryUnitForUser(testUser.getId(), units.get(0).getId(), null).block();
                testUser = usersRepository.findById(testUser.getId()).orElse(testUser);
            }
        }

        // Final verification: ensure primaryUnit is set
        if (testUser.getPrimaryUnit() == null) {
            throw new IllegalStateException("Failed to set primary unit for test user " + testUser.getId());
        }
        log.info("Test user {} has primary unit: {}", testUser.getId(), testUser.getPrimaryUnit().getId());

        // Use existing InfoEntity from test data
        infoEntity = infoRepository.findByIdWithContactNumbers(
                UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .orElse(null);

        if (infoEntity == null) {
            infoEntity = new InfoEntity();
            infoEntity.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
            infoEntity = infoRepository.save(infoEntity);
        }

        // Create or find common room space for availability tests
        commonRoomSpace = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.COMMON_ROOM &&
                        (s.getName().toLowerCase().contains("salle") ||
                                s.getName().toLowerCase().contains("commune") ||
                                s.getName().toLowerCase().contains("common")))
                .findFirst()
                .orElse(null);

        if (commonRoomSpace == null) {
            commonRoomSpace = new SpaceEntity();
            commonRoomSpace.setId(UUID.randomUUID());
            commonRoomSpace.setName("Salle commune");
            commonRoomSpace.setType(SpaceTypeForEntity.COMMON_ROOM);
            commonRoomSpace.setStatus(SpaceStatusForEntity.ACTIVE);
            commonRoomSpace.setTenantPrice(BigDecimal.ZERO);
            commonRoomSpace.setCurrency("EUR");
            commonRoomSpace = spaceRepository.save(commonRoomSpace);
        }

        // Create parking spaces for parking reservation tests
        var existingParkings = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.PARKING)
                .collect(Collectors.toList());

        if (existingParkings.size() < 2) {
            // Create parking space 1
            parkingSpace1 = new SpaceEntity();
            parkingSpace1.setId(UUID.randomUUID());
            parkingSpace1.setName("Place de parking A1");
            parkingSpace1.setType(SpaceTypeForEntity.PARKING);
            parkingSpace1.setStatus(SpaceStatusForEntity.ACTIVE);
            parkingSpace1.setTenantPrice(BigDecimal.ZERO);
            parkingSpace1.setCurrency("EUR");
            parkingSpace1 = spaceRepository.save(parkingSpace1);

            // Create parking space 2
            parkingSpace2 = new SpaceEntity();
            parkingSpace2.setId(UUID.randomUUID());
            parkingSpace2.setName("Place de parking A2");
            parkingSpace2.setType(SpaceTypeForEntity.PARKING);
            parkingSpace2.setStatus(SpaceStatusForEntity.ACTIVE);
            parkingSpace2.setTenantPrice(BigDecimal.ZERO);
            parkingSpace2.setCurrency("EUR");
            parkingSpace2 = spaceRepository.save(parkingSpace2);
        } else {
            parkingSpace1 = existingParkings.get(0);
            parkingSpace2 = existingParkings.size() > 1 ? existingParkings.get(1) : existingParkings.get(0);
        }
    }

    protected MatrixAssistantAuthContext createAuthContext() {
        return MatrixAssistantAuthContext.builder()
                .matrixUserId("@testuser:chat.neohoods.com")
                .roomId("!testroom:chat.neohoods.com")
                .isDirectMessage(true)
                .userEntity(testUser)
                .build();
    }
}
