package com.neohoods.portal.platform.assistant.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a reservation period with start and end dates/times.
 * Used in the LLM protocol for reservation workflow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationPeriod {
    @JsonProperty("startDate")
    private String startDate; // Format: YYYY-MM-DD or null

    @JsonProperty("endDate")
    private String endDate; // Format: YYYY-MM-DD or null

    @JsonProperty("startTime")
    private String startTime; // Format: HH:mm or null

    @JsonProperty("endTime")
    private String endTime; // Format: HH:mm or null

    /**
     * Checks if the period has all required date information
     */
    public boolean hasDates() {
        return startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty();
    }

    /**
     * Checks if the period has time information
     */
    public boolean hasTimes() {
        return startTime != null && !startTime.isEmpty() && endTime != null && !endTime.isEmpty();
    }

    /**
     * Checks if the period is complete (has dates and optionally times)
     */
    public boolean isComplete() {
        return hasDates();
    }
}

