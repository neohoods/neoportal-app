package com.neohoods.portal.platform.assistant.model;

/**
 * Enum representing the current step in the reservation workflow.
 * 
 * The reservation workflow follows a 7-step process:
 * 1. REQUEST_SPACE_INFO - User asks for information about spaces
 * 2. CHOOSE_SPACE - User chooses a specific space to reserve
 * 3. CHOOSE_PERIOD - User chooses the reservation period (dates and times)
 * 4. CONFIRM_RESERVATION_SUMMARY - Automatically show reservation summary after period is chosen
 * 5. COMPLETE_RESERVATION - Ask user for confirmation and create reservation if confirmed
 * 6. PAYMENT_INSTRUCTIONS - Show payment link if payment is required
 * 7. PAYMENT_CONFIRMED - Background step (handled by payment webhook)
 */
public enum ReservationStep {
    /**
     * Step 1: Request space information
     * - User asks for information about available spaces
     * - Agent can call list_spaces or get_space_info to provide information
     * - Once user chooses a space, move to Step 2
     */
    REQUEST_SPACE_INFO,

    /**
     * Step 2: Choose the space
     * - User has chosen a specific space
     * - Agent must extract spaceId UUID from list_spaces or user's choice
     * - Once spaceId is found and stored, move to Step 3
     */
    CHOOSE_SPACE,

    /**
     * Step 3: Choose the period
     * - Agent has spaceId from Step 2
     * - Agent must get dates and times from user or extract from message
     * - Optionally call check_space_availability to verify availability
     * - Once dates and times are collected, automatically move to Step 4
     */
    CHOOSE_PERIOD,

    /**
     * Step 4: Confirm reservation summary (automatic)
     * - Agent has spaceId, startDate, endDate, startTime, endTime
     * - Automatically triggered after Step 3 completes
     * - Agent must display a summary of the reservation (without creating it)
     * - After showing summary, move to Step 5
     */
    CONFIRM_RESERVATION_SUMMARY,

    /**
     * Step 5: Complete reservation
     * - Agent has shown the summary in Step 4
     * - Agent must ask user for confirmation (or detect confirmation in user message)
     * - If user confirms, call create_reservation
     * - After successful creation, move to Step 6
     */
    COMPLETE_RESERVATION,

    /**
     * Step 6: Payment instructions
     * - Reservation has been created successfully
     * - If payment is required, call generate_payment_link
     * - Show payment URL and instructions to user
     * - Workflow is complete (Step 7 happens in background via webhook)
     */
    PAYMENT_INSTRUCTIONS,

    /**
     * Step 7: Payment confirmed (background)
     * - Handled automatically by Stripe webhook
     * - Payment confirmation message is sent via MatrixNotificationRouterService
     * - Not managed by the agent workflow
     */
    PAYMENT_CONFIRMED
}

