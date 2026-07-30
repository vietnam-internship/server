-- Test branches. Manual fixture — see README.md. business_hours format is parsed by
-- BusinessHoursParser: "<요일/평일/주말> HH:mm-HH:mm" segments separated by commas.
--
-- 20개 지점, 두 실제 위치 반경 1km 이내에 랜덤 배치(강/바다 회피):
--   id 1~10:  171 Thanh Thuy, Hai Chau, Da Nang 기준 (anchor 16.082917, 108.215562) — 강이
--             동쪽에 가까워 서쪽으로 편향 배치.
--   id 11~20: Nguyen Huu Tho, Cam Le, Da Nang 기준 (anchor 16.0088, 108.2133) — 사방으로 배치.
INSERT INTO branches
    (id, name, address, latitude, longitude, phone, business_hours, pickup_location_detail,
     time_slot_capacity, active, created_at, updated_at)
VALUES
    (1, 'TravelX Thanh Thuy 1', '171 Thanh Thuy, Hai Chau, Da Nang 550000', 16.082917, 108.215562,
     '0236-700-1001', '평일 08:00-20:00, 주말 09:00-17:00', '1F Desk', 6, 1, NOW(6), NOW(6)),
    (2, 'TravelX Thanh Thuy 2', '45 Thanh Thuy, Hai Chau, Da Nang 550000', 16.081115, 108.212757,
     '0236-700-1002', '평일 08:00-20:00, 주말 09:00-17:00', '1F Desk', 5, 1, NOW(6), NOW(6)),
    (3, 'TravelX Thanh Thuy 3', '88 Bach Dang, Hai Chau, Da Nang 550000', 16.084268, 108.212290,
     '0236-700-1003', '평일 08:00-20:00, 주말 09:00-17:00', '2F Desk', 4, 1, NOW(6), NOW(6)),
    (4, 'TravelX Thanh Thuy 4', '12 Tran Phu, Hai Chau, Da Nang 550000', 16.079764, 108.214160,
     '0236-700-1004', '평일 06:00-22:00, 주말 06:00-22:00', '1F Desk', 8, 1, NOW(6), NOW(6)),
    (5, 'TravelX Thanh Thuy 5', '60 Yen Bai, Hai Chau, Da Nang 550000', 16.085620, 108.210887,
     '0236-700-1005', '평일 08:00-20:00, 주말 09:00-17:00', '1F Desk', 5, 1, NOW(6), NOW(6)),
    (6, 'TravelX Thanh Thuy 6', '9 Le Loi, Hai Chau, Da Nang 550000', 16.077962, 108.211822,
     '0236-700-1006', '평일 08:00-20:00, 주말 09:00-17:00', '1F Desk', 6, 1, NOW(6), NOW(6)),
    (7, 'TravelX Thanh Thuy 7', '150 Hung Vuong, Hai Chau, Da Nang 550000', 16.083818, 108.209018,
     '0236-700-1007', '평일 08:00-20:00, 주말 09:00-17:00', '2F Desk', 4, 1, NOW(6), NOW(6)),
    (8, 'TravelX Thanh Thuy 8', '21 Phan Chau Trinh, Hai Chau, Da Nang 550000', 16.076160, 108.213692,
     '0236-700-1008', '평일 08:00-20:00, 주말 09:00-17:00', '1F Desk', 5, 1, NOW(6), NOW(6)),
    (9, 'TravelX Thanh Thuy 9', '77 Ly Tu Trong, Hai Chau, Da Nang 550000', 16.086971, 108.209485,
     '0236-700-1009', '평일 08:00-20:00, 주말 09:00-17:00', '1F Desk', 7, 1, NOW(6), NOW(6)),
    (10, 'TravelX Thanh Thuy 10', '33 Nguyen Chi Thanh, Hai Chau, Da Nang 550000', 16.079313, 108.207615,
     '0236-700-1010', '평일 08:00-20:00, 주말 09:00-17:00', '1F Desk', 4, 1, NOW(6), NOW(6)),
    (11, 'TravelX Cam Le 1', '123 Nguyen Huu Tho, Cam Le, Da Nang 550000', 16.008800, 108.213300,
     '0236-700-1011', '평일 08:00-20:00, 주말 09:00-17:00', '1F Desk', 6, 1, NOW(6), NOW(6)),
    (12, 'TravelX Cam Le 2', '58 Nguyen Huu Tho, Cam Le, Da Nang 550000', 16.012053, 108.215634,
     '0236-700-1012', '평일 08:00-20:00, 주말 09:00-17:00', '1F Desk', 5, 1, NOW(6), NOW(6)),
    (13, 'TravelX Cam Le 3', '9 Le Van Hien, Cam Le, Da Nang 550000', 16.005647, 108.215634,
     '0236-700-1013', '평일 08:00-20:00, 주말 09:00-17:00', '2F Desk', 4, 1, NOW(6), NOW(6)),
    (14, 'TravelX Cam Le 4', '77 Ton Dan, Cam Le, Da Nang 550000', 16.012053, 108.210966,
     '0236-700-1014', '평일 06:00-22:00, 주말 06:00-22:00', '1F Desk', 8, 1, NOW(6), NOW(6)),
    (15, 'TravelX Cam Le 5', '210 Nguyen Huu Tho, Cam Le, Da Nang 550000', 16.005647, 108.210966,
     '0236-700-1015', '평일 08:00-20:00, 주말 09:00-17:00', '1F Desk', 5, 1, NOW(6), NOW(6)),
    (16, 'TravelX Cam Le 6', '32 Cach Mang Thang Tam, Cam Le, Da Nang 550000', 16.013755, 108.213300,
     '0236-700-1016', '평일 08:00-20:00, 주말 09:00-17:00', '1F Desk', 6, 1, NOW(6), NOW(6)),
    (17, 'TravelX Cam Le 7', '9 Truong Chinh, Cam Le, Da Nang 550000', 16.003845, 108.213300,
     '0236-700-1017', '평일 08:00-20:00, 주말 09:00-17:00', '1F Desk', 4, 1, NOW(6), NOW(6)),
    (18, 'TravelX Cam Le 8', '64 Duy Tan, Cam Le, Da Nang 550000', 16.008800, 108.218434,
     '0236-700-1018', '평일 08:00-20:00, 주말 09:00-17:00', '1F Desk', 5, 1, NOW(6), NOW(6)),
    (19, 'TravelX Cam Le 9', '18 Vo Chi Cong, Cam Le, Da Nang 550000', 16.008800, 108.208166,
     '0236-700-1019', '평일 08:00-20:00, 주말 09:00-17:00', '1F Desk', 7, 1, NOW(6), NOW(6)),
    (20, 'TravelX Cam Le 10', '101 Nguyen Huu Tho, Cam Le, Da Nang 550000', 16.015106, 108.216567,
     '0236-700-1020', '평일 08:00-20:00, 주말 09:00-17:00', '1F Desk', 4, 1, NOW(6), NOW(6));
