package com.neohoods.portal.platform.assistant.model;

/**
 * Enum representing the current step in the space workflow.
 * 
 * The space workflow follows a 6-step process:
 * 1. REQUEST_SPACE_INFO - User asks for information about spaces
 * 2. CHOOSE_SPACE - User chooses a specific space AND period (dates and times) to reserve
 * 3. CONFIRM_RESERVATION_SUMMARY - Automatically show reservation summary after
 * space and period are chosen
 * 4. COMPLETE_RESERVATION - Ask user for confirmation and create reservation if
 * confirmed
 * 5. PAYMENT_INSTRUCTIONS - Show payment link if payment is required
 * 6. PAYMENT_CONFIRMED - Background step (handled by payment webhook)
 */
public enum SpaceStep {
    /**
     * Step 1: Request space information
     * - User asks for information about available spaces
     * - Agent can call list_spaces or get_space_info to provide information
     * - Once user chooses a space, move to Step 2
     */
    REQUEST_SPACE_INFO,

    /**
     * Step 2: Choose the space and period
     * - User chooses a specific space AND reservation period (dates and times)
     * - Agent must extract spaceId UUID from list_spaces or user's choice
     * - Agent must extract dates and times from user message or ask for them
     * - Optionally call check_space_availability to verify availability
     * - Once both spaceId and period are collected, automatically move to Step 3
     */
    CHOOSE_SPACE,

    /**
     * Step 3: Confirm reservation summary (automatic)
     * - Agent has spaceId, startDate, endDate, startTime, endTime
     * - Automatically triggered after Step 2 completes
     * - Agent must display a summary of the reservation (without creating it)
     * - After showing summary, move to Step 4
     */
    CONFIRM_RESERVATION_SUMMARY,

    /**
     * Step 5: Complete reservation
     * - Agent has shown the summary in Step 4
     * - Agent must ask user for confirmation (or detect confirmation in user
     * message)
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
