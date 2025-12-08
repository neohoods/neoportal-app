package com.neohoods.portal.platform.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.exceptions.CodedException;
import com.neohoods.portal.platform.services.matrix.space.MatrixMessageService;
import com.neohoods.portal.platform.services.matrix.space.MatrixRoomService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for sending exception notifications to Matrix IT room
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = { "neohoods.portal.matrix.enabled" }, havingValue = "true", matchIfMissing = false)
public class MatrixExceptionNotificationService {

    private final MatrixMessageService matrixMessageService;
    private final MatrixRoomService matrixRoomService;

    @Value("${neohoods.portal.matrix.space-id}")
    private String spaceId;

    @Value("${neohoods.portal.matrix.exception-notification.enabled:true}")
    private boolean enabled;

    @Value("${neohoods.portal.matrix.exception-notification.grafana-url:}")
    private String grafanaUrl;

    @Value("${neohoods.portal.matrix.exception-notification.app-name:portal-neohoods-dev-backend}")
    private String appName;

    private static final String IT_ROOM_NAME = "IT";

    /**
     * Sends an exception notification to the IT room
     * 
     * @param exception The exception that occurred
     * @param traceId   The trace ID for the request
     */
    public void notifyException(Exception exception, String traceId) {
        if (!enabled) {
            log.debug("Exception notification is disabled");
            return;
        }

        try {
            // Get IT room ID
            Optional<String> itRoomIdOpt = matrixRoomService.getRoomIdByName(IT_ROOM_NAME, spaceId);
            if (itRoomIdOpt.isEmpty()) {
                log.warn("Cannot send exception notification: IT room not found in space {}", spaceId);
                return;
            }

            String itRoomId = itRoomIdOpt.get();

            // Build error message
            String message = buildErrorMessage(exception, traceId);

            // Send message
            boolean sent = matrixMessageService.sendMessage(itRoomId, message);
            if (sent) {
                log.info("Sent exception notification to IT room for traceId: {}", traceId);
            } else {
                log.error("Failed to send exception notification to IT room for traceId: {}", traceId);
            }
        } catch (Exception e) {
            // Don't let notification errors break the exception handling
            log.error("Error while sending exception notification to IT room", e);
        }
    }

    /**
     * Builds the error message with details and Grafana link
     */
    private String buildErrorMessage(Exception exception, String traceId) {
        StringBuilder message = new StringBuilder();
        message.append("🚨 **Exception dans le backend**\n\n");

        // Exception details
        message.append("**Type:** `").append(exception.getClass().getSimpleName()).append("`\n");
        message.append("**Message:** ").append(exception.getMessage() != null ? exception.getMessage() : "N/A")
                .append("\n");

        // Trace ID
        if (traceId != null && !traceId.isEmpty()) {
            message.append("**Trace ID:** `").append(traceId).append("`\n");
        }

        // CodedException specific details
        if (exception instanceof CodedException) {
            CodedException codedEx = (CodedException) exception;
            message.append("**Code d'erreur:** `").append(codedEx.getCode()).append("`\n");
            if (codedEx.getVariables() != null && !codedEx.getVariables().isEmpty()) {
                message.append("**Variables:** ").append(codedEx.getVariables()).append("\n");
            }
        }

        // Stack trace (first few lines)
        StackTraceElement[] stackTrace = exception.getStackTrace();
        if (stackTrace != null && stackTrace.length > 0) {
            message.append("\n**Stack trace (premiers éléments):**\n");
            message.append("```\n");
            int maxLines = Math.min(5, stackTrace.length);
            for (int i = 0; i < maxLines; i++) {
                message.append(stackTrace[i].toString()).append("\n");
            }
            message.append("```\n");
        }

        // Grafana link
        if (grafanaUrl != null && !grafanaUrl.isEmpty()
                && appName != null && !appName.isEmpty()
                && traceId != null && !traceId.isEmpty()) {
            String grafanaLink = buildGrafanaLink(traceId);
            if (grafanaLink != null) {
                message.append("\n**🔍 [Voir dans Grafana](").append(grafanaLink).append(")**\n");
            }
        }

        return message.toString();
    }

    /**
     * Builds a Grafana explore link with filter for trace ID
     * Uses conversation_trace_id if available, otherwise traceId
     * Filters on backend app logs: {app="portal-neohoods-dev-backend",
     * conversation_trace_id="..."}
     */
    private String buildGrafanaLink(String traceId) {
        try {
            // Try to get conversation_trace_id from MDC first
            String conversationTraceId = MDC.get("conversationTraceId");
            String filterValue = conversationTraceId != null && !conversationTraceId.isEmpty()
                    ? conversationTraceId
                    : traceId;

            // Build Loki query filter for backend logs
            // Format: {app="portal-neohoods-dev-backend", conversation_trace_id="..."}
            // or {app="portal-neohoods-dev-backend", traceId="..."}
            String appFilter = "{app=\"" + escapeLogQL(appName) + "\"";
            String traceFilter;
            if (conversationTraceId != null && !conversationTraceId.isEmpty()) {
                traceFilter = ", conversation_trace_id=\"" + escapeLogQL(filterValue) + "\"}";
            } else {
                traceFilter = ", traceId=\"" + escapeLogQL(filterValue) + "\"}";
            }
            String logqlQuery = appFilter + traceFilter;

            // Build Grafana explore URL
            // Base URL from config
            String baseUrl = grafanaUrl.endsWith("/")
                    ? grafanaUrl.substring(0, grafanaUrl.length() - 1)
                    : grafanaUrl;

            // Build the explore query
            // Format:
            // /explore?orgId=1&left={"datasource":"P982945308D3682D1","queries":[{"refId":"A","datasource":{"type":"loki","uid":"P982945308D3682D1"},"expr":"{conversation_trace_id=\"...\"}"}],"range":{"from":"now-1h","to":"now"}}
            // Note: The LogQL query string in JSON needs escaped quotes and backslashes
            // LogQL: {conversation_trace_id="abc"} -> JSON: "expr":
            // "{conversation_trace_id=\\\"abc\\\"}"
            String jsonEscapedLogQL = logqlQuery
                    .replace("\\", "\\\\") // Escape backslashes first
                    .replace("\"", "\\\""); // Then escape quotes
            String exploreQuery = String.format(
                    "/explore?orgId=1&left=%s",
                    URLEncoder.encode(
                            String.format(
                                    "{\"datasource\":\"P982945308D3682D1\",\"queries\":[{\"refId\":\"A\",\"datasource\":{\"type\":\"loki\",\"uid\":\"P982945308D3682D1\"},\"expr\":\"%s\"}],\"range\":{\"from\":\"now-1h\",\"to\":\"now\"}}",
                                    jsonEscapedLogQL),
                            StandardCharsets.UTF_8));

            return baseUrl + exploreQuery;
        } catch (Exception e) {
            log.warn("Failed to build Grafana link for traceId: {}", traceId, e);
            return null;
        }
    }

    /**
     * Escapes special characters for LogQL query
     */
    private String escapeLogQL(String value) {
        if (value == null) {
            return "";
        }
        // Escape backslashes and quotes for LogQL
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
