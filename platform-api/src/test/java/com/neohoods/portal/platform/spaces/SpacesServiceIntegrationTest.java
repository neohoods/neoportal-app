package com.neohoods.portal.platform.spaces;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.neohoods.portal.platform.BaseIntegrationTest;
import com.neohoods.portal.platform.SharedPostgresContainer;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.services.SpacesService;

/**
 * Integration tests for SpacesService.
 * 
 * This is a smoke test to verify:
 * - Database connection works
 * - init.sql and data.sql are loaded correctly
 * - Basic JPA operations function properly
 */
public class SpacesServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SpacesService spacesService;

    @Test
    public void testGetAllActiveSpaces_Success() {
        // Act
        List<SpaceEntity> spaces = spacesService.getAllActiveSpaces();

        // Assert
        assertNotNull(spaces, "Spaces list should not be null");
        assertFalse(spaces.isEmpty(), "Should have at least one active space from data.sql");

        // Verify we have some expected space types
        boolean hasGuestRoom = spaces.stream()
                .anyMatch(s -> "GUEST_ROOM".equals(s.getType().toString()));
        assertTrue(hasGuestRoom, "Should have at least one GUEST_ROOM space");

        // Log for debugging
        System.out.println("Found " + spaces.size() + " active spaces:");
        spaces.forEach(space -> {
            System.out.println("  - " + space.getName() + " (" + space.getType() + ")");
        });
    }

    @Test
    public void testGetSpaceById_Success() {
        // Arrange - Get a space ID from the list of active spaces
        List<SpaceEntity> spaces = spacesService.getAllActiveSpaces();
        assertFalse(spaces.isEmpty(), "Need at least one space for this test");

        UUID spaceId = spaces.get(0).getId();

        // Act
        SpaceEntity space = spacesService.getSpaceById(spaceId);

        // Assert
        assertNotNull(space, "Space should not be null");
        assertEquals(spaceId, space.getId(), "Space ID should match");
        assertNotNull(space.getName(), "Space name should not be null");
        assertNotNull(space.getType(), "Space type should not be null");
        assertNotNull(space.getStatus(), "Space status should not be null");

        System.out.println("Successfully retrieved space: " + space.getName());
    }

    @Test
    public void testDatabaseConnection() {
        // This test verifies that the database connection is working
        // and the database schema is properly initialized
        SharedPostgresContainer sharedContainer = SharedPostgresContainer.getInstance();
        
        if (sharedContainer.isUsingLocalPostgres()) {
            // When using local PostgreSQL, verify connection by testing a database operation
            // If we can query spaces, the connection is working
            List<SpaceEntity> spaces = spacesService.getAllActiveSpaces();
            assertNotNull(spaces, "Should be able to query database");
            String jdbcUrl = "jdbc:postgresql://localhost:5432/...";
            System.out.println("Database connection successful (local PostgreSQL): " + jdbcUrl);
        } else {
            // When using Testcontainers, verify the container is running
            var postgresContainer = getPostgresContainer();
            assertTrue(postgresContainer.isRunning(), "PostgreSQL container should be running");
            
            String jdbcUrl = postgresContainer.getJdbcUrl();
            assertNotNull(jdbcUrl, "JDBC URL should not be null");
            assertTrue(jdbcUrl.contains("postgresql"), "JDBC URL should be for PostgreSQL");
            
            System.out.println("Database connection successful: " + jdbcUrl);
        }
    }
}















