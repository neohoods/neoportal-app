package com.neohoods.portal.platform.spaces.api.spaces;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.ImagesApiApiDelegate;
import com.neohoods.portal.platform.model.ImageMetadata;
import com.neohoods.portal.platform.spaces.entities.SpaceImageEntity;
import com.neohoods.portal.platform.spaces.services.ImagesService;
import reactor.core.publisher.Mono;

@Service
public class ImagesApiApiDelegateImpl implements ImagesApiApiDelegate {

    @Autowired
    private ImagesService imagesService;

    @Override
    public Mono<ResponseEntity<Resource>> getImage(UUID imageId, ServerWebExchange exchange) {
        // Get image resource from service
        Resource resource = imagesService.getImageResource(imageId);

        return Mono.just(ResponseEntity.ok(resource));
    }

    @Override
    public Mono<ResponseEntity<ImageMetadata>> getImageMetadata(UUID imageId, ServerWebExchange exchange) {
        // Get image metadata from service
        SpaceImageEntity entity = imagesService.getImageMetadata(imageId);

        ImageMetadata metadata = new ImageMetadata();
        metadata.setId(imageId);
        metadata.setFileName(entity.getFileName());
        metadata.setMimeType(entity.getMimeType());
        metadata.setFileSize(entity.getFileSize());
        return Mono.just(ResponseEntity.ok(metadata));
    }

}
