package com.neohoods.portal.platform.spaces.api.spaces;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import com.neohoods.portal.platform.BaseIntegrationTest;
import com.neohoods.portal.platform.api.SpacesApiApiDelegate;
import com.neohoods.portal.platform.model.PaginatedSpaces;
import com.neohoods.portal.platform.model.Space;
import com.neohoods.portal.platform.spaces.services.SpacesService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Integration tests for SpacesApiApiDelegateImpl.
 * 
 * Tests:
 * - getSpaces() should not throw LazyInitializationException when accessing
 * images
 * - getSpaces() should properly load images with fetch join
 * - getSpace() should not throw LazyInitializationException when accessing
 * images
 */
@org.springframework.transaction.annotation.Transactional
public class SpacesApiApiDelegateImplTest extends BaseIntegrationTest {

    @Autowired
    private SpacesApiApiDelegate spacesApiDelegate;

    @Autowired
    private SpacesService spacesService;

    @Test
    @DisplayName("getSpaces() should not throw LazyInitializationException when accessing images")
    public void testGetSpaces_NoLazyInitializationException() {
        // Act & Assert - Should not throw LazyInitializationException
        Mono<ResponseEntity<PaginatedSpaces>> result = spacesApiDelegate.getSpaces(
                null, null, null, null, null, 0, 20, null);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response.getBody());
                    PaginatedSpaces paginatedSpaces = response.getBody();
                    assertNotNull(paginatedSpaces.getContent());

                    // Access images and allowedDays on each space - should not throw
                    // LazyInitializationException
                    paginatedSpaces.getContent().forEach(space -> {
                        assertDoesNotThrow(() -> {
                            assertNotNull(space.getImages(), "Images list should not be null");
                            // Accessing the list should not throw exception
                            space.getImages().size(); // Force collection access
                        }, "Should not throw LazyInitializationException when accessing images");
                        assertDoesNotThrow(() -> {
                            if (space.getRules() != null && space.getRules().getAllowedDays() != null) {
                                space.getRules().getAllowedDays().size(); // Force collection access
                            }
                        }, "Should not throw LazyInitializationException when accessing allowedDays");
                    });
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getSpaces() with date range should not throw LazyInitializationException")
    public void testGetSpaces_WithDateRange_NoLazyInitializationException() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = LocalDate.now().plusDays(20);

        // Act & Assert - Should not throw LazyInitializationException
        Mono<ResponseEntity<PaginatedSpaces>> result = spacesApiDelegate.getSpaces(
                null, null, startDate, endDate, null, 0, 20, null);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response.getBody());
                    PaginatedSpaces paginatedSpaces = response.getBody();
                    assertNotNull(paginatedSpaces.getContent());

                    // Access images and allowedDays on each space - should not throw
                    // LazyInitializationException
                    paginatedSpaces.getContent().forEach(space -> {
                        assertDoesNotThrow(() -> {
                            assertNotNull(space.getImages(), "Images list should not be null");
                            // Accessing the list should not throw exception
                            space.getImages().size(); // Force collection access
                        }, "Should not throw LazyInitializationException when accessing images");
                        assertDoesNotThrow(() -> {
                            if (space.getRules() != null && space.getRules().getAllowedDays() != null) {
                                space.getRules().getAllowedDays().size(); // Force collection access
                            }
                        }, "Should not throw LazyInitializationException when accessing allowedDays");
                    });
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getSpace() should not throw LazyInitializationException when accessing images")
    public void testGetSpace_NoLazyInitializationException() {
        // Arrange - Get a space ID from the database
        List<com.neohoods.portal.platform.spaces.entities.SpaceEntity> spaces = spacesService.getAllActiveSpaces();
        assertFalse(spaces.isEmpty(), "Need at least one space for this test");
        java.util.UUID spaceId = spaces.get(0).getId();

        // Act & Assert - Should not throw LazyInitializationException
        Mono<ResponseEntity<Space>> result = spacesApiDelegate.getSpace(spaceId, null);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response.getBody());
                    Space space = response.getBody();

                    // Access images and allowedDays - should not throw LazyInitializationException
                    assertDoesNotThrow(() -> {
                        assertNotNull(space.getImages(), "Images list should not be null");
                        // Accessing the list should not throw exception
                        space.getImages().size(); // Force collection access
                    }, "Should not throw LazyInitializationException when accessing images");
                    assertDoesNotThrow(() -> {
                        if (space.getRules() != null && space.getRules().getAllowedDays() != null) {
                            space.getRules().getAllowedDays().size(); // Force collection access
                        }
                    }, "Should not throw LazyInitializationException when accessing allowedDays");
                })
                .verifyComplete();
    }
}
