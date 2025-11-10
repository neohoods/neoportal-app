package com.neohoods.portal.platform.entities;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "settings")
public class SettingsEntity {
    @Id
    private UUID id;
    @Column(name = "is_registration_enabled")
    private boolean isRegistrationEnabled;

    // Constructors
    public SettingsEntity() {
    }

    public SettingsEntity(UUID id, boolean isRegistrationEnabled) {
        this.id = id;
        this.isRegistrationEnabled = isRegistrationEnabled;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public boolean isRegistrationEnabled() {
        return isRegistrationEnabled;
    }

    public void setRegistrationEnabled(boolean isRegistrationEnabled) {
        this.isRegistrationEnabled = isRegistrationEnabled;
    }

    // Builder pattern
    public static SettingsEntityBuilder builder() {
        return new SettingsEntityBuilder();
    }

    public static class SettingsEntityBuilder {
        private UUID id;
        private boolean isRegistrationEnabled;

        public SettingsEntityBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public SettingsEntityBuilder isRegistrationEnabled(boolean isRegistrationEnabled) {
            this.isRegistrationEnabled = isRegistrationEnabled;
            return this;
        }

        public SettingsEntity build() {
            return new SettingsEntity(id, isRegistrationEnabled);
        }
    }
}