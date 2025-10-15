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
    @Column(name = "sso_enabled")
    private boolean ssoEnabled;
    @Column(name = "sso_client_id")
    private String ssoClientId;
    @Column(name = "sso_client_secret")
    private String ssoClientSecret;
    @Column(name = "sso_token_endpoint")
    private String ssoTokenEndpoint;
    @Column(name = "sso_authorization_endpoint")
    private String ssoAuthorizationEndpoint;
    @Column(name = "sso_scope")
    private String ssoScope;

    // Constructors
    public SettingsEntity() {
    }

    public SettingsEntity(UUID id, boolean isRegistrationEnabled, boolean ssoEnabled, String ssoClientId,
            String ssoClientSecret, String ssoTokenEndpoint, String ssoAuthorizationEndpoint, String ssoScope) {
        this.id = id;
        this.isRegistrationEnabled = isRegistrationEnabled;
        this.ssoEnabled = ssoEnabled;
        this.ssoClientId = ssoClientId;
        this.ssoClientSecret = ssoClientSecret;
        this.ssoTokenEndpoint = ssoTokenEndpoint;
        this.ssoAuthorizationEndpoint = ssoAuthorizationEndpoint;
        this.ssoScope = ssoScope;
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

    public boolean isSsoEnabled() {
        return ssoEnabled;
    }

    public void setSsoEnabled(boolean ssoEnabled) {
        this.ssoEnabled = ssoEnabled;
    }

    public String getSsoClientId() {
        return ssoClientId;
    }

    public void setSsoClientId(String ssoClientId) {
        this.ssoClientId = ssoClientId;
    }

    public String getSsoClientSecret() {
        return ssoClientSecret;
    }

    public void setSsoClientSecret(String ssoClientSecret) {
        this.ssoClientSecret = ssoClientSecret;
    }

    public String getSsoTokenEndpoint() {
        return ssoTokenEndpoint;
    }

    public void setSsoTokenEndpoint(String ssoTokenEndpoint) {
        this.ssoTokenEndpoint = ssoTokenEndpoint;
    }

    public String getSsoAuthorizationEndpoint() {
        return ssoAuthorizationEndpoint;
    }

    public void setSsoAuthorizationEndpoint(String ssoAuthorizationEndpoint) {
        this.ssoAuthorizationEndpoint = ssoAuthorizationEndpoint;
    }

    public String getSsoScope() {
        return ssoScope;
    }

    public void setSsoScope(String ssoScope) {
        this.ssoScope = ssoScope;
    }

    // Builder pattern
    public static SettingsEntityBuilder builder() {
        return new SettingsEntityBuilder();
    }

    public static class SettingsEntityBuilder {
        private UUID id;
        private boolean isRegistrationEnabled;
        private boolean ssoEnabled;
        private String ssoClientId;
        private String ssoClientSecret;
        private String ssoTokenEndpoint;
        private String ssoAuthorizationEndpoint;
        private String ssoScope;

        public SettingsEntityBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public SettingsEntityBuilder isRegistrationEnabled(boolean isRegistrationEnabled) {
            this.isRegistrationEnabled = isRegistrationEnabled;
            return this;
        }

        public SettingsEntityBuilder ssoEnabled(boolean ssoEnabled) {
            this.ssoEnabled = ssoEnabled;
            return this;
        }

        public SettingsEntityBuilder ssoClientId(String ssoClientId) {
            this.ssoClientId = ssoClientId;
            return this;
        }

        public SettingsEntityBuilder ssoClientSecret(String ssoClientSecret) {
            this.ssoClientSecret = ssoClientSecret;
            return this;
        }

        public SettingsEntityBuilder ssoTokenEndpoint(String ssoTokenEndpoint) {
            this.ssoTokenEndpoint = ssoTokenEndpoint;
            return this;
        }

        public SettingsEntityBuilder ssoAuthorizationEndpoint(String ssoAuthorizationEndpoint) {
            this.ssoAuthorizationEndpoint = ssoAuthorizationEndpoint;
            return this;
        }

        public SettingsEntityBuilder ssoScope(String ssoScope) {
            this.ssoScope = ssoScope;
            return this;
        }

        public SettingsEntity build() {
            return new SettingsEntity(id, isRegistrationEnabled, ssoEnabled, ssoClientId, ssoClientSecret,
                    ssoTokenEndpoint, ssoAuthorizationEndpoint, ssoScope);
        }
    }
}