package com.neohoods.portal.platform.entities;

public enum AnnouncementCategory {
    COMMUNITY_EVENT("CommunityEvent"),
    LOST_AND_FOUND("LostAndFound"),
    SAFETY_ALERT("SafetyAlert"),
    MAINTENANCE_NOTICE("MaintenanceNotice"),
    SOCIAL_GATHERING("SocialGathering"),
    OTHER("Other");

    private final String value;

    AnnouncementCategory(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AnnouncementCategory fromValue(String value) {
        for (AnnouncementCategory category : AnnouncementCategory.values()) {
            if (category.value.equals(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }

    public static AnnouncementCategory fromOpenApiAnnouncementCategory(
            com.neohoods.portal.platform.model.AnnouncementCategory openApiCategory) {
        if (openApiCategory == null) {
            return OTHER;
        }
        return fromValue(openApiCategory.getValue());
    }

    public com.neohoods.portal.platform.model.AnnouncementCategory toOpenApiAnnouncementCategory() {
        return com.neohoods.portal.platform.model.AnnouncementCategory.fromValue(this.value);
    }
}
