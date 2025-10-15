package com.neohoods.portal.platform.spaces.entities;

public enum SpaceStatusForEntity {
    ACTIVE("Actif"),
    INACTIVE("Inactif"),
    MAINTENANCE("Maintenance"),
    DISABLED("Désactivé");

    private final String displayName;

    SpaceStatusForEntity(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
