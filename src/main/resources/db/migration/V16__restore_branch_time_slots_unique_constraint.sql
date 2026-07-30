-- branch_time_slots에 V8에서 정의했던 uk_branch_time_slots_branch_slot 제약이
-- 운영 DB에는 실제로 없는 상태(스키마 드리프트)였다. 이 unique 제약은
-- BranchTimeSlotRepository.ensureExists()의 INSERT ... ON DUPLICATE KEY UPDATE가
-- 동시성 방어를 위해 전적으로 의존하는 제약이라, 없으면 같은 (branch_id, slot_date,
-- slot_time)에 중복 행이 쌓여 lockForUpdate() 단건 조회가 깨진다
-- (docs/bug-report-branch-time-slots-duplicate.md 참고).
--
-- 주의: 이미 중복 행이 있는 환경(예: prod)에서는 아래 ALTER TABLE이
-- "Duplicate entry" 에러로 실패한다 — 이 마이그레이션을 적용하기 전에 반드시
-- docs/bug-report-branch-time-slots-duplicate.md의 "즉시 조치" 절차로 중복 행을
-- 먼저 정리해야 한다.
--
-- issue#88: V8__create_branch_time_slots_table.sql 자체엔 이 제약이 처음부터 정의돼
-- 있어서(스키마 드리프트는 운영 DB에서만 있었던 특수 상황), V8부터 순서대로 실행되는
-- 신규(빈) DB에서는 이 ALTER가 "이미 있는 제약을 또 추가"하려다 실패했다(Testcontainers
-- 테스트, 새 개발자 로컬 셋업, CI 전부 영향받음). 존재 여부를 먼저 확인해 없을 때만
-- 추가하도록 조건부 DDL로 바꾼다 — 신규 DB(이미 있음, no-op)와 드리프트 있던 운영 DB
-- (없음, 추가) 양쪽 다 안전하다.
SET @constraint_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'branch_time_slots'
      AND CONSTRAINT_NAME = 'uk_branch_time_slots_branch_slot'
);

SET @ddl = IF(@constraint_exists = 0,
    'ALTER TABLE branch_time_slots ADD CONSTRAINT uk_branch_time_slots_branch_slot UNIQUE (branch_id, slot_date, slot_time)',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
