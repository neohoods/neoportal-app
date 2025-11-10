package com.neohoods.portal.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.entities.UnitInvitationEntity;
import com.neohoods.portal.platform.entities.UnitInvitationStatus;

@Repository
public interface UnitInvitationRepository extends JpaRepository<UnitInvitationEntity, UUID> {

    List<UnitInvitationEntity> findByUnitId(UUID unitId);

    List<UnitInvitationEntity> findByInvitedUserId(UUID invitedUserId);

    List<UnitInvitationEntity> findByInvitedEmail(String invitedEmail);

    List<UnitInvitationEntity> findByInvitedUserIdAndStatus(UUID invitedUserId, UnitInvitationStatus status);

    List<UnitInvitationEntity> findByInvitedEmailAndStatus(String invitedEmail, UnitInvitationStatus status);

    Optional<UnitInvitationEntity> findByIdAndStatus(UUID id, UnitInvitationStatus status);
}





