ALTER TABLE users
    ADD COLUMN branch_id BIGINT NULL AFTER role,
    ADD CONSTRAINT fk_users_branch FOREIGN KEY (branch_id) REFERENCES branches (id);
