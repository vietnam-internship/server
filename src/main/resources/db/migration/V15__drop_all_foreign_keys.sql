-- 지금까지(V1~V14) 추가된 모든 FK 제약을 제거한다.
-- FK가 걸려있으면 데모/테스트 중 지점·유저·예약 데이터를 자유롭게 초기화하기 어렵다
-- (자식 테이블 행부터 순서대로 지워야 FK 위반이 안 남). 참조 무결성은 이제
-- 애플리케이션 레이어(서비스 코드)에서만 보장한다.

ALTER TABLE refresh_token DROP FOREIGN KEY fk_refresh_token_user;

ALTER TABLE branch_supported_currencies DROP FOREIGN KEY fk_branch_supported_currencies_branch;

ALTER TABLE branch_currency_rates DROP FOREIGN KEY fk_branch_currency_rates_branch;

ALTER TABLE reservations DROP FOREIGN KEY fk_reservations_user;
ALTER TABLE reservations DROP FOREIGN KEY fk_reservations_branch;

ALTER TABLE branch_time_slots DROP FOREIGN KEY fk_branch_time_slots_branch;

ALTER TABLE ai_recommendation_signals DROP FOREIGN KEY fk_ai_recommendation_signals_recommendation;
ALTER TABLE ai_recommendation_signals DROP FOREIGN KEY fk_ai_recommendation_signals_signal;

ALTER TABLE payments DROP FOREIGN KEY fk_payments_reservation;

ALTER TABLE branch_recommendations DROP FOREIGN KEY fk_rec_user;

ALTER TABLE branch_recommendation_items DROP FOREIGN KEY fk_item_rec;
ALTER TABLE branch_recommendation_items DROP FOREIGN KEY fk_item_branch;

ALTER TABLE branch_recommendation_clicks DROP FOREIGN KEY fk_click_item;

ALTER TABLE exchange_rate_histories DROP FOREIGN KEY fk_exchange_rate_histories_currency;

ALTER TABLE users DROP FOREIGN KEY fk_users_branch;
