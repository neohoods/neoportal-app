package com.neohoods.portal.platform.entities;

import java.util.UUID;

import com.neohoods.portal.platform.model.CustomPage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "custom_pages")
public class CustomPageEntity {
    @Id
    private UUID id;

    @Column(unique = true)
    private String ref;

    @Column(name = "page_order")
    private Integer order;

    @Enumerated(EnumType.STRING)
    private CustomPagePosition position;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    public CustomPage toCustomPage() {
        return CustomPage.builder()
                .id(id)
                .ref(ref)
                .order(order)
                .position(position != null ? position.toOpenApiPosition() : null)
                .title(title)
                .content(content)
                .build();
    }

    public static CustomPageEntity fromCustomPage(CustomPage customPage) {
        return CustomPageEntity.builder()
                .id(customPage.getId() != null ? customPage.getId() : UUID.randomUUID())
                .ref(customPage.getRef())
                .order(customPage.getOrder())
                .position(CustomPagePosition.fromOpenApiPosition(customPage.getPosition()))
                .title(customPage.getTitle())
                .content(customPage.getContent())
                .build();
    }
}