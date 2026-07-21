CREATE TABLE users (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50)  NOT NULL,
    email      VARCHAR(255) NULL,
    password   VARCHAR(255) NULL,
    google_id  VARCHAR(255) NULL,
    role       VARCHAR(20)  NOT NULL,
    phone      VARCHAR(20)  NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_google_id UNIQUE (google_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
