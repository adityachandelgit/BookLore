package com.adityachandel.booklore.model.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookShelfKey implements Serializable {

    private Long bookId;
    private Long shelfId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BookShelfKey that)) return false;
        return Objects.equals(bookId, that.bookId) && Objects.equals(shelfId, that.shelfId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bookId, shelfId);
    }
}