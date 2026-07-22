CREATE TABLE branch_currency_rates (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    branch_id              BIGINT       NOT NULL,
    currency_code          VARCHAR(10)  NOT NULL,
    preferential_rate      DOUBLE       NOT NULL DEFAULT 0,
    reservation_only_stock DOUBLE       NOT NULL DEFAULT 0,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_branch_currency_rates_branch_currency UNIQUE (branch_id, currency_code),
    CONSTRAINT fk_branch_currency_rates_branch FOREIGN KEY (branch_id) REFERENCES branches (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
