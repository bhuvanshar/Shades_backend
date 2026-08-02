-- Safe to execute repeatedly on the same MySQL schema.
SET @drop_old_review_index = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'REVIEWS'
             AND INDEX_NAME = 'UQ_USER_PRODUCT_REVIEW'),
    'ALTER TABLE REVIEWS DROP INDEX UQ_USER_PRODUCT_REVIEW',
    'SELECT 1'
);
PREPARE review_migration_statement FROM @drop_old_review_index;
EXECUTE review_migration_statement;
DEALLOCATE PREPARE review_migration_statement;

SET @add_variant_review_index = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'REVIEWS'
             AND INDEX_NAME = 'UQ_USER_ORDER_ITEM_REVIEW'),
    'SELECT 1',
    'ALTER TABLE REVIEWS ADD CONSTRAINT UQ_USER_ORDER_ITEM_REVIEW UNIQUE (USER_ID, ORDER_ITEM_ID)'
);
PREPARE review_migration_statement FROM @add_variant_review_index;
EXECUTE review_migration_statement;
DEALLOCATE PREPARE review_migration_statement;
