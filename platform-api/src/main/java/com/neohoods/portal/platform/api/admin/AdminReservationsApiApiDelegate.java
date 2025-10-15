package com.neohoods.portal.platform.api.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.model.ReservationAuditLog;

import reactor.core.publisher.Mono;

public interface AdminReservationsApiApiDelegate {

    /**
     * GET /admin/reservations/{reservationId}/audit-logs
     * Get audit logs for a specific reservation
     */
    @RequestMapping(method = RequestMethod.GET, value = "/admin/reservations/{reservationId}/audit-logs", produces = {
            "application/json" })
    default Mono<ResponseEntity<List<ReservationAuditLog>>> getReservationAuditLogs(
            @PathVariable("reservationId") UUID reservationId,
            ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.notFound().build());
    }
}
