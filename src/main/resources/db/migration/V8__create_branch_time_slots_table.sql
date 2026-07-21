CREATE TABLE branch_time_slots (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    branch_id  BIGINT      NOT NULL,
    slot_date  DATE        NOT NULL,
    slot_time  TIME        NOT NULL,
    remaining  INT         NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_branch_time_slots_branch_slot UNIQUE (branch_id, slot_date, slot_time),
    CONSTRAINT fk_branch_time_slots_branch FOREIGN KEY (branch_id) REFERENCES branches (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
