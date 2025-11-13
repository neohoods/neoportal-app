package com.neohoods.portal.platform.spaces.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.services.UnitsService;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;

/**
 * Unit tests for owner definition logic in ReservationsService.
 * 
 * Tests verify that:
 * 1. The isOwner flag is correctly determined based on user type
 * 2. The correct price is calculated and returned in the reservation
 * 
 * For GUEST_ROOM spaces:
 * - ADMIN, OWNER, LANDLORD should use ownerPrice (lower price)
 * - All other user types should use tenantPrice (higher price)
 * 
 * The test uses a GUEST_ROOM with:
 * - ownerPrice: 50.00 EUR/night
 * - tenantPrice: 100.00 EUR/night
 * - cleaningFee: 20.00 EUR
 * - deposit: 50.00 EUR
 * - platform fees: 10% + 5.00 EUR fixed
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Owner Definition and Pricing Tests")
public class OwnerDefinitionTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private SpacesService spacesService;

    @Mock
    private AccessCodeService accessCodeService;

    @Mock
    private ReservationAuditService auditService;

    @Mock
    private UnitsService unitsService;

    @InjectMocks
    private ReservationsService reservationsService;

    private SpaceEntity guestRoomSpace;
    private LocalDate startDate;
    private LocalDate endDate;
    
    // Test pricing constants
    private static final BigDecimal OWNER_PRICE_PER_NIGHT = new BigDecimal("50.00");
    private static final BigDecimal TENANT_PRICE_PER_NIGHT = new BigDecimal("100.00");
    private static final BigDecimal CLEANING_FEE = new BigDecimal("20.00");
    private static final BigDecimal DEPOSIT = new BigDecimal("50.00");
    private static final BigDecimal PLATFORM_FEE_PERCENTAGE = new BigDecimal("10.00");
    private static final BigDecimal PLATFORM_FIXED_FEE = new BigDecimal("5.00");
    private static final int RESERVATION_DURATION_DAYS = 3; // 3 nights

    @BeforeEach
    void setUp() {
        // Setup test space - GUEST_ROOM with different prices for owner and tenant
        guestRoomSpace = new SpaceEntity();
        guestRoomSpace.setId(UUID.randomUUID());
        guestRoomSpace.setName("Test Guest Room");
        guestRoomSpace.setType(SpaceTypeForEntity.GUEST_ROOM);
        guestRoomSpace.setTenantPrice(TENANT_PRICE_PER_NIGHT);
        guestRoomSpace.setOwnerPrice(OWNER_PRICE_PER_NIGHT);
        guestRoomSpace.setCleaningFee(CLEANING_FEE);
        guestRoomSpace.setDeposit(DEPOSIT);
        guestRoomSpace.setMaxAnnualReservations(0); // Unlimited

        // Setup test dates - 3 nights reservation
        startDate = LocalDate.now().plusDays(10);
        endDate = startDate.plusDays(RESERVATION_DURATION_DAYS);

        // Mock spacesService.validateUserCanReserveSpace to not throw (void method)
        org.mockito.Mockito.doNothing().when(spacesService).validateUserCanReserveSpace(
                any(UUID.class), any(UUID.class), any(LocalDate.class), any(LocalDate.class));

        // Mock calculatePriceBreakdown to return different prices based on isOwner parameter
        when(spacesService.calculatePriceBreakdown(any(UUID.class), any(LocalDate.class), 
                any(LocalDate.class), anyBoolean())).thenAnswer(invocation -> {
            boolean isOwner = invocation.getArgument(3);
            BigDecimal pricePerNight = isOwner ? OWNER_PRICE_PER_NIGHT : TENANT_PRICE_PER_NIGHT;
            BigDecimal totalDaysPrice = pricePerNight.multiply(BigDecimal.valueOf(RESERVATION_DURATION_DAYS));
            
            // Calculate unit price
            BigDecimal unitPrice = totalDaysPrice.divide(BigDecimal.valueOf(RESERVATION_DURATION_DAYS), 2, java.math.RoundingMode.HALF_UP);
            
            // Calculate subtotal: totalDaysPrice + cleaningFee
            BigDecimal subtotal = totalDaysPrice.add(CLEANING_FEE);
            
            // Calculate platform fees (10% of basePrice + cleaningFee + 5.00 fixed)
            // Platform fees are calculated on basePrice + cleaningFee, rounded up to 1 decimal (CEILING)
            BigDecimal basePriceWithCleaning = totalDaysPrice.add(CLEANING_FEE);
            BigDecimal platformFeeAmount = BigDecimal.ZERO;
            BigDecimal platformFixedFeeAmount = BigDecimal.ZERO;
            
            if (basePriceWithCleaning.compareTo(BigDecimal.ZERO) > 0) {
                // Platform fee amount = percentage of (base price + cleaning fee)
                // Round up to 1 decimal place (no cents) - always round up
                platformFeeAmount = basePriceWithCleaning.multiply(PLATFORM_FEE_PERCENTAGE)
                        .divide(BigDecimal.valueOf(100), 1, java.math.RoundingMode.CEILING);
                
                // Platform fixed fee is constant per transaction (only for paid reservations)
                // Round up to 1 decimal place (no cents) - always round up
                platformFixedFeeAmount = PLATFORM_FIXED_FEE.setScale(1, java.math.RoundingMode.CEILING);
            }
            
            // Calculate total: totalDaysPrice + cleaningFee + deposit + platformFees
            BigDecimal totalPrice = totalDaysPrice
                    .add(CLEANING_FEE)
                    .add(DEPOSIT)
                    .add(platformFeeAmount)
                    .add(platformFixedFeeAmount);
            
            return new PriceCalculationResult(
                    totalDaysPrice,
                    unitPrice,
                    RESERVATION_DURATION_DAYS,
                    subtotal,
                    CLEANING_FEE,
                    platformFeeAmount,
                    platformFixedFeeAmount,
                    DEPOSIT,
                    totalPrice
            );
        });

        // Mock reservationRepository.save
        when(reservationRepository.save(any(ReservationEntity.class))).thenAnswer(invocation -> {
            ReservationEntity reservation = invocation.getArgument(0);
            if (reservation.getId() == null) {
                reservation.setId(UUID.randomUUID());
            }
            return reservation;
        });

        // Mock unitsService.getPrimaryUnitForUser to return empty Mono (not required for GUEST_ROOM)
        when(unitsService.getPrimaryUnitForUser(any(UUID.class))).thenReturn(
                reactor.core.publisher.Mono.empty());

        // Mock auditService.logEvent (void method)
        org.mockito.Mockito.doNothing().when(auditService).logEvent(
                any(UUID.class), any(String.class), any(), any(), any(), any());

        // Mock spacesService.incrementUsedAnnualReservations (void method)
        org.mockito.Mockito.doNothing().when(spacesService).incrementUsedAnnualReservations(any(UUID.class));
    }

    @Test
    @DisplayName("ADMIN user should use owner price (lower price)")
    void testAdminUser_UsesOwnerPrice() {
        // Arrange
        UserEntity adminUser = createUser(UserType.ADMIN);
        
        // Expected price for owner: 50.00 * 3 nights = 150.00 base
        // + 20.00 cleaning = 170.00
        // Platform fee = 170 * 10% = 17.0 (CEILING, 1 decimal)
        // Platform fixed = 5.0 (CEILING, 1 decimal)
        // Total = 150 + 20 + 50 + 17.0 + 5.0 = 242.0
        BigDecimal expectedTotalPrice = new BigDecimal("242.00");

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, adminUser, startDate, endDate);

        // Assert
        assertNotNull(reservation);
        assertTrue(expectedTotalPrice.compareTo(reservation.getTotalPrice()) == 0, 
                "ADMIN user should get owner price (50 EUR/night). Expected: " + expectedTotalPrice + ", but was: " + reservation.getTotalPrice());
        
        // Verify that calculatePriceBreakdown was called with isOwner = true
        verify(spacesService).calculatePriceBreakdown(
                eq(guestRoomSpace.getId()),
                eq(startDate),
                eq(endDate),
                eq(true)); // isOwner should be true for ADMIN
    }

    @Test
    @DisplayName("OWNER user should use owner price (lower price)")
    void testOwnerUser_UsesOwnerPrice() {
        // Arrange
        UserEntity ownerUser = createUser(UserType.OWNER);
        
        // Expected price for owner: 50.00 * 3 nights = 150.00 base
        // + 20.00 cleaning = 170.00
        // Platform fee = 170 * 10% = 17.0 (CEILING, 1 decimal)
        // Platform fixed = 5.0 (CEILING, 1 decimal)
        // Total = 150 + 20 + 50 + 17.0 + 5.0 = 242.0
        BigDecimal expectedTotalPrice = new BigDecimal("242.00");

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, ownerUser, startDate, endDate);

        // Assert
        assertNotNull(reservation);
        assertTrue(expectedTotalPrice.compareTo(reservation.getTotalPrice()) == 0, 
                "OWNER user should get owner price (50 EUR/night). Expected: " + expectedTotalPrice + ", but was: " + reservation.getTotalPrice());
        
        // Verify that calculatePriceBreakdown was called with isOwner = true
        verify(spacesService).calculatePriceBreakdown(
                eq(guestRoomSpace.getId()),
                eq(startDate),
                eq(endDate),
                eq(true)); // isOwner should be true for OWNER
    }

    @Test
    @DisplayName("LANDLORD user should use owner price (lower price)")
    void testLandlordUser_UsesOwnerPrice() {
        // Arrange
        UserEntity landlordUser = createUser(UserType.LANDLORD);
        
        // Expected price for owner: 50.00 * 3 nights = 150.00 base
        // + 20.00 cleaning = 170.00
        // Platform fee = 170 * 10% = 17.0 (CEILING, 1 decimal)
        // Platform fixed = 5.0 (CEILING, 1 decimal)
        // Total = 150 + 20 + 50 + 17.0 + 5.0 = 242.0
        BigDecimal expectedTotalPrice = new BigDecimal("242.00");

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, landlordUser, startDate, endDate);

        // Assert
        assertNotNull(reservation);
        assertTrue(expectedTotalPrice.compareTo(reservation.getTotalPrice()) == 0, 
                "LANDLORD user should get owner price (50 EUR/night). Expected: " + expectedTotalPrice + ", but was: " + reservation.getTotalPrice());
        
        // Verify that calculatePriceBreakdown was called with isOwner = true
        verify(spacesService).calculatePriceBreakdown(
                eq(guestRoomSpace.getId()),
                eq(startDate),
                eq(endDate),
                eq(true)); // isOwner should be true for LANDLORD
    }

    @Test
    @DisplayName("TENANT user should use tenant price (higher price)")
    void testTenantUser_UsesTenantPrice() {
        // Arrange
        UserEntity tenantUser = createUser(UserType.TENANT);
        
        // Expected price for tenant: 100.00 * 3 nights = 300.00 base
        // + 20.00 cleaning = 320.00
        // Platform fee = 320 * 10% = 32.0 (CEILING, 1 decimal)
        // Platform fixed = 5.0 (CEILING, 1 decimal)
        // Total = 300 + 20 + 50 + 32.0 + 5.0 = 407.0
        BigDecimal expectedTotalPrice = new BigDecimal("407.00");

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Assert
        assertNotNull(reservation);
        assertTrue(expectedTotalPrice.compareTo(reservation.getTotalPrice()) == 0, 
                "TENANT user should get tenant price (100 EUR/night). Expected: " + expectedTotalPrice + ", but was: " + reservation.getTotalPrice());
        
        // Verify that calculatePriceBreakdown was called with isOwner = false
        verify(spacesService).calculatePriceBreakdown(
                eq(guestRoomSpace.getId()),
                eq(startDate),
                eq(endDate),
                eq(false)); // isOwner should be false for TENANT
    }

    @Test
    @DisplayName("SYNDIC user should use tenant price (higher price)")
    void testSyndicUser_UsesTenantPrice() {
        // Arrange
        UserEntity syndicUser = createUser(UserType.SYNDIC);
        
        // Expected price for tenant: 100.00 * 3 nights = 300.00 base
        // + 20.00 cleaning = 320.00
        // Platform fee = 320 * 10% = 32.0 (CEILING, 1 decimal)
        // Platform fixed = 5.0 (CEILING, 1 decimal)
        // Total = 300 + 20 + 50 + 32.0 + 5.0 = 407.0
        BigDecimal expectedTotalPrice = new BigDecimal("407.00");

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, syndicUser, startDate, endDate);

        // Assert
        assertNotNull(reservation);
        assertTrue(expectedTotalPrice.compareTo(reservation.getTotalPrice()) == 0, 
                "SYNDIC user should get tenant price (100 EUR/night). Expected: " + expectedTotalPrice + ", but was: " + reservation.getTotalPrice());
        
        verify(spacesService).calculatePriceBreakdown(
                eq(guestRoomSpace.getId()),
                eq(startDate),
                eq(endDate),
                eq(false)); // isOwner should be false for SYNDIC
    }

    @Test
    @DisplayName("EXTERNAL user should use tenant price (higher price)")
    void testExternalUser_UsesTenantPrice() {
        // Arrange
        UserEntity externalUser = createUser(UserType.EXTERNAL);
        BigDecimal expectedTotalPrice = new BigDecimal("407.00");

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, externalUser, startDate, endDate);

        // Assert
        assertNotNull(reservation);
        assertTrue(expectedTotalPrice.compareTo(reservation.getTotalPrice()) == 0, 
                "EXTERNAL user should get tenant price (100 EUR/night). Expected: " + expectedTotalPrice + ", but was: " + reservation.getTotalPrice());
        
        verify(spacesService).calculatePriceBreakdown(
                eq(guestRoomSpace.getId()),
                eq(startDate),
                eq(endDate),
                eq(false));
    }

    @Test
    @DisplayName("CONTRACTOR user should use tenant price (higher price)")
    void testContractorUser_UsesTenantPrice() {
        // Arrange
        UserEntity contractorUser = createUser(UserType.CONTRACTOR);
        BigDecimal expectedTotalPrice = new BigDecimal("407.00");

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, contractorUser, startDate, endDate);

        // Assert
        assertNotNull(reservation);
        assertTrue(expectedTotalPrice.compareTo(reservation.getTotalPrice()) == 0, 
                "CONTRACTOR user should get tenant price (100 EUR/night). Expected: " + expectedTotalPrice + ", but was: " + reservation.getTotalPrice());
        
        verify(spacesService).calculatePriceBreakdown(
                eq(guestRoomSpace.getId()),
                eq(startDate),
                eq(endDate),
                eq(false));
    }

    @Test
    @DisplayName("COMMERCIAL_PROPERTY_OWNER user should use tenant price (higher price)")
    void testCommercialPropertyOwnerUser_UsesTenantPrice() {
        // Arrange
        UserEntity commercialPropertyOwnerUser = createUser(UserType.COMMERCIAL_PROPERTY_OWNER);
        BigDecimal expectedTotalPrice = new BigDecimal("407.00");

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, commercialPropertyOwnerUser, startDate, endDate);

        // Assert
        assertNotNull(reservation);
        assertTrue(expectedTotalPrice.compareTo(reservation.getTotalPrice()) == 0, 
                "COMMERCIAL_PROPERTY_OWNER user should get tenant price (100 EUR/night). Expected: " + expectedTotalPrice + ", but was: " + reservation.getTotalPrice());
        
        verify(spacesService).calculatePriceBreakdown(
                eq(guestRoomSpace.getId()),
                eq(startDate),
                eq(endDate),
                eq(false));
    }

    @Test
    @DisplayName("GUEST user should use tenant price (higher price)")
    void testGuestUser_UsesTenantPrice() {
        // Arrange
        UserEntity guestUser = createUser(UserType.GUEST);
        BigDecimal expectedTotalPrice = new BigDecimal("407.00");

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, guestUser, startDate, endDate);

        // Assert
        assertNotNull(reservation);
        assertTrue(expectedTotalPrice.compareTo(reservation.getTotalPrice()) == 0, 
                "GUEST user should get tenant price (100 EUR/night). Expected: " + expectedTotalPrice + ", but was: " + reservation.getTotalPrice());
        
        verify(spacesService).calculatePriceBreakdown(
                eq(guestRoomSpace.getId()),
                eq(startDate),
                eq(endDate),
                eq(false));
    }

    @Test
    @DisplayName("PROPERTY_MANAGEMENT user should use tenant price (higher price)")
    void testPropertyManagementUser_UsesTenantPrice() {
        // Arrange
        UserEntity propertyManagementUser = createUser(UserType.PROPERTY_MANAGEMENT);
        BigDecimal expectedTotalPrice = new BigDecimal("407.00");

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, propertyManagementUser, startDate, endDate);

        // Assert
        assertNotNull(reservation);
        assertTrue(expectedTotalPrice.compareTo(reservation.getTotalPrice()) == 0, 
                "PROPERTY_MANAGEMENT user should get tenant price (100 EUR/night). Expected: " + expectedTotalPrice + ", but was: " + reservation.getTotalPrice());
        
        verify(spacesService).calculatePriceBreakdown(
                eq(guestRoomSpace.getId()),
                eq(startDate),
                eq(endDate),
                eq(false));
    }

    @Test
    @DisplayName("User with null type should use tenant price (higher price)")
    void testNullUserType_UsesTenantPrice() {
        // Arrange
        UserEntity userWithNullType = createUser(null);
        BigDecimal expectedTotalPrice = new BigDecimal("407.00");

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, userWithNullType, startDate, endDate);

        // Assert
        assertNotNull(reservation);
        assertTrue(expectedTotalPrice.compareTo(reservation.getTotalPrice()) == 0, 
                "User with null type should get tenant price (100 EUR/night). Expected: " + expectedTotalPrice + ", but was: " + reservation.getTotalPrice());
        
        verify(spacesService).calculatePriceBreakdown(
                eq(guestRoomSpace.getId()),
                eq(startDate),
                eq(endDate),
                eq(false));
    }

    /**
     * Helper method to create a UserEntity with the specified type
     */
    private UserEntity createUser(UserType userType) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("test@" + (userType != null ? userType.name().toLowerCase() : "null") + ".com");
        user.setUsername("test-" + (userType != null ? userType.name().toLowerCase() : "null"));
        user.setFirstName("Test");
        user.setLastName("User");
        user.setType(userType);
        return user;
    }
}

