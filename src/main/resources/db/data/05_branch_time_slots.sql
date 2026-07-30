-- 신규 지점(02_branches.sql, id 1~20)의 향후 5일치(오늘 포함) 30분 단위 예약 슬롯.
-- README에는 "ReservationHoldService.lockTimeSlot이 lazy 생성하니 시드 불필요"라고 돼있지만,
-- AI 환전소 추천(RECOMMAND.feature.data_fetch.fetch_branch_candidates)은 branch_time_slots에
-- 해당 날짜/시각 행이 이미 있고 remaining>0이어야 후보로 잡히므로, 즉시 테스트 가능하도록 미리 심는다.
-- remaining은 각 지점의 time_slot_capacity를 그대로 사용.
INSERT INTO branch_time_slots (branch_id, slot_date, slot_time, remaining, created_at, updated_at)
SELECT b.id, d.slot_date, t.slot_time, b.time_slot_capacity, NOW(6), NOW(6)
FROM branches b
CROSS JOIN (
    SELECT CURDATE() + INTERVAL n DAY AS slot_date
    FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) days
) d
CROSS JOIN (
    SELECT '08:00:00' AS slot_time UNION ALL SELECT '08:30:00' UNION ALL
    SELECT '09:00:00' UNION ALL SELECT '09:30:00' UNION ALL
    SELECT '10:00:00' UNION ALL SELECT '10:30:00' UNION ALL
    SELECT '11:00:00' UNION ALL SELECT '11:30:00' UNION ALL
    SELECT '12:00:00' UNION ALL SELECT '12:30:00' UNION ALL
    SELECT '13:00:00' UNION ALL SELECT '13:30:00' UNION ALL
    SELECT '14:00:00' UNION ALL SELECT '14:30:00' UNION ALL
    SELECT '15:00:00' UNION ALL SELECT '15:30:00' UNION ALL
    SELECT '16:00:00' UNION ALL SELECT '16:30:00' UNION ALL
    SELECT '17:00:00' UNION ALL SELECT '17:30:00' UNION ALL
    SELECT '18:00:00' UNION ALL SELECT '18:30:00' UNION ALL
    SELECT '19:00:00' UNION ALL SELECT '19:30:00' UNION ALL
    SELECT '20:00:00' UNION ALL SELECT '20:30:00'
) t
WHERE b.id BETWEEN 1 AND 20;
