package com.neohoods.portal.platform.assistant.workflows.space;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.model.SpaceStep;
import com.neohoods.portal.platform.assistant.model.SpaceStepResponse;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;
import com.neohoods.portal.platform.assistant.workflows.space.steps.SpaceStepHandler;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Central state machine for managing space workflow transitions.
 * 
 * Responsibilities:
 * - Manages state transitions
 * - Handles SWITCH_STEP loops
 * - Validates transitions
 * - Notifies observers
 * - Handles errors and retries
 */
@Component
@Slf4j
public class SpaceStateMachine {

    private final TransitionValidator transitionValidator;
    private final List<StateMachineObserver> observers;

    @Autowired
    public SpaceStateMachine(
            TransitionValidator transitionValidator,
            List<StateMachineObserver> observers) {
        this.transitionValidator = transitionValidator;
        this.observers = observers != null ? observers : new ArrayList<>();
    }

    /**
     * Processes a step with automatic SWITCH_STEP loop handling
     * 
     * @param handler             Current step handler
     * @param currentStep         Current step
     * @param userMessage         User message
     * @param conversationHistory Conversation history
     * @param context             Agent context
     * @param authContext         Auth context
     * @param stepHandlers        Map of all step handlers
     * @return Final step response
     */
    public Mono<SpaceStepResponse> processStepWithSwitchLoop(
            SpaceStepHandler handler,
            SpaceStep currentStep,
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext,
            Map<SpaceStep, SpaceStepHandler> stepHandlers) {

        return processStepWithSwitchLoop(
                handler, currentStep, userMessage, conversationHistory, context, authContext, stepHandlers, 0);
    }

    /**
     * Processes a step with automatic SWITCH_STEP loop handling (internal recursive
     * method)
     */
    private Mono<SpaceStepResponse> processStepWithSwitchLoop(
            SpaceStepHandler handler,
            SpaceStep currentStep,
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext,
            Map<SpaceStep, SpaceStepHandler> stepHandlers,
            int iteration) {

        // Prevent infinite loops
        if (iteration >= 10) {
            log.error("❌ SWITCH_STEP loop exceeded max iterations (10), stopping");
            SpaceStepResponse errorResponse = SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ERROR)
                    .response("Une erreur s'est produite lors du traitement de votre demande.")
                    .build();
            notifyError(currentStep, new IllegalStateException("SWITCH_STEP loop exceeded max iterations"), null);
            return Mono.just(errorResponse);
        }

        // Notify observers: entering step
        notifyStepEntered(currentStep, null);

