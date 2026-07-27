CREATE TABLE recommendation_signals (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    currency_id BIGINT      NOT NULL,
    signal_type VARCHAR(10) NOT NULL,
    window_days INT         NOT NULL,
    value       DOUBLE      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_recommendation_signals_currency_id (currency_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE ai_recommendations (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    currency_id       BIGINT       NOT NULL,
    recommendation    VARCHAR(20)  NOT NULL,
    rationale         TEXT         NULL,
    confidence_score  DOUBLE       NOT NULL,
    model_version     VARCHAR(50)  NOT NULL,
    expires_at        DATETIME(6)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ai_recommendations_currency_id (currency_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE ai_recommendation_signals (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    recommendation_id  BIGINT      NOT NULL,
    signal_id          BIGINT      NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ai_recommendation_signals_recommendation_id (recommendation_id),
    KEY idx_ai_recommendation_signals_signal_id (signal_id),
    CONSTRAINT fk_ai_recommendation_signals_recommendation
        FOREIGN KEY (recommendation_id) REFERENCES ai_recommendations (id),
    CONSTRAINT fk_ai_recommendation_signals_signal
        FOREIGN KEY (signal_id) REFERENCES recommendation_signals (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE backtest_results (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    currency_id      BIGINT       NOT NULL,
    strategy_type    VARCHAR(30)  NOT NULL,
    period_start     DATE         NOT NULL,
    period_end       DATE         NOT NULL,
    total_signals    INT          NOT NULL,
    correct_signals  INT          NOT NULL,
    accuracy_rate    DOUBLE       NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_backtest_results_currency_id (currency_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE macro_indicators (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    country_code    VARCHAR(10)  NOT NULL,
    indicator_type  VARCHAR(50)  NOT NULL,
    value           DOUBLE       NOT NULL,
    recorded_at     DATE         NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
