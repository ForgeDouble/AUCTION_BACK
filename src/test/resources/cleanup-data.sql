SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM bid WHERE product_id IN (SELECT product_id FROM product WHERE product_name = 'test-product');
DELETE FROM product WHERE product_name = 'test-product';
DELETE FROM user WHERE email LIKE '%@test.com';
SET FOREIGN_KEY_CHECKS = 1;