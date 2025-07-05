package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.LibraryPath;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.CategoryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import org.mapstruct.*;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {BookMetadataMapper.class, ShelfMapper.class})
public interface BookMapper {

    @Mapping(source = "library.id", target = "libraryId")
    @Mapping(source = "libraryPath", target = "libraryPath", qualifiedByName = "mapLibraryPathIdOnly")
    @Mapping(source = "metadata", target = "metadata")
    @Mapping(source = "shelves", target = "shelves")
    Book toBook(BookEntity bookEntity);

    @Mapping(source = "library.id", target = "libraryId")
    @Mapping(source = "libraryPath", target = "libraryPath", qualifiedByName = "mapLibraryPathIdOnly")
    @Mapping(source = "metadata", target = "metadata")
    @Mapping(source = "shelves", target = "shelves")
    Book toBookWithDescription(BookEntity bookEntity, @Context boolean includeDescription);

    @Mapping(source = "library.id", target = "libraryId")
    @Mapping(source = "libraryPath", target = "libraryPath", qualifiedByName = "mapLibraryPathIdOnly")
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "shelves", ignore = true)
    Book toBookWithoutMetadataAndShelves(BookEntity bookEntity);

    default Set<String> mapAuthors(Set<AuthorEntity> authors) {
        if (authors == null) return null;
        return authors.stream()
                .map(AuthorEntity::getName)
                .collect(Collectors.toSet());
    }

    default Set<String> mapCategories(Set<CategoryEntity> categories) {
        if (categories == null) return null;
        return categories.stream()
                .map(CategoryEntity::getName)
                .collect(Collectors.toSet());
    }

    @Named("mapLibraryPathIdOnly")
    default LibraryPath mapLibraryPathIdOnly(LibraryPathEntity entity) {
        if (entity == null) return null;
        return LibraryPath.builder()
                .id(entity.getId())
                .build();
    }
}