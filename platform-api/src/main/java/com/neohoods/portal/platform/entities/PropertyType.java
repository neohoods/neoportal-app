package com.neohoods.portal.platform.entities;

public enum PropertyType {
    APARTMENT,
    GARAGE,
    PARKING,
    COMMERCIAL,
    OTHER;

    /**
     * Converts from OpenAPI PropertyType to Entity PropertyType
     */
    public static PropertyType fromOpenApiPropertyType(
            com.neohoods.portal.platform.model.PropertyType openApiPropertyType) {
        if (openApiPropertyType == null) {
            return null;
        }
        return PropertyType.valueOf(openApiPropertyType.getValue());
    }

    /**
     * Converts from Entity PropertyType to OpenAPI PropertyType
     */
    public com.neohoods.portal.platform.model.PropertyType toOpenApiPropertyType() {
        return com.neohoods.portal.platform.model.PropertyType.fromValue(this.name());
    }
}
