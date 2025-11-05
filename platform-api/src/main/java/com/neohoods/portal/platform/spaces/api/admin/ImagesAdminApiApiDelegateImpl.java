package com.neohoods.portal.platform.spaces.api.admin;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.ImagesAdminApiApiDelegate;
import com.neohoods.portal.platform.model.SpaceImage;
import com.neohoods.portal.platform.spaces.entities.SpaceImageEntity;
import com.neohoods.portal.platform.spaces.services.ImagesService;
import org.springframework.http.codec.multipart.Part;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ImagesAdminApiApiDelegateImpl implements ImagesAdminApiApiDelegate {

    @Autowired
    private ImagesService imagesService;

    @Override
    public Mono<ResponseEntity<Flux<SpaceImage>>> getSpaceImages(UUID spaceId, ServerWebExchange exchange) {
        // Get space images from service
        List<SpaceImageEntity> entities = imagesService
                .getSpaceImages(spaceId);

        List<SpaceImage> images = entities.stream()
                .map(this::convertToApiModel)
                .toList();

        return Mono.just(ResponseEntity.ok(Flux.fromIterable(images)));
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteImage(UUID imageId, ServerWebExchange exchange) {
        // Delete image using service
        imagesService.deleteImage(imageId);
        return Mono.just(ResponseEntity.noContent().<Void>build());
    }

    @Override
    public Mono<ResponseEntity<SpaceImage>> uploadSpaceImage(
            UUID spaceId, org.springframework.http.codec.multipart.Part file, String altText, Boolean isPrimary,
            Integer orderIndex,
            ServerWebExchange exchange) {
        // Convert single Part to List for service
        List<org.springframework.http.codec.multipart.Part> parts = file != null 
            ? List.of(file) 
            : List.of();
        
        // Upload image using service
        SpaceImageEntity entity = imagesService.uploadSpaceImage(spaceId, parts, altText, isPrimary, orderIndex);

        SpaceImage image = convertToApiModel(entity);
        return Mono.just(ResponseEntity.ok(image));
    }

    @Override
    public Mono<ResponseEntity<SpaceImage>> addExternalImage(
            UUID spaceId, URI url, String altText, Boolean isPrimary, Integer orderIndex, ServerWebExchange exchange) {
        // Add external image using service
        SpaceImageEntity entity = imagesService.addExternalImage(spaceId, url, altText, isPrimary, orderIndex);
        SpaceImage image = convertToApiModel(entity);
        return Mono.just(ResponseEntity.ok(image));
    }

    @Override
    public Mono<ResponseEntity<SpaceImage>> updateImage(
            UUID imageId, String altText, Boolean isPrimary, Integer orderIndex, ServerWebExchange exchange) {
        // Update image using service
        SpaceImageEntity entity = imagesService
                .updateImage(imageId, altText, isPrimary, orderIndex);

        SpaceImage image = convertToApiModel(entity);
        return Mono.just(ResponseEntity.ok(image));
    }

    // Helper method to convert entity to API model
    private SpaceImage convertToApiModel(SpaceImageEntity entity) {
        SpaceImage image = new SpaceImage();
        image.setId(entity.getId());
        image.setSpaceId(entity.getSpace().getId());
        image.setUrl(java.net.URI.create(entity.getUrl()));
        image.setFileName(entity.getFileName());
        image.setMimeType(entity.getMimeType());
        image.setFileSize(entity.getFileSize());
        image.setAltText(entity.getAltText());
        image.setIsPrimary(entity.getIsPrimary());
        image.setOrderIndex(entity.getOrderIndex());
        return image;
    }
}