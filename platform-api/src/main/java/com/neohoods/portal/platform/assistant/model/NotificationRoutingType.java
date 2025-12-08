package com.neohoods.portal.platform.assistant.model;

/**
 * Enum representing the routing type for Matrix notifications
 */
public enum NotificationRoutingType {
    /**
     * Direct message to the user
     */
    DM,

    /**
     * General room (or equivalent)
     */
    GENERAL_ROOM,

    /**
     * IT room for technical notifications
     */
    IT_ROOM,

    /**
     * Room specific to a unit/residence
     */
    UNIT_ROOM,

    /**
     * Custom room specified by the caller
     */
    CUSTOM_ROOM
}


