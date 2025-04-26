package com.adityachandel.booklore.model.dto;

import com.adityachandel.booklore.model.dto.settings.BookPreferences;
import lombok.Data;

import java.util.List;

@Data
public class BookLoreUser {
    private Long id;
    private String username;
    private boolean isDefaultPassword;
    private String name;
    private String email;

    private UserPermissions permissions;
    private List<Library> assignedLibraries;
    private BookPreferences bookPreferences;
}
