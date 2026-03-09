-- 为证据版本增加图片水印派生字段（保留原图 + 水印图）
ALTER TABLE evidence_version
    ADD COLUMN IF NOT EXISTS watermarked_file_path TEXT,
    ADD COLUMN IF NOT EXISTS watermarked_filename TEXT;

COMMENT ON COLUMN evidence_version.watermarked_file_path IS '水印图存储相对路径（仅图片）';
COMMENT ON COLUMN evidence_version.watermarked_filename IS '水印图文件名（仅图片）';

