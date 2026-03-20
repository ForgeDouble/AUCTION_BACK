-- test-data.sql
SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM bid WHERE product_id IN (SELECT product_id FROM product WHERE product_name = 'test-product');
DELETE FROM product WHERE product_name = 'test-product';
DELETE FROM user WHERE email LIKE '%@test.com';
SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO user (email, name, password, gender, address, birthday, phone, warning, authority, nickname, view_only, birthday_calendar_enabled, del_yn, created_at, updated_at)
VALUES ('seller@test.com', 'seller', '$2a$10$test', 'M', 'Seoul', '2000-01-01', '01000000000', 0, 'USER', 'sellernick', false, false, 'N', NOW(), NOW());

INSERT INTO user (email, name, password, gender, address, birthday, phone, warning, authority, nickname, view_only, birthday_calendar_enabled, del_yn, created_at, updated_at)
SELECT
    CONCAT('user', seq, '@test.com'), CONCAT('user', seq), '$2a$10$test',
    'M', 'Seoul', '2000-01-01', CONCAT('010', LPAD(seq, 8, '0')),
    0, 'USER', CONCAT('nick', seq), false, false, 'N', NOW(), NOW()
FROM (
         SELECT a.N + b.N * 10 + c.N * 100 + 1 AS seq
         FROM
             (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
             (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
             (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) c
         WHERE a.N + b.N * 10 + c.N * 100 < 500
     ) numbers;

INSERT INTO product (user_id, product_name, product_content, price, status, blocked, del_yn, created_at, updated_at)
SELECT user_id, 'test-product', 'concurrency-test', 1000, 'PROCESSING', false, 'N', NOW(), NOW()
FROM user WHERE email = 'seller@test.com';