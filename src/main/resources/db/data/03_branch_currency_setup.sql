-- Supported currencies + preferential rate/stock per branch. Manual fixture — see README.md.
-- reservation_only_stock must stay > 0 for ReservationHoldService.createHold's
-- findForUpdate/decreaseStock to succeed (STOCK_EXCEEDED otherwise).

INSERT INTO branch_supported_currencies (branch_id, currency_code) VALUES
    (1, 'USD'), (1, 'VND'), (1, 'JPY'),
    (2, 'USD'), (2, 'VND'), (2, 'EUR'),
    (3, 'USD'), (3, 'VND'), (3, 'JPY'), (3, 'EUR'), (3, 'CNY'),
    (4, 'USD'), (4, 'VND'), (4, 'JPY'), (4, 'EUR');

INSERT INTO branch_currency_rates
    (branch_id, currency_code, preferential_rate, reservation_only_stock, created_at, updated_at)
VALUES
    (1, 'USD', 0.5, 5000,      NOW(6), NOW(6)),
    (1, 'VND', 0.3, 500000000, NOW(6), NOW(6)),
    (1, 'JPY', 0.4, 1000000,   NOW(6), NOW(6)),

    (2, 'USD', 0.6, 5000,      NOW(6), NOW(6)),
    (2, 'VND', 0.2, 500000000, NOW(6), NOW(6)),
    (2, 'EUR', 0.5, 200000,    NOW(6), NOW(6)),

    (3, 'USD', 1.0, 20000,     NOW(6), NOW(6)),
    (3, 'VND', 0.8, 2000000000, NOW(6), NOW(6)),
    (3, 'JPY', 0.9, 5000000,   NOW(6), NOW(6)),
    (3, 'EUR', 1.0, 1000000,   NOW(6), NOW(6)),
    (3, 'CNY', 0.7, 1000000,   NOW(6), NOW(6)),

    (4, 'USD', 0.9, 20000,     NOW(6), NOW(6)),
    (4, 'VND', 0.7, 2000000000, NOW(6), NOW(6)),
    (4, 'JPY', 0.8, 5000000,   NOW(6), NOW(6)),
    (4, 'EUR', 0.9, 1000000,   NOW(6), NOW(6));
