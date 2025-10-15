package com.neohoods.portal.platform.spaces.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.BaseIntegrationTest;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

/**
 * Integration tests for pricing calculation functionality.
 */
@Transactional
public class PricingCalculationTest extends BaseIntegrationTest {

    @Autowired
    private SpacesService spacesService;

    @Autowired
    private ReservationsService reservationsService;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private UsersRepository usersRepository;

    private SpaceEntity commonRoomSpace;
    private SpaceEntity parkingSpace;
    private UserEntity ownerUser;
    private UserEntity tenantUser;

    @BeforeEach
    public void setUp() {
        commonRoomSpace = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.COMMON_ROOM)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No common room space found"));

        parkingSpace = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.PARKING)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No parking space found"));

        Iterable<UserEntity> allUsers = usersRepository.findAll();
        for (UserEntity user : allUsers) {
            if (user.getType() == UserType.OWNER) {
                ownerUser = user;
            }
            if (user.getType() == UserType.TENANT) {
                tenantUser = user;
            }
        }
        if (ownerUser == null) {
            ownerUser = new UserEntity();
            ownerUser.setEmail("test-owner@neohoods.com");
            ownerUser.setType(UserType.OWNER);
            ownerUser.setStatus(com.neohoods.portal.platform.entities.UserStatus.ACTIVE);
            ownerUser = usersRepository.save(ownerUser);
        }
        if (tenantUser == null) {
            tenantUser = new UserEntity();
            tenantUser.setEmail("test-tenant@neohoods.com");
            tenantUser.setType(UserType.TENANT);
            tenantUser.setStatus(com.neohoods.portal.platform.entities.UserStatus.ACTIVE);
            tenantUser = usersRepository.save(tenantUser);
        }
    }

    @Test
    @DisplayName("Owner - Common room pricing")
    public void testPricing_Owner_CommonRoom() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(1);

        // Act
        var priceBreakdown = spacesService.calculatePriceBreakdown(
                commonRoomSpace.getId(), startDate, endDate, true);

        // Assert
        assertNotNull(priceBreakdown);
        assertNotNull(priceBreakdown.getTotalPrice());
        assertTrue(priceBreakdown.getTotalPrice().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Tenant - Common room pricing with markup")
    public void testPricing_Tenant_CommonRoom() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(1);

        // Act
        var ownerPrice = spacesService.calculatePriceBreakdown(
                commonRoomSpace.getId(), startDate, endDate, true);
        var tenantPrice = spacesService.calculatePriceBreakdown(
                commonRoomSpace.getId(), startDate, endDate, false);

        // Assert
        assertTrue(tenantPrice.getTotalPrice().compareTo(ownerPrice.getTotalPrice()) >= 0);
    }

    @Test
    @DisplayName("Parking is free")
    public void testPricing_Parking_Free() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(1);

        // Act
        var priceBreakdown = spacesService.calculatePriceBreakdown(
                parkingSpace.getId(), startDate, endDate, true);

        // Assert
        assertEquals(BigDecimal.ZERO, priceBreakdown.getTotalPrice());
    }

    @Test
    @DisplayName("Multi-day pricing calculation")
    public void testPricing_MultiDay_Calculation() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(5); // 5 days

        // Act
        var priceBreakdown = spacesService.calculatePriceBreakdown(
                commonRoomSpace.getId(), startDate, endDate, true);

        // Assert
        assertNotNull(priceBreakdown.getTotalPrice());
        assertTrue(priceBreakdown.getTotalPrice().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Platform fees calculated correctly")
    public void testPricing_PlatformFee_Percentage() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        // Act
        var priceBreakdown = spacesService.calculatePriceBreakdown(
                commonRoomSpace.getId(), startDate, endDate, true);

        // Assert
        assertNotNull(priceBreakdown.getPlatformFeeAmount());
        assertNotNull(priceBreakdown.getPlatformFixedFeeAmount());

        // Platform fees should be greater than 0 for paid spaces
        // Default settings: 2% percentage fee + 0.25€ fixed fee
        assertTrue(priceBreakdown.getPlatformFeeAmount().compareTo(BigDecimal.ZERO) > 0,
                "Platform fee percentage should be calculated");
        assertEquals(BigDecimal.valueOf(0.25), priceBreakdown.getPlatformFixedFeeAmount(),
                "Platform fixed fee should be 0.25€");

        // Verify total includes platform fees
        BigDecimal expectedTotal = priceBreakdown.getBasePrice()
                .add(priceBreakdown.getCleaningFee())
                .add(priceBreakdown.getDeposit())
                .add(priceBreakdown.getPlatformFeeAmount())
                .add(priceBreakdown.getPlatformFixedFeeAmount());
        assertEquals(expectedTotal, priceBreakdown.getTotalPrice(),
                "Total price should include all fees including platform fees");
    }

    @Test
    @DisplayName("Platform fees stored in reservation when created")
    public void testPricing_PlatformFees_StoredInReservation() {
        // Arrange - Use space's max duration to ensure we stay within limits
        LocalDate startDate = LocalDate.now().plusDays(400); // Far future to avoid conflicts
        // Use exactly maxDurationDays if available, otherwise use 1 day
        int maxDays = commonRoomSpace.getMaxDurationDays() > 0
                ? Math.min(commonRoomSpace.getMaxDurationDays(), 1)
                : 1;
        LocalDate endDate = startDate.plusDays(maxDays - 1); // Respect max duration

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                commonRoomSpace, tenantUser, startDate, endDate);

        // Assert - Platform fees should be saved in reservation
        assertNotNull(reservation.getPlatformFeeAmount(),
                "Platform fee amount should be stored in reservation");
        assertNotNull(reservation.getPlatformFixedFeeAmount(),
                "Platform fixed fee amount should be stored in reservation");

        assertTrue(reservation.getPlatformFeeAmount().compareTo(BigDecimal.ZERO) > 0,
                "Platform fee amount should be greater than 0 for paid spaces");
        assertEquals(BigDecimal.valueOf(0.25), reservation.getPlatformFixedFeeAmount(),
                "Platform fixed fee should be 0.25€");

        // Verify total price includes platform fees
        BigDecimal expectedTotal = reservation.getTotalPrice();
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        BigDecimal calculatedTotal = reservation.getSpace().getTenantPrice()
                .multiply(BigDecimal.valueOf(days))
                .add(reservation.getSpace().getCleaningFee())
                .add(reservation.getSpace().getDeposit())
                .add(reservation.getPlatformFeeAmount())
                .add(reservation.getPlatformFixedFeeAmount());

        assertEquals(expectedTotal, calculatedTotal,
                "Reservation total price should include platform fees");
    }
}
