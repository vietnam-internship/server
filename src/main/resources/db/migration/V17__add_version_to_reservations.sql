-- discussion#41 방안 2(낙관적 락): 취소/redeem/만료 스윕이 같은 예약을 동시에 처리하면
-- 재고·슬롯이 중복 복원될 수 있는 문제(재현 확인: 동시 취소 10건 중 5건 성공, 재고 400 초과 복원).
-- Reservation 행 자체에는 락이 없었던 게 근본 원인이라, @Version 컬럼으로 낙관적 락을 건다.
ALTER TABLE reservations
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
