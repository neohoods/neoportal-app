package com.neohoods.portal.platform.assistant.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the structured response from the LLM for space workflow steps.
 * This is the protocol between LLM and backend for state machine transitions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpaceStepResponse {
    /**
     * Status of the step processing
     * - ANSWER_USER: Return message to user without state change
     * - ASK_USER: Return message to user and remember we're waiting for answer on
     * this step
     * - SWITCH_STEP: Move to next step without user interaction (internal message
     * passed to next step)
     * - CANCEL: User wants to cancel/change, should clear context
     * - COMPLETED: Workflow is complete
     * - ERROR: LLM couldn't complete, reset context to REQUEST_SPACE_INFO
     */
    @JsonProperty("status")
    private StepStatus status;

    /**
     * Next step when status == SWITCH_STEP
     */
    @JsonProperty("nextStep")
    private SpaceStep nextStep;

    /**
     * Internal message passed to next step when status == SWITCH_STEP
     * This is NOT shown to the user, but used as context for the next step's LLM
     */
    @JsonProperty("internalMessage")
    private String internalMessage;

    /**
     * Response message to return to the user
     * Usually a conversational message from the LLM
     */
    @JsonProperty("response")
    private String response;

    /**
     * Space ID UUID if identified, null otherwise
     * Required for COMPLETED status in Step 2 and beyond
     */
    @JsonProperty("spaceId")
    private String spaceId;

    /**
     * Reservation period if identified, null otherwise
     * Required for COMPLETED status in Step 3 and beyond
     */
    @JsonProperty("period")
    private ReservationPeriod period;

    /**
     * User's preferred locale (language code, e.g., "fr", "en")
     * Should be identified in Step 1 and stored in context
     */
    @JsonProperty("locale")
    private String locale;

    /**
     * Enum for step status
     */
    public enum StepStatus {
        ANSWER_USER, // Return message to user without state change
        ASK_USER, // Return message to user and remember we're waiting for answer on this step
        SWITCH_STEP, // Move to next step without user interaction
        CANCEL, // User wants to cancel/change
        COMPLETED, // Workflow is complete
        ERROR, // LLM couldn't complete, reset context
        RETRY // Retry the same handler with enriched context (see section 14)
    }

    /**
     * Checks if the response has a spaceId
     */
    public boolean hasSpaceId() {
        return spaceId != null && !spaceId.isEmpty();
    }

    /**
     * Checks if the response has a complete period
     */
    public boolean hasCompletePeriod() {
        return period != null && period.isComplete();
    }

    /**
     * Checks if the response is in a completed state
     */
    public boolean isCompleted() {
        return status == StepStatus.COMPLETED;
    }

    /**
     * Checks if the response indicates cancellation
     */
    public boolean isCanceled() {
        return status == StepStatus.CANCEL;
    }

    /**
     * Checks if the response is asking user (waiting for answer)
     */
    public boolean isAskingUser() {
        return status == StepStatus.ASK_USER;
    }

    /**
     * Checks if the response is switching to next step
     */
    public boolean isSwitchingStep() {
        return status == StepStatus.SWITCH_STEP;
    }

    /**
     * Checks if the response is an error
     */
    public boolean isError() {
        return status == StepStatus.ERROR;
    }
}
