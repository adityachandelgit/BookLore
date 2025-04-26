package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.UserPermissions;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.UserPermissionsEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookLoreUserMapper {

    @Mapping(source = "permissions", target = "permissions")
    @Mapping(source = "libraries", target = "assignedLibraries")
    BookLoreUser toDto(BookLoreUserEntity entity);

    default UserPermissions mapPermissions(UserPermissionsEntity permissions) {
        if (permissions == null) {
            return null;
        }
        UserPermissions dto = new UserPermissions();
        dto.setAdmin(permissions.isPermissionAdmin());
        dto.setCanUpload(permissions.isPermissionUpload());
        dto.setCanDownload(permissions.isPermissionDownload());
        dto.setCanManipulateLibrary(permissions.isPermissionManipulateLibrary());
        dto.setCanEditMetadata(permissions.isPermissionEditMetadata());
        dto.setCanEmailBook(permissions.isPermissionEmailBook());
        return dto;
    }
}