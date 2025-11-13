package com.neohoods.portal.platform.spaces.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.BaseIntegrationTest;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.spaces.entities.ReservationAuditLogEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationAuditLogRepository;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

/**
 * Integration tests for reservation audit logging.
 * 
 * Verifies that all operations on reservations are properly logged in the audit
 * trail.
 */
@Transactional
public class ReservationAuditTest extends BaseIntegrationTest {

    @Autowired
    private ReservationsService reservationsService;

    @Autowired
    private ReservationAuditLogRepository auditRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private com.neohoods.portal.platform.services.UnitsService unitsService;

    private SpaceEntity guestRoomSpace;
    private UserEntity tenantUser;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        // Get guest room space
        guestRoomSpace = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.GUEST_ROOM)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No guest room space found"));

        // Get or create tenant user
        Iterable<UserEntity> allUsers = usersRepository.findAll();
        for (UserEntity user : allUsers) {
            if (user.getType() == UserType.TENANT) {
                tenantUser = user;
                break;
            }
        }

        if (tenantUser == null) {
            tenantUser = new UserEntity();
            tenantUser.setEmail("test-tenant-audit@neohoods.com");
            tenantUser.setUsername("test-tenant-audit");
            tenantUser.setType(UserType.TENANT);
            tenantUser.setStatus(com.neohoods.portal.platform.entities.UserStatus.ACTIVE);
            tenantUser = usersRepository.save(tenantUser);
        }

        // Create unit for tenant user (required for GUEST_ROOM reservations)
        if (unitsService.getUserUnits(tenantUser.getId()).count().block() == 0) {
            unitsService.createUnit("Test Unit " + tenantUser.getId(), null, tenantUser.getId()).block();
        }

        // Ensure tenant user has a primary unit set
        tenantUser = usersRepository.findById(tenantUser.getId()).orElse(tenantUser);
        if (tenantUser.getPrimaryUnit() == null) {
            var units = unitsService.getUserUnits(tenantUser.getId()).collectList().block();
            if (units != null && !units.isEmpty() && units.get(0).getId() != null) {
                unitsService.setPrimaryUnitForUser(tenantUser.getId(), units.get(0).getId(), null).block();
                tenantUser = usersRepository.findById(tenantUser.getId()).orElse(tenantUser);
            }
        }
    }

    @Test
    @DisplayName("Audit log created when reservation is created")
    public void testAuditLog_CreatedOnReservationCreation() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Assert
        assertNotNull(reservation.getId());

        // Get audit logs
        List<ReservationAuditLogEntity> logs = auditRepository
                .findByReservationIdOrderByCreatedAtDesc(reservation.getId());

        // Verify audit log was created
        assertTrue(logs.size() >= 1, "Should have at least 1 audit log entry");

        ReservationAuditLogEntity log = logs.get(0);
        assertEquals(reservation.getId(), log.getReservationId());
        assertNotNull(log.getCreatedAt());
        assertNotNull(log.getPerformedBy());
        assertTrue(log.getPerformedBy().length() > 0);
        assertNotNull(log.getEventType());
        assertTrue(log.getEventType().length() > 0);
    }

    @Test
    @DisplayName("Audit log for confirmation")
    public void testAuditLog_StatusChangeOnConfirmation() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Act
        reservationsService.confirmReservation(
                reservation.getId(), "pi_audit_confirm", "cs_audit_confirm");

        // Assert
        List<ReservationAuditLogEntity> logs = auditRepository
                .findByReservationIdOrderByCreatedAtDesc(reservation.getId());

        // Should have at least 2 logs: creation + confirmation
        assertTrue(logs.size() >= 2, "Should have at least 2 audit log entries");

        // Verify all logs have required fields
        for (ReservationAuditLogEntity log : logs) {
            assertNotNull(log.getCreatedAt());
            assertNotNull(log.getPerformedBy());
            assertTrue(log.getPerformedBy().length() > 0);
            assertNotNull(log.getEventType());
            assertTrue(log.getEventType().length() > 0);
        }
    }

    @Test
    @DisplayName("Audit log for cancellation")
    public void testAuditLog_CancellationLogged() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Act
        reservationsService.cancelReservation(reservation.getId(), "Change of plans", tenantUser.getEmail());

        // Assert
        List<ReservationAuditLogEntity> logs = auditRepository
                .findByReservationIdOrderByCreatedAtDesc(reservation.getId());

        // Should have cancellation log
        assertTrue(logs.size() >= 2, "Should have at least 2 audit log entries (creation + cancellation)");
    }

    @Test
    @DisplayName("Multiple operations create multiple logs")
    public void testAuditLog_MultipleOperationsCreateMultipleLogs() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        // Act - Create, confirm, then cancel
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        reservationsService.confirmReservation(reservation.getId(), "pi_test", "cs_test");

        reservationsService.cancelReservation(reservation.getId(), "Change of mind", tenantUser.getEmail());

        // Assert - Should have multiple logs
        List<ReservationAuditLogEntity> logs = auditRepository
                .findByReservationIdOrderByCreatedAtDesc(reservation.getId());

        assertTrue(logs.size() >= 3, "Should have at least 3 audit log entries for create, confirm, cancel");

        // Verify chronological order (DESC so newest first)
        for (int i = 0; i < logs.size() - 1; i++) {
            assertTrue(logs.get(i).getCreatedAt().isAfter(logs.get(i + 1).getCreatedAt())
                    || logs.get(i).getCreatedAt().equals(logs.get(i + 1).getCreatedAt()),
                    "Audit logs should be in reverse chronological order (newest first)");
        }
    }

    @Test
    @DisplayName("Audit log timestamps are valid")
    public void testAuditLog_TimestampsValid() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Assert
        List<ReservationAuditLogEntity> logs = auditRepository
                .findByReservationIdOrderByCreatedAtDesc(reservation.getId());

        for (ReservationAuditLogEntity log : logs) {
            assertNotNull(log.getCreatedAt(), "CreatedAt should not be null");
            // Timestamp should be in the past (or very recent)
            assertTrue(log.getCreatedAt().isBefore(java.time.LocalDateTime.now().plusSeconds(5)));
        }
    }

    @Test
    @DisplayName("Audit log count per reservation")
    public void testAuditLog_CountPerReservation() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        long countAfterCreation = auditRepository.countByReservationId(reservation.getId());
        assertTrue(countAfterCreation >= 1, "Should have at least 1 audit log after creation");

        // Act - Confirm
        reservationsService.confirmReservation(reservation.getId(), "pi_test", "cs_test");

        // Assert
        long countAfterConfirmation = auditRepository.countByReservationId(reservation.getId());
        assertTrue(countAfterConfirmation >= 2, "Should have at least 2 audit logs after confirmation");
        assertTrue(countAfterConfirmation > countAfterCreation, "Count should increase after confirmation");
    }
}
