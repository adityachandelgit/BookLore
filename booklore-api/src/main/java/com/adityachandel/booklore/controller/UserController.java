package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.request.ChangePasswordRequest;
import com.adityachandel.booklore.model.dto.request.ChangeUserPasswordRequest;
import com.adityachandel.booklore.model.dto.settings.BookPreferences;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.request.UserUpdateRequest;
import com.adityachandel.booklore.service.user.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<BookLoreUser> getMyself() {
        return ResponseEntity.ok(userService.getMyself());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityUtil.canViewUserProfile(#id)")
    public ResponseEntity<BookLoreUser> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getBookLoreUser(id));
    }

    @GetMapping
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<List<BookLoreUser>> getAllUsers() {
        return ResponseEntity.ok(userService.getBookLoreUsers());
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<BookLoreUser> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest updateRequest) {
        BookLoreUser updatedUser = userService.updateUser(id, updateRequest);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityUtil.isAdmin() and not @securityUtil.isSelf(#id)")
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
    }

    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/change-user-password")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<?> changeUserPassword(@RequestBody ChangeUserPasswordRequest request) {
        userService.changeUserPassword(request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/book-preferences")
    @PreAuthorize("@securityUtil.isSelf(#id)")
    public void updateUserBookPreferences(@PathVariable Long id, @RequestBody BookPreferences bookPreferences) {
        userService.updateBookPreferences(id, bookPreferences);
    }

}