        // Call handler
        return handler.handle(userMessage, conversationHistory, context, authContext)
                .flatMap(stepResponse -> {
                    // Handle different statuses
                    switch (stepResponse.getStatus()) {
                        case SWITCH_STEP:
                            return handleSwitchStep(
                                    currentStep, stepResponse, userMessage, conversationHistory,
                                    context, authContext, stepHandlers, iteration);

                        case ASK_USER:
                            return handleAskUser(currentStep, stepResponse, context);

                        case ANSWER_USER:
                            return handleAnswerUser(currentStep, stepResponse);

                        case COMPLETED:
                            return handleCompleted(currentStep, stepResponse, context, authContext);

                        case CANCEL:
                            return handleCancel(currentStep, stepResponse, context, authContext);

                        case ERROR:
                            return handleError(currentStep, stepResponse, context);

                        case RETRY:
                            return handleRetry(
                                    handler, currentStep, userMessage, conversationHistory,
                                    context, authContext, stepHandlers, iteration, stepResponse);

                        default:
                            log.warn("Unknown status: {}, treating as ANSWER_USER", stepResponse.getStatus());
                            return Mono.just(stepResponse);
                    }
                })
                .doOnError(error -> {
                    notifyError(currentStep, error, null);
                });
    }

    /**
     * Handles SWITCH_STEP status - transitions to next step
     */
    private Mono<SpaceStepResponse> handleSwitchStep(
            SpaceStep currentStep,
            SpaceStepResponse stepResponse,
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext,
            Map<SpaceStep, SpaceStepHandler> stepHandlers,
            int iteration) {

        SpaceStep nextStep = stepResponse.getNextStep();
        if (nextStep == null) {
            log.error("❌ SWITCH_STEP status but no nextStep specified");
            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ERROR)
                    .response("Une erreur s'est produite lors du traitement de votre demande.")
                    .build());
        }

        // Create transition context
        StepTransitionContext transitionContext = StepTransitionContext.forSwitchStep(
                currentStep, nextStep, stepResponse, "SWITCH_STEP from " + currentStep);

        // Extract data from response
        if (stepResponse.hasSpaceId()) {
            transitionContext.withData("spaceId", stepResponse.getSpaceId());
        }
        if (stepResponse.hasCompletePeriod()) {
            transitionContext.withData("period", stepResponse.getPeriod());
        }
        if (stepResponse.getLocale() != null) {
            transitionContext.withData("locale", stepResponse.getLocale());
        }
        if (stepResponse.getInternalMessage() != null) {
            transitionContext.withData("internalMessage", stepResponse.getInternalMessage());
        }
        transitionContext.withMetadata("iteration", iteration + 1);

        // Validate transition
        StepTransitionContext.TransitionValidationResult validationResult = transitionValidator.validateTransition(
                currentStep, nextStep, stepResponse, transitionContext);

        if (!validationResult.isValid()) {
            log.warn("❌ Transition validation failed: {}", validationResult.getErrorMessage());
            notifyTransitionRejected(transitionContext, validationResult);
            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ERROR)
                    .response("Une erreur s'est produite lors du traitement de votre demande.")
                    .build());
        }

        transitionContext.setValidationResult(validationResult);

        // Notify observers: transition starting
        notifyTransitionStarting(transitionContext);

        // Update context
        if (context != null) {
            context.updateWorkflowState("reservationStep", nextStep.name());
            // Store data from transition
            if (stepResponse.hasSpaceId()) {
                context.updateWorkflowState("spaceId", stepResponse.getSpaceId());
            }
            if (stepResponse.hasCompletePeriod()) {
                if (stepResponse.getPeriod().getStartDate() != null) {
                    context.updateWorkflowState("startDate", stepResponse.getPeriod().getStartDate().toString());
                }
                if (stepResponse.getPeriod().getEndDate() != null) {
                    context.updateWorkflowState("endDate", stepResponse.getPeriod().getEndDate().toString());
                }
                if (stepResponse.getPeriod().getStartTime() != null) {
                    context.updateWorkflowState("startTime", stepResponse.getPeriod().getStartTime().toString());
                }
                if (stepResponse.getPeriod().getEndTime() != null) {
                    context.updateWorkflowState("endTime", stepResponse.getPeriod().getEndTime().toString());
                }
            }
        }

        // Get next handler
        SpaceStepHandler nextHandler = stepHandlers.get(nextStep);
        if (nextHandler == null) {
            log.error("❌ No handler found for next step: {}", nextStep);
            notifyError(nextStep, new IllegalStateException("No handler found for step: " + nextStep),
                    transitionContext);
            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ERROR)
                    .response("Une erreur s'est produite lors du traitement de votre demande.")
                    .build());
        }

        // Use internalMessage as context for next step, or use original userMessage
        String nextUserMessage = stepResponse.getInternalMessage() != null
                ? stepResponse.getInternalMessage()
                : userMessage;

        log.info("🔄 SWITCH_STEP: {} -> {} (iteration: {})", currentStep, nextStep, iteration + 1);
        notifySwitchStepIteration(currentStep, nextStep, iteration + 1);

        // Notify observers: exiting current step
        notifyStepExited(currentStep, transitionContext);

        // Recursively process next step
        return processStepWithSwitchLoop(
                nextHandler, nextStep, nextUserMessage, conversationHistory, context, authContext, stepHandlers,
                iteration + 1)
                .doOnSuccess(response -> {
                    notifyTransitionCompleted(transitionContext, response);
                });
    }

    /**
     * Handles ASK_USER status - stores state and returns response
     */
    private Mono<SpaceStepResponse> handleAskUser(
            SpaceStep currentStep,
            SpaceStepResponse stepResponse,
            MatrixAssistantAgentContextService.AgentContext context) {

        // Store in context that we're waiting for user input on this step
        if (context != null) {
            context.updateWorkflowState("awaitingUserInput", "true");
            context.updateWorkflowState("awaitingStep", currentStep.name());
        }

        return Mono.just(stepResponse);
    }

    /**
     * Handles ANSWER_USER status - just returns the response
     */
    private Mono<SpaceStepResponse> handleAnswerUser(
            SpaceStep currentStep,
            SpaceStepResponse stepResponse) {

        return Mono.just(stepResponse);
    }

    /**
     * Handles COMPLETED status - workflow is complete
     */
    private Mono<SpaceStepResponse> handleCompleted(
            SpaceStep currentStep,
            SpaceStepResponse stepResponse,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {

        // Clear context
        String roomId = authContext != null ? authContext.getRoomId() : null;
        if (roomId != null && context != null) {
            // Note: We don't clear context here, let the agent handle it
            // as it might need to transition to payment step
        }

        return Mono.just(stepResponse);
    }

    /**
     * Handles CANCEL status - clears context
     */
    private Mono<SpaceStepResponse> handleCancel(
            SpaceStep currentStep,
            SpaceStepResponse stepResponse,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {

        // Clear context
        String roomId = authContext != null ? authContext.getRoomId() : null;
        if (roomId != null) {
            // Note: Context clearing is handled by the agent
        }

        return Mono.just(stepResponse);
    }

    /**
     * Handles ERROR status - resets to initial step
     */
    private Mono<SpaceStepResponse> handleError(
            SpaceStep currentStep,
            SpaceStepResponse stepResponse,
            MatrixAssistantAgentContextService.AgentContext context) {

        // Reset to initial step
        if (context != null) {
            context.updateWorkflowState("reservationStep", SpaceStep.REQUEST_SPACE_INFO.name());
        }

        return Mono.just(stepResponse);
    }

    /**
     * Handles RETRY status - retries the same handler with enriched context
     */
    private Mono<SpaceStepResponse> handleRetry(
            SpaceStepHandler handler,
            SpaceStep currentStep,
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext,
            Map<SpaceStep, SpaceStepHandler> stepHandlers,
            int iteration,
            SpaceStepResponse stepResponse) {

        log.info("🔄 RETRY: Retrying step {} (iteration: {})", currentStep, iteration);

        // Enrich conversation history with error context if available
        if (stepResponse.getInternalMessage() != null) {
            // Add internal message to conversation history for context
            // This allows the LLM to understand what went wrong
        }

        // Retry with same handler
        return processStepWithSwitchLoop(
                handler, currentStep, userMessage, conversationHistory, context, authContext, stepHandlers,
                iteration + 1);
    }

    // Observer notification methods

    private void notifyTransitionStarting(StepTransitionContext context) {
        observers.forEach(observer -> {
            try {
                observer.onTransitionStarting(context);
            } catch (Exception e) {
                log.warn("Error in observer onTransitionStarting", e);
            }
        });
    }

    private void notifyTransitionCompleted(StepTransitionContext context, SpaceStepResponse response) {
        observers.forEach(observer -> {
            try {
                observer.onTransitionCompleted(context, response);
            } catch (Exception e) {
                log.warn("Error in observer onTransitionCompleted", e);
            }
        });
    }

    private void notifyTransitionRejected(StepTransitionContext context,
            StepTransitionContext.TransitionValidationResult validationResult) {
        observers.forEach(observer -> {
            try {
                observer.onTransitionRejected(context, validationResult);
            } catch (Exception e) {
                log.warn("Error in observer onTransitionRejected", e);
            }
        });
    }

    private void notifyStepEntered(SpaceStep step, StepTransitionContext context) {
        observers.forEach(observer -> {
            try {
                observer.onStepEntered(step, context);
            } catch (Exception e) {
                log.warn("Error in observer onStepEntered", e);
            }
        });
    }

    private void notifyStepExited(SpaceStep step, StepTransitionContext context) {
        observers.forEach(observer -> {
            try {
                observer.onStepExited(step, context);
            } catch (Exception e) {
                log.warn("Error in observer onStepExited", e);
            }
        });
    }

    private void notifySwitchStepIteration(SpaceStep fromStep, SpaceStep toStep, int iteration) {
        observers.forEach(observer -> {
            try {
                observer.onSwitchStepIteration(fromStep, toStep, iteration);
            } catch (Exception e) {
                log.warn("Error in observer onSwitchStepIteration", e);
            }
        });
    }

    private void notifyError(SpaceStep currentStep, Throwable error, StepTransitionContext context) {
        observers.forEach(observer -> {
            try {
                observer.onError(currentStep, error, context);
            } catch (Exception e) {
                log.warn("Error in observer onError", e);
            }
        });
    }
}
