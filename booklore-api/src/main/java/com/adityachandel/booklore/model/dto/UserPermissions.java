package com.adityachandel.booklore.model.dto;

import lombok.Data;

@Data
public class UserPermissions {
    private boolean isAdmin;
    private boolean canUpload;
    private boolean canDownload;
    private boolean canEditMetadata;
    private boolean canManipulateLibrary;
    private boolean canEmailBook;
}
