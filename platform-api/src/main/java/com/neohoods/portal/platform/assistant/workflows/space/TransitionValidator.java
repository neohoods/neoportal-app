package com.neohoods.portal.platform.assistant.workflows.space;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.neohoods.portal.platform.assistant.model.SpaceStep;
import com.neohoods.portal.platform.assistant.model.SpaceStepResponse;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Validates transitions between states in the space workflow state machine.
 * 
 * Defines allowed transitions and validates that:
 * - The transition is allowed from the current step
 * - Required data is present (e.g., spaceId, period)
 * - The transition makes sense in the workflow context
 */
@Component
@Slf4j
public class TransitionValidator {
    
    /**
     * Map of allowed transitions from each step
     * Key: from step, Value: set of allowed target steps
     */
    private static final Map<SpaceStep, Set<SpaceStep>> ALLOWED_TRANSITIONS = new HashMap<>();
    
    static {
        // REQUEST_SPACE_INFO can transition to:
        // - Itself (ANSWER_USER, ASK_USER)
        // - CHOOSE_SPACE (when spaceId is identified)
        // - End (CANCEL, ERROR)
        ALLOWED_TRANSITIONS.put(SpaceStep.REQUEST_SPACE_INFO, EnumSet.of(
                SpaceStep.REQUEST_SPACE_INFO,
                SpaceStep.CHOOSE_SPACE
        ));
        
        // CHOOSE_SPACE can transition to:
        // - Itself (ANSWER_USER, ASK_USER) - when spaceId or period is missing
        // - CONFIRM_RESERVATION_SUMMARY (when spaceId + period are both present)
        // - End (CANCEL, ERROR)
        ALLOWED_TRANSITIONS.put(SpaceStep.CHOOSE_SPACE, EnumSet.of(
                SpaceStep.CHOOSE_SPACE,
                SpaceStep.CONFIRM_RESERVATION_SUMMARY
        ));
        
        // CONFIRM_RESERVATION_SUMMARY can transition to:
        // - Itself (ANSWER_USER, ASK_USER)
        // - COMPLETE_RESERVATION (when user confirms)
        // - End (CANCEL, ERROR)
        ALLOWED_TRANSITIONS.put(SpaceStep.CONFIRM_RESERVATION_SUMMARY, EnumSet.of(
                SpaceStep.CONFIRM_RESERVATION_SUMMARY,
                SpaceStep.COMPLETE_RESERVATION
        ));
        
        // COMPLETE_RESERVATION can transition to:
        // - PAYMENT_INSTRUCTIONS (when payment is required)
        // - End (COMPLETED when no payment, CANCEL, ERROR)
        ALLOWED_TRANSITIONS.put(SpaceStep.COMPLETE_RESERVATION, EnumSet.of(
                SpaceStep.PAYMENT_INSTRUCTIONS
        ));
        
        // PAYMENT_INSTRUCTIONS can transition to:
        // - End (COMPLETED, CANCEL, ERROR)
        // Note: PAYMENT_CONFIRMED is handled by webhook, not by state machine
        ALLOWED_TRANSITIONS.put(SpaceStep.PAYMENT_INSTRUCTIONS, EnumSet.of(
                SpaceStep.PAYMENT_INSTRUCTIONS
        ));
    }
    
    /**
     * Validates a transition from one step to another
     */
    public StepTransitionContext.TransitionValidationResult validateTransition(
            SpaceStep fromStep,
            SpaceStep toStep,
            SpaceStepResponse response,
            StepTransitionContext context) {
        
        // Check if transition is allowed
        Set<SpaceStep> allowedTargets = ALLOWED_TRANSITIONS.get(fromStep);
        if (allowedTargets == null || !allowedTargets.contains(toStep)) {
            String errorMessage = String.format(
                    "Transition from %s to %s is not allowed. Allowed targets: %s",
                    fromStep, toStep, allowedTargets);
            log.warn("❌ Invalid transition: {}", errorMessage);
            return StepTransitionContext.TransitionValidationResult.invalid(
                    "INVALID_TRANSITION", errorMessage);
        }
        
        // Validate required data based on target step
        StepTransitionContext.TransitionValidationResult dataValidation = validateRequiredData(
                toStep, response, context);
        if (!dataValidation.isValid()) {
            return dataValidation;
        }
        
        // All validations passed
        return StepTransitionContext.TransitionValidationResult.valid();
    }
    
    /**
     * Validates that required data is present for the target step
     */
    private StepTransitionContext.TransitionValidationResult validateRequiredData(
            SpaceStep targetStep,
            SpaceStepResponse response,
            StepTransitionContext context) {
        
        switch (targetStep) {
            case CHOOSE_SPACE:
                // No specific data required (will be collected in this step)
                return StepTransitionContext.TransitionValidationResult.valid();
                
            case CONFIRM_RESERVATION_SUMMARY:
                // spaceId and period must be present
                boolean hasSpaceId = (response != null && response.hasSpaceId()) ||
                        (context != null && context.getData("spaceId", String.class) != null);
                boolean hasPeriod = (response != null && response.hasCompletePeriod()) ||
                        (context != null && context.getData("period", Object.class) != null);
                
                if (!hasSpaceId) {
                    return StepTransitionContext.TransitionValidationResult.invalid(
                            "MISSING_SPACE_ID",
                            "spaceId is required to transition to CONFIRM_RESERVATION_SUMMARY");
                }
                if (!hasPeriod) {
                    return StepTransitionContext.TransitionValidationResult.invalid(
                            "MISSING_PERIOD",
                            "period is required to transition to CONFIRM_RESERVATION_SUMMARY");
                }
                return StepTransitionContext.TransitionValidationResult.valid();
                
            case COMPLETE_RESERVATION:
                // spaceId and period must be present
                hasSpaceId = (response != null && response.hasSpaceId()) ||
                        (context != null && context.getData("spaceId", String.class) != null);
                hasPeriod = (response != null && response.hasCompletePeriod()) ||
                        (context != null && context.getData("period", Object.class) != null);
                
                if (!hasSpaceId) {
                    return StepTransitionContext.TransitionValidationResult.invalid(
                            "MISSING_SPACE_ID",
                            "spaceId is required to transition to COMPLETE_RESERVATION");
                }
                if (!hasPeriod) {
                    return StepTransitionContext.TransitionValidationResult.invalid(
                            "MISSING_PERIOD",
                            "period is required to transition to COMPLETE_RESERVATION");
                }
                return StepTransitionContext.TransitionValidationResult.valid();
                
            case PAYMENT_INSTRUCTIONS:
                // Reservation must be created (handled by COMPLETE_RESERVATION step)
                return StepTransitionContext.TransitionValidationResult.valid();
                
            default:
                // Other steps don't have specific data requirements
                return StepTransitionContext.TransitionValidationResult.valid();
        }
    }
    
    /**
     * Checks if a transition is allowed
     */
    public boolean isTransitionAllowed(SpaceStep fromStep, SpaceStep toStep) {
        Set<SpaceStep> allowedTargets = ALLOWED_TRANSITIONS.get(fromStep);
        return allowedTargets != null && allowedTargets.contains(toStep);
    }
    
    /**
     * Gets all allowed target steps from a given step
     */
    public Set<SpaceStep> getAllowedTargets(SpaceStep fromStep) {
        return ALLOWED_TRANSITIONS.getOrDefault(fromStep, EnumSet.noneOf(SpaceStep.class));
    }
}

