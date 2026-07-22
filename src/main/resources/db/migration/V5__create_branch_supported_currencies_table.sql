CREATE TABLE branch_supported_currencies (
    branch_id     BIGINT      NOT NULL,
    currency_code VARCHAR(10) NOT NULL,
    PRIMARY KEY (branch_id, currency_code),
    CONSTRAINT fk_branch_supported_currencies_branch FOREIGN KEY (branch_id) REFERENCES branches (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
