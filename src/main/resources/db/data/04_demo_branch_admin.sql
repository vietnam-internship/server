-- 지점(02_branches.sql, id 1~20)마다 BRANCH_ADMIN 계정 1명씩 매핑.
-- 비밀번호는 전부 동일한 bcrypt 해시(BCryptPasswordEncoder로 검증 완료) — 평문은 아래 주석 참고,
-- 데모 끝나면 계정 삭제 권장.
--   email: branch{N}.admin@travelx.test (N = branch id)
--   password: Demo1234!
INSERT INTO users (name, email, password, role, phone_verified, branch_id, created_at, updated_at) VALUES
    ('Thanh Thuy 1 Branch Admin', 'branch1.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 1, NOW(6), NOW(6)),
    ('Thanh Thuy 2 Branch Admin', 'branch2.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 2, NOW(6), NOW(6)),
    ('Thanh Thuy 3 Branch Admin', 'branch3.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 3, NOW(6), NOW(6)),
    ('Thanh Thuy 4 Branch Admin', 'branch4.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 4, NOW(6), NOW(6)),
    ('Thanh Thuy 5 Branch Admin', 'branch5.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 5, NOW(6), NOW(6)),
    ('Thanh Thuy 6 Branch Admin', 'branch6.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 6, NOW(6), NOW(6)),
    ('Thanh Thuy 7 Branch Admin', 'branch7.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 7, NOW(6), NOW(6)),
    ('Thanh Thuy 8 Branch Admin', 'branch8.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 8, NOW(6), NOW(6)),
    ('Thanh Thuy 9 Branch Admin', 'branch9.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 9, NOW(6), NOW(6)),
    ('Thanh Thuy 10 Branch Admin', 'branch10.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 10, NOW(6), NOW(6)),
    ('Cam Le 1 Branch Admin', 'branch11.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 11, NOW(6), NOW(6)),
    ('Cam Le 2 Branch Admin', 'branch12.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 12, NOW(6), NOW(6)),
    ('Cam Le 3 Branch Admin', 'branch13.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 13, NOW(6), NOW(6)),
    ('Cam Le 4 Branch Admin', 'branch14.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 14, NOW(6), NOW(6)),
    ('Cam Le 5 Branch Admin', 'branch15.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 15, NOW(6), NOW(6)),
    ('Cam Le 6 Branch Admin', 'branch16.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 16, NOW(6), NOW(6)),
    ('Cam Le 7 Branch Admin', 'branch17.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 17, NOW(6), NOW(6)),
    ('Cam Le 8 Branch Admin', 'branch18.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 18, NOW(6), NOW(6)),
    ('Cam Le 9 Branch Admin', 'branch19.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 19, NOW(6), NOW(6)),
    ('Cam Le 10 Branch Admin', 'branch20.admin@travelx.test', '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6', 'BRANCH_ADMIN', 1, 20, NOW(6), NOW(6));
