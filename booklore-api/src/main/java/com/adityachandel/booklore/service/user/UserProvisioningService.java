package com.adityachandel.booklore.service.user;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.UserCreateRequest;
import com.adityachandel.booklore.model.dto.request.InitialUserRequest;
import com.adityachandel.booklore.model.dto.settings.OidcAutoProvisionDetails;
import com.adityachandel.booklore.model.dto.settings.SidebarSortOption;
import com.adityachandel.booklore.model.dto.settings.UserSettingKey;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.CbxPageSpread;
import com.adityachandel.booklore.model.enums.CbxPageViewMode;
import com.adityachandel.booklore.model.enums.ProvisioningMethod;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.repository.ShelfRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@AllArgsConstructor
public class UserProvisioningService {

    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final LibraryRepository libraryRepository;
    private final ShelfRepository shelfRepository;
    private final ObjectMapper objectMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public boolean isInitialUserAlreadyProvisioned() {
        return userRepository.count() > 0;
    }

    @Transactional
    public void provisionInitialUser(InitialUserRequest request) {
        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDefaultPassword(false);
        user.setProvisioningMethod(ProvisioningMethod.LOCAL);

        UserPermissionsEntity perms = new UserPermissionsEntity();
        perms.setPermissionAdmin(true);
        perms.setPermissionUpload(true);
        perms.setPermissionDownload(true);
        perms.setPermissionEditMetadata(true);
        perms.setPermissionManipulateLibrary(true);
        perms.setPermissionEmailBook(true);

        user.setPermissions(perms);
        createUser(user);
    }

    @Transactional
    public void provisionInternalUser(UserCreateRequest request) {
        Optional<BookLoreUserEntity> existingUser = userRepository.findByUsername(request.getUsername());
        if (existingUser.isPresent()) {
            throw ApiError.USERNAME_ALREADY_TAKEN.createException(request.getUsername());
        }

        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setUsername(request.getUsername());
        user.setDefaultPassword(true);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setProvisioningMethod(ProvisioningMethod.LOCAL);

        UserPermissionsEntity permissions = new UserPermissionsEntity();
        permissions.setUser(user);
        permissions.setPermissionUpload(request.isPermissionUpload());
        permissions.setPermissionDownload(request.isPermissionDownload());
        permissions.setPermissionEditMetadata(request.isPermissionEditMetadata());
        permissions.setPermissionEmailBook(request.isPermissionEmailBook());
        permissions.setPermissionAdmin(request.isPermissionAdmin());
        user.setPermissions(permissions);

        if (request.getSelectedLibraries() != null && !request.getSelectedLibraries().isEmpty()) {
            List<LibraryEntity> libraries = libraryRepository.findAllById(request.getSelectedLibraries());
            user.setLibraries(new ArrayList<>(libraries));
        }

        createUser(user);
    }

    @Transactional
    public BookLoreUserEntity provisionOidcUser(String username, String email, String name, OidcAutoProvisionDetails oidcAutoProvisionDetails) {
        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setName(name);
        user.setDefaultPassword(false);
        user.setPasswordHash("OIDC_USER_" + UUID.randomUUID());
        user.setProvisioningMethod(ProvisioningMethod.OIDC);

        UserPermissionsEntity perms = new UserPermissionsEntity();
        List<String> defaultPermissions = oidcAutoProvisionDetails.getDefaultPermissions();
        if (defaultPermissions != null) {
            perms.setPermissionUpload(defaultPermissions.contains("permissionUpload"));
            perms.setPermissionDownload(defaultPermissions.contains("permissionDownload"));
            perms.setPermissionEditMetadata(defaultPermissions.contains("permissionEditMetadata"));
            perms.setPermissionManipulateLibrary(defaultPermissions.contains("permissionManipulateLibrary"));
            perms.setPermissionEmailBook(defaultPermissions.contains("permissionEmailBook"));
        }
        user.setPermissions(perms);

        List<Long> defaultLibraryIds = oidcAutoProvisionDetails.getDefaultLibraryIds();
        if (defaultLibraryIds != null && !defaultLibraryIds.isEmpty()) {
            List<LibraryEntity> libraries = libraryRepository.findAllById(defaultLibraryIds);
            user.setLibraries(new ArrayList<>(libraries));
        }

        return createUser(user);
    }

