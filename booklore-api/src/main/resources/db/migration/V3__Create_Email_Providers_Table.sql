CREATE TABLE IF NOT EXISTS email_provider
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    host       VARCHAR(255) NOT NULL,
    port       INT          NOT NULL,
    username   VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    auth       BOOLEAN      NOT NULL,
    start_tls  BOOLEAN      NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (name, host, username)
);