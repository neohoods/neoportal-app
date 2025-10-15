package com.neohoods.portal.platform.spaces.entities;

public enum SpaceTypeForEntity {
    GUEST_ROOM("Chambre d'amis"),
    COMMON_ROOM("Salle commune"),
    COWORKING("Espace coworking"),
    PARKING("Place de parking");

    private final String displayName;

    SpaceTypeForEntity(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
