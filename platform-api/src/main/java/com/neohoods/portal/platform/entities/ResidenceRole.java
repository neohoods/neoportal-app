package com.neohoods.portal.platform.entities;

public enum ResidenceRole {
    PROPRIETAIRE,
    BAILLEUR,
    MANAGER,
    TENANT;

    public static ResidenceRole fromString(String role) {
        if (role == null) {
            return null;
        }
        return ResidenceRole.valueOf(role.toUpperCase());
    }
}

