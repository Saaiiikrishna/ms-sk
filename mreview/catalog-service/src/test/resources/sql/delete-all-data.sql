-- This script can be used to clean up data before certain tests if needed.
-- For @DataJpaTest, transactions are usually rolled back, so this might not always be necessary
-- unless specific test scenarios require it or non-transactional operations occur.

-- Order of deletion matters due to foreign key constraints.
-- Start with entities that are referenced by others.
DELETE FROM cart_items;
DELETE FROM carts;
DELETE FROM stock_levels;
DELETE FROM price_history;
DELETE FROM bulk_pricing_rules;
DELETE FROM catalog_items;
DELETE FROM categories;
