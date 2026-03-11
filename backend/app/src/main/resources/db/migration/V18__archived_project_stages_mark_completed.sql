-- 已归档项目的阶段统一标记为已完成，避免详情页仍显示「进行中」
UPDATE project_stage ps
SET status = 'COMPLETED', completed_at = COALESCE(ps.completed_at, now()), updated_at = now()
FROM project p
WHERE p.id = ps.project_id AND p.status = 'archived' AND (ps.status IS NULL OR ps.status != 'COMPLETED');
