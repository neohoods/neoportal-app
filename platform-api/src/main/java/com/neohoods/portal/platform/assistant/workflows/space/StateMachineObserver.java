package com.neohoods.portal.platform.assistant.workflows.space;

import com.neohoods.portal.platform.assistant.model.SpaceStep;
import com.neohoods.portal.platform.assistant.model.SpaceStepResponse;

/**
 * Observer interface for monitoring and tracing state machine transitions.
 * 
 * Implementations can be used for:
 * - Logging transitions
 * - Metrics collection
 * - Debugging
 * - Analytics
 */
public interface StateMachineObserver {
    
    /**
     * Called when a transition is about to occur
     */
    default void onTransitionStarting(StepTransitionContext context) {
        // Default: no-op
    }
    
    /**
     * Called when a transition has completed successfully
     */
    default void onTransitionCompleted(StepTransitionContext context, SpaceStepResponse response) {
        // Default: no-op
    }
    
    /**
     * Called when a transition fails
     */
    default void onTransitionFailed(StepTransitionContext context, Throwable error) {
        // Default: no-op
    }
    
    /**
     * Called when a transition is rejected by validation
     */
    default void onTransitionRejected(StepTransitionContext context, StepTransitionContext.TransitionValidationResult validationResult) {
        // Default: no-op
    }
    
    /**
     * Called when entering a new step
     */
    default void onStepEntered(SpaceStep step, StepTransitionContext context) {
        // Default: no-op
    }
    
    /**
     * Called when exiting a step
     */
    default void onStepExited(SpaceStep step, StepTransitionContext context) {
        // Default: no-op
    }
    
    /**
     * Called when a SWITCH_STEP loop iteration occurs
     */
    default void onSwitchStepIteration(SpaceStep fromStep, SpaceStep toStep, int iteration) {
        // Default: no-op
    }
    
    /**
     * Called when an error occurs in the state machine
     */
    default void onError(SpaceStep currentStep, Throwable error, StepTransitionContext context) {
        // Default: no-op
    }
}




