ALTER TABLE book
    ADD COLUMN initial_hash TEXT,
    ADD COLUMN current_hash TEXT,
    ADD COLUMN deleted BOOLEAN DEFAULT FALSE,
    ADD COLUMN deleted_on TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_book_initial_hash ON book(initial_hash);
CREATE INDEX IF NOT EXISTS idx_book_current_hash ON book(current_hash);
CREATE INDEX IF NOT EXISTS idx_book_deleted ON book(deleted);
CREATE INDEX IF NOT EXISTS idx_book_deleted_on ON book(deleted_on);