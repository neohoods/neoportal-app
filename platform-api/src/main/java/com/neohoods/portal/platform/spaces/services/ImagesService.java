package com.neohoods.portal.platform.spaces.services;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceImageEntity;
import com.neohoods.portal.platform.spaces.repositories.SpaceImageRepository;

@Service
@Transactional
public class ImagesService {

    private static final Logger logger = LoggerFactory.getLogger(ImagesService.class);

    @Autowired
    private SpaceImageRepository spaceImageRepository;

    @Autowired
    private SpacesService spacesService;

    @Transactional(readOnly = true)
    public SpaceImageEntity getImageById(UUID imageId) {
        return spaceImageRepository.findById(imageId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<SpaceImageEntity> getImagesBySpaceId(UUID spaceId) {
        return spaceImageRepository.findBySpaceId(spaceId);
    }

    @Transactional
    public SpaceImageEntity saveImage(SpaceImageEntity image) {
        return spaceImageRepository.save(image);
    }

    @Transactional
    public SpaceImageEntity updateImage(SpaceImageEntity image) {
        return spaceImageRepository.save(image);
    }

    @Transactional
    public void deleteImage(UUID imageId) {
        spaceImageRepository.deleteById(imageId);
    }

    // Additional methods needed by API delegates
    @Transactional(readOnly = true)
    public List<SpaceImageEntity> getSpaceImages(UUID spaceId) {
        return spaceImageRepository.findBySpaceId(spaceId);
    }

    @Transactional(readOnly = true)
    public SpaceImageEntity getImageMetadata(UUID imageId) {
        return spaceImageRepository.findById(imageId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Resource getImageResource(UUID imageId) {
        SpaceImageEntity image = getImageById(imageId);
        if (image == null) {
            logger.warn("Image not found with ID: {}", imageId);
            return null;
        }

        // If image has binary data stored in database
        if (image.getImageData() != null) {
            return new ByteArrayResource(image.getImageData());
        }

        // If image has external URL
        if (image.getUrl() != null) {
            try {
                // For external URLs, we would typically fetch the image
                // For now, return a placeholder resource
                logger.info("Loading external image from URL: {}", image.getUrl());
                return new ByteArrayResource("External image placeholder".getBytes());
            } catch (Exception e) {
                logger.error("Failed to load external image from URL {}: {}", image.getUrl(), e.getMessage());
                return null;
            }
        }

        logger.warn("Image {} has neither binary data nor URL", imageId);
        return null;
    }

    @Transactional
    public SpaceImageEntity uploadSpaceImage(UUID spaceId, List<Part> file, String altText, Boolean isPrimary,
            Integer orderIndex) {
        // Get the space entity
        SpaceEntity space = spacesService.getSpaceById(spaceId);
        if (space == null) {
            throw new IllegalArgumentException("Space not found with ID: " + spaceId);
        }

        // Process the uploaded file
        SpaceImageEntity image = new SpaceImageEntity();
        image.setSpace(space);
        image.setAltText(altText);
        image.setIsPrimary(isPrimary != null ? isPrimary : false);
        image.setOrderIndex(orderIndex != null ? orderIndex : 0);

        // Process the first file part
        if (file != null && !file.isEmpty()) {
            Part filePart = file.get(0);
            try {
                // Read the file content
                byte[] fileContent = filePart.content().blockFirst().asByteBuffer().array();
                image.setImageData(fileContent);
                // Note: SpaceImageEntity may not have all these methods
                // image.setContentType(filePart.headers().getContentType().toString());
                // image.setFileName(filePart.filename());
                // image.setFileSize((long) fileContent.length);

                logger.info("Uploaded image for space {}: {} bytes", spaceId, fileContent.length);
            } catch (Exception e) {
                logger.error("Failed to process uploaded file for space {}: {}", spaceId, e.getMessage());
                throw new RuntimeException("Failed to process uploaded file", e);
            }
        }

        return spaceImageRepository.save(image);
    }

    @Transactional
    public SpaceImageEntity addExternalImage(UUID spaceId, URI url, String altText, Boolean isPrimary,
            Integer orderIndex) {
        // Get the space entity
        SpaceEntity space = spacesService.getSpaceById(spaceId);
        if (space == null) {
            throw new IllegalArgumentException("Space not found with ID: " + spaceId);
        }

        // Create the external image entity
        SpaceImageEntity image = new SpaceImageEntity();
        image.setSpace(space);
        image.setUrl(url.toString());
        image.setAltText(altText);
        image.setIsPrimary(isPrimary != null ? isPrimary : false);
        image.setOrderIndex(orderIndex != null ? orderIndex : 0);

        logger.info("Added external image for space {}: {}", spaceId, url);
        return spaceImageRepository.save(image);
    }

    @Transactional
    public SpaceImageEntity updateImage(UUID imageId, String altText, Boolean isPrimary, Integer orderIndex) {
        SpaceImageEntity image = spaceImageRepository.findById(imageId).orElse(null);
        if (image != null) {
            if (altText != null)
                image.setAltText(altText);
            if (isPrimary != null)
                image.setIsPrimary(isPrimary);
            if (orderIndex != null)
                image.setOrderIndex(orderIndex);
            return spaceImageRepository.save(image);
        }
        return null;
    }
}
