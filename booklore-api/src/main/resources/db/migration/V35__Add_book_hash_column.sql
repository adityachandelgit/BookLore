ALTER TABLE book
    ADD COLUMN initial_hash TEXT,
    ADD COLUMN current_hash TEXT;

CREATE INDEX IF NOT EXISTS idx_book_initial_hash ON book(initial_hash);
CREATE INDEX IF NOT EXISTS idx_book_current_hash ON book(current_hash);