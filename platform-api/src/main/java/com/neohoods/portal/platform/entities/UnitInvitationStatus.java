package com.neohoods.portal.platform.entities;

public enum UnitInvitationStatus {
    PENDING,
    ACCEPTED,
    REJECTED;

    public static UnitInvitationStatus fromString(String status) {
        if (status == null) {
            return null;
        }
        return UnitInvitationStatus.valueOf(status.toUpperCase());
    }
}


