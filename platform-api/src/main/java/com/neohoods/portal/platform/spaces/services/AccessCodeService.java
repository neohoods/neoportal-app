package com.neohoods.portal.platform.spaces.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.exceptions.ResourceNotFoundException;
import com.neohoods.portal.platform.spaces.entities.AccessCodeEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.repositories.AccessCodeRepository;
import com.neohoods.portal.platform.spaces.repositories.DigitalLockRepository;

@Service
@Transactional
public class AccessCodeService {

    private static final Logger logger = LoggerFactory.getLogger(AccessCodeService.class);

    @Autowired
    private AccessCodeRepository accessCodeRepository;

    @Autowired
    private DigitalLockRepository digitalLockRepository;

    @Autowired
    private TTlockRemoteAPIService ttlockRemoteAPIService;

    @Autowired
    @Lazy
    private ReservationsService reservationsService;

    @Autowired
    private DigitalLockService digitalLockService;

    @Autowired
    private ReservationAuditService auditService;

    private static final String CODE_CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 6;
    private static final Random RANDOM = new Random();

    /**
     * Generate a unique access code
     */
    private String generateUniqueCode() {
        String code;
        do {
            code = generateCode();
        } while (accessCodeRepository.existsByCode(code));
        return code;
    }

    /**
     * Generate a random access code
     */
    private String generateCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_CHARACTERS.charAt(RANDOM.nextInt(CODE_CHARACTERS.length())));
        }
        return code.toString();
    }

    /**
     * Generate access code for a reservation
     */
    public AccessCodeEntity generateAccessCode(ReservationEntity reservation) {
        // Check if access code already exists
        Optional<AccessCodeEntity> existingCode = accessCodeRepository.findByReservation(reservation);
        if (existingCode.isPresent()) {
            return existingCode.get();
        }

        // Generate new access code through DigitalLockService
        String code = digitalLockService.generateAccessCode(
                reservation.getSpace().getDigitalLockId(),
                24, // 24 hours duration
                "Reservation access code");

        // Calculate expiration time - ensure it's at least 1 hour after creation
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reservationEndTime = reservation.getEndDate().atTime(23, 59, 59);
        LocalDateTime expiresAt = reservationEndTime.isAfter(now.plusHours(1))
                ? reservationEndTime
                : now.plusHours(1); // At least 1 hour from now

        AccessCodeEntity accessCode = new AccessCodeEntity(reservation, code, expiresAt);
        accessCode.setIsActive(true);

        // Save the access code first
        accessCode = accessCodeRepository.save(accessCode);

        // Log audit event
        auditService.logCodeGenerated(reservation.getId(), code, "system");

        // The DigitalLockService handles the integration with the appropriate digital
        // lock service
        // (TTLock, Nuki, etc.) based on the digital lock type
        logger.info("Generated access code {} for reservation {} through DigitalLockService", code,
                reservation.getId());

        return accessCode;
    }

    /**
     * Regenerate access code for a reservation
     */
    public AccessCodeEntity regenerateAccessCode(UUID reservationId, String regeneratedBy) {
        ReservationEntity reservation = reservationsService.getReservationById(reservationId);

        // Deactivate existing access code if it exists
        Optional<AccessCodeEntity> existingCode = accessCodeRepository.findByReservation(reservation);
        String oldCode = null;
        if (existingCode.isPresent()) {
            AccessCodeEntity oldCodeEntity = existingCode.get();
            oldCode = oldCodeEntity.getCode();
            oldCodeEntity.setIsActive(false);
            accessCodeRepository.save(oldCodeEntity);
        }

        // Generate new access code
        String code = generateUniqueCode();

        // Calculate expiration time - ensure it's at least 1 hour after creation
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reservationEndTime = reservation.getEndDate().atTime(23, 59, 59);
        LocalDateTime expiresAt = reservationEndTime.isAfter(now.plusHours(1))
                ? reservationEndTime
                : now.plusHours(1); // At least 1 hour from now

        AccessCodeEntity accessCode = new AccessCodeEntity(reservation, code, expiresAt);
        accessCode.setIsActive(true);
        accessCode.setRegeneratedAt(LocalDateTime.now());
        accessCode.setRegeneratedBy(regeneratedBy);

        // Save the access code first
        accessCode = accessCodeRepository.save(accessCode);

        // Update the code through DigitalLockService
        // The DigitalLockService handles the integration with the appropriate digital
        // lock service
        // (TTLock, Nuki, etc.) based on the digital lock type
        try {
            String updatedCode = digitalLockService.generateAccessCode(
                    reservation.getSpace().getDigitalLockId(),
                    24, // 24 hours duration
                    "Regenerated reservation access code");
            accessCode.setCode(updatedCode);
            accessCode = accessCodeRepository.save(accessCode);

            // Log audit event
            auditService.logCodeRegenerated(reservation.getId(), oldCode, updatedCode, regeneratedBy);

            logger.info("Regenerated access code {} for reservation {} through DigitalLockService",
                    updatedCode, reservation.getId());
        } catch (Exception e) {
            logger.error("Failed to regenerate access code for reservation {}: {}",
                    reservation.getId(), e.getMessage(), e);
            throw new com.neohoods.portal.platform.exceptions.CodedException(
                    com.neohoods.portal.platform.exceptions.CodedError.EXTERNAL_SERVICE_ERROR.getCode(),
                    "Failed to regenerate access code through digital lock service: " + e.getMessage());
        }

        return accessCode;
    }

    /**
     * Get access code by reservation
     */
    @Transactional(readOnly = true)
    public Optional<AccessCodeEntity> getAccessCodeByReservation(ReservationEntity reservation) {
        return accessCodeRepository.findByReservationWithDetails(reservation);
    }

    /**
     * Get access code by code string
     */
    @Transactional(readOnly = true)
    public Optional<AccessCodeEntity> getAccessCodeByCode(String code) {
        return accessCodeRepository.findByCode(code);
    }

    /**
     * Validate access code
     */
    @Transactional(readOnly = true)
    public boolean validateAccessCode(String code) {
        Optional<AccessCodeEntity> accessCode = accessCodeRepository.findByCode(code);

        if (accessCode.isEmpty()) {
            return false;
        }

        AccessCodeEntity codeEntity = accessCode.get();

        // Check if code is active
        if (!codeEntity.getIsActive()) {
            return false;
        }

        // Check if code has expired
        if (codeEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }

        // Check if reservation is active
        if (codeEntity.getReservation().getStatus() != ReservationStatusForEntity.ACTIVE) {
            return false;
        }

        return true;
    }

    /**
     * Use access code (mark as used)
     */
    public AccessCodeEntity useAccessCode(String code) {
        AccessCodeEntity accessCode = accessCodeRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Access code not found"));

        if (!validateAccessCode(code)) {
            throw new CodedErrorException(CodedError.ACCESS_CODE_NOT_VALID, "code", code);
        }

        accessCode.setUsedAt(LocalDateTime.now());
        return accessCodeRepository.save(accessCode);
    }

    /**
     * Deactivate access code
     */
    public AccessCodeEntity deactivateAccessCode(AccessCodeEntity accessCode) {
        accessCode.setIsActive(false);

        // Delete the code from TTlock device if it exists
        if (accessCode.getDigitalLockCodeId() != null) {
            try {
                ttlockRemoteAPIService.deleteTemporaryCode(accessCode.getDigitalLockCodeId());
            } catch (Exception e) {
                logger.error("Failed to delete TTlock code {}: {}",
                        accessCode.getDigitalLockCodeId(), e.getMessage(), e);
                // Continue without TTlock integration for now
                // In a production environment, this should throw a proper exception
            }
        }

        return accessCodeRepository.save(accessCode);
    }

    /**
     * Deactivate access code by ID
     */
    public void deactivateAccessCodeById(UUID accessCodeId) {
        AccessCodeEntity accessCode = accessCodeRepository.findById(accessCodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Access code not found with id: " + accessCodeId));

        deactivateAccessCode(accessCode);
    }

    /**
     * Get all active access codes
     */
    @Transactional(readOnly = true)
    public List<AccessCodeEntity> getAllActiveAccessCodes() {
        return accessCodeRepository.findByIsActiveTrue();
    }

    /**
     * Get expired access codes
     */
    @Transactional(readOnly = true)
    public List<AccessCodeEntity> getExpiredAccessCodes() {
        return accessCodeRepository.findExpiredAccessCodes(LocalDateTime.now());
    }

    /**
     * Get access codes expiring soon
     */
    @Transactional(readOnly = true)
    public List<AccessCodeEntity> getAccessCodesExpiringSoon(int hours) {
        LocalDateTime currentTime = LocalDateTime.now();
        LocalDateTime expiryTime = currentTime.plusHours(hours);
        return accessCodeRepository.findAccessCodesExpiringSoon(currentTime, expiryTime);
    }

    /**
     * Process expired access codes (deactivate them)
     */
    public void processExpiredAccessCodes() {
        List<AccessCodeEntity> expiredCodes = accessCodeRepository.findAccessCodesToDeactivate(LocalDateTime.now());

        for (AccessCodeEntity accessCode : expiredCodes) {
            deactivateAccessCode(accessCode);
        }
    }

    /**
     * Get access codes by digital lock ID
     */
    @Transactional(readOnly = true)
    public List<AccessCodeEntity> getAccessCodesByDigitalLockId(UUID digitalLockId) {
        return accessCodeRepository.findByDigitalLockId(digitalLockId);
    }

    /**
     * Get access code by digital lock code ID
     */
    @Transactional(readOnly = true)
    public Optional<AccessCodeEntity> getAccessCodeByDigitalLockCodeId(String digitalLockCodeId) {
        return accessCodeRepository.findByDigitalLockCodeId(digitalLockCodeId);
    }

    /**
     * Update digital lock information for access code
     */
    public AccessCodeEntity updateDigitalLockInfo(UUID accessCodeId, UUID digitalLockId, String digitalLockCodeId) {
        AccessCodeEntity accessCode = accessCodeRepository.findById(accessCodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Access code not found with id: " + accessCodeId));
        accessCode.setDigitalLockId(digitalLockId);
        accessCode.setDigitalLockCodeId(digitalLockCodeId);

        return accessCodeRepository.save(accessCode);
    }

    /**
     * Get all access codes with reservation details
     */
    @Transactional(readOnly = true)
    public List<AccessCodeEntity> getAllAccessCodesWithDetails() {
        return accessCodeRepository.findAllWithReservationDetails();
    }
}
