CREATE TABLE branch_recommendations (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    user_id     BIGINT,
    status      VARCHAR(20)   NOT NULL,
    currency    VARCHAR(10)   NOT NULL,
    amount      DECIMAL(18,2) NOT NULL,
    latitude    DECIMAL(10,7) NOT NULL,
    longitude   DECIMAL(10,7) NOT NULL,
    radius_km   DOUBLE        NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_rec_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE branch_recommendation_items (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    recommendation_id   BIGINT        NOT NULL,
    branch_id           BIGINT        NOT NULL,
    ranking             INT           NOT NULL,
    score               DECIMAL(6,4),
    distance_score      DECIMAL(6,4),
    rate_score          DECIMAL(6,4),
    availability_score  DECIMAL(6,4),
    reservation_score   DECIMAL(6,4),
    created_at          DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_rec_ranking (recommendation_id, ranking),
    UNIQUE KEY uq_rec_branch  (recommendation_id, branch_id),
    CONSTRAINT fk_item_rec    FOREIGN KEY (recommendation_id) REFERENCES branch_recommendations (id),
    CONSTRAINT fk_item_branch FOREIGN KEY (branch_id)         REFERENCES branches (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE branch_recommendation_clicks (
    id                     BIGINT      NOT NULL AUTO_INCREMENT,
    recommendation_item_id BIGINT      NOT NULL,
    clicked_at             DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_click_item FOREIGN KEY (recommendation_item_id) REFERENCES branch_recommendation_items (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
