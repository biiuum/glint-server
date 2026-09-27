package com.glint.server.biz.system;

public record SystemStatus(
        String status,
        String database,
        String redis,
        String schemaVersion,
        String expectedSchemaVersion
) {
}
