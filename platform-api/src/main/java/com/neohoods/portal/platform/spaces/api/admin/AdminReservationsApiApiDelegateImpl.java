package com.neohoods.portal.platform.spaces.api.admin;

import java.util.List;
import java.util.UUID;

import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.admin.AdminReservationsApiApiDelegate;
import com.neohoods.portal.platform.model.ReservationAuditLog;
import com.neohoods.portal.platform.spaces.entities.ReservationAuditLogEntity;
import com.neohoods.portal.platform.spaces.services.ReservationAuditService;

import reactor.core.publisher.Mono;

@Service
public class AdminReservationsApiApiDelegateImpl implements AdminReservationsApiApiDelegate {

    @Autowired
    private ReservationAuditService auditService;

    @Override
    public Mono<ResponseEntity<List<ReservationAuditLog>>> getReservationAuditLogs(
            UUID reservationId, ServerWebExchange exchange) {

        try {
            List<ReservationAuditLogEntity> auditLogs = auditService.getAuditLogsForReservation(reservationId);

            List<ReservationAuditLog> responseLogs = auditLogs.stream()
                    .map(this::convertToResponse)
                    .toList();

            return Mono.just(ResponseEntity.ok(responseLogs));
        } catch (Exception e) {
            return Mono.just(ResponseEntity.internalServerError().build());
        }
    }

    private ReservationAuditLog convertToResponse(ReservationAuditLogEntity entity) {
        ReservationAuditLog log = new ReservationAuditLog();
        log.setId(entity.getId());
        log.setReservationId(entity.getReservationId());
        log.setEventType(ReservationAuditLog.EventTypeEnum.fromValue(entity.getEventType()));
        log.setOldValue(JsonNullable.of(entity.getOldValue()));
        log.setNewValue(JsonNullable.of(entity.getNewValue()));
        log.setLogMessage(entity.getLogMessage());
        log.setPerformedBy(entity.getPerformedBy());
        log.setCreatedAt(entity.getCreatedAt().atOffset(java.time.ZoneOffset.UTC));
        return log;
    }
}
