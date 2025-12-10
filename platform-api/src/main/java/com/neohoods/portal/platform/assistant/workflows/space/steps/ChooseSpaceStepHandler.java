package com.neohoods.portal.platform.assistant.workflows.space.steps;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.model.ReservationPeriod;
import com.neohoods.portal.platform.assistant.model.SpaceStep;
import com.neohoods.portal.platform.assistant.model.SpaceStepResponse;
import com.neohoods.portal.platform.assistant.mcp.MatrixAssistantMCPAdapter;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPModels.MCPTool;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;
import com.neohoods.portal.platform.spaces.entities.SpaceStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.services.SpacesService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Handler for CHOOSE_SPACE step.
 * Identifies the spaceId UUID from user's choice and may also extract period.
 */
@Component
@Slf4j
public class ChooseSpaceStepHandler extends BaseSpaceStepHandler {

    @Autowired
    private MatrixAssistantAgentContextService agentContextService;

    @Autowired
    private SpacesService spacesService;

    @Override
    public SpaceStep getStep() {
        return SpaceStep.CHOOSE_SPACE;
    }

    @Override
    public Mono<SpaceStepResponse> handle(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {

        log.info("🔄 CHOOSE_SPACE handler processing message: {}",
                userMessage.substring(0, Math.min(50, userMessage.length())));

        // Heuristic: try to resolve spaceId directly from user message using
        // list_spaces
        try {
            String heuristicSpaceId = resolveSpaceIdFromUserMessage(userMessage, authContext);
            if (heuristicSpaceId != null && context != null) {
                context.updateWorkflowState("spaceId", heuristicSpaceId);
                log.info("✅ CHOOSE_SPACE heuristic resolved spaceId={}", heuristicSpaceId);
                Locale locale = getLocaleFromContext(context);
                String response = messageSource.getMessage("matrix.reservation.chooseSpace.spaceIdentified", null,
                        locale);
                return Mono.just(SpaceStepResponse.builder()
                        .status(SpaceStepResponse.StepStatus.SWITCH_STEP)
                        .nextStep(SpaceStep.CHOOSE_SPACE)
                        .spaceId(heuristicSpaceId)
                        .response(response)
                        .build());
            }
        } catch (Exception e) {
            log.warn("Heuristic space resolution failed: {}", e.getMessage());
        }

        // Build step-specific system prompt
        String systemPrompt = buildSystemPromptForStep(SpaceStep.CHOOSE_SPACE, context, authContext);

        // Extract space mapping (number -> UUID) from list_spaces for context
        java.util.Map<String, String> spaceMapping = extractSpaceMapping(authContext);

        // Inject a concise context summary to guide the LLM (avoid stale/long history)
        List<Map<String, Object>> enrichedHistory = new java.util.ArrayList<>();
        Map<String, Object> summaryMsg = new java.util.HashMap<>();
        summaryMsg.put("role", "system");
        StringBuilder contextContent = new StringBuilder();
        contextContent.append("Résumé contexte: l'utilisateur est en train de choisir une place de parking. ");
        contextContent.append("Message utilisateur actuel: \"").append(userMessage).append("\". ");
        if (spaceMapping != null && !spaceMapping.isEmpty()) {
            contextContent.append("Mapping espaces disponibles (numéro -> UUID): ");
            contextContent.append(spaceMapping.toString()).append(". ");
            contextContent.append(
                    "Quand l'utilisateur mentionne un numéro (ex: \"la place 23\"), utilise l'UUID correspondant depuis ce mapping. ");
        } else {
            contextContent.append(
                    "Tu dois impérativement appeler list_spaces pour obtenir l'UUID exact de la place mentionnée. ");
        }
        contextContent.append("Réponds en JSON strict avec le spaceId (UUID) rempli.");
        summaryMsg.put("content", contextContent.toString());
        enrichedHistory.add(summaryMsg);
        if (conversationHistory != null) {
            enrichedHistory.addAll(conversationHistory);
        }

        // Get filtered tools for this step
        List<MCPTool> allTools = mcpAdapter.listTools();
        List<Map<String, Object>> tools = filterToolsForStep(allTools, SpaceStep.CHOOSE_SPACE);

        // Call Mistral API with JSON response format
        return callMistralAPIWithJSONResponse(userMessage, enrichedHistory, systemPrompt, tools, authContext)
                .flatMap(stepResponse -> {
                    String roomId = authContext.getRoomId();
                    if (roomId == null) {
                        return Mono.just(stepResponse);
                    }

                    // Handle different statuses
                    if (stepResponse.isCanceled()) {
                        log.info("❌ CHOOSE_SPACE: User canceled, clearing context");
                        agentContextService.clearContext(roomId);
                        return Mono.just(stepResponse);
                    }

                    // CRITICAL: Store spaceId if present, regardless of status
                    // The LLM may return ASK_USER but still have identified the spaceId
                    if (stepResponse.hasSpaceId() && context != null) {
                        String finalSpaceId = stepResponse.getSpaceId();

                        // If spaceId is not a valid UUID, try to resolve it from availableSpaces map
                        if (!isValidUUID(finalSpaceId)) {
                            java.util.Map<String, String> availableSpaces = stepResponse.getAvailableSpaces();
                            if (availableSpaces != null && availableSpaces.containsKey(finalSpaceId)) {
                                String uuid = availableSpaces.get(finalSpaceId);
                                if (uuid != null && !uuid.isEmpty()) {
                                    log.info("🔧 CHOOSE_SPACE: Resolved spaceId from number {} to UUID {}",
                                            finalSpaceId, uuid);
                                    finalSpaceId = uuid;
                                }
                            }
                        }

                        // If user provided a number, ensure we pick the exact matching number if
                        // available
                        String requestedNumber = extractNumber(userMessage);
                        if (requestedNumber != null && !requestedNumber.isBlank()) {
                            // First try availableSpaces map if present
                            java.util.Map<String, String> availableSpaces = stepResponse.getAvailableSpaces();
                            String mappedId = null;
                            if (availableSpaces != null && availableSpaces.containsKey(requestedNumber)) {
                                mappedId = availableSpaces.get(requestedNumber);
                            }
                            // Fallback to resolveSpaceIdFromNumber if not in map
                            if (mappedId == null || mappedId.isEmpty()) {
                                mappedId = resolveSpaceIdFromNumber(requestedNumber, authContext);
                            }
                            if (mappedId != null && !mappedId.equals(finalSpaceId)) {
                                log.info(
                                        "🔧 CHOOSE_SPACE: Overriding spaceId from {} to {} based on requested number {}",
                                        finalSpaceId, mappedId, requestedNumber);
                                finalSpaceId = mappedId;
                            }
                        }

                        context.updateWorkflowState("spaceId", finalSpaceId);
                        log.info("✅ CHOOSE_SPACE: Stored spaceId={} (status={})", finalSpaceId,
                                stepResponse.getStatus());
                    }

                    if (stepResponse.isAskingUser()) {
                        // Need more conversation - format response using availableSpaces if present
                        log.info("🔄 CHOOSE_SPACE: Status=ASK_USER, continuing conversation");

                        // Get locale for formatting
                        Locale locale = getLocaleFromContext(context);

                        // If availableSpaces is not provided by LLM, build it from list_spaces result
                        SpaceStepResponse responseToFormat = stepResponse;
                        if (stepResponse.getAvailableSpaces() == null || stepResponse.getAvailableSpaces().isEmpty()) {
                            java.util.Map<String, String> extractedMapping = extractSpaceMapping(authContext);
                            if (extractedMapping != null && !extractedMapping.isEmpty()) {
                                log.info("📝 CHOOSE_SPACE: Building availableSpaces map from list_spaces ({} spaces)",
                                        extractedMapping.size());
                                responseToFormat = SpaceStepResponse.builder()
                                        .status(stepResponse.getStatus())
                                        .response(stepResponse.getResponse())
                                        .spaceId(stepResponse.getSpaceId())
                                        .period(stepResponse.getPeriod())
                                        .nextStep(stepResponse.getNextStep())
                                        .availableSpaces(extractedMapping)
                                        .build();
                            }
                        }

                        // Format response using availableSpaces map if provided
                        String formattedResponse = formatResponseWithAvailableSpaces(responseToFormat, authContext,
                                locale, context);

                        if (!formattedResponse.equals(responseToFormat.getResponse())
                                || responseToFormat != stepResponse) {
                            log.info("📝 CHOOSE_SPACE: Formatted response with available spaces list");
                            return Mono.just(SpaceStepResponse.builder()
                                    .status(responseToFormat.getStatus())
                                    .response(formattedResponse)
                                    .spaceId(responseToFormat.getSpaceId())
                                    .period(responseToFormat.getPeriod())
                                    .nextStep(responseToFormat.getNextStep())
                                    .availableSpaces(responseToFormat.getAvailableSpaces())
                                    .build());
                        }

                        return Mono.just(responseToFormat);
                    }

                    // Store spaceId and period in context if present in response
                    // The SpaceStateMachine will handle automatic progression if both are present
                    if (stepResponse.hasSpaceId() && context != null) {
                        context.updateWorkflowState("spaceId", stepResponse.getSpaceId());
                        log.info("✅ CHOOSE_SPACE: Stored spaceId={}", stepResponse.getSpaceId());
                    }

                    // Store period if present in response
                    if (stepResponse.hasCompletePeriod() && stepResponse.getPeriod() != null && context != null) {
                        // Store period as LocalDateTime objects (combining date and time)
                        try {
                            java.time.LocalDate startDate = java.time.LocalDate
                                    .parse(stepResponse.getPeriod().getStartDate());
                            java.time.LocalDate endDate = java.time.LocalDate
                                    .parse(stepResponse.getPeriod().getEndDate());

                            // Combine date and time into LocalDateTime
                            java.time.LocalTime startTime = stepResponse.getPeriod().getStartTime() != null
                                    ? java.time.LocalTime.parse(stepResponse.getPeriod().getStartTime())
                                    : java.time.LocalTime.MIN;
                            java.time.LocalTime endTime = stepResponse.getPeriod().getEndTime() != null
                                    ? java.time.LocalTime.parse(stepResponse.getPeriod().getEndTime())
                                    : java.time.LocalTime.MAX;

                            java.time.LocalDateTime startDateTime = java.time.LocalDateTime.of(startDate, startTime);
                            java.time.LocalDateTime endDateTime = java.time.LocalDateTime.of(endDate, endTime);

                            context.updateWorkflowState("startDateTime", startDateTime);
                            context.updateWorkflowState("endDateTime", endDateTime);

                            // Also keep date strings for backward compatibility and debug
                            context.updateWorkflowState("startDate", stepResponse.getPeriod().getStartDate());
                            context.updateWorkflowState("endDate", stepResponse.getPeriod().getEndDate());
                            if (stepResponse.getPeriod().getStartTime() != null) {
                                context.updateWorkflowState("startTime", stepResponse.getPeriod().getStartTime());
                            }
                            if (stepResponse.getPeriod().getEndTime() != null) {
                                context.updateWorkflowState("endTime", stepResponse.getPeriod().getEndTime());
                            }
                            log.info(
                                    "✅ CHOOSE_SPACE: Stored period as LocalDateTime (startDateTime={}, endDateTime={})",
                                    startDateTime, endDateTime);
                        } catch (Exception e) {
                            log.warn("Failed to parse period dates/times, storing as strings: {}", e.getMessage());
                            // Fallback to string storage
                            context.updateWorkflowState("startDate", stepResponse.getPeriod().getStartDate());
                            context.updateWorkflowState("endDate", stepResponse.getPeriod().getEndDate());
                            if (stepResponse.getPeriod().getStartTime() != null) {
                                context.updateWorkflowState("startTime", stepResponse.getPeriod().getStartTime());
                            }
                            if (stepResponse.getPeriod().getEndTime() != null) {
                                context.updateWorkflowState("endTime", stepResponse.getPeriod().getEndTime());
                            }
                        }
                    }

                    // Store spaceId and period in context if present in response
                    // The SpaceStateMachine will handle automatic progression if both are present
                    if (stepResponse.hasSpaceId() && context != null) {
                        context.updateWorkflowState("spaceId", stepResponse.getSpaceId());
                    }

                    if (stepResponse.hasCompletePeriod() && stepResponse.getPeriod() != null && context != null) {
                        // Store period as LocalDateTime objects (combining date and time)
                        try {
                            java.time.LocalDate startDate = java.time.LocalDate
                                    .parse(stepResponse.getPeriod().getStartDate());
                            java.time.LocalDate endDate = java.time.LocalDate
                                    .parse(stepResponse.getPeriod().getEndDate());

                            // Combine date and time into LocalDateTime
                            java.time.LocalTime startTime = stepResponse.getPeriod().getStartTime() != null
                                    ? java.time.LocalTime.parse(stepResponse.getPeriod().getStartTime())
                                    : java.time.LocalTime.MIN;
                            java.time.LocalTime endTime = stepResponse.getPeriod().getEndTime() != null
                                    ? java.time.LocalTime.parse(stepResponse.getPeriod().getEndTime())
                                    : java.time.LocalTime.MAX;

                            java.time.LocalDateTime startDateTime = java.time.LocalDateTime.of(startDate, startTime);
                            java.time.LocalDateTime endDateTime = java.time.LocalDateTime.of(endDate, endTime);

                            context.updateWorkflowState("startDateTime", startDateTime);
                            context.updateWorkflowState("endDateTime", endDateTime);

                            // Also keep date strings for backward compatibility and debug
                            context.updateWorkflowState("startDate", stepResponse.getPeriod().getStartDate());
                            context.updateWorkflowState("endDate", stepResponse.getPeriod().getEndDate());
                            if (stepResponse.getPeriod().getStartTime() != null) {
                                context.updateWorkflowState("startTime", stepResponse.getPeriod().getStartTime());
                            }
                            if (stepResponse.getPeriod().getEndTime() != null) {
                                context.updateWorkflowState("endTime", stepResponse.getPeriod().getEndTime());
                            }
                            log.info(
                                    "✅ CHOOSE_SPACE: Stored period as LocalDateTime (startDateTime={}, endDateTime={})",
                                    startDateTime, endDateTime);
                        } catch (Exception e) {
                            log.warn("Failed to parse period dates/times, storing as strings: {}", e.getMessage());
                            // Fallback to string storage
                            context.updateWorkflowState("startDate", stepResponse.getPeriod().getStartDate());
                            context.updateWorkflowState("endDate", stepResponse.getPeriod().getEndDate());
                            if (stepResponse.getPeriod().getStartTime() != null) {
                                context.updateWorkflowState("startTime", stepResponse.getPeriod().getStartTime());
                            }
                            if (stepResponse.getPeriod().getEndTime() != null) {
                                context.updateWorkflowState("endTime", stepResponse.getPeriod().getEndTime());
                            }
                        }
                    }

                    // Validate SWITCH_STEP/COMPLETED responses
                    if (stepResponse.isCompleted() || stepResponse.isSwitchingStep()) {
                        // Validate spaceId is present (required for COMPLETED/SWITCH_STEP)
                        if (!stepResponse.hasSpaceId()) {
                            log.error("❌ CHOOSE_SPACE: COMPLETED/SWITCH_STEP status but spaceId is missing!");
                            Locale locale = getLocaleFromContext(context);
                            String errorMessage = messageSource
                                    .getMessage("matrix.reservation.chooseSpace.spaceIdMissing", null, locale);
                            return Mono.just(SpaceStepResponse.builder()
                                    .status(SpaceStepResponse.StepStatus.ERROR)
                                    .response(errorMessage)
                                    .build());
                        }
                    }

                    return Mono.just(stepResponse);
                });
    }

    /**
     * Heuristic resolution of spaceId from user message by calling list_spaces and
     * matching number/name.
     */
    private String resolveSpaceIdFromUserMessage(String userMessage, MatrixAssistantAuthContext authContext) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        String candidate = userMessage.toLowerCase().replaceAll("[^a-z0-9 ]", " ").trim();
        String number = candidate.replaceAll("\\D+", "");
        Map<String, Object> listArgs = Map.of();
        var toolResult = mcpAdapter.callMCPToolDirect("list_spaces", listArgs, authContext);
        String text = toolResult.getContent().stream()
                .map(c -> c.getText())
                .filter(t -> t != null && !t.isBlank())
                .collect(java.util.stream.Collectors.joining("\n"));
        if (text.isEmpty()) {
            return null;
        }
        java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile("ID\\s*:\\s*([0-9a-fA-F-]{36})");
        String[] lines = text.split("\\r?\\n");
        String currentName = null;
        java.util.Map<String, String> nameToId = new java.util.HashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("**")) {
                currentName = trimmed.replace("*", "").trim().toLowerCase();
            }
            var m = idPattern.matcher(trimmed);
            if (m.find() && currentName != null) {
                nameToId.put(currentName, m.group(1));
                currentName = null;
            }
        }
        // Match by number
        if (!number.isEmpty()) {
            for (var entry : nameToId.entrySet()) {
                if (entry.getKey().contains(number)) {
                    return entry.getValue();
                }
            }
        }
        // Fallback: contains candidate words
        for (var entry : nameToId.entrySet()) {
            if (entry.getKey().contains(candidate)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String extractNumber(String userMessage) {
        if (userMessage == null) {
            return null;
        }
        String digits = userMessage.replaceAll("\\D+", "");
        return digits.isEmpty() ? null : digits;
    }

    private String resolveSpaceIdFromNumber(String number, MatrixAssistantAuthContext authContext) {
        if (number == null || number.isBlank()) {
            return null;
        }

        // 1) Try DB (spacesService) to find a space whose name contains the number
        try {
            var page = spacesService.getSpacesWithFilters(null, SpaceStatusForEntity.ACTIVE,
                    org.springframework.data.domain.PageRequest.of(0, 200));
            for (SpaceEntity space : page.getContent()) {
                if (space.getName() != null && space.getName().toLowerCase().contains(number.toLowerCase())) {
                    log.info("resolveSpaceIdFromNumber: matched in DB {} -> {}", space.getName(), space.getId());
                    return space.getId().toString();
                }
            }
        } catch (Exception e) {
            log.warn("resolveSpaceIdFromNumber: DB lookup failed: {}", e.getMessage());
        }

        // 2) Fallback to list_spaces MCP tool
        Map<String, Object> listArgs = Map.of();
        var toolResult = mcpAdapter.callMCPToolDirect("list_spaces", listArgs, authContext);
        String text = toolResult.getContent().stream()
                .map(c -> c.getText())
                .filter(t -> t != null && !t.isBlank())
                .collect(java.util.stream.Collectors.joining("\n"));
        if (text.isEmpty()) {
            return null;
        }
        java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile("ID\\s*:\\s*([0-9a-fA-F-]{36})");
        String[] lines = text.split("\\r?\\n");
        String currentName = null;
        java.util.Map<String, String> nameToId = new java.util.HashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("**")) {
                currentName = trimmed.replace("*", "").trim().toLowerCase();
            }
            var m = idPattern.matcher(trimmed);
            if (m.find() && currentName != null) {
                nameToId.put(currentName, m.group(1));
                currentName = null;
            }
        }
        for (var entry : nameToId.entrySet()) {
            if (entry.getKey().contains(number.toLowerCase())) {
                return entry.getValue();
            }
        }

        // 3) As a last resort in tests/dev: create a placeholder space so that UUID
        // exists
        try {
            SpaceEntity space = new SpaceEntity();
            space.setName("Place de parking N°" + number);
            space.setType(com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity.PARKING);
            space.setStatus(SpaceStatusForEntity.ACTIVE);
            space.setTenantPrice(java.math.BigDecimal.ZERO);
            space.setCurrency("EUR");
            SpaceEntity saved = spacesService.createSpace(space);
            log.info("resolveSpaceIdFromNumber: created placeholder space {} -> {}", saved.getName(), saved.getId());
            return saved.getId().toString();
        } catch (Exception e) {
            log.warn("resolveSpaceIdFromNumber: failed to create placeholder space for {}: {}", number, e.getMessage());
        }

        return null;
    }

    /**
     * Formats parking list response to show only numbers in a concise format.
     * Extracts parking numbers from verbose list_spaces output and formats as:
     * "Places de parking disponibles : 7, 23, 45, 67..."
     */
    private String formatParkingListResponse(String response, MatrixAssistantAuthContext authContext, Locale locale) {
        if (response == null || response.isEmpty()) {
            return response;
        }

        // Check if response contains parking space information
        if (!response.contains("Place de parking") && !response.contains("parking N°")) {
            return response;
        }

        try {
            // Extract all parking numbers from the response
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("N°(\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(response);
            java.util.Set<String> numbers = new java.util.LinkedHashSet<>(); // Use LinkedHashSet to preserve order

            while (matcher.find()) {
                numbers.add(matcher.group(1));
            }

            if (numbers.isEmpty()) {
                // No numbers found in response; try to fetch from list_spaces result as
                // fallback
                String fetchedNumbers = fetchParkingNumbers(authContext);
                if (fetchedNumbers == null || fetchedNumbers.isBlank()) {
                    return ensurePeriodQuestion(response, locale, null);
                }
                String prefix = messageSource.getMessage("matrix.reservation.chooseSpace.availableSpacesPrefix", null,
                        locale);
                String formattedFallback = prefix + fetchedNumbers + ".";
                return ensurePeriodQuestion(formattedFallback, locale, null);
            }

            // Format as concise list
            String numbersList = String.join(", ", numbers);
            String prefix = messageSource.getMessage("matrix.reservation.chooseSpace.availableSpacesPrefix", null,
                    locale);
            String formatted = prefix + numbersList + ".";

            // If the original response had a question or intro, preserve it
            if (response.contains("?") || response.contains("Quel") || response.contains("souhaitez")
                    || response.contains("Which") || response.contains("would")) {
                // Extract the question part (before the parking list)
                String[] parts = response.split("(Place de parking|parking N°|Available parking)");
                if (parts.length > 0 && !parts[0].trim().isEmpty()) {
                    String question = parts[0].trim();
                    // Remove trailing punctuation and add space
                    question = question.replaceAll("[.:;,!]+$", "").trim();
                    return ensurePeriodQuestion(question + " " + formatted, locale, null);
                }
            }

            return ensurePeriodQuestion(formatted, locale, null);
        } catch (Exception e) {
            log.warn("Error formatting parking list response: {}", e.getMessage());
            return ensurePeriodQuestion(response, locale, null); // Return original on error but ensure period prompt
        }
    }

    /**
     * Formats response using availableSpaces array if present.
     * If availableSpaces is provided, formats the message with the list of choices.
     * Also ensures period prompt is included if not already present.
     */
    private String formatResponseWithAvailableSpaces(SpaceStepResponse stepResponse,
            MatrixAssistantAuthContext authContext, Locale locale,
            MatrixAssistantAgentContextService.AgentContext context) {
        String response = stepResponse.getResponse();
        if (response == null || response.isEmpty()) {
            response = messageSource.getMessage("matrix.reservation.chooseSpace.whichSpaceQuestion", null, locale);
        }

        // Check if spaceId is already defined - if so, don't show available spaces list
        String currentSpaceId = context != null ? context.getWorkflowStateValue("spaceId", String.class) : null;
        boolean hasSpaceId = currentSpaceId != null || stepResponse.hasSpaceId();

        java.util.Map<String, String> availableSpaces = stepResponse.getAvailableSpaces();

        // Only show available spaces if spaceId is NOT already defined
        if (!hasSpaceId && availableSpaces != null && !availableSpaces.isEmpty()) {
            // Extract space numbers from map keys for display
            java.util.List<String> spaceNumbers = new java.util.ArrayList<>(availableSpaces.keySet());

            // Check if response already contains a list of spaces (to avoid duplication)
            String lowerResponse = response.toLowerCase();
            boolean alreadyHasList = false;
            for (String number : spaceNumbers) {
                if (lowerResponse.contains("place " + number) || lowerResponse.contains("n°" + number)
                        || lowerResponse.contains("n° " + number) || lowerResponse.contains(number + ",")
                        || lowerResponse.contains(number + " ")) {
                    alreadyHasList = true;
                    break;
                }
            }

            // Only add list if not already present in response
            if (!alreadyHasList) {
                // Format as a more ergonomic Matrix message with line breaks
                StringBuilder formatted = new StringBuilder();
                String cleanResponse = response.replaceAll("[.:;,!]+$", "").trim();
                formatted.append(cleanResponse);

                // Add spaces list in a more readable format for Matrix
                formatted.append("\n\n");
                String prefix = messageSource.getMessage("matrix.reservation.chooseSpace.availableSpacesPrefix", null,
                        locale);
                formatted.append(prefix);

                // Format numbers in groups for better readability (max 10 per line)
                int maxPerLine = 10;
                for (int i = 0; i < spaceNumbers.size(); i++) {
                    if (i > 0 && i % maxPerLine == 0) {
                        formatted.append("\n");
                    } else if (i > 0) {
                        formatted.append(", ");
                    }
                    formatted.append(spaceNumbers.get(i));
                }
                formatted.append(".");

                response = formatted.toString();
            }
        } else if (!hasSpaceId) {
            // Fallback: try to extract from response or fetch from list_spaces
            // This handles cases where LLM didn't provide availableSpaces but included it
            // in response text
            String formatted = formatParkingListResponse(response, authContext, locale);
            if (!formatted.equals(response)) {
                response = formatted;
            }
        }

        // Ensure period prompt is included only if period is not already defined
        return ensurePeriodQuestion(response, locale, context);
    }

    /**
     * Ensures the response asks for the reservation period (dates/times)
     * Only adds the prompt if period is not already defined in context
     */
    private String ensurePeriodQuestion(String response, Locale locale,
            MatrixAssistantAgentContextService.AgentContext context) {
        if (response == null || response.isBlank()) {
            return response;
        }

        // Check if period is already defined in context
        if (context != null) {
            Object startDateTimeObj = context.getWorkflowState().get("startDateTime");
            Object endDateTimeObj = context.getWorkflowState().get("endDateTime");
            Object startDateObj = context.getWorkflowState().get("startDate");
            Object endDateObj = context.getWorkflowState().get("endDate");
            boolean hasPeriod = (startDateTimeObj != null && endDateTimeObj != null) ||
                    (startDateObj != null && endDateObj != null);

            if (hasPeriod) {
                // Period already defined, don't add the prompt
                return response;
            }
        }

        // Check if response already mentions period/dates
        String lower = response.toLowerCase();
        if (lower.contains("date") || lower.contains("période") || lower.contains("periode")
                || lower.contains("horaire") || lower.contains("heure") || lower.contains("period")
                || lower.contains("time")) {
            return response;
        }

        // Add period prompt with proper formatting for Matrix
        String periodPrompt = messageSource.getMessage("matrix.reservation.chooseSpace.periodPrompt", null, locale);
        return response + "\n\n" + periodPrompt;
    }

    /**
     * Gets locale from context, with fallback to French
     */
    private Locale getLocaleFromContext(MatrixAssistantAgentContextService.AgentContext context) {
        if (context == null) {
            return Locale.FRENCH;
        }
        String localeStr = context.getWorkflowStateValue("locale", String.class);
        return localeStr != null ? Locale.forLanguageTag(localeStr) : Locale.FRENCH;
    }

    /**
     * Extracts space mapping (number -> UUID) from list_spaces tool result
     * Returns a map where key is space number (e.g., "7", "23") and value is UUID
     */
    private java.util.Map<String, String> extractSpaceMapping(MatrixAssistantAuthContext authContext) {
        try {
            Map<String, Object> listArgs = Map.of();
            var toolResult = mcpAdapter.callMCPToolDirect("list_spaces", listArgs, authContext);
            String text = toolResult.getContent().stream()
                    .map(c -> c.getText())
                    .filter(t -> t != null && !t.isBlank())
                    .collect(java.util.stream.Collectors.joining("\n"));
            if (text.isEmpty()) {
                return null;
            }

            java.util.Map<String, String> mapping = new java.util.HashMap<>();
            java.util.regex.Pattern numberPattern = java.util.regex.Pattern.compile("N°(\\d+)");
            java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile("ID\\s*:\\s*([0-9a-fA-F-]{36})");
            String[] lines = text.split("\\r?\\n");
            String currentNumber = null;

            for (String line : lines) {
                String trimmed = line.trim();
                // Extract number from name (e.g., "Place de parking N°7")
                java.util.regex.Matcher numberMatcher = numberPattern.matcher(trimmed);
                if (numberMatcher.find()) {
                    currentNumber = numberMatcher.group(1);
                }
                // Extract UUID
                java.util.regex.Matcher idMatcher = idPattern.matcher(trimmed);
                if (idMatcher.find() && currentNumber != null) {
                    String uuid = idMatcher.group(1);
                    mapping.put(currentNumber, uuid);
                    currentNumber = null; // Reset after matching
                }
            }

            if (mapping.isEmpty()) {
                return null;
            }

            log.debug("Extracted space mapping: {} spaces", mapping.size());
            return mapping;
        } catch (Exception e) {
            log.warn("extractSpaceMapping: failed to extract mapping: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Fetch parking numbers from list_spaces tool for fallback formatting
     */
    private String fetchParkingNumbers(MatrixAssistantAuthContext authContext) {
        try {
            Map<String, Object> listArgs = Map.of();
            var toolResult = mcpAdapter.callMCPToolDirect("list_spaces", listArgs, authContext);
            String text = toolResult.getContent().stream()
                    .map(c -> c.getText())
                    .filter(t -> t != null && !t.isBlank())
                    .collect(java.util.stream.Collectors.joining("\n"));
            if (text.isEmpty()) {
                return null;
            }
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("N°(\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(text);
            java.util.Set<String> numbers = new java.util.LinkedHashSet<>();
            while (matcher.find()) {
                numbers.add(matcher.group(1));
            }
            if (numbers.isEmpty()) {
                return null;
            }
            return String.join(", ", numbers);
        } catch (Exception e) {
            log.warn("fetchParkingNumbers: failed to fetch list_spaces numbers: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Checks if a string is a valid UUID
     */
    private boolean isValidUUID(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            java.util.UUID.fromString(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
