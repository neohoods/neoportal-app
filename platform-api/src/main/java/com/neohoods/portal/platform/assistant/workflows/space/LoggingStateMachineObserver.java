package com.neohoods.portal.platform.assistant.workflows.space;

import com.neohoods.portal.platform.assistant.model.SpaceStep;
import com.neohoods.portal.platform.assistant.model.SpaceStepResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Default logging observer for state machine transitions.
 * Logs all transitions for debugging and monitoring.
 */
@Component
@Slf4j
public class LoggingStateMachineObserver implements StateMachineObserver {
    
    @Override
    public void onTransitionStarting(StepTransitionContext context) {
        log.info("🔄 Transition starting: {} -> {} (reason: {})",
                context.getFromStep(), context.getToStep(), context.getReason());
    }
    
    @Override
    public void onTransitionCompleted(StepTransitionContext context, SpaceStepResponse response) {
        log.info("✅ Transition completed: {} -> {} (status: {})",
                context.getFromStep(), context.getToStep(), response.getStatus());
    }
    
    @Override
    public void onTransitionFailed(StepTransitionContext context, Throwable error) {
        log.error("❌ Transition failed: {} -> {} - {}",
                context.getFromStep(), context.getToStep(), error.getMessage(), error);
    }
    
    @Override
    public void onTransitionRejected(StepTransitionContext context, StepTransitionContext.TransitionValidationResult validationResult) {
        log.warn("⚠️ Transition rejected: {} -> {} - {}",
                context.getFromStep(), context.getToStep(), validationResult.getErrorMessage());
    }
    
    @Override
    public void onStepEntered(SpaceStep step, StepTransitionContext context) {
        log.debug("📍 Entering step: {}", step);
    }
    
    @Override
    public void onStepExited(SpaceStep step, StepTransitionContext context) {
        log.debug("📍 Exiting step: {}", step);
    }
    
    @Override
    public void onSwitchStepIteration(SpaceStep fromStep, SpaceStep toStep, int iteration) {
        log.info("🔄 SWITCH_STEP iteration {}: {} -> {}", iteration, fromStep, toStep);
    }
    
    @Override
    public void onError(SpaceStep currentStep, Throwable error, StepTransitionContext context) {
        log.error("❌ Error in step {}: {}", currentStep, error.getMessage(), error);
    }
}




