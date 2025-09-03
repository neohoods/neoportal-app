package com.neohoods.portal.platform.entities;

public enum CustomPagePosition {
    FOOTER_LINKS("footer-links"),
    COPYRIGHT("copyright"),
    FOOTER_HELP("footer-help");

    private final String value;

    CustomPagePosition(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CustomPagePosition fromValue(String value) {
        for (CustomPagePosition position : CustomPagePosition.values()) {
            if (position.value.equals(value)) {
                return position;
            }
        }
        throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }

    public static CustomPagePosition fromOpenApiPosition(
            com.neohoods.portal.platform.model.CustomPage.PositionEnum openApiPosition) {
        if (openApiPosition == null) {
            return null;
        }
        return CustomPagePosition.fromValue(openApiPosition.getValue());
    }

    public com.neohoods.portal.platform.model.CustomPage.PositionEnum toOpenApiPosition() {
        return com.neohoods.portal.platform.model.CustomPage.PositionEnum.fromValue(this.value);
    }
}
