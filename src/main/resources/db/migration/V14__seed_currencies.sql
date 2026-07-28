-- currencies 테이블은 스키마만 있고(V12) 초기 데이터가 없어서, CurrencySyncService가
-- findAll()로 기존 row만 갱신하는 구조상 시드가 없으면 /currencies가 항상 빈 목록이고
-- 예약 생성(assertAmountWithinLimits)도 CURRENCY_NOT_FOUND로 항상 실패한다.
-- buy_rate/sell_rate는 exchangerate-api.com 실시간 응답 기준으로 CurrencySyncService와
-- 동일한 공식(1/targetRate ± 1.5% 스프레드)으로 계산한 값이며, 다음 날 자정 배치가 갱신한다.
INSERT INTO currencies (code, country, buy_rate, sell_rate, created_at, updated_at) VALUES
    ('USD', 'United States', 1445.5957, 1489.6240, NOW(6), NOW(6)),
    ('EUR', 'Euro Area', 1646.6891, 1696.8420, NOW(6), NOW(6)),
    ('JPY', 'Japan', 8.8341, 9.1031, NOW(6), NOW(6)),
    ('GBP', 'United Kingdom', 1925.5581, 1984.2046, NOW(6), NOW(6)),
    ('CNY', 'China', 212.2845, 218.7500, NOW(6), NOW(6)),
    ('VND', 'Vietnam', 0.0553, 0.0570, NOW(6), NOW(6));
