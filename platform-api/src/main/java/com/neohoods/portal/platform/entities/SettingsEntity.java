package com.neohoods.portal.platform.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "settings")
public class SettingsEntity {
    @Id
    private String id;
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
}