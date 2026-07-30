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

ALTER TABLE branch_time_slots
  ADD CONSTRAINT uk_branch_time_slots_branch_slot UNIQUE (branch_id, slot_date, slot_time);
