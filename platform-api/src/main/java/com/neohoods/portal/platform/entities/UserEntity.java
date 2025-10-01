package com.neohoods.portal.platform.entities;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.neohoods.portal.platform.model.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
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
@Table(name = "users")
public class UserEntity {
    @Id
    private UUID id;
    private String username;
    private String password;
    private String email;
    @Column(name = "first_name")
    private String firstName;
    @Column(name = "last_name")
    private String lastName;
    @Column(name = "flat_number")
    private String flatNumber;
    @Column(name = "street_address")
    private String streetAddress;
    private String city;
    @Column(name = "postal_code")
    private String postalCode;
    private String country;
    @Column(name = "preferred_language")
    private String preferredLanguage;
    @Column(name = "avatar_url")
    private String avatarUrl;
    @Builder.Default
    private boolean disabled = false;
    @Column(name = "is_email_verified")
    @Builder.Default
    private boolean isEmailVerified = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    private Set<String> roles;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserStatus status = UserStatus.WAITING_FOR_EMAIL;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type")
    private UserType type;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<PropertyEntity> properties;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private NotificationSettingsEntity notificationSettings;

    public User.UserBuilder toUser() {
        return User.builder()
                .id(id)
                .username(username)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .flatNumber(flatNumber)
                .streetAddress(streetAddress)
                .city(city)
                .postalCode(postalCode)
                .country(country)
                .preferredLanguage(preferredLanguage)
                .avatarUrl(avatarUrl)
                .disabled(disabled)
                .isEmailVerified(isEmailVerified)
                .roles(Arrays.asList("hub")) // TODO
                .type(type != null ? type.toOpenApiUserType() : null)
                .properties(properties != null
                        ? properties.stream().map(PropertyEntity::toProperty).collect(Collectors.toList())
                        : List.of());
    }

    public Locale getLocale() {
        if (preferredLanguage == null || preferredLanguage.trim().isEmpty()) {
            return Locale.ENGLISH; // Default to English
        }
        // Handle both formats: "en" and "en-US"
        String[] parts = preferredLanguage.split("-");
        return parts.length > 1
                ? new Locale(parts[0], parts[1])
                : new Locale(parts[0]);
    }
}
