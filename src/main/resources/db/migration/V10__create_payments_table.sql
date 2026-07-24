CREATE TABLE payments (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    reservation_id        BIGINT       NOT NULL,
    provider              VARCHAR(20)  NOT NULL,
    idempotency_key       VARCHAR(64)  NOT NULL,
    pg_payment_intent_id  VARCHAR(64)  NULL,
    amount                DOUBLE       NOT NULL,
    currency_code         VARCHAR(10)  NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    requested_at          DATETIME(6)  NULL,
    approved_at           DATETIME(6)  NULL,
    failed_at             DATETIME(6)  NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_reservation_id UNIQUE (reservation_id),
    CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_payments_pg_payment_intent_id UNIQUE (pg_payment_intent_id),
    CONSTRAINT fk_payments_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

ALTER TABLE reservations
    ADD COLUMN payment_expires_at DATETIME(6) NULL AFTER locked_rate;
