package com.neohoods.portal.platform.entities;

public enum UnitMemberRole {
    ADMIN,
    MEMBER;

    public static UnitMemberRole fromString(String role) {
        if (role == null) {
            return null;
        }
        return UnitMemberRole.valueOf(role.toUpperCase());
    }
}









