package com.neohoods.portal.platform.assistant.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the structured response from the LLM for reservation workflow steps.
 * This is the protocol between LLM and backend for state machine transitions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationStepResponse {
    /**
     * Status of the step processing
     * - PENDING: LLM needs more conversation with user
     * - COMPLETED: Step is complete, can proceed to next step
     * - CANCELED: User wants to cancel/change, should clear context
     */
    @JsonProperty("status")
    private StepStatus status;

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
        PENDING,   // LLM needs more conversation
        COMPLETED, // Step is complete
        CANCELED   // User wants to cancel/change
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
        return status == StepStatus.CANCELED;
    }

    /**
     * Checks if the response is pending (needs more conversation)
     */
    public boolean isPending() {
        return status == StepStatus.PENDING;
    }
}

