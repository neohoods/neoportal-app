package com.neohoods.portal.platform.entities;

public enum NotificationType {
    ADMIN_NEW_USER("admin-new-user"),
    NEW_ANNOUNCEMENT("new-announcement");

    private String emailTemplate;

    NotificationType(String emailTemplate) {
        this.emailTemplate = emailTemplate;
    }

    public String getEmailTemplate() {
        return "notification-" + emailTemplate;
    }
}