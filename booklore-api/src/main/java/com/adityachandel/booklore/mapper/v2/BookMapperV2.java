package com.adityachandel.booklore.mapper.v2;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.LibraryPath;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.CategoryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import org.mapstruct.*;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface BookMapperV2 {

    @Mapping(source = "library.id", target = "libraryId")
    @Mapping(source = "libraryPath", target = "libraryPath", qualifiedByName = "mapLibraryPathIdOnly")
    @Mapping(target = "metadata", qualifiedByName = "mapMetadata")
    Book toDTO(BookEntity bookEntity);

    @Named("mapMetadata")
    @Mapping(target = "authors", source = "authors", qualifiedByName = "mapAuthors")
    @Mapping(target = "categories", source = "categories", qualifiedByName = "mapCategories")
    BookMetadata mapMetadata(BookMetadataEntity metadataEntity);

    @Named("mapAuthors")
    default Set<String> mapAuthors(Set<AuthorEntity> authors) {
        return authors == null ? Set.of() :
                authors.stream().map(AuthorEntity::getName).collect(Collectors.toSet());
    }

    @Named("mapCategories")
    default Set<String> mapCategories(Set<CategoryEntity> categories) {
        return categories == null ? Set.of() :
                categories.stream().map(CategoryEntity::getName).collect(Collectors.toSet());
    }

    @Named("mapLibraryPathIdOnly")
    default LibraryPath mapLibraryPathIdOnly(LibraryPathEntity entity) {
        if (entity == null) return null;
        return LibraryPath.builder()
                .id(entity.getId())
                .build();
    }
}