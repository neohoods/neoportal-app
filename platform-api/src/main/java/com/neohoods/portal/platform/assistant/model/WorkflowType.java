package com.neohoods.portal.platform.assistant.model;

/**
 * Enum representing the different workflow types that can be identified by the router.
 */
public enum WorkflowType {
    /**
     * General questions about the copropriété (copro)
     * Uses RAG + info tools
     */
    GENERAL,

    /**
     * Questions about residents
     * Uses get_resident_info, get_emergency_numbers
     */
    RESIDENT_INFO,

    /**
     * Space reservations
     * Uses list_spaces, check_space_availability, create_reservation, generate_payment_link
     */
    SPACE,

    /**
     * Support requests (for future implementation)
     */
    SUPPORT,

    /**
     * Help questions (what can you do, etc.)
     * Uses pre-templated response, no LLM needed
     */
    HELP
}


