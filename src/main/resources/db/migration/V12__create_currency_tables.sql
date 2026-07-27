CREATE TABLE currencies (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    code       VARCHAR(10)  NOT NULL,
    country    VARCHAR(100) NOT NULL,
    buy_rate   DOUBLE       NOT NULL,
    sell_rate   DOUBLE       NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_currencies_code UNIQUE (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE exchange_rate_histories (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    currency_id BIGINT      NOT NULL,
    rate        DOUBLE      NOT NULL,
    recorded_at DATE        NOT NULL,
    PRIMARY KEY (id),
    KEY idx_exchange_rate_histories_currency_date (currency_id, recorded_at),
    CONSTRAINT fk_exchange_rate_histories_currency FOREIGN KEY (currency_id) REFERENCES currencies (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
