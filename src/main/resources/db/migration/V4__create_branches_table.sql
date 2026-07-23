CREATE TABLE branches (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    name                   VARCHAR(100) NOT NULL,
    address                VARCHAR(255) NOT NULL,
    latitude               DOUBLE       NOT NULL,
    longitude              DOUBLE       NOT NULL,
    phone                  VARCHAR(20)  NOT NULL,
    business_hours         VARCHAR(255) NOT NULL,
    pickup_location_detail VARCHAR(255) NULL,
    time_slot_capacity     INT          NOT NULL,
    active                 TINYINT(1)   NOT NULL DEFAULT 1,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