    @Deprecated
    @Transactional
    public BookLoreUserEntity provisionRemoteUser(String name, String username, String email, String groups) {
        boolean isAdmin = false;
        if (groups != null && appProperties.getRemoteAuth().getAdminGroup() != null) {
            String groupsContent = groups.trim();
            if (groupsContent.startsWith("[") && groupsContent.endsWith("]")) {
                groupsContent = groupsContent.substring(1, groupsContent.length() - 1);
            }
            List<String> groupsList = Arrays.asList(groupsContent.split("\\s+"));
            isAdmin = groupsList.contains(appProperties.getRemoteAuth().getAdminGroup());
            log.debug("Remote-Auth: user {} will be admin: {}", username, isAdmin);
        }

        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setUsername(username);
        user.setName(name != null ? name : username);
        user.setEmail(email);
        user.setDefaultPassword(false);
        user.setProvisioningMethod(ProvisioningMethod.REMOTE);
        user.setPasswordHash("RemoteUser_" + RandomStringUtils.secure().nextAlphanumeric(32));

        UserPermissionsEntity permissions = new UserPermissionsEntity();
        permissions.setUser(user);
        permissions.setPermissionUpload(true);
        permissions.setPermissionDownload(true);
        permissions.setPermissionEditMetadata(true);
        permissions.setPermissionEmailBook(true);
        permissions.setPermissionAdmin(isAdmin);
        user.setPermissions(permissions);

        if (isAdmin) {
            List<LibraryEntity> libraries = libraryRepository.findAll();
            user.setLibraries(new ArrayList<>(libraries));
        }

        return createUser(user);
    }

    @Transactional
    protected BookLoreUserEntity createUser(BookLoreUserEntity user) {
        user = userRepository.save(user);

        if (user.getShelves() == null || user.getShelves().isEmpty()) {
            ShelfEntity shelfEntity = ShelfEntity.builder()
                    .user(user)
                    .name("Favorites")
                    .icon("heart")
                    .build();
            shelfRepository.save(shelfEntity);
        }

        addUserSetting(user, UserSettingKey.PER_BOOK_SETTING, buildDefaultPerBookSetting());
        addUserSetting(user, UserSettingKey.PDF_READER_SETTING, buildDefaultPdfReaderSetting());
        addUserSetting(user, UserSettingKey.EPUB_READER_SETTING, buildDefaultEpubReaderSetting());
        addUserSetting(user, UserSettingKey.CBX_READER_SETTING, buildDefaultCbxReaderSetting());
        addUserSetting(user, UserSettingKey.SIDEBAR_LIBRARY_SORTING, buildDefaultSidebarLibrarySorting());
        addUserSetting(user, UserSettingKey.SIDEBAR_SHELF_SORTING, buildDefaultSidebarShelfSorting());

        return user;
    }

    private void addUserSetting(BookLoreUserEntity user, UserSettingKey key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            UserSettingEntity setting = UserSettingEntity.builder()
                    .user(user)
                    .settingKey(key.toString())
                    .settingValue(json)
                    .build();
            user.getSettings().add(setting);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize setting {} for user {}", key, user.getUsername(), e);
        }
    }

    private BookLoreUser.UserSettings.PerBookSetting buildDefaultPerBookSetting() {
        return BookLoreUser.UserSettings.PerBookSetting.builder()
                .epub(BookLoreUser.UserSettings.PerBookSetting.GlobalOrIndividual.Individual)
                .pdf(BookLoreUser.UserSettings.PerBookSetting.GlobalOrIndividual.Individual)
                .cbx(BookLoreUser.UserSettings.PerBookSetting.GlobalOrIndividual.Individual)
                .build();
    }

    private BookLoreUser.UserSettings.PdfReaderSetting buildDefaultPdfReaderSetting() {
        return BookLoreUser.UserSettings.PdfReaderSetting.builder()
                .pageSpread("odd")
                .pageZoom("page-fit")
                .build();
    }

    private BookLoreUser.UserSettings.EpubReaderSetting buildDefaultEpubReaderSetting() {
        return BookLoreUser.UserSettings.EpubReaderSetting.builder()
                .theme("white")
                .font("serif")
                .fontSize(150)
                .flow("paginated")
                .build();
    }

    public BookLoreUser.UserSettings.CbxReaderSetting buildDefaultCbxReaderSetting() {
        return BookLoreUser.UserSettings.CbxReaderSetting.builder()
                .pageViewMode(CbxPageViewMode.SINGLE_PAGE)
                .pageSpread(CbxPageSpread.ODD)
                .build();
    }

    private Object buildDefaultSidebarLibrarySorting() {
        return SidebarSortOption.builder()
                .field("id")
                .order("asc")
                .build();
    }

    private Object buildDefaultSidebarShelfSorting() {
        return SidebarSortOption.builder()
                .field("id")
                .order("asc")
                .build();
    }
}