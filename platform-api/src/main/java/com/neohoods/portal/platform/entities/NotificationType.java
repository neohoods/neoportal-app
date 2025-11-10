package com.neohoods.portal.platform.entities;

public enum NotificationType {
    ADMIN_NEW_USER("admin-new-user"),
    NEW_ANNOUNCEMENT("new-announcement"),
    RESERVATION("reservation"),
    UNIT_INVITATION("unit-invitation"),
    UNIT_JOIN_REQUEST("unit-join-request");

    private String emailTemplate;

    NotificationType(String emailTemplate) {
        this.emailTemplate = emailTemplate;
    }

    public String getEmailTemplate() {
        return "notification-" + emailTemplate;
    }
}