-- 同步 project 表 id 序列到当前最大值，避免插入时主键冲突（如曾手动插入或初始化数据导致序列不同步）
SELECT setval(
    pg_get_serial_sequence('project', 'id'),
    COALESCE((SELECT MAX(id) FROM project), 1)
);
