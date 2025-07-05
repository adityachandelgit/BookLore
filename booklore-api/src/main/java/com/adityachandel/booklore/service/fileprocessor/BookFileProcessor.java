package com.adityachandel.booklore.service.fileprocessor;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;

public interface BookFileProcessor {
    Book processNewFile(LibraryFile libraryFile);
    boolean generateCover(BookEntity bookEntity);
}
