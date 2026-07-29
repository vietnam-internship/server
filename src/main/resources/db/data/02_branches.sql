-- Test branches. Manual fixture — see README.md. business_hours format is parsed by
-- BusinessHoursParser: "<요일/평일/주말> HH:mm-HH:mm" segments separated by commas.
INSERT INTO branches
    (id, name, address, latitude, longitude, phone, business_hours, pickup_location_detail,
     time_slot_capacity, active, created_at, updated_at)
VALUES
    (1, 'TravelX Myeongdong', '26-28 Myeongdong-gil, Jung-gu, Seoul', 37.5636, 126.9834,
     '02-1234-5678', '평일 09:00-21:00, 주말 10:00-18:00', '2F, Departure hall, TravelX booth',
     5, 1, NOW(6), NOW(6)),
    (2, 'TravelX Gangnam', '152 Teheran-ro, Gangnam-gu, Seoul', 37.5006, 127.0364,
     '02-2345-6789', '평일 09:00-20:00, 주말 10:00-17:00', '1F, Lobby, TravelX booth',
     5, 1, NOW(6), NOW(6)),
    (3, 'TravelX Incheon Airport T1', '272 Gonghang-ro, Jung-gu, Incheon', 37.4602, 126.4407,
     '032-1234-5678', '평일 06:00-22:00, 주말 06:00-22:00', '3F, Gate G, TravelX booth',
     10, 1, NOW(6), NOW(6)),
    (4, 'TravelX Incheon Airport T2', '446 Incheon Tour-ro, Jung-gu, Incheon', 37.4467, 126.4831,
     '032-2345-6789', '평일 06:00-22:00, 주말 06:00-22:00', '1F, Arrival hall, TravelX booth',
     10, 1, NOW(6), NOW(6));
