package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.dto.UserPermissions;
import lombok.Data;

import java.util.Set;

@Data
public class UserCreateRequest {
    private String username;
    private String password;
    private String name;
    private String email;

    private UserPermissions permissions;
    private Set<Long> assignedLibraries;
}