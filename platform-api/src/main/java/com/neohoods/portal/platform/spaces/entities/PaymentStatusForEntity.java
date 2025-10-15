package com.neohoods.portal.platform.spaces.entities;

public enum PaymentStatusForEntity {
    PENDING("En attente"),
    PROCESSING("En cours"),
    SUCCEEDED("Réussi"),
    FAILED("Échoué"),
    CANCELLED("Annulé"),
    REFUNDED("Remboursé"),
    PARTIALLY_REFUNDED("Partiellement remboursé");

    private final String displayName;

    PaymentStatusForEntity(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
