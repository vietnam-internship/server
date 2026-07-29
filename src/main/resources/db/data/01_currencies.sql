-- Base currency rates (KRW per 1 unit of the foreign currency). Manual fixture — see README.md.
INSERT INTO currencies (id, code, country, buy_rate, sell_rate, created_at, updated_at) VALUES
    (1, 'USD', 'United States', 1340.00, 1360.00, NOW(6), NOW(6)),
    (2, 'VND', 'Vietnam',          0.0565,   0.0580, NOW(6), NOW(6)),
    (3, 'JPY', 'Japan',          895.00,   910.00, NOW(6), NOW(6)),
    (4, 'EUR', 'European Union', 1440.00, 1460.00, NOW(6), NOW(6)),
    (5, 'CNY', 'China',          185.00,   190.00, NOW(6), NOW(6));
