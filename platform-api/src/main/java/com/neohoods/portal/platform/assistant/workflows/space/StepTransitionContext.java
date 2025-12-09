package com.neohoods.portal.platform.assistant.workflows.space;

import java.util.HashMap;
import java.util.Map;

import com.neohoods.portal.platform.assistant.model.SpaceStep;
import com.neohoods.portal.platform.assistant.model.SpaceStepResponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Context enriched for state transitions in the space workflow state machine.
 * 
 * This context is passed during transitions to provide:
 * - Data extracted from the previous step
 * - Reason for the transition
 * - Metadata about the transition
 * - Validation information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepTransitionContext {
    
    /**
     * Source step (where we're transitioning from)
     */
    private SpaceStep fromStep;
    
    /**
     * Target step (where we're transitioning to)
     */
    private SpaceStep toStep;
    
    /**
     * Reason for the transition (e.g., "spaceId identified", "period collected", "user confirmed")
     */
    private String reason;
    
    /**
     * Response from the previous step that triggered this transition
     */
    private SpaceStepResponse previousResponse;
    
    /**
     * Additional data extracted during the transition
     * Common keys:
     * - "spaceId": UUID of the space
     * - "period": ReservationPeriod object
     * - "locale": User's preferred language
     * - "internalMessage": Message to pass to next step
     */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();
    
    /**
     * Metadata about the transition
     * Common keys:
     * - "iteration": Current iteration in SWITCH_STEP loop
     * - "timestamp": When the transition occurred
     * - "validated": Whether the transition was validated
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    /**
     * Validation result (if validation was performed)
     */
    private TransitionValidationResult validationResult;
    
    /**
     * Creates a transition context for a SWITCH_STEP transition
     */
    public static StepTransitionContext forSwitchStep(
            SpaceStep fromStep,
            SpaceStep toStep,
            SpaceStepResponse previousResponse,
            String reason) {
        return StepTransitionContext.builder()
                .fromStep(fromStep)
                .toStep(toStep)
                .reason(reason)
                .previousResponse(previousResponse)
                .data(new HashMap<>())
                .metadata(new HashMap<>())
                .build();
    }
    
    /**
     * Adds data to the context
     */
    public StepTransitionContext withData(String key, Object value) {
        if (this.data == null) {
            this.data = new HashMap<>();
        }
        this.data.put(key, value);
        return this;
    }
    
    /**
     * Adds metadata to the context
     */
    public StepTransitionContext withMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
        return this;
    }
    
    /**
     * Gets data from the context
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(String key, Class<T> type) {
        if (data == null) {
            return null;
        }
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Gets metadata from the context
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Result of transition validation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransitionValidationResult {
        private boolean valid;
        private String errorMessage;
        private String errorCode;
        
        public static TransitionValidationResult valid() {
            return TransitionValidationResult.builder()
                    .valid(true)
                    .build();
        }
        
        public static TransitionValidationResult invalid(String errorMessage) {
            return TransitionValidationResult.builder()
                    .valid(false)
                    .errorMessage(errorMessage)
                    .build();
        }
        
        public static TransitionValidationResult invalid(String errorCode, String errorMessage) {
            return TransitionValidationResult.builder()
                    .valid(false)
                    .errorCode(errorCode)
                    .errorMessage(errorMessage)
                    .build();
        }
    }
}




