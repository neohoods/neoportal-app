package com.neohoods.portal.platform.services.matrix.space;

import java.util.Map;

/**
 * Room configuration from YAML
 */
@SuppressWarnings("unchecked")
public class MatrixRoomConfig {
    private String name;
    private String description;
    private String image;
    private Boolean autoJoin;

    public MatrixRoomConfig(Map<String, Object> map) {
        this.name = (String) map.get("name");
        this.description = (String) map.get("description");
        this.image = (String) map.get("image");
        this.autoJoin = map.get("auto-join") != null ? (Boolean) map.get("auto-join") : true;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getImage() {
        return image;
    }

    public Boolean getAutoJoin() {
        return autoJoin;
    }
}

