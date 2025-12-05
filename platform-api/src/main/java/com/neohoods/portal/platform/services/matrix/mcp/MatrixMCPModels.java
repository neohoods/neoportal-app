package com.neohoods.portal.platform.services.matrix.mcp;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * MCP tool models
 */
public class MatrixMCPModels {

        @Data
        @Builder
        public static class MCPTool {
                private String name;
                private String description;
                private Map<String, Object> inputSchema;
        }

        @Data
        @Builder
        public static class MCPToolResult {
                private boolean isError;
                private List<MCPContent> content;
        }

        @Data
        @Builder
        public static class MCPContent {
                private String type; // "text" or "image"
                private String text;
                private String data; // base64 for images
                private String mimeType; // for images
        }
}

