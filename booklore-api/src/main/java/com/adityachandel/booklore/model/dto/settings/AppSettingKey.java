package com.adityachandel.booklore.model.dto.settings;

import lombok.Getter;

@Getter
public enum AppSettingKey {
    QUICK_BOOK_MATCH("quick_book_match", true),
    OIDC_PROVIDER_DETAILS("oidc_provider_details", true),
    OIDC_AUTO_PROVISION_DETAILS("oidc_auto_provision_details", true),
    SIDEBAR_LIBRARY_SORTING("sidebar_library_sorting", true),
    SIDEBAR_SHELF_SORTING("sidebar_shelf_sorting", true),
    METADATA_PROVIDER_SETTINGS("metadata_provider_settings", true),
    METADATA_MATCH_WEIGHTS("metadata_match_weights", true),
    METADATA_PERSISTENCE_SETTINGS("metadata_persistence_settings", true),

    AUTO_BOOK_SEARCH("auto_book_search", false),
    COVER_IMAGE_RESOLUTION("cover_image_resolution", false),
    SIMILAR_BOOK_RECOMMENDATION("similar_book_recommendation", false),
    UPLOAD_FILE_PATTERN("upload_file_pattern", false),
    MOVE_FILE_PATTERN("move_file_pattern", false),
    OPDS_SERVER_ENABLED("opds_server_enabled", false),
    OIDC_ENABLED("oidc_enabled", false),
    CBX_CACHE_SIZE_IN_MB("cbx_cache_size_in_mb", false),
    PDF_CACHE_SIZE_IN_MB("pdf_cache_size_in_mb", false),
    BOOK_DELETION_ENABLED("book_deletion_enabled", false),
    MAX_FILE_UPLOAD_SIZE_IN_MB("max_file_upload_size_in_mb", false);

    private final String dbKey;
    private final boolean isJson;

    AppSettingKey(String dbKey, boolean isJson) {
        this.dbKey = dbKey;
        this.isJson = isJson;
    }

    @Override
    public String toString() {
        return dbKey;
    }

    public static AppSettingKey fromDbKey(String dbKey) {
        for (AppSettingKey key : values()) {
            if (key.dbKey.equals(dbKey)) {
                return key;
            }
        }
        throw new IllegalArgumentException("Unknown setting key: " + dbKey);
    }
}