package com.neohoods.portal.platform.entities;

import java.util.UUID;

import com.neohoods.portal.platform.model.Application;

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
@Table(name = "applications")
public class ApplicationEntity {
    @Id
    private UUID id;

    private String name;
    private String url;
    private String icon;
    private String helpText;

    @Builder.Default
    private Boolean disabled = false;

    public Application toApplication() {
        return new Application()
                .id(id)
                .name(name)
                .url(url)
                .icon(icon)
                .helpText(helpText)
                .disabled(disabled);
    }

    public static ApplicationEntity fromApplication(Application application) {
        return ApplicationEntity.builder()
                .id(application.getId() != null ? application.getId() : UUID.randomUUID())
                .name(application.getName())
                .url(application.getUrl())
                .icon(application.getIcon())
                .helpText(application.getHelpText())
                .disabled(application.getDisabled() != null ? application.getDisabled() : false)
                .build();
    }
}
