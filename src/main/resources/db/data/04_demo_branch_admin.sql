-- 데모용 BRANCH_ADMIN 계정 — branch_id=1(TravelX Myeongdong, 02_branches.sql)에 매핑.
-- AdminQrScanPage.tsx가 BRANCH_ID=1로 하드코딩돼 있어 이 지점과 짝을 맞췄다.
-- 비밀번호는 bcrypt 해시(BCryptPasswordEncoder로 검증 완료) — 평문은 아래 주석 참고, 데모 끝나면 계정 삭제 권장.
--   email: branch1.admin@travelx.test
--   password: Demo1234!
INSERT INTO users (name, email, password, role, phone_verified, branch_id, created_at, updated_at) VALUES
    ('Myeongdong Branch Admin', 'branch1.admin@travelx.test',
     '$2b$10$XTGBS8jpe.pz7ZLuB1F.u.tuYdTLaMzBwYdVVO9cvmK6U8qbgJHh6',
     'BRANCH_ADMIN', 1, 1, NOW(6), NOW(6));
