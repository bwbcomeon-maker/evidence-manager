-- ============================================================
-- 归档审批流：申请单表、附件级退回记录表、消息待办表
-- 执行前需已存在：project, evidence_item, sys_user
-- ============================================================

-- ------------------------------------------------------------
-- 1. project_archive_application 归档申请单表
-- ------------------------------------------------------------
CREATE TABLE project_archive_application (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id          BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    applicant_user_id   BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE RESTRICT,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING_APPROVAL'
        CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    submit_time         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approver_user_id    BIGINT REFERENCES sys_user(id) ON DELETE SET NULL,
    approve_time        TIMESTAMPTZ,
    reject_time         TIMESTAMPTZ,
    reject_comment      TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE project_archive_application IS '归档申请单表';
COMMENT ON COLUMN project_archive_application.id IS '申请单ID';
COMMENT ON COLUMN project_archive_application.project_id IS '所属项目ID';
COMMENT ON COLUMN project_archive_application.applicant_user_id IS '申请人（项目经理）sys_user.id';
COMMENT ON COLUMN project_archive_application.status IS '状态：PENDING_APPROVAL 待审批 / APPROVED 已通过 / REJECTED 已退回';
COMMENT ON COLUMN project_archive_application.submit_time IS '提交时间';
COMMENT ON COLUMN project_archive_application.approver_user_id IS '审批人（PMO/管理员）sys_user.id';
COMMENT ON COLUMN project_archive_application.approve_time IS '审批通过时间';
COMMENT ON COLUMN project_archive_application.reject_time IS '退回时间';
COMMENT ON COLUMN project_archive_application.reject_comment IS '退回意见全文';
COMMENT ON COLUMN project_archive_application.created_at IS '创建时间';
COMMENT ON COLUMN project_archive_application.updated_at IS '更新时间';

CREATE INDEX idx_project_archive_application_project_id ON project_archive_application(project_id);
CREATE INDEX idx_project_archive_application_applicant_user_id ON project_archive_application(applicant_user_id);
CREATE INDEX idx_project_archive_application_status ON project_archive_application(status);
-- 一个项目同一时刻只能有一条「待审批」申请；已退回为历史记录，不限制新申请创建
CREATE UNIQUE INDEX idx_project_archive_application_project_pending
    ON project_archive_application(project_id)
    WHERE status = 'PENDING_APPROVAL';

-- ------------------------------------------------------------
-- 2. archive_reject_evidence 附件级退回记录表（不符合项）
-- ------------------------------------------------------------
CREATE TABLE archive_reject_evidence (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id  BIGINT NOT NULL REFERENCES project_archive_application(id) ON DELETE CASCADE,
    evidence_id     BIGINT NOT NULL REFERENCES evidence_item(id) ON DELETE CASCADE,
    reject_comment  TEXT NOT NULL,
    created_by      BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE RESTRICT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE archive_reject_evidence IS '归档退回不符合项表（附件级）';
COMMENT ON COLUMN archive_reject_evidence.id IS '记录ID';
COMMENT ON COLUMN archive_reject_evidence.application_id IS '所属归档申请单ID';
COMMENT ON COLUMN archive_reject_evidence.evidence_id IS '证据ID evidence_item.id';
COMMENT ON COLUMN archive_reject_evidence.reject_comment IS '不符合原因说明';
COMMENT ON COLUMN archive_reject_evidence.created_by IS '标注人 sys_user.id';
COMMENT ON COLUMN archive_reject_evidence.created_at IS '创建时间';

CREATE INDEX idx_archive_reject_evidence_application_id ON archive_reject_evidence(application_id);
CREATE INDEX idx_archive_reject_evidence_evidence_id ON archive_reject_evidence(evidence_id);

-- ------------------------------------------------------------
-- 3. notification 消息待办表
-- ------------------------------------------------------------
CREATE TABLE notification (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    type                    VARCHAR(32) NOT NULL,
    title                   VARCHAR(200) NOT NULL,
    body                    TEXT,
    related_project_id      BIGINT REFERENCES project(id) ON DELETE SET NULL,
    related_application_id  BIGINT REFERENCES project_archive_application(id) ON DELETE SET NULL,
    link_path               VARCHAR(500),
    read_at                 TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE notification IS '用户消息待办表';
COMMENT ON COLUMN notification.id IS '消息ID';
COMMENT ON COLUMN notification.user_id IS '接收人 sys_user.id';
COMMENT ON COLUMN notification.type IS '类型：如 ARCHIVE_PENDING 归档待审批 / ARCHIVE_RETURNED 归档已退回';
COMMENT ON COLUMN notification.title IS '标题';
COMMENT ON COLUMN notification.body IS '正文';
COMMENT ON COLUMN notification.related_project_id IS '关联项目ID';
COMMENT ON COLUMN notification.related_application_id IS '关联归档申请单ID';
COMMENT ON COLUMN notification.link_path IS '前端跳转路径，如 /projects/123?tab=evidence';
COMMENT ON COLUMN notification.read_at IS '已读时间';
COMMENT ON COLUMN notification.created_at IS '创建时间';

CREATE INDEX idx_notification_user_id ON notification(user_id);
CREATE INDEX idx_notification_type ON notification(type);
CREATE INDEX idx_notification_read_at ON notification(read_at);
CREATE INDEX idx_notification_created_at ON notification(created_at DESC);
