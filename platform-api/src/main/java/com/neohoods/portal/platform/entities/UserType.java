package com.neohoods.portal.platform.entities;

public enum UserType {
    ADMIN,
    OWNER,
    LANDLORD,
    TENANT,
    SYNDIC,
    EXTERNAL,
    CONTRACTOR,
    COMMERCIAL_PROPERTY_OWNER,
    GUEST;

    /**
     * Converts from OpenAPI UserType to Entity UserType
     */
    public static UserType fromOpenApiUserType(com.neohoods.portal.platform.model.UserType openApiUserType) {
        if (openApiUserType == null) {
            return null;
        }
        return UserType.valueOf(openApiUserType.getValue());
    }

    /**
     * Converts from Entity UserType to OpenAPI UserType
     */
    public com.neohoods.portal.platform.model.UserType toOpenApiUserType() {
        return com.neohoods.portal.platform.model.UserType.fromValue(this.name());
    }
}
