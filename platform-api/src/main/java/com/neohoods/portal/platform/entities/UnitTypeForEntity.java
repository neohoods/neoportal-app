package com.neohoods.portal.platform.entities;

public enum UnitTypeForEntity {
    FLAT,
    GARAGE,
    PARKING,
    COMMERCIAL,
    OTHER;

    public static UnitTypeForEntity fromString(String type) {
        if (type == null) {
            return FLAT; // Default
        }
        return UnitTypeForEntity.valueOf(type.toUpperCase());
    }
}

