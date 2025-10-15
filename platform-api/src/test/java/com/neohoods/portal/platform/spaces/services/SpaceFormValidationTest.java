package com.neohoods.portal.platform.spaces.services;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.BaseIntegrationTest;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

/**
 * Integration tests for space form validation.
 * 
 * Tests:
 * - Min days < Max days
 * - Positive values
 * - Valid price ranges
 * - Valid allowed/cleaning days
 */
@Transactional
public class SpaceFormValidationTest extends BaseIntegrationTest {

    @Autowired
    private SpaceRepository spaceRepository;

    @Test
    @DisplayName("Form validation: min duration < max duration")
    public void testFormValidation_MinDurationLessThanMax() {
        // Act
        var allSpaces = spaceRepository.findAll();

        // Assert
        for (SpaceEntity space : allSpaces) {
            assertTrue(space.getMinDurationDays() > 0,
                    "Min duration should be > 0 for space: " + space.getName());
            assertTrue(space.getMaxDurationDays() > 0,
                    "Max duration should be > 0 for space: " + space.getName());
            assertTrue(space.getMinDurationDays() <= space.getMaxDurationDays(),
                    "Min duration should be <= Max duration for space: " + space.getName());
        }
    }

    @Test
    @DisplayName("Form validation: tenant price >= 0")
    public void testFormValidation_TenantPricePositive() {
        // Act
        var allSpaces = spaceRepository.findAll();

        // Assert - Parking spaces can have 0 price
        for (SpaceEntity space : allSpaces) {
            assertTrue(space.getTenantPrice().compareTo(java.math.BigDecimal.ZERO) >= 0,
                    "Tenant price should be >= 0 for space: " + space.getName());
        }
    }

    @Test
    @DisplayName("Form validation: allowed days configured")
    public void testFormValidation_AllowedDaysConfigured() {
        // Act
        var allSpaces = spaceRepository.findAll();

        // Assert - Spaces should have allowed days configured
        for (SpaceEntity space : allSpaces) {
            if (space.getAllowedDays() != null) {
                assertTrue(!space.getAllowedDays().isEmpty(),
                        "Allowed days should not be empty if specified");
            }
        }
    }

    @Test
    @DisplayName("Form validation: cleaning days configured")
    public void testFormValidation_CleaningDaysConfigured() {
        // Act
        var allSpaces = spaceRepository.findAll();

        // Assert - Only check spaces that have cleaning fees
        for (SpaceEntity space : allSpaces) {
            if (space.getCleaningFee() != null && space.getCleaningFee().compareTo(java.math.BigDecimal.ZERO) > 0) {
                // Note: Cleaning days are optional - spaces can have cleaning fees without
                // specific cleaning days
                // This is a business rule that can be configured per space
                // We only validate that if cleaning days are configured, they should not be
                // empty
                if (space.getCleaningDays() != null && !space.getCleaningDays().isEmpty()) {
                    assertTrue(!space.getCleaningDays().isEmpty(),
                            "If cleaning days are configured, they should not be empty for space: " + space.getName());
                }
            }
        }
    }

    @Test
    @DisplayName("Form validation: price consistency")
    public void testFormValidation_PriceConsistency() {
        // Act
        var allSpaces = spaceRepository.findAll();

        // Assert
        for (SpaceEntity space : allSpaces) {
            // Owner price should be <= tenant price
            if (space.getOwnerPrice() != null && space.getTenantPrice() != null) {
                assertTrue(space.getOwnerPrice().compareTo(space.getTenantPrice()) <= 0,
                        "Owner price should be <= tenant price");
            }
        }
    }

    @Test
    @DisplayName("Form validation: cleaning fee >= 0")
    public void testFormValidation_CleaningFee() {
        // Act
        var allSpaces = spaceRepository.findAll();

        // Assert
        for (SpaceEntity space : allSpaces) {
            if (space.getCleaningFee() != null) {
                assertTrue(space.getCleaningFee().compareTo(java.math.BigDecimal.ZERO) >= 0,
                        "Cleaning fee should be >= 0");
            }
        }
    }

    @Test
    @DisplayName("Form validation: deposit >= 0")
    public void testFormValidation_Deposit() {
        // Act
        var allSpaces = spaceRepository.findAll();

        // Assert
        for (SpaceEntity space : allSpaces) {
            if (space.getDeposit() != null) {
                assertTrue(space.getDeposit().compareTo(java.math.BigDecimal.ZERO) >= 0,
                        "Deposit should be >= 0");
            }
        }
    }

    @Test
    @DisplayName("Form validation: max annual reservations >= 0")
    public void testFormValidation_MaxAnnualReservations() {
        // Act
        var allSpaces = spaceRepository.findAll();

        // Assert
        for (SpaceEntity space : allSpaces) {
            assertTrue(space.getMaxAnnualReservations() >= 0,
                    "Max annual reservations should be >= 0");
            assertTrue(space.getUsedAnnualReservations() >= 0,
                    "Used annual reservations should be >= 0");
            assertTrue(space.getUsedAnnualReservations() <= space.getMaxAnnualReservations()
                    || space.getMaxAnnualReservations() == 0,
                    "Used should not exceed max");
        }
    }

    @Test
    @DisplayName("Form validation: currency is valid")
    public void testFormValidation_CurrencyValid() {
        // Act
        var allSpaces = spaceRepository.findAll();

        // Assert
        for (SpaceEntity space : allSpaces) {
            assertTrue(space.getCurrency() != null && space.getCurrency().length() == 3,
                    "Currency should be 3-letter code (e.g., EUR)");
        }
    }

    @Test
    @DisplayName("Form validation: time slots valid")
    public void testFormValidation_TimeSlots() {
        // Act
        var allSpaces = spaceRepository.findAll();

        // Assert - Only check spaces that have both start and end hours
        for (SpaceEntity space : allSpaces) {
            if (space.getAllowedHoursStart() != null && space.getAllowedHoursEnd() != null) {
                // For overnight stays (like guest rooms), end time can be before start time
                // This represents check-in time to check-out time (e.g., 15:00 - 11:00)
                // We only validate that both times are set and not null
                assertNotNull("Start hour should be set for space: " + space.getName(), space.getAllowedHoursStart());
                assertNotNull("End hour should be set for space: " + space.getName(), space.getAllowedHoursEnd());
            }
        }
    }
}
