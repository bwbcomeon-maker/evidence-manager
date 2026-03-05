<template>
  <div class="project-detail" :class="{ 'approval-bar-visible': project?.status === 'pending_approval' && isPMOOrAdmin }">
    <div class="content">
      <!-- 顶部摘要卡片：项目名称、状态胶囊、核心负责人 -->
      <div v-if="project && !projectLoading" class="detail-header-card">
        <!-- 待审批时 PM 只读提示 -->
        <van-notice-bar
          v-if="project.status === 'pending_approval' && isPM"
          left-icon="info-o"
          color="#ed6a0c"
          background="#fff7e8"
          class="detail-notice-bar"
        >
          项目归档正在审核中，不可修改材料
        </van-notice-bar>
        <!-- 已退回且仍有未处理的不符合项：红色警告横幅 + 「去处理(n)」 -->
        <div v-else-if="project.status === 'returned' && !isAllResolved" class="returned-banner">
          <div class="returned-banner__content">
            <van-icon name="warning-o" class="returned-banner__icon" />
            <span class="returned-banner__text">{{ project.rejectComment || '项目归档申请已被退回，请根据意见修改后重新申请。' }}</span>
            <button
              type="button"
              class="returned-banner__action"
              @click="showRejectedPopup = true"
            >
              去处理({{ rejectedEvidencesList.length }}) &gt;
            </button>
          </div>
        </div>
        <!-- 已退回且所有不符合项已处理：成功/引导横幅，引导重新申请归档，不展示「去处理」 -->
        <van-notice-bar
          v-else-if="project.status === 'returned' && isAllResolved"
          left-icon="passed"
          color="#07c160"
          background="#e8f8f0"
          class="detail-notice-bar returned-banner--resolved"
        >
          所有不符合项已处理完毕，可重新申请归档。
        </van-notice-bar>
        <h1 class="detail-header-title">{{ project.name }}</h1>
        <div class="detail-header-meta">
          <span class="detail-header-badge" :class="statusBadgeClass">
            {{ statusBadgeText }}
          </span>
          <span v-if="project.currentPmDisplayName" class="detail-header-responsible">负责人：{{ project.currentPmDisplayName }}</span>
        </div>
        <button
          type="button"
          class="detail-header-history-btn"
          @click="openArchiveHistoryPopup"
        >
          <van-icon name="clock-o" />
          历次审批记录
        </button>
      </div>

      <!-- 分段控制器：详情 / 证据 -->
      <div class="segmented-control">
        <button type="button" class="segmented-item" :class="{ active: activeTab === 0 }" @click="activeTab = 0">基本信息</button>
        <button type="button" class="segmented-item" :class="{ active: activeTab === 1 }" @click="activeTab = 1">证据管理</button>
      </div>

      <van-tabs v-model:active="activeTab" sticky class="tabs-custom-nav">
        <van-tab title="详情">
          <van-loading v-if="projectLoading" class="detail-loading" vertical>加载中...</van-loading>
          <van-empty v-else-if="projectError" :description="projectError" />
          <template v-else-if="project">
            <!-- 信息列表区：左右布局，字段名置灰，数据加深，分割线 -->
            <div class="info-card">
              <div class="info-row">
                <span class="info-label">项目令号</span>
                <span class="info-value">{{ project.code }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">项目名称</span>
                <span class="info-value">{{ project.name }}</span>
              </div>
              <div class="info-row" v-if="project.description">
                <span class="info-label">项目描述</span>
                <span class="info-value">{{ project.description }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">项目状态</span>
                <span class="info-value">
                  <span class="info-badge" :class="statusBadgeClass">
                    {{ statusBadgeText }}
                  </span>
                </span>
              </div>
              <div class="info-row">
                <span class="info-label">是否含采购</span>
                <span class="info-value">
                  <van-switch
                    v-if="project.canManageMembers && project.status !== 'archived'"
                    :model-value="project.hasProcurement"
                    size="20"
                    @update:model-value="onHasProcurementChange"
                  />
                  <span v-else>{{ project.hasProcurement ? '是' : '否' }}</span>
                </span>
              </div>
              <div class="info-row">
                <span class="info-label">项目创建人</span>
                <span class="info-value">{{ project.createdByDisplayName || '—' }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">创建时间</span>
                <span class="info-value">{{ project.createdAt }}</span>
              </div>
            </div>
            <!-- 证据完成度（详情页也展示） -->
            <div v-if="stageProgress" class="stage-progress-header detail-tab-progress">
              <div class="completion-row">
                <span class="completion-label">证据完成度</span>
                <van-progress :percentage="stageProgress.overallCompletionPercent" stroke-width="8" />
                <span class="completion-value">{{ stageProgress.overallCompletionPercent }}%</span>
              </div>
              <div v-if="hasKeyMissing" class="key-missing-card">
                <div class="key-missing-card-inner">
                  <van-icon name="warning-o" class="key-missing-icon" />
                  <div class="key-missing-body">
                    <div class="key-missing-title">关键缺失</div>
                    <div v-if="displayedKeyMissing.notUploaded.length" class="key-missing-category">
                      <span class="key-missing-category-label">未上传</span>
                      <div class="key-missing-tags">
                        <van-tag
                          v-for="entry in displayedKeyMissing.notUploaded"
                          :key="'u-' + entry.stageCode + '-' + entry.evidenceTypeCode"
                          plain
                          type="danger"
                          class="key-missing-tag key-missing-tag--clickable"
                          @click="scrollToEvidence(entry)"
                        >
                          {{ entry.displayName }}
                        </van-tag>
                      </div>
                    </div>
                    <div v-if="displayedKeyMissing.shortfall.length" class="key-missing-category">
                      <span class="key-missing-category-label">需补充</span>
                      <div class="key-missing-tags key-missing-shortfall-wrap">
                        <span
                          v-for="entry in displayedKeyMissing.shortfall"
                          :key="'s-' + entry.stageCode + '-' + entry.evidenceTypeCode"
                          class="key-missing-shortfall-item key-missing-shortfall-item--clickable"
                          @click="scrollToEvidence(entry)"
                        >
                          {{ entry.displayName }}
                          <span class="key-missing-shortfall-num">（还差 {{ entry.shortfall }} 份）</span>
                        </span>
                      </div>
                    </div>
                    <div v-if="displayedKeyMissing.notSubmitted.length" class="key-missing-category">
                      <span class="key-missing-category-label">待提交</span>
                      <div class="key-missing-tags">
                        <van-tag
                          v-for="entry in displayedKeyMissing.notSubmitted"
                          :key="'n-' + entry.stageCode + '-' + entry.evidenceTypeCode"
                          plain
                          type="primary"
                          class="key-missing-tag key-missing-tag--clickable"
                          @click="scrollToEvidence(entry)"
                        >
                          {{ entry.displayName }}
                        </van-tag>
                      </div>
                    </div>
                    <div v-if="keyMissingHasMore && !keyMissingExpanded" class="key-missing-toggle" @click="keyMissingExpanded = true">
                      展开（还有 {{ keyMissingHiddenCount }} 项）
                    </div>
                    <div v-else-if="keyMissingHasMore && keyMissingExpanded" class="key-missing-toggle" @click="keyMissingExpanded = false">
                      收起
                    </div>
                  </div>
                </div>
              </div>
              <div class="archive-row">
                <van-button
                  v-if="!(project.status === 'pending_approval' && isPM)"
                  type="primary"
                  :class="{ 'archive-btn--disabled': !canArchiveForApply }"
                  :title="!canArchiveForApply ? getArchiveDisabledMessage() : undefined"
                  @click="onArchiveClick"
                >
                  {{ project?.status === 'returned' ? '重新申请归档' : '申请归档' }}
                </van-button>
              </div>
            </div>
            <van-loading v-else-if="stageProgressLoading" class="stage-progress-loading" vertical>加载阶段进度...</van-loading>
            <!-- 项目成员列表 -->
          <div class="members-section">
            <div class="members-section-title">项目成员</div>
            <van-loading v-if="membersLoading" class="members-loading" size="20" vertical>加载中...</van-loading>
            <van-cell-group v-else-if="members.length">
              <van-cell
                v-for="m in sortedMembers"
                :key="m.userId"
                :title="m.displayName || m.username || String(m.userId)"
                :label="m.username && (m.displayName || '') !== m.username ? `@${m.username}` : undefined"
              >
                <template #value>
                  <span class="member-role-desc">{{ memberRoleLabel(m.role) }}</span>
                </template>
              </van-cell>
            </van-cell-group>
            <van-empty v-else description="暂无成员" :image-size="60" />
          </div>
          </template>
        </van-tab>

        <!-- 证据 Tab（阶段驱动：完整度 + 阶段折叠 + 模板项证据列表） -->
        <van-tab title="证据">
          <div class="evidence-section">
            <!-- 阶段进度顶部：完整度、关键缺失、申请归档 -->
            <div v-if="stageProgress" class="stage-progress-header">
              <div class="completion-row">
                <span class="completion-label">证据完成度</span>
                <van-progress :percentage="stageProgress.overallCompletionPercent" stroke-width="8" />
                <span class="completion-value">{{ stageProgress.overallCompletionPercent }}%</span>
              </div>
              <div v-if="hasKeyMissing" class="key-missing-card">
                <div class="key-missing-card-inner">
                  <van-icon name="warning-o" class="key-missing-icon" />
                  <div class="key-missing-body">
                    <div class="key-missing-title">关键缺失</div>
                    <div v-if="displayedKeyMissing.notUploaded.length" class="key-missing-category">
                      <span class="key-missing-category-label">未上传</span>
                      <div class="key-missing-tags">
                        <van-tag
                          v-for="entry in displayedKeyMissing.notUploaded"
                          :key="'u-' + entry.stageCode + '-' + entry.evidenceTypeCode"
                          plain
                          type="danger"
                          class="key-missing-tag key-missing-tag--clickable"
                          @click="scrollToEvidence(entry)"
                        >
                          {{ entry.displayName }}
                        </van-tag>
                      </div>
                    </div>
                    <div v-if="displayedKeyMissing.shortfall.length" class="key-missing-category">
                      <span class="key-missing-category-label">需补充</span>
                      <div class="key-missing-tags key-missing-shortfall-wrap">
                        <span
                          v-for="entry in displayedKeyMissing.shortfall"
                          :key="'s-' + entry.stageCode + '-' + entry.evidenceTypeCode"
                          class="key-missing-shortfall-item key-missing-shortfall-item--clickable"
                          @click="scrollToEvidence(entry)"
                        >
                          {{ entry.displayName }}
                          <span class="key-missing-shortfall-num">（还差 {{ entry.shortfall }} 份）</span>
                        </span>
                      </div>
                    </div>
                    <div v-if="displayedKeyMissing.notSubmitted.length" class="key-missing-category">
                      <span class="key-missing-category-label">待提交</span>
                      <div class="key-missing-tags">
                        <van-tag
                          v-for="entry in displayedKeyMissing.notSubmitted"
                          :key="'n-' + entry.stageCode + '-' + entry.evidenceTypeCode"
                          plain
                          type="primary"
                          class="key-missing-tag key-missing-tag--clickable"
                          @click="scrollToEvidence(entry)"
                        >
                          {{ entry.displayName }}
                        </van-tag>
                      </div>
                    </div>
                    <div v-if="keyMissingHasMore && !keyMissingExpanded" class="key-missing-toggle" @click="keyMissingExpanded = true">
                      展开（还有 {{ keyMissingHiddenCount }} 项）
                    </div>
                    <div v-else-if="keyMissingHasMore && keyMissingExpanded" class="key-missing-toggle" @click="keyMissingExpanded = false">
                      收起
                    </div>
                  </div>
                </div>
              </div>
              <div class="archive-row">
                <van-button
                  v-if="!(project.status === 'pending_approval' && isPM)"
                  type="primary"
                  :class="{ 'archive-btn--disabled': !canArchiveForApply }"
                  :title="!canArchiveForApply ? getArchiveDisabledMessage() : undefined"
                  @click="onArchiveClick"
                >
                  {{ project?.status === 'returned' ? '重新申请归档' : '申请归档' }}
                </van-button>
              </div>
            </div>
            <van-loading v-else-if="stageProgressLoading" class="stage-progress-loading" vertical>加载阶段进度...</van-loading>

            <!-- 阶段折叠清单 -->
            <van-collapse v-if="stageProgress?.stages?.length" v-model="expandedStages" class="stage-collapse">
              <van-collapse-item
                v-for="s in stageProgress.stages"
                :key="s.stageCode"
                :name="s.stageCode"
              >
                <template #title>
                  <div class="stage-title-row">
                    <span class="stage-name">{{ s.stageName || s.stageCode }}</span>
                    <span class="stage-count">{{ (s.displayCompletedCount ?? s.completedCount) }}/{{ (s.displayItemCount ?? s.itemCount) }}</span>
                    <van-tag :type="healthTagType(s.healthStatus)">{{ healthStatusText(s.healthStatus) }}</van-tag>
                    <van-tag v-if="s.stageCompleted" type="success">已完成</van-tag>
                    <van-tag v-if="project?.status === 'returned' && stageHasRejected(s)" type="danger" size="medium" class="stage-rejected-badge">包含待修改项</van-tag>
                  </div>
                </template>
                <!-- 模板项平铺卡片 -->
                <div class="stage-items-flat">
                  <div
                    v-for="(item, idx) in uniqueStageItems(s)"
                    :key="item.evidenceTypeCode + '-' + idx"
                    :id="'evidence-card-' + s.stageCode + '-' + item.evidenceTypeCode"
                    class="evidence-card"
                  >
                    <!-- 卡片标题行：大点击区域列表项，右侧状态透出 + 箭头 -->
                    <div class="evidence-card-header">
                      <div class="evidence-card-title">
                        <span class="card-name">{{ item.groupDisplayName || item.displayName }}</span>
                        <van-tag v-if="item.isRequired || item.required" type="danger" size="mini" class="card-required">必填</van-tag>
                        <van-tag v-else type="default" size="mini">选填</van-tag>
                      </div>
                      <div class="evidence-card-status">
                        <span
                          class="card-status-text"
                          :class="{ 'card-status-text--insufficient': (item.minCount ?? 1) > 0 && ((item.uploadCount ?? item.currentCount) < (item.minCount ?? 1)) }"
                        >
                          {{ (item.minCount ?? 1) === 0 ? `选填（已 ${item.uploadCount ?? item.currentCount} 份）` : `已 ${item.uploadCount ?? item.currentCount} / 需 ${item.minCount ?? 1} 份` }}
                        </span>
                        <van-icon name="arrow" class="card-arrow" />
                      </div>
                    </div>
                    <!-- 3列网格：缩略图/文件图标 + 上传入口 -->
                    <div class="evidence-card-grid">
                      <van-loading v-if="isItemLoading(s.stageCode, item.evidenceTypeCode)" size="20" />
                      <template v-else>
                        <div
                          v-for="ev in getItemEvidences(s.stageCode, item.evidenceTypeCode)"
                          :key="ev.evidenceId"
                          :id="'evidence-item-' + ev.evidenceId"
                          class="grid-item"
                          :class="{ 'grid-item--voided': getEffectiveEvidenceStatus(ev) === 'INVALID' }"
                          :title="getDisplayRejectComment(ev) ? '不符合原因：' + getDisplayRejectComment(ev) : undefined"
                          @click="goToEvidenceDetail(ev.evidenceId, s.stageCode)"
                        >
                          <!-- 图片缩略图：圆角、cover，左下角状态标签；已作废卡片弱化显示且不展示替换/删除 -->
                          <div
                            v-if="isImageType(ev.contentType) && ev.latestVersion"
                            class="grid-thumb"
                            :class="{ 'grid-thumb--rejected': getEffectiveEvidenceStatus(ev) !== 'INVALID' && getDisplayRejectComment(ev), 'grid-thumb--has-reject-comment': getEffectiveEvidenceStatus(ev) !== 'INVALID' && getDisplayRejectComment(ev) }"
                            :title="getDisplayRejectComment(ev) ? '不符合原因：' + getDisplayRejectComment(ev) : undefined"
                            @click.stop="goToEvidenceDetail(ev.evidenceId, s.stageCode)"
                          >
                            <img :src="`/api/evidence/versions/${ev.latestVersion.versionId}/download`" :alt="ev.title" />
                            <div class="grid-thumb-overlay">
                              <!-- 已作废：仅显示灰色「已作废」标签，不显示不符合/替换/删除 -->
                              <span class="evidence-badge" :class="'evidence-badge--' + evidenceBadgeType(ev)">
                                {{ evidenceBadgeText(ev) }}
                              </span>
                              <template v-if="getEffectiveEvidenceStatus(ev) === 'INVALID'">
                                <van-tag type="default" size="mini" class="reject-tag-inline voided-tag">已作废</van-tag>
                              </template>
                              <template v-else-if="getDisplayRejectComment(ev)">
                                <van-tag type="danger" size="mini" class="reject-tag-inline">不符合</van-tag>
                                <span class="reject-reason-inline">{{ getDisplayRejectComment(ev) }}</span>
                                <template v-if="project?.status === 'pending_approval' && isPMOOrAdmin">
                                  <button type="button" class="grid-reject-btn" @click.stop="openMarkRejectDialog(ev, true)">修改</button>
                                  <button type="button" class="grid-reject-btn" @click.stop="clearMarkReject(ev)">取消</button>
                                </template>
                                <template v-else-if="project?.status === 'returned' && canUploadAndEdit && route.query.from !== 'evidence-by-project'">
                                  <button type="button" class="grid-reject-btn grid-reject-btn--replace" :disabled="replaceLoadingEvidenceId === ev.evidenceId" @click.stop="triggerReplaceFileInput(ev, s, item)">
                                    {{ replaceLoadingEvidenceId === ev.evidenceId ? '替换中...' : '替换' }}
                                  </button>
                                </template>
                              </template>
                              <template v-else-if="project?.status === 'pending_approval' && isPMOOrAdmin">
                                <button type="button" class="grid-reject-btn grid-reject-btn--mark" @click.stop="openMarkRejectDialog(ev, false)">标记不符合</button>
                              </template>
                            </div>
                            <button v-if="canUploadAndEdit && getEffectiveEvidenceStatus(ev) !== 'INVALID' && route.query.from !== 'evidence-by-project'" type="button" class="grid-thumb-delete" aria-label="删除" @click.stop="onDeleteEvidence(ev, s, item)"><van-icon name="delete-o" /></button>
                          </div>
                          <!-- 非图片：文档类列表式展示；已作废仅显示灰色「已作废」标签，不展示替换/删除 -->
                          <div
                            v-else
                            class="grid-file"
                            :class="{ 'grid-file--voided': getEffectiveEvidenceStatus(ev) === 'INVALID', 'grid-file--rejected': getEffectiveEvidenceStatus(ev) !== 'INVALID' && getDisplayRejectComment(ev), 'grid-file--has-reject-comment': getEffectiveEvidenceStatus(ev) !== 'INVALID' && getDisplayRejectComment(ev) }"
                            :style="{ background: getFileIconConfig(getEvidenceFileName(ev)).bg }"
                            :title="getDisplayRejectComment(ev) ? '不符合原因：' + getDisplayRejectComment(ev) : undefined"
                            @click.stop="goToEvidenceDetail(ev.evidenceId, s.stageCode)"
                          >
                            <span class="grid-file-type-badge" :style="{ background: getFileIconConfig(getEvidenceFileName(ev)).color }">
                              {{ getFileIconConfig(getEvidenceFileName(ev)).label }}
                            </span>
                            <van-icon
                              :name="getFileIconConfig(getEvidenceFileName(ev)).icon"
                              size="28"
                              :color="getFileIconConfig(getEvidenceFileName(ev)).color"
                            />
                            <span class="grid-file-name">{{ ev.latestVersion?.originalFilename || ev.title }}</span>
                            <span class="evidence-badge evidence-badge--file" :class="'evidence-badge--' + evidenceBadgeType(ev)">
                              {{ evidenceBadgeText(ev) }}
                            </span>
                            <div v-if="getEffectiveEvidenceStatus(ev) === 'INVALID'" class="grid-file-reject-bar">
                              <van-tag type="default" size="mini" class="voided-tag">已作废</van-tag>
                            </div>
                            <div v-else-if="getDisplayRejectComment(ev)" class="grid-file-reject-bar">
                              <van-tag type="danger" size="mini">不符合</van-tag>
                              <span class="reject-reason-inline">{{ getDisplayRejectComment(ev) }}</span>
                              <template v-if="project?.status === 'pending_approval' && isPMOOrAdmin">
                                <button type="button" class="grid-reject-btn" @click.stop="openMarkRejectDialog(ev, true)">修改</button>
                                <button type="button" class="grid-reject-btn" @click.stop="clearMarkReject(ev)">取消</button>
                              </template>
                              <template v-else-if="project?.status === 'returned' && canUploadAndEdit && route.query.from !== 'evidence-by-project'">
                                <button type="button" class="grid-reject-btn grid-reject-btn--replace" :disabled="replaceLoadingEvidenceId === ev.evidenceId" @click.stop="triggerReplaceFileInput(ev, s, item)">
                                  {{ replaceLoadingEvidenceId === ev.evidenceId ? '替换中...' : '替换' }}
                                </button>
                              </template>
                            </div>
                            <button v-else-if="project?.status === 'pending_approval' && isPMOOrAdmin" type="button" class="grid-reject-btn grid-reject-btn--mark grid-file-mark-btn" @click.stop="openMarkRejectDialog(ev, false)">标记不符合</button>
                            <button v-if="canUploadAndEdit && getEffectiveEvidenceStatus(ev) !== 'INVALID' && route.query.from !== 'evidence-by-project'" type="button" class="grid-file-delete" aria-label="删除" @click.stop="onDeleteEvidence(ev, s, item)"><van-icon name="delete-o" /></button>
                          </div>
                        </div>
                        <!-- 上传入口：自定义大加号 + 动态文案（未达标：继续上传 + 还差 X 张；已达标：上传更多） -->
                        <div
                          v-if="canUploadAndEdit && s.stageId && route.query.from !== 'evidence-by-project'"
                          class="grid-item grid-upload-btn"
                          @click="openUploadForItem(s, item)"
                        >
                          <div class="grid-upload-inner">
                            <span class="grid-upload-plus">+</span>
                            <span class="grid-upload-label">{{ uploadShortfall(s, item) > 0 ? '继续上传' : '上传更多' }}</span>
                            <span v-if="uploadShortfall(s, item) > 0" class="grid-upload-hint">(还差 {{ uploadShortfall(s, item) }} 张)</span>
                          </div>
                        </div>
                      </template>
                    </div>
                  </div>
                </div>
              </van-collapse-item>
            </van-collapse>
            <van-empty v-else-if="stageProgress && !stageProgressLoading" description="暂无阶段配置" :image-size="60" />
          </div>
        </van-tab>
      </van-tabs>

      <!-- 成员管理入口：仅在「详情」Tab 显示；从「按项目查看证据」进入时为只读，不显示 -->
      <div v-if="project?.canManageMembers && activeTab === 0 && route.query.from !== 'evidence-by-project'" class="member-entry-wrap">
        <van-button type="primary" class="member-entry-btn" @click="goToMembers">
          成员管理
        </van-button>
      </div>
    </div>

    <!-- 审批操作区（PMO/管理员 + 项目待审批时固定底部） -->
    <div v-if="project?.status === 'pending_approval' && isPMOOrAdmin" class="approval-bar-wrap">
      <div class="approval-bar">
        <van-button type="success" class="approval-btn" @click="onApproveClick">审批通过</van-button>
        <van-button type="warning" class="approval-btn" @click="showRejectDialog = true">退回整改</van-button>
      </div>
    </div>

    <!-- 退回整改弹窗 -->
    <van-dialog
      v-model:show="showRejectDialog"
      title="退回整改"
      show-cancel-button
      confirm-button-text="确认退回"
      :before-close="onRejectConfirm"
    >
      <van-field
        v-model="rejectCommentText"
        type="textarea"
        rows="4"
        placeholder="请填写退回原因（必填）"
        maxlength="500"
        show-word-limit
        class="reject-reason-field"
      />
    </van-dialog>

    <!-- 标记不符合弹窗（PMO 附件级打回原因，仅本地暂存） -->
    <van-dialog
      v-model:show="showMarkRejectDialog"
      title="标记不符合"
      show-cancel-button
      confirm-button-text="确定"
      :before-close="onMarkRejectConfirm"
    >
      <van-field
        v-model="markRejectCommentText"
        type="textarea"
        rows="3"
        placeholder="请填写不符合原因"
        maxlength="300"
        show-word-limit
        class="reject-reason-field"
      />
    </van-dialog>

    <!-- 一键替换不符合项：隐藏的 file 选择框 -->
    <input
      ref="replaceFileInputRef"
      type="file"
      accept="image/*,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.zip"
      class="replace-file-input-hidden"
      @change="onReplaceFileSelect"
    />

    <!-- 退回状态下：不符合项列表弹窗，点击项平滑滚动定位到对应证据 -->
    <van-popup
      v-model:show="showRejectedPopup"
      position="bottom"
      round
      :style="{ maxHeight: '60vh' }"
      class="rejected-list-popup"
    >
      <div class="rejected-list-popup-header">不符合项（点击定位）</div>
      <div class="rejected-list-popup-body">
        <div
          v-for="entry in rejectedEvidencesList"
          :key="entry.evidenceId"
          class="rejected-list-item"
          @click="scrollToRejectedEvidence(entry)"
        >
          <span class="rejected-list-item-name">{{ entry.displayName }}</span>
          <span class="rejected-list-item-reason">{{ entry.rejectComment }}</span>
        </div>
      </div>
    </van-popup>

    <!-- 审批历史：右侧弹出层，时间轴展示历次申请/通过/退回 -->
    <van-popup
      v-model:show="showHistoryPopup"
      position="right"
      :style="{ width: '85%', height: '100%' }"
      class="archive-history-popup"
    >
      <div class="archive-history-header">
        <span class="archive-history-title">审批历史</span>
        <van-icon name="cross" class="archive-history-close" @click="showHistoryPopup = false" />
      </div>
      <div class="archive-history-body">
        <van-loading v-if="historyLoading" class="archive-history-loading" vertical size="24">
          加载中...
        </van-loading>
        <van-empty
          v-else-if="!historyList.length"
          description="暂无审批记录"
          :image-size="80"
          class="archive-history-empty"
        />
        <div v-else class="archive-history-timeline">
          <div
            v-for="(item, index) in historyList"
            :key="item.applicationId"
            class="timeline-item"
            :class="'timeline-item--' + item.status"
          >
            <div class="timeline-node">
              <span class="timeline-node__icon" :class="'timeline-node__icon--' + item.status">
                <van-icon :name="historyStepIcon(item.status)" />
              </span>
              <div class="timeline-node__content">
                <div class="timeline-node__title">{{ historyStepTitle(item.status) }}</div>
                <div class="timeline-node__meta">
                  <span class="timeline-node__time">{{ historyStepTime(item) }}</span>
                  <span class="timeline-node__user">{{ historyStepOperator(item) }}</span>
                </div>
                <div v-if="item.rejectComment" class="timeline-reject-comment">
                  {{ item.rejectComment }}
                </div>
                <ul v-if="item.rejectEvidences && item.rejectEvidences.length > 0" class="timeline-reject-evidences">
                  <li
                    v-for="ev in item.rejectEvidences"
                    :key="ev.evidenceId"
                    class="timeline-reject-evidence-item"
                  >
                    • [{{ ev.stageName || '—' }}] {{ ev.evidenceName || '证据' }}：{{ ev.rejectComment }}
                  </li>
                </ul>
              </div>
            </div>
            <div v-if="index < historyList.length - 1" class="timeline-line" />
          </div>
        </div>
      </div>
    </van-popup>

    <!-- 上传弹窗：两阶段（表单 -> 结果与操作） -->
    <van-dialog
      v-model:show="showUploadDialog"
      :title="uploadPhase === 'form' ? '上传证据' : '上传结果'"
      :show-cancel-button="uploadPhase === 'form'"
      :show-confirm-button="uploadPhase === 'result'"
      confirm-button-text="关闭"
      @confirm="closeUploadDialog"
      @cancel="resetUploadForm"
    >
      <!-- 阶段1：表单 -->
      <template v-if="uploadPhase === 'form'">
        <van-cell-group inset>
          <van-cell v-if="uploadContext" :title="'上传至'" :value="uploadContext.displayName" class="upload-context-cell" />
          <van-cell v-else title="提示" class="upload-hint-cell">
            <template #value>
              <span class="upload-hint-text">请先在下方向某证据类型（如「启动现场照片」）点击展开，再点击该类型下的「上传」按钮</span>
            </template>
          </van-cell>
          <van-field
            v-model="uploadForm.name"
            name="name"
            label="证据标题"
            placeholder="请输入证据标题"
            :rules="[{ required: true, message: '请输入证据标题' }]"
          />
          <van-field
            v-if="!uploadContext"
            :model-value="bizTypeMap[uploadForm.type] || '其他'"
            name="type"
            label="业务类型"
            placeholder="请选择业务类型"
            is-link
            readonly
            @click="showBizTypePicker = true"
          />
          <van-field
            v-model="uploadForm.remark"
            name="remark"
            label="备注"
            type="textarea"
            placeholder="请输入备注（可选）"
            rows="3"
          />
          <div class="upload-file-section">
            <van-uploader
              v-model="uploadFileList"
              :max-count="1"
              accept="*/*"
              :before-read="onUploaderBeforeRead"
            >
              <van-button icon="plus" type="primary">选择文件</van-button>
            </van-uploader>
          </div>
        </van-cell-group>
        <div class="upload-dialog-footer-inner">
          <van-button type="primary" block :loading="uploading" @click="handleUpload">
            确认上传
          </van-button>
        </div>
      </template>

      <!-- 阶段2：结果与操作 -->
      <template v-else>
        <div class="upload-result-block">
          <van-cell-group inset>
            <van-cell title="证据标题" :value="uploadResult?.title" />
            <van-cell title="文件名" :value="uploadResult?.fileName || '—'" />
            <van-cell title="上传时间" :value="uploadResult?.createdAt || '—'" />
            <van-cell title="当前状态">
              <template #value>
                <van-tag :type="uploadResultStatusTagType">
                  {{ uploadResultStatusText }}
                </van-tag>
              </template>
            </van-cell>
          </van-cell-group>
          <div class="upload-result-actions">
            <van-button
              v-if="showUploadResultSubmit"
              type="primary"
              block
              :loading="submitLoading"
              @click="handleUploadResultSubmit"
            >
              提交
            </van-button>
            <van-button
              v-if="showUploadResultDelete"
              type="danger"
              plain
              block
              :loading="deleteLoading"
              @click="handleUploadResultDelete"
            >
              删除
            </van-button>
            <van-button
              type="primary"
              plain
              block
              @click="goToUploadResultDetail"
            >
              查看详情
            </van-button>
            <van-button
              v-if="showUploadResultInvalidate"
              type="danger"
              plain
              block
              :loading="invalidateLoading"
              @click="handleUploadResultInvalidate"
            >
              作废
            </van-button>
            <van-button plain block @click="continueUpload">
              继续上传
            </van-button>
          </div>
        </div>
      </template>
    </van-dialog>

    <!-- 作废原因弹窗（项目页上传结果内作废） -->
    <van-dialog
      v-model:show="showInvalidateReasonDialog"
      title="确认作废"
      show-cancel-button
      :before-close="onInvalidateReasonConfirm"
    >
      <van-field
        v-model="invalidateReasonText"
        type="textarea"
        rows="3"
        placeholder="请填写作废原因（必填）"
        maxlength="500"
        show-word-limit
        class="invalidate-reason-field"
      />
    </van-dialog>

    <!-- 业务类型选择器（支持鼠标滚轮滚动） -->
    <van-popup v-model:show="showBizTypePicker" position="bottom">
      <div class="picker-wheel-wrap" @wheel.prevent="onBizTypePickerWheel">
        <van-picker
          :model-value="[uploadForm.type]"
          :columns="bizTypePickerOptions"
          @confirm="onBizTypeConfirm"
          @cancel="showBizTypePicker = false"
        />
      </div>
    </van-popup>

    <!-- 归档失败弹窗（400 时展示 archiveBlockReason / keyMissing / blockedByStages / blockedByRequiredItems） -->
    <van-dialog v-model:show="showArchiveBlockDialog" title="无法归档" :show-confirm-button="true" confirm-button-text="知道了">
      <div class="archive-block-content">
        <p v-if="archiveBlockMessage" class="archive-block-reason">{{ archiveBlockMessage }}</p>
        <p v-if="archiveBlockData?.keyMissing?.length" class="archive-block-list">
          <strong>关键缺失：</strong>{{ archiveBlockData.keyMissing.join('、') }}
        </p>
        <p v-if="archiveBlockData?.blockedByStages?.length" class="archive-block-list">
          <strong>未完成阶段：</strong>{{ archiveBlockData.blockedByStages.join('、') }}
        </p>
      </div>
    </van-dialog>

    <!-- 申请归档前：存在草稿证据时确认弹窗 -->
    <van-dialog
      v-model:show="showDraftConfirmDialog"
      title="确认归档"
      show-cancel-button
      cancel-button-text="取消"
      confirm-button-text="确认归档"
      :before-close="onDraftArchiveConfirm"
    >
      <div class="draft-confirm-content">
        <p class="draft-confirm-tip">以下材料处于草稿状态，请确认是否需要调整。如不调整，草稿状态的材料将随项目一并归档（自动转为已归档状态）。</p>
        <ul v-if="draftListForArchive.length" class="draft-confirm-list">
          <li v-for="d in draftListForArchive" :key="d.evidenceId">
            {{ draftItemPathLabel(d) }}：{{ d.title || '未命名' }}
          </li>
        </ul>
        <p class="draft-confirm-ask">确认继续归档？</p>
      </div>
    </van-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NavBar,
  Cell,
  CellGroup,
  Button,
  Tag,
  Tabs,
  Tab,
  DropdownMenu,
  DropdownItem,
  List,
  PullRefresh,
  Empty,
  Loading,
  Dialog,
  Form,
  Field,
  Uploader,
  Popup,
  Picker,
  showToast,
  showLoadingToast,
  showSuccessToast,
  closeToast
} from 'vant'
import type { UploaderFileListItem } from 'vant'
import {
  getEvidenceList,
  uploadEvidence,
  downloadVersionFile,
  submitEvidence,
  deleteEvidence,
  invalidateEvidence,
  type EvidenceListItem
} from '@/api/evidence'
import {
  getProjectDetail,
  getProjectMembers,
  getStageProgress,
  archiveApply,
  archiveApprove,
  archiveReject,
  getArchiveHistory,
  updateProject,
  getStructuredErrorData,
  type ProjectMemberVO,
  type StageProgressVO,
  type StageVO,
  type StageItemVO,
  type ArchiveBlockVO,
  type ProjectArchiveHistoryVO
} from '@/api/projects'
import { getEvidencesByStageType } from '@/api/evidence'
import { useAuthStore } from '@/stores/auth'
import { useArchiveRejectDraftStore } from '@/stores/archiveRejectDraft'
import { showConfirmDialog } from 'vant'
import { getEffectiveEvidenceStatus, mapStatusToText, statusTagType as evidenceStatusTagType } from '@/utils/evidenceStatus'
import { validateFileSize, isImageFile } from '@/utils/uploadFileLimit'
import { compressImageIfNeeded } from '@/utils/imageCompress'
import { getFriendlyErrorMessage } from '@/utils/errorMessage'
import { formatDateTimeFull } from '@/utils/format'

interface Project {
  id: number
  code: string
  name: string
  description: string
  status: string
  createdAt: string
  /** 是否含采购（项目启动阶段「项目前期产品比测报告」勾选后为必填） */
  hasProcurement?: boolean
  permissions?: { canUpload?: boolean; canInvalidate?: boolean; canManageMembers?: boolean }
  canInvalidate?: boolean
  canUpload?: boolean
  canManageMembers?: boolean
  currentPmUserId?: number
  currentPmDisplayName?: string
  /** 退回原因（returned 时后端可返回，暂无则前端展示通用提示） */
  rejectComment?: string
}

const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.id))
const project = ref<Project | null>(null)
const projectLoading = ref(false)
const projectError = ref('')
const activeTab = ref(0)

// 阶段进度（stage-progress 唯一事实源）
const stageProgress = ref<StageProgressVO | null>(null)
const stageProgressLoading = ref(false)
const expandedStages = ref<string[]>([])
const openedStageCode = ref<string | null>(null)
const openedEvidenceTypeCode = ref<string | null>(null)
const evidenceByTypeList = ref<EvidenceListItem[]>([])
const evidenceByTypeLoading = ref(false)
/** 平铺模式：每个模板项的证据列表缓存 key = "stageCode:evidenceTypeCode" */
const evidenceByItemMap = ref<Record<string, EvidenceListItem[]>>({})
const evidenceByItemLoading = ref<Record<string, boolean>>({})
// 归档失败弹窗（400 结构化 data）
const showArchiveBlockDialog = ref(false)
const archiveBlockMessage = ref('')
const archiveBlockData = ref<ArchiveBlockVO | null>(null)
// 申请归档前草稿确认：存在草稿时展示列表，用户确认后再执行归档
const showDraftConfirmDialog = ref(false)
const draftListForArchive = ref<EvidenceListItem[]>([])
// 退回整改弹窗
const showRejectDialog = ref(false)
const rejectCommentText = ref('')
// PMO 附件级不符合：通过 Pinia 仓库在项目详情页与证据详情间持久化草稿（key = evidenceId）
const archiveRejectDraftStore = useArchiveRejectDraftStore()
const localRejectMap = computed<Record<string, string>>({
  get() {
    const id = projectId.value
    if (!id) return {}
    return archiveRejectDraftStore.getProjectDraft(id)
  },
  set(val) {
    const id = projectId.value
    if (!id) return
    archiveRejectDraftStore.setProjectDraft(id, val)
  }
})
// 标记不符合弹窗（evidenceId 当前正在编辑的）
const showMarkRejectDialog = ref(false)
const markRejectEvidenceId = ref<number | null>(null)
const markRejectCommentText = ref('')
/** 退回状态下「查看不符合项」列表弹窗 */
const showRejectedPopup = ref(false)
/** 一键替换不符合项：当前正在请求的证据 ID，用于禁用重复点击 */
const replaceLoadingEvidenceId = ref<number | null>(null)
/** 替换用隐藏 file input 的 context，在 input change 时使用 */
const replaceContextRef = ref<{ ev: EvidenceListItem; stage: StageVO; item: StageItemVO } | null>(null)
/** 替换用隐藏 file input 的 DOM 引用 */
const replaceFileInputRef = ref<HTMLInputElement | null>(null)

/** 审批历史弹窗 */
const showHistoryPopup = ref(false)
const historyList = ref<ProjectArchiveHistoryVO[]>([])
const historyLoading = ref(false)

/** 上传上下文：从某阶段某模板项点击「上传」时带入，用于提交时带 stageId + evidenceTypeCode */
const uploadContext = ref<{ stageId: number; evidenceTypeCode: string; displayName: string } | null>(null)

// 项目成员列表（详情 Tab 展示）
const members = ref<ProjectMemberVO[]>([])
const membersLoading = ref(false)
const memberRoleLabels: Record<string, string> = {
  owner: '负责人',
  editor: '编辑',
  viewer: '查看'
}
/** 角色排序权重：负责人 > 编辑 > 查看 */
const memberRoleOrder: Record<string, number> = {
  owner: 0,
  editor: 1,
  viewer: 2
}
/** 按负责人、编辑、查看顺序排列的成员列表 */
const sortedMembers = computed(() => {
  return [...members.value].sort(
    (a, b) => (memberRoleOrder[a.role] ?? 99) - (memberRoleOrder[b.role] ?? 99)
  )
})
function memberRoleLabel(role: string): string {
  return memberRoleLabels[role] || role
}

/** 关键缺失项：含 stageCode、evidenceTypeCode 用于点击后滚动定位（若后端改为返回 stageId/evidenceId，在此做映射即可） */
interface KeyMissingTarget {
  displayName: string
  stageCode: string
  evidenceTypeCode: string
  shortfall?: number
}

/** 关键缺失详情：按「未上传」「已上传未提交」「数量不足」分类，每项带定位信息 */
const keyMissingDetails = computed(() => {
  const progress = stageProgress.value
  const empty = {
    notUploaded: [] as KeyMissingTarget[],
    notSubmitted: [] as KeyMissingTarget[],
    shortfall: [] as (KeyMissingTarget & { shortfall: number })[]
  }
  if (!progress?.stages?.length) return empty
  const notUploaded: KeyMissingTarget[] = []
  const notSubmitted: KeyMissingTarget[] = []
  const shortfall: (KeyMissingTarget & { shortfall: number })[] = []
  const seenGroups = new Set<string>()

  for (const stage of progress.stages) {
    // 与界面展示保持一致：按展示名称去重后的模板项作为「关键缺失」计算基础
    const items = uniqueStageItems(stage)
    for (const item of items) {
      const required = item.required === true || item.isRequired === true
      const inGroup = !!item.ruleGroup && !!item.groupDisplayName
      const groupNotCompleted = inGroup && item.groupCompleted === false
      const stageCode = stage.stageCode
      const evidenceTypeCode = item.evidenceTypeCode

      if (inGroup && groupNotCompleted) {
        const key = `${stageCode}:${item.ruleGroup}`
        if (seenGroups.has(key)) continue
        seenGroups.add(key)
        const groupItems = items.filter((i: StageItemVO) => i.ruleGroup === item.ruleGroup)
        const allZero = groupItems.every((i: StageItemVO) => (i.uploadCount ?? 0) === 0)
        const anyCompleted = groupItems.some((i: StageItemVO) => i.completed === true)
        const displayName = item.groupDisplayName || item.displayName
        const target = { displayName, stageCode, evidenceTypeCode }
        if (allZero) notUploaded.push(target)
        else if (!anyCompleted) notSubmitted.push(target)
        else shortfall.push({ ...target, shortfall: 1 })
        continue
      }

      if (!required || item.completed) continue
      const displayName = item.displayName
      const up = item.uploadCount ?? 0
      const cur = item.currentCount ?? 0
      const min = item.minCount ?? 1
      const target = { displayName, stageCode, evidenceTypeCode }
      if (up === 0) notUploaded.push(target)
      else if (cur === 0) notSubmitted.push(target)
      else shortfall.push({ ...target, shortfall: Math.max(0, min - cur) })
    }
  }
  return { notUploaded, notSubmitted, shortfall }
})

/** 是否有任意关键缺失（用于是否展示关键缺失区块） */
const hasKeyMissing = computed(() => {
  const d = keyMissingDetails.value
  return d.notUploaded.length > 0 || d.notSubmitted.length > 0 || d.shortfall.length > 0
})

/** 关键缺失展开/收起：缺失总数 > 6 时默认收起，只展示前 6 条（按类均分） */
const keyMissingExpanded = ref(false)
const KEY_MISSING_COLLAPSE_THRESHOLD = 6
const displayedKeyMissing = computed(() => {
  const d = keyMissingDetails.value
  const total = d.notUploaded.length + d.notSubmitted.length + d.shortfall.length
  const needCollapse = total > KEY_MISSING_COLLAPSE_THRESHOLD && !keyMissingExpanded.value
  if (!needCollapse) return d
  const cap = 2
  return {
    notUploaded: d.notUploaded.slice(0, cap),
    notSubmitted: d.notSubmitted.slice(0, cap),
    shortfall: d.shortfall.slice(0, cap)
  }
})
const keyMissingHasMore = computed(() => {
  const d = keyMissingDetails.value
  const total = d.notUploaded.length + d.notSubmitted.length + d.shortfall.length
  return total > KEY_MISSING_COLLAPSE_THRESHOLD
})
const keyMissingHiddenCount = computed(() => {
  if (!keyMissingHasMore.value || keyMissingExpanded.value) return 0
  const d = keyMissingDetails.value
  const shown = displayedKeyMissing.value
  return (
    d.notUploaded.length - shown.notUploaded.length +
    d.notSubmitted.length - shown.notSubmitted.length +
    d.shortfall.length - shown.shortfall.length
  )
})

const HIGHLIGHT_FLASH_DURATION = 1500

/** 证据管理 Tab 的索引（与 van-tabs 顺序一致：0=详情/基本信息，1=证据） */
const EVIDENCE_TAB_INDEX = 1

/**
 * 点击关键缺失项：跨 Tab 时先切到证据管理 → 等待渲染 → 展开对应阶段 → 再等待展开 → 平滑滚动 → 高亮闪烁
 * @param entry 当前项，含 stageCode、evidenceTypeCode（若数据结构为 tabName/stageId/evidenceId，在此做映射即可）
 */
async function scrollToEvidence(entry: KeyMissingTarget) {
  const { stageCode, evidenceTypeCode } = entry

  // 1. 跨 Tab 切换：若当前不是“证据管理”Tab，先切换过去
  if (activeTab.value !== EVIDENCE_TAB_INDEX) {
    activeTab.value = EVIDENCE_TAB_INDEX
    await nextTick()
  }

  // 2. 第一次 DOM 等待：证据管理 Tab 内容挂载完成
  await nextTick()

  // 3. 展开对应折叠面板（van-collapse v-model 为 expandedStages，项 name 为 stageCode）
  if (!expandedStages.value.includes(stageCode)) {
    expandedStages.value = [...expandedStages.value, stageCode]
  }

  // 4. 第二次 DOM 等待：折叠面板撑开，获得真实高度
  await nextTick()

  const el = document.getElementById(`evidence-card-${stageCode}-${evidenceTypeCode}`)
  if (!el) return

  // 5. 平滑滚动到视区中间
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })

  // 6. 视觉高亮：短暂黄色闪烁后移除
  el.classList.add('highlight-flash')
  setTimeout(() => el.classList.remove('highlight-flash'), HIGHLIGHT_FLASH_DURATION)
}

// 证据列表相关
const evidenceList = ref<EvidenceListItem[]>([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)

// 筛选相关
const filterBizType = ref('')
const filterContentType = ref('')
const bizTypeOptions = [
  { text: '全部类型', value: '' },
  { text: '方案', value: 'PLAN' },
  { text: '报告', value: 'REPORT' },
  { text: '纪要', value: 'MINUTES' },
  { text: '测试', value: 'TEST' },
  { text: '验收', value: 'ACCEPTANCE' },
  { text: '其他', value: 'OTHER' }
]
const contentTypeOptions = [
  { text: '全部格式', value: '' },
  { text: 'PDF', value: 'application/pdf' },
  { text: 'Word', value: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' },
  { text: 'Excel', value: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' },
  { text: 'PNG图片', value: 'image/png' },
  { text: 'JPEG图片', value: 'image/jpeg' }
]

// 上传相关
const showUploadDialog = ref(false)
const uploadPhase = ref<'form' | 'result'>('form')
const uploadResult = ref<{
  evidenceId: number
  evidenceStatus: string
  title: string
  fileName?: string
  createdAt?: string
} | null>(null)
const uploading = ref(false)
const submitLoading = ref(false)
const deleteLoading = ref(false)
const invalidateLoading = ref(false)
const uploadForm = ref({
  name: '',
  type: 'OTHER',
  remark: ''
})
const uploadFileList = ref<UploaderFileListItem[]>([])
const showBizTypePicker = ref(false)
const showInvalidateReasonDialog = ref(false)
const invalidateReasonText = ref('')
const pendingInvalidateEvidenceId = ref<number | null>(null)
/** 从网格作废时用于确认后刷新该分类列表 */
const pendingInvalidateStageCode = ref<string | null>(null)
const pendingInvalidateEvidenceTypeCode = ref<string | null>(null)
const auth = useAuthStore()

/** 当前用户是否为该项目项目经理（owner） */
const isPM = computed(() => {
  const p = project.value
  const uid = auth.currentUser?.id
  if (!p || uid == null) return false
  return p.currentPmUserId != null && p.currentPmUserId === uid
})

/** 当前用户是否为 PMO 或系统管理员（可审批/退回） */
const isPMOOrAdmin = computed(() => {
  const code = auth.currentUser?.roleCode
  return code === 'PMO' || code === 'SYSTEM_ADMIN'
})

/** 项目状态胶囊样式 */
const statusBadgeClass = computed(() => {
  const s = project.value?.status
  if (s === 'active') return 'badge--active'
  if (s === 'pending_approval') return 'badge--pending'
  if (s === 'returned') return 'badge--returned'
  if (s === 'archived') return 'badge--archived'
  return 'badge--archived'
})

/** 项目状态展示文案 */
const statusBadgeText = computed(() => {
  const s = project.value?.status
  if (s === 'active') return '进行中'
  if (s === 'pending_approval') return '待审批'
  if (s === 'returned') return '已退回'
  if (s === 'archived') return '已归档'
  return s || '—'
})

/** 是否允许点击「申请归档」：门禁通过、非已归档且非待审批；returned 时须先处理完所有不符合项 */
const canArchiveForApply = computed(() => {
  const status = project.value?.status
  if (status === 'archived' || status === 'pending_approval') return false
  if (status === 'returned' && !isAllResolved.value) return false
  return !!stageProgress.value?.canArchive
})

/** 上传+删除可见：有上传权限；待审批时 PM 只读；已归档时仅 PMO/系统管理员可编辑 */
const canUploadAndEdit = computed(() => {
  const can = project.value?.permissions?.canUpload === true || project.value?.canUpload === true
  if (!can) return false
  // 项目已归档：仅 PMO / SYSTEM_ADMIN 可编辑，其它角色只读
  if (project.value?.status === 'archived' && !isPMOOrAdmin.value) return false
  if (project.value?.status === 'pending_approval' && isPM.value) return false
  return true
})

/** V1：上传按钮仅在后端返回 canUpload 时显示 */
const canUpload = computed(() => project.value?.permissions?.canUpload === true || project.value?.canUpload === true)

// 上传结果：状态展示（与列表/详情统一用 evidenceStatus 优先）
const uploadResultStatusText = computed(() => mapStatusToText(getEffectiveEvidenceStatus(uploadResult.value)))
const uploadResultStatusTagType = computed(() => evidenceStatusTagType(getEffectiveEvidenceStatus(uploadResult.value)))
const showUploadResultSubmit = computed(
  () => getEffectiveEvidenceStatus(uploadResult.value) === 'DRAFT'
)
const showUploadResultDelete = computed(() => getEffectiveEvidenceStatus(uploadResult.value) === 'DRAFT')
const showUploadResultInvalidate = computed(() => {
  return getEffectiveEvidenceStatus(uploadResult.value) === 'SUBMITTED' && project.value?.canInvalidate === true
})
const bizTypePickerOptions = [
  { text: '方案', value: 'PLAN' },
  { text: '报告', value: 'REPORT' },
  { text: '纪要', value: 'MINUTES' },
  { text: '测试', value: 'TEST' },
  { text: '验收', value: 'ACCEPTANCE' },
  { text: '其他', value: 'OTHER' }
]

// 业务类型映射
const bizTypeMap: Record<string, string> = {
  PLAN: '方案',
  REPORT: '报告',
  MINUTES: '纪要',
  TEST: '测试',
  ACCEPTANCE: '验收',
  OTHER: '其他'
}

// 列表项状态展示：统一用 getEffectiveEvidenceStatus（evidenceStatus 优先）
function evidenceListStatusText(evidence: EvidenceListItem) {
  return mapStatusToText(getEffectiveEvidenceStatus(evidence))
}
function evidenceListStatusTagType(evidence: EvidenceListItem) {
  return evidenceStatusTagType(getEffectiveEvidenceStatus(evidence))
}

/** 证据状态 → 现场展示三类：待提交 / 已提交(含归档) / 不合格 */
function evidenceBadgeType(evidence: EvidenceListItem): 'pending' | 'submitted' | 'rejected' {
  const status = getEffectiveEvidenceStatus(evidence)
  if (status === 'INVALID') return 'rejected'
  if (status === 'SUBMITTED' || status === 'ARCHIVED') return 'submitted'
  return 'pending'
}
function evidenceBadgeText(evidence: EvidenceListItem): string {
  const status = getEffectiveEvidenceStatus(evidence)
  if (status === 'INVALID') return '不合格'
  if (status === 'ARCHIVED') return '已归档'
  if (status === 'SUBMITTED') return '已提交'
  return '待提交'
}

/**
 * 去重后的阶段模板项列表：
 * 后端 StageProgressService 按 stage_evidence_template 行构造 items，
 * 若存在重复配置（同一阶段内 evidenceTypeCode 重复），这里以 evidenceTypeCode 为 key 去重，
 * 仅保留遇到的第一项，避免上传页出现两个一模一样的证据类别。
 */
function uniqueStageItems(stage: StageVO): StageItemVO[] {
  if (!stage?.items?.length) return []
  const seen = new Set<string>()
  const result: StageItemVO[] = []
  for (const item of stage.items) {
    // 以「展示给用户看的名称」优先去重：同一阶段内若显示名称相同，视为同一证据类别
    const key = (item.groupDisplayName || item.displayName || item.evidenceTypeCode || '').trim()
    if (!key) {
      result.push(item)
      continue
    }
    if (seen.has(key)) continue
    seen.add(key)
    result.push(item)
  }
  return result
}

/** 当前项还差几张（用于上传入口文案） */
function uploadShortfall(_stage: StageVO, item: StageItemVO): number {
  const need = item.minCount ?? 1
  const have = item.uploadCount ?? item.currentCount ?? 0
  return Math.max(0, need - have)
}

// 打开上传弹窗（重置为阶段1）
function openUploadDialog() {
  uploadPhase.value = 'form'
  uploadResult.value = null
  resetUploadForm()
  showUploadDialog.value = true
}

/** 从某阶段某模板项的证据列表点击「上传」：带上阶段与类型，打开上传弹窗 */
function openUploadForStage(stage: StageVO) {
  const code = openedEvidenceTypeCode.value
  if (!code || stage.stageId == null) {
    showToast('无法获取阶段或类型信息')
    return
  }
  uploadContext.value = {
    stageId: stage.stageId,
    evidenceTypeCode: code,
    displayName: getOpenedItemDisplayName(stage)
  }
  openUploadDialog()
}

// 关闭上传弹窗并重置
function closeUploadDialog() {
  showUploadDialog.value = false
  uploadPhase.value = 'form'
  uploadResult.value = null
  uploadContext.value = null
  resetUploadForm()
}

// 跳转证据详情（带上当前阶段 code 与项目状态，返回时用于恢复展开，并在详情页判断是否可作废等）
function goToEvidenceDetail(id: number, stageCode?: string) {
  const query: Record<string, string> = {
    fromProject: String(projectId.value),
    projectStatus: project.value?.status || ''
  }
  if (route.query.from === 'evidence-by-project') query.from = 'evidence-by-project'
  else if (route.query.from === 'evidence') query.from = 'evidence'
  if (route.query.returnKeyword) query.returnKeyword = String(route.query.returnKeyword)
  if (stageCode) query.expandedStage = stageCode
  router.push({ path: `/evidence/detail/${id}`, query })
}

/** 网格内删除/作废证据：草稿物理删除，已提交则先弹窗填写作废原因再作废 */
async function onDeleteEvidence(ev: EvidenceListItem, stage?: StageVO, evidenceItem?: StageItemVO) {
  const id = ev.evidenceId
  const status = getEffectiveEvidenceStatus(ev)
  try {
    if (status === 'DRAFT') {
      await showConfirmDialog({ title: '确认删除', message: '删除后不可恢复，确定继续？' })
      const res = (await deleteEvidence(id)) as { code: number; message?: string }
      if (res?.code === 0) {
        showToast('已删除')
        if (stage?.stageCode && evidenceItem?.evidenceTypeCode) loadEvidenceForItem(stage.stageCode, evidenceItem.evidenceTypeCode)
        loadStageProgress()
      } else {
        showToast(res?.message || '删除失败')
      }
    } else {
      // 已提交：先弹出填写作废原因弹窗，确认后再调用作废接口
      pendingInvalidateEvidenceId.value = id
      pendingInvalidateStageCode.value = stage?.stageCode ?? null
      pendingInvalidateEvidenceTypeCode.value = evidenceItem?.evidenceTypeCode ?? null
      invalidateReasonText.value = ''
      showInvalidateReasonDialog.value = true
    }
  } catch {
    // 用户取消
  }
}

function goToMembers() {
  router.push({ path: `/projects/${projectId.value}/members` })
}

// 文件类型映射
const getFileTypeText = (contentType: string) => {
  if (contentType?.includes('pdf')) return 'PDF'
  if (contentType?.includes('wordprocessingml')) return 'Word'
  if (contentType?.includes('spreadsheetml')) return 'Excel'
  if (contentType?.includes('image')) return '图片'
  return '文件'
}

/** 切换「是否含采购」后请求后端并刷新 */
async function onHasProcurementChange(value: boolean) {
  if (!projectId.value || !project.value) return
  try {
    const res = await updateProject(projectId.value, { hasProcurement: value })
    if (res?.code === 0 && res.data) {
      project.value.hasProcurement = res.data.hasProcurement ?? false
      showSuccessToast(value ? '已设为含采购' : '已设为不含采购')
      loadStageProgress()
    } else {
      showToast(res?.message || '更新失败')
    }
  } catch (e: unknown) {
    showToast(getFriendlyErrorMessage(e, '更新失败'))
  }
}

// 加载项目信息（真实 API）
const loadProject = async () => {
  projectError.value = ''
  projectLoading.value = true
  try {
    const res = await getProjectDetail(projectId.value)
    if (res.code === 0 && res.data) {
      const p = res.data
      project.value = {
        id: p.id,
        code: p.code,
        name: p.name,
        description: p.description ?? '',
        status: p.status,
        createdAt: p.createdAt ?? '',
        hasProcurement: p.hasProcurement ?? false,
        permissions: p.permissions,
        canInvalidate: p.canInvalidate ?? false,
        canManageMembers: p.canManageMembers ?? false,
        canUpload: p.canUpload ?? p.permissions?.canUpload ?? false,
        currentPmUserId: p.currentPmUserId,
        currentPmDisplayName: p.currentPmDisplayName,
        rejectComment: (p as { rejectComment?: string }).rejectComment
      }
      loadMembers()
    } else {
      projectError.value = res.message || '加载失败'
    }
  } catch (e: any) {
    projectError.value = e?.message || '加载失败'
    if (e?.response?.data?.code === 403) {
      projectError.value = '无权限访问该项目'
    }
  } finally {
    projectLoading.value = false
  }
}

// 加载项目成员列表（详情页展示）
const loadMembers = async () => {
  if (!projectId.value) return
  membersLoading.value = true
  try {
    const res = await getProjectMembers(projectId.value)
    if (res.code === 0 && res.data) members.value = res.data
    else members.value = []
  } catch {
    members.value = []
  } finally {
    membersLoading.value = false
  }
}

// 加载证据列表
const loadEvidenceList = async () => {
  if (loading.value) return
  
  loading.value = true
  try {
    const params: any = {}
    if (filterBizType.value) {
      params.bizType = filterBizType.value
    }
    if (filterContentType.value) {
      // 如果是图片筛选，需要特殊处理（后端可能需要精确匹配）
      params.contentType = filterContentType.value
    }
    
    const response = await getEvidenceList(projectId.value, params)
    if (response.code === 0) {
      evidenceList.value = response.data || []
      finished.value = true
    } else {
      showToast(response.message || '加载失败')
    }
  } catch (error: unknown) {
    console.error('Load evidence list error:', error)
    showToast(getFriendlyErrorMessage(error, '加载失败'))
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

// 下拉刷新
const onRefresh = () => {
  finished.value = false
  evidenceList.value = []
  loadEvidenceList()
}

// 上拉加载
const onLoad = () => {
  loadEvidenceList()
}

// 筛选变化
const onFilterChange = () => {
  finished.value = false
  evidenceList.value = []
  loadEvidenceList()
}

// 获取证据标签
const getEvidenceLabel = (evidence: EvidenceListItem) => {
  const parts: string[] = []
  parts.push(`类型: ${bizTypeMap[evidence.bizType] || evidence.bizType}`)
  parts.push(`格式: ${getFileTypeText(evidence.contentType)}`)
  if (evidence.latestVersion) {
    parts.push(`版本: v${evidence.latestVersion.versionNo}`)
  }
  parts.push(`更新: ${formatDate(evidence.updatedAt)}`)
  return parts.join(' | ')
}

// 格式化日期
const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return `${date.getMonth() + 1}-${date.getDate()} ${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}`
}

// 下载文件
const handleDownload = async (evidence: EvidenceListItem) => {
  if (!evidence.latestVersion) {
    showToast('暂无版本文件')
    return
  }

  try {
    showLoadingToast({ message: '下载中...', forbidClick: true, duration: 0 })
    const blob = await downloadVersionFile(evidence.latestVersion.versionId)
    
    // 创建下载链接
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = evidence.latestVersion.originalFilename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    
    showSuccessToast('下载成功')
  } catch (error: any) {
    console.error('Download error:', error)
    showToast(getFriendlyErrorMessage(error, '下载失败'))
  } finally {
    closeToast()
  }
}

// 预览文件（支持 PDF / 图片 / 文本；其余类型提示下载）
const handlePreview = async (evidence: EvidenceListItem) => {
  if (!evidence.latestVersion) {
    showToast('暂无版本文件')
    return
  }

  const ct = (evidence.contentType || '').toLowerCase()
  const isPreviewable =
    ct.includes('pdf') ||
    ct.startsWith('image/') ||
    ct.startsWith('text/') ||
    ct.includes('json') ||
    ct.includes('xml')

  if (!isPreviewable) {
    showToast('该文件类型暂不支持预览，请下载查看')
    return
  }

  try {
    showLoadingToast({ message: '打开预览...', forbidClick: true, duration: 0 })
    const blob = await downloadVersionFile(evidence.latestVersion.versionId)
    const url = window.URL.createObjectURL(blob)

    // 打开新窗口预览（部分浏览器会在新标签内渲染 PDF/图片/文本）
    window.open(url, '_blank', 'noopener,noreferrer')

    // 延迟释放，避免新窗口尚未读取完成就 revoke
    setTimeout(() => window.URL.revokeObjectURL(url), 60_000)
  } catch (error: any) {
    console.error('Preview error:', error)
    showToast(getFriendlyErrorMessage(error, '预览失败'))
  } finally {
    closeToast()
  }
}

// 业务类型选择确认
const onBizTypeConfirm = ({ selectedOptions }: any) => {
  if (selectedOptions && selectedOptions.length > 0) {
    uploadForm.value.type = selectedOptions[0].value
  }
  showBizTypePicker.value = false
}

/** 业务类型选择器滚轮：向上滚上一项，向下滚下一项 */
function onBizTypePickerWheel(e: WheelEvent) {
  const opts = bizTypePickerOptions
  const idx = opts.findIndex((c) => c.value === uploadForm.value.type)
  const cur = idx < 0 ? 0 : idx
  const next = e.deltaY > 0 ? cur + 1 : cur - 1
  const newIdx = Math.max(0, Math.min(next, opts.length - 1))
  if (newIdx === cur) return
  uploadForm.value.type = opts[newIdx].value
}

/** 选择文件前校验大小：图片 5MB、文档 50MB */
function onUploaderBeforeRead(file: File | File[]): boolean {
  const f = Array.isArray(file) ? file[0] : file
  if (!f) return true
  const r = validateFileSize(f)
  if (!r.ok) {
    showToast(r.message)
    return false
  }
  return true
}

// 上传证据（阶段驱动：必须从某模板项「上传」带入 stageId + evidenceTypeCode）
// 支持：图片前端轻度压缩 + 真实上传进度展示
const handleUpload = async () => {
  if (!uploadContext.value) {
    showToast('请先点击下方某证据类型（如「启动现场照片」）旁的「上传」按钮')
    return
  }
  if (!uploadForm.value.name.trim()) {
    showToast('请输入证据标题')
    return
  }

  if (uploadFileList.value.length === 0) {
    showToast('请选择要上传的文件')
    return
  }

  let file = uploadFileList.value[0].file as File | undefined
  if (!file || !(file instanceof File)) {
    showToast('文件无效')
    return
  }
  const sizeCheck = validateFileSize(file)
  if (!sizeCheck.ok) {
    showToast(sizeCheck.message)
    return
  }

  uploading.value = true
  try {
    // 仅对图片进行前端轻度压缩，PDF/Word 等直接上传
    if (isImageFile(file)) {
      showLoadingToast({ message: '图片压缩中...', forbidClick: true, duration: 0 })
      file = await compressImageIfNeeded(file)
      closeToast()
    }

    showLoadingToast({ message: '正在上传 0%...', forbidClick: true, duration: 0 })

    const formData = new FormData()
    formData.append('name', uploadForm.value.name)
    formData.append('stageId', String(uploadContext.value.stageId))
    formData.append('evidenceTypeCode', uploadContext.value.evidenceTypeCode)
    if (uploadForm.value.remark) {
      formData.append('remark', uploadForm.value.remark)
    }
    formData.append('file', file)

    const response = (await uploadEvidence(projectId.value, formData, {
      onUploadProgress(percent) {
        showLoadingToast({
          message: percent > 0 ? `正在上传 ${percent}%...` : '正在上传...',
          forbidClick: true,
          duration: 0
        })
      }
    })) as {
      code: number
      message?: string
      data?: {
        id: number
        evidenceStatus?: string
        title?: string
        createdAt?: string
      }
    }
    closeToast()

    if (response.code === 0 && response.data) {
      const data = response.data
      const status = data.evidenceStatus ?? 'DRAFT'
      uploadResult.value = {
        evidenceId: data.id,
        evidenceStatus: status,
        title: data.title ?? uploadForm.value.name,
        fileName: file instanceof File ? file.name : undefined,
        createdAt: data.createdAt ? formatDate(data.createdAt) : undefined
      }
      uploadPhase.value = 'result'
      showSuccessToast('上传成功')
      refreshCurrentItemEvidences()
      loadStageProgress()
    } else {
      showToast(response.message || '上传失败')
    }
  } catch (error: any) {
    closeToast()
    console.error('Upload error:', error)
    showToast(getFriendlyErrorMessage(error, '上传失败'))
  } finally {
    uploading.value = false
  }
}

// 阶段2：提交
async function handleUploadResultSubmit() {
  if (!uploadResult.value || getEffectiveEvidenceStatus(uploadResult.value) !== 'DRAFT') return
  try {
    await showConfirmDialog({
      title: '确认提交',
      message: '提交后将进入管理流程，是否继续？'
    })
  } catch {
    return
  }
  submitLoading.value = true
  try {
    const res = (await submitEvidence(uploadResult.value.evidenceId)) as { code: number; message?: string }
    if (res?.code === 0) {
      uploadResult.value = { ...uploadResult.value!, evidenceStatus: 'SUBMITTED' }
      showSuccessToast('已提交')
      onRefresh()
      // 刷新证据 Tab 中当前模板项列表与阶段进度，使「草稿」及时变为「已提交」
      refreshCurrentItemEvidences()
      loadStageProgress()
    } else {
      showToast(res?.message || '提交失败')
    }
  } catch (e: any) {
    showToast(getFriendlyErrorMessage(e, '提交失败'))
  } finally {
    submitLoading.value = false
  }
}

// 阶段2：草稿物理删除
async function handleUploadResultDelete() {
  if (!uploadResult.value || getEffectiveEvidenceStatus(uploadResult.value) !== 'DRAFT') return
  try {
    await showConfirmDialog({ title: '确认删除', message: '删除后不可恢复，是否继续？' })
  } catch {
    return
  }
  const id = uploadResult.value.evidenceId
  deleteLoading.value = true
  try {
    const res = (await deleteEvidence(id)) as { code: number; message?: string }
    if (res?.code === 0) {
      showSuccessToast('已删除')
      closeUploadDialog()
      onRefresh()
      refreshCurrentItemEvidences()
      loadStageProgress()
    } else {
      showToast(res?.message || '删除失败')
    }
  } catch (e: any) {
    showToast(getFriendlyErrorMessage(e, '删除失败'))
  } finally {
    deleteLoading.value = false
  }
}

// 阶段2：作废（先弹出填写原因，仅已提交可作废）
function handleUploadResultInvalidate() {
  if (!uploadResult.value) return
  pendingInvalidateEvidenceId.value = uploadResult.value.evidenceId
  pendingInvalidateStageCode.value = null
  pendingInvalidateEvidenceTypeCode.value = null
  invalidateReasonText.value = ''
  showInvalidateReasonDialog.value = true
}

async function onInvalidateReasonConfirm(action: string): Promise<boolean> {
  if (action !== 'confirm') return true
  const reason = invalidateReasonText.value?.trim()
  if (!reason) {
    showToast('请填写作废原因')
    return false
  }
  const id = pendingInvalidateEvidenceId.value
  if (id == null) return true
  const fromGrid = pendingInvalidateStageCode.value != null && pendingInvalidateEvidenceTypeCode.value != null
  invalidateLoading.value = true
  try {
    const res = (await invalidateEvidence(id, reason)) as { code: number; message?: string }
    if (res?.code === 0) {
      if (uploadResult.value?.evidenceId === id) {
        uploadResult.value = { ...uploadResult.value!, evidenceStatus: 'INVALID' }
      }
      showSuccessToast('已作废')
      onRefresh()
      if (fromGrid && pendingInvalidateStageCode.value && pendingInvalidateEvidenceTypeCode.value) {
        loadEvidenceForItem(pendingInvalidateStageCode.value, pendingInvalidateEvidenceTypeCode.value)
      } else {
        refreshCurrentItemEvidences()
      }
      loadStageProgress()
      pendingInvalidateEvidenceId.value = null
      pendingInvalidateStageCode.value = null
      pendingInvalidateEvidenceTypeCode.value = null
      invalidateReasonText.value = ''
      return true
    }
    showToast(res?.message || '作废失败')
    return false
  } catch (e: any) {
    showToast(getFriendlyErrorMessage(e, '作废失败'))
    return false
  } finally {
    invalidateLoading.value = false
  }
}

// 阶段2：查看详情（关闭弹窗并跳转）
function goToUploadResultDetail() {
  if (uploadResult.value) {
    closeUploadDialog()
    const query: Record<string, string> = { fromProject: String(projectId.value) }
    if (route.query.from === 'evidence-by-project') query.from = 'evidence-by-project'
    else if (route.query.from === 'evidence') query.from = 'evidence'
    if (route.query.returnKeyword) query.returnKeyword = String(route.query.returnKeyword)
    const stageCode = stageProgress.value?.stages?.find(st => st.stageId === uploadContext.value?.stageId)?.stageCode
    if (stageCode) query.expandedStage = stageCode
    router.push({ path: `/evidence/detail/${uploadResult.value.evidenceId}`, query })
  }
}

// 阶段2：继续上传（回到阶段1）
function continueUpload() {
  uploadPhase.value = 'form'
  uploadResult.value = null
  resetUploadForm()
}

// 重置上传表单
const resetUploadForm = () => {
  uploadForm.value = {
    name: '',
    type: 'OTHER',
    remark: ''
  }
  uploadFileList.value = []
}

// 阶段进度加载
async function loadStageProgress() {
  if (!projectId.value) return
  stageProgressLoading.value = true
  try {
    const res = await getStageProgress(projectId.value)
    if (res?.code === 0 && res.data) {
      stageProgress.value = res.data
      const expanded = route.query.expandedStage as string | undefined
      const scrollStage = route.query.scrollStage as string | undefined
      const toExpand: string[] = []
      if (expanded && res.data.stages?.some((st: { stageCode: string }) => st.stageCode === expanded)) {
        toExpand.push(expanded)
      }
      if (scrollStage && res.data.stages?.some((st: { stageCode: string }) => st.stageCode === scrollStage)) {
        toExpand.push(scrollStage)
      }
      if (toExpand.length) {
        expandedStages.value = [...new Set([...expandedStages.value, ...toExpand])]
      }
      loadAllItemEvidences()
    } else {
      stageProgress.value = null
    }
  } catch {
    stageProgress.value = null
  } finally {
    stageProgressLoading.value = false
  }
}

function healthTagType(health: string): 'success' | 'warning' | 'default' {
  if (health === 'COMPLETE') return 'success'
  if (health === 'PARTIAL') return 'warning'
  return 'default'
}

/** 阶段健康状态中文展示 */
function healthStatusText(health: string): string {
  if (health === 'COMPLETE') return '已完成'
  if (health === 'PARTIAL') return '进行中'
  if (health === 'NOT_STARTED') return '未开始'
  return health || '—'
}

function getOpenedItemDisplayName(stage: StageVO): string {
  const code = openedEvidenceTypeCode.value
  if (!code || !stage.items) return ''
  const item = stage.items.find((i) => i.evidenceTypeCode === code)
  return item ? (item.groupDisplayName || item.displayName || code) : code
}

function toggleEvidenceList(stageCode: string, item: StageItemVO) {
  const code = item.evidenceTypeCode
  if (!code) return
  if (openedStageCode.value === stageCode && openedEvidenceTypeCode.value === code) {
    openedStageCode.value = null
    openedEvidenceTypeCode.value = null
    evidenceByTypeList.value = []
    return
  }
  openedStageCode.value = stageCode
  openedEvidenceTypeCode.value = code
  loadEvidenceByType()
}

async function loadEvidenceByType() {
  if (!projectId.value || !openedStageCode.value || !openedEvidenceTypeCode.value) return
  evidenceByTypeLoading.value = true
  try {
    const res = await getEvidencesByStageType(projectId.value, openedStageCode.value, openedEvidenceTypeCode.value)
    if (res?.code === 0 && Array.isArray(res.data)) {
      evidenceByTypeList.value = res.data
    } else {
      evidenceByTypeList.value = []
    }
  } catch {
    evidenceByTypeList.value = []
  } finally {
    evidenceByTypeLoading.value = false
  }
}

/** 平铺模式：为某模板项加载证据列表 */
async function loadEvidenceForItem(stageCode: string, evidenceTypeCode: string) {
  if (!projectId.value || !stageCode || !evidenceTypeCode) return
  const mapKey = `${stageCode}:${evidenceTypeCode}`
  evidenceByItemLoading.value[mapKey] = true
  try {
    const res = await getEvidencesByStageType(projectId.value, stageCode, evidenceTypeCode)
    if (res?.code === 0 && Array.isArray(res.data)) {
      evidenceByItemMap.value[mapKey] = res.data
    } else {
      evidenceByItemMap.value[mapKey] = []
    }
  } catch {
    evidenceByItemMap.value[mapKey] = []
  } finally {
    evidenceByItemLoading.value[mapKey] = false
  }
}

/** 阶段进度加载完成后，自动加载所有模板项的证据列表 */
function loadAllItemEvidences() {
  if (!stageProgress.value?.stages) return
  for (const s of stageProgress.value.stages) {
    for (const item of (s.items || [])) {
      if (item.evidenceTypeCode) {
        loadEvidenceForItem(s.stageCode, item.evidenceTypeCode)
      }
    }
  }
}

/** 上传成功后刷新对应模板项 */
function refreshCurrentItemEvidences() {
  if (uploadContext.value) {
    const stageCode = stageProgress.value?.stages?.find(s => s.stageId === uploadContext.value?.stageId)?.stageCode
    if (stageCode && uploadContext.value.evidenceTypeCode) {
      loadEvidenceForItem(stageCode, uploadContext.value.evidenceTypeCode)
    }
  }
}

/**
 * 一键替换不符合项：先上传新文件得到新 evidence，再作废旧 evidence（已提交不可物理删除），最后刷新列表
 */
async function handleReplaceRejectedEvidence(
  ev: EvidenceListItem,
  stage: StageVO,
  item: StageItemVO,
  file: File
) {
  if (!projectId.value || stage.stageId == null || !item.evidenceTypeCode) {
    showToast('无法获取阶段或类型信息')
    return
  }
  replaceLoadingEvidenceId.value = ev.evidenceId
  try {
    if (isImageFile(file)) {
      showLoadingToast({ message: '图片处理中...', forbidClick: true, duration: 0 })
      file = await compressImageIfNeeded(file)
      closeToast()
    }
    showLoadingToast({ message: '正在上传...', forbidClick: true, duration: 0 })
    const formData = new FormData()
    formData.append('name', file.name || '替换上传')
    formData.append('stageId', String(stage.stageId))
    formData.append('evidenceTypeCode', item.evidenceTypeCode)
    formData.append('file', file)
    const uploadRes = (await uploadEvidence(projectId.value, formData)) as {
      code: number
      message?: string
      data?: { id: number }
    }
    closeToast()
    if (uploadRes?.code !== 0 || !uploadRes?.data?.id) {
      showToast(uploadRes?.message || '上传失败')
      return
    }
    const invalidateRes = (await invalidateEvidence(ev.evidenceId, '替换为新材料')) as { code: number; message?: string }
    if (invalidateRes?.code !== 0) {
      showToast(invalidateRes?.message || '作废旧项失败')
      return
    }
    await loadEvidenceForItem(stage.stageCode, item.evidenceTypeCode)
    loadStageProgress()
    showSuccessToast('已替换')
  } catch (e: unknown) {
    closeToast()
    showToast(getFriendlyErrorMessage(e, '替换失败'))
  } finally {
    replaceLoadingEvidenceId.value = null
  }
}

/** 替换用 file input 的 change：从 context 取 ev/stage/item，选文件后执行替换并清空 input */
async function onReplaceFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  const ctx = replaceContextRef.value
  input.value = ''
  replaceContextRef.value = null
  if (!file || !ctx) return
  await handleReplaceRejectedEvidence(ctx.ev, ctx.stage, ctx.item, file)
}

function triggerReplaceFileInput(ev: EvidenceListItem, stage: StageVO, item: StageItemVO) {
  if (replaceLoadingEvidenceId.value != null) return
  replaceContextRef.value = { ev, stage, item }
  nextTick(() => replaceFileInputRef.value?.click())
}

function getItemEvidences(stageCode: string, evidenceTypeCode: string): EvidenceListItem[] {
  return evidenceByItemMap.value[`${stageCode}:${evidenceTypeCode}`] || []
}
function isItemLoading(stageCode: string, evidenceTypeCode: string): boolean {
  return !!evidenceByItemLoading.value[`${stageCode}:${evidenceTypeCode}`]
}

/** 判断是否为图片类型 */
function isImageType(contentType?: string): boolean {
  return !!contentType && contentType.startsWith('image/')
}

/** 文件类型图标配置 */
interface FileIconConfig {
  icon: string      // Vant icon name
  label: string     // 右上角文字标签（如 PDF、DOC）
  color: string     // 图标颜色
  bg: string        // 卡片背景色
}

function getFileIconConfig(fileName?: string): FileIconConfig {
  const ext = (fileName || '').split('.').pop()?.toLowerCase() || ''
  switch (ext) {
    case 'pdf':
      return { icon: 'description', label: 'PDF', color: '#ee4d2d', bg: '#fff1f0' }
    case 'doc':
    case 'docx':
      return { icon: 'description', label: 'DOC', color: '#1989fa', bg: '#e8f4ff' }
    case 'xls':
    case 'xlsx':
    case 'csv':
      return { icon: 'bar-chart-o', label: 'XLS', color: '#07c160', bg: '#eefbf3' }
    case 'ppt':
    case 'pptx':
      return { icon: 'photo-o', label: 'PPT', color: '#ff976a', bg: '#fff7e8' }
    case 'zip':
    case 'rar':
    case '7z':
    case 'tar':
    case 'gz':
      return { icon: 'gift-o', label: 'ZIP', color: '#faad14', bg: '#fffbe6' }
    case 'txt':
    case 'md':
      return { icon: 'notes-o', label: 'TXT', color: '#969799', bg: '#f7f8fa' }
    default:
      return { icon: 'description', label: ext.toUpperCase() || '?', color: '#969799', bg: '#f7f8fa' }
  }
}

function getEvidenceFileName(ev: EvidenceListItem): string {
  return ev.latestVersion?.originalFilename || ev.title || ''
}

/** 从某阶段某模板项直接点上传 */
function openUploadForItem(stage: StageVO, item: StageItemVO) {
  if (!item.evidenceTypeCode || stage.stageId == null) {
    showToast('无法获取阶段或类型信息')
    return
  }
  uploadContext.value = {
    stageId: stage.stageId,
    evidenceTypeCode: item.evidenceTypeCode,
    displayName: item.groupDisplayName || item.displayName || item.evidenceTypeCode
  }
  openUploadDialog()
}

/** 实际执行归档申请（审批流）：调用 archiveApply，成功后刷新；门禁失败时弹窗 */
async function doArchive(): Promise<boolean> {
  if (!projectId.value) return false
  try {
    const res = await archiveApply(projectId.value)
    if (res?.code === 0) {
      showSuccessToast('已提交归档申请')
      loadProject()
      loadStageProgress()
      return true
    }
    if (res?.code === 400 && res.data) {
      archiveBlockMessage.value = (res as { message?: string }).message ?? '不满足归档条件'
      archiveBlockData.value = res.data as ArchiveBlockVO
      showArchiveBlockDialog.value = true
    } else {
      showToast((res as { message?: string })?.message ?? '申请失败')
    }
    return false
  } catch (err: unknown) {
    const structured = getStructuredErrorData(err as { response?: { data?: { code?: number; data?: unknown; message?: string } } })
    if (structured?.data) {
      archiveBlockMessage.value = structured.message
      archiveBlockData.value = structured.data as ArchiveBlockVO
      showArchiveBlockDialog.value = true
    } else {
      showToast(getFriendlyErrorMessage(err, '申请失败'))
    }
    return false
  }
}

/** 不可归档时的提示文案（与当前业务状态一致）；returned 时区分「未处理完不符合项」与「完成度未达 100%」 */
function getArchiveDisabledMessage(): string {
  const status = project.value?.status
  if (status === 'archived') return '项目已归档'
  if (status === 'pending_approval') return '归档申请审批中，请等待审批结果'
  if (status === 'returned') {
    if (!isAllResolved.value) return '请先处理所有不符合项后再重新申请归档'
    if (!stageProgress.value?.canArchive || stageProgress.value?.overallCompletionPercent !== 100) {
      return '证据完成度未达 100%，请补全后再重新申请归档'
    }
  }
  return '请先补全关键证据再申请归档'
}

/** 申请归档按钮点击：先确认再执行；不可归档时按状态提示 */
async function onArchiveClick() {
  if (!canArchiveForApply.value || project.value?.status === 'archived') {
    showToast(getArchiveDisabledMessage())
    return
  }
  try {
    const message =
      project.value?.status === 'returned'
        ? '确定要重新提交归档申请吗？提交后将再次进入审批流程。'
        : '确定要提交归档申请吗？提交后将进入审批流程。'
    await showConfirmDialog({ title: '确认申请归档', message })
  } catch {
    return
  }
  handleArchive()
}

/** 审批通过 */
async function onApproveClick() {
  if (!projectId.value) return
  try {
    await showConfirmDialog({ title: '确认通过', message: '确认通过该项目的归档申请？' })
  } catch {
    return
  }
  try {
    const res = await archiveApprove(projectId.value)
    if (res?.code === 0) {
      showSuccessToast('已通过')
      loadProject()
      loadStageProgress()
    } else {
      showToast((res as { message?: string })?.message ?? '操作失败')
    }
  } catch (e: unknown) {
    showToast(getFriendlyErrorMessage(e, '操作失败'))
  }
}

/** 退回弹窗确认：必填退回原因后调用 archiveReject */
async function onRejectConfirm(action: string): Promise<boolean> {
  if (action !== 'confirm') {
    showRejectDialog.value = false
    rejectCommentText.value = ''
    return true
  }
  const comment = rejectCommentText.value?.trim()
  if (!comment) {
    showToast('请填写退回原因')
    return false
  }
  const evidenceComments = Object.entries(localRejectMap.value)
    .filter(([, c]) => c != null && String(c).trim() !== '')
    .map(([id, c]) => ({ evidenceId: Number(id), comment: String(c).trim() }))
  try {
    const res = await archiveReject(projectId.value, {
      comment,
      ...(evidenceComments.length > 0 ? { evidenceComments } : {})
    })
    if (res?.code === 0) {
      showRejectDialog.value = false
      rejectCommentText.value = ''
      localRejectMap.value = {}
      archiveRejectDraftStore.clearProjectDraft(projectId.value)
      showSuccessToast('已退回')
      loadProject()
      loadStageProgress()
      return true
    }
    showToast((res as { message?: string })?.message ?? '操作失败')
    resetRejectDialogState()
    return false
  } catch (e: unknown) {
    showToast(getFriendlyErrorMessage(e, '操作失败'))
    resetRejectDialogState()
    return false
  }
}

/** 关闭退回弹窗并清空输入与本地不符合项，避免接口报错后状态残留 */
function resetRejectDialogState() {
  showRejectDialog.value = false
  rejectCommentText.value = ''
  localRejectMap.value = {}
  archiveRejectDraftStore.clearProjectDraft(projectId.value)
}

/** 当前证据卡片展示的退回原因：returned 时用接口返回；pending 时 PMO 用本地暂存 */
function getDisplayRejectComment(ev: EvidenceListItem): string | null {
  if (!project.value) return null
  if (project.value.status === 'returned') return ev.rejectComment ?? null
  if (project.value.status === 'pending_approval' && isPMOOrAdmin.value) {
    const local = localRejectMap.value[String(ev.evidenceId)]
    return (local != null && String(local).trim() !== '') ? String(local).trim() : (ev.rejectComment ?? null)
  }
  return ev.rejectComment ?? null
}

/** 打开「标记不符合」弹窗（isEdit 时预填当前原因） */
function openMarkRejectDialog(ev: EvidenceListItem, isEdit: boolean) {
  markRejectEvidenceId.value = ev.evidenceId
  markRejectCommentText.value = isEdit ? (localRejectMap.value[String(ev.evidenceId)] ?? '') : ''
  showMarkRejectDialog.value = true
}

/** 标记不符合弹窗确认：写入 localRejectMap */
function onMarkRejectConfirm(action: string): boolean {
  if (action !== 'confirm') {
    showMarkRejectDialog.value = false
    markRejectEvidenceId.value = null
    markRejectCommentText.value = ''
    return true
  }
  const id = markRejectEvidenceId.value
  const text = markRejectCommentText.value?.trim()
  if (id == null) return true
  if (!text) {
    showToast('请填写不符合原因')
    return false
  }
  localRejectMap.value = { ...localRejectMap.value, [String(id)]: text }
  showMarkRejectDialog.value = false
  markRejectEvidenceId.value = null
  markRejectCommentText.value = ''
  showSuccessToast('已标记')
  return true
}

/** 取消某条证据的「已标记」 */
function clearMarkReject(ev: EvidenceListItem) {
  const next = { ...localRejectMap.value }
  delete next[String(ev.evidenceId)]
  localRejectMap.value = next
  showSuccessToast('已取消标记')
}

/** 不符合项条目（用于顶部「查看不符合项」列表与锚点定位） */
interface RejectedEvidenceEntry {
  evidenceId: number
  stageCode: string
  evidenceTypeCode: string
  displayName: string
  rejectComment: string
}

/** 当前项目下所有被标记为「不符合」且未作废的证据项（仅 returned 时有值；作废项不参与门禁与红条计数） */
const rejectedEvidencesList = computed(() => {
  if (project.value?.status !== 'returned' || !stageProgress.value?.stages?.length) return []
  const list: RejectedEvidenceEntry[] = []
  for (const stage of stageProgress.value.stages) {
    const stageCode = stage.stageCode
    for (const item of stage.items || []) {
      const evidenceTypeCode = item.evidenceTypeCode
      const evidences = evidenceByItemMap.value[`${stageCode}:${evidenceTypeCode}`] || []
      const displayName = item.groupDisplayName || item.displayName || evidenceTypeCode || ''
      for (const ev of evidences) {
        const status = getEffectiveEvidenceStatus(ev)
        if (status === 'INVALID') continue
        const comment = getDisplayRejectComment(ev)
        if (comment) {
          list.push({
            evidenceId: ev.evidenceId,
            stageCode,
            evidenceTypeCode,
            displayName,
            rejectComment: comment
          })
        }
      }
    }
  }
  return list
})

/** 是否所有不符合项已处理（已删除或已重新上传等，当前列表无红标即视为已处理） */
const isAllResolved = computed(() => {
  if (project.value?.status !== 'returned') return true
  return rejectedEvidencesList.value.length === 0
})

/** 某阶段是否包含未作废的不符合项（用于折叠标题右侧红标；已作废项不计入） */
function stageHasRejected(stage: { stageCode: string; items?: Array<{ evidenceTypeCode: string }> }): boolean {
  if (!stage?.items?.length) return false
  for (const item of stage.items) {
    const evs = evidenceByItemMap.value[`${stage.stageCode}:${item.evidenceTypeCode}`] || []
    if (evs.some(ev => getEffectiveEvidenceStatus(ev) !== 'INVALID' && getDisplayRejectComment(ev))) return true
  }
  return false
}

/**
 * 滚动定位到某条不符合项：切到证据 Tab → 展开所在阶段 → 平滑滚动到该证据 DOM
 */
async function scrollToRejectedEvidence(entry: RejectedEvidenceEntry) {
  showRejectedPopup.value = false
  if (activeTab.value !== EVIDENCE_TAB_INDEX) {
    activeTab.value = EVIDENCE_TAB_INDEX
    await nextTick()
  }
  await nextTick()
  if (!expandedStages.value.includes(entry.stageCode)) {
    expandedStages.value = [...expandedStages.value, entry.stageCode]
  }
  await nextTick()
  const el = document.getElementById(`evidence-item-${entry.evidenceId}`)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    el.classList.add('highlight-flash')
    setTimeout(() => el.classList.remove('highlight-flash'), HIGHLIGHT_FLASH_DURATION)
  }
}

/** 打开审批历史弹窗并拉取数据 */
function openArchiveHistoryPopup() {
  showHistoryPopup.value = true
  fetchArchiveHistory()
}

/** 获取项目归档审批历史 */
async function fetchArchiveHistory() {
  if (!projectId.value) return
  historyLoading.value = true
  try {
    const res = await getArchiveHistory(projectId.value)
    if (res?.code === 0 && Array.isArray(res.data)) {
      historyList.value = res.data
    } else {
      historyList.value = []
    }
  } catch {
    historyList.value = []
    showToast('加载审批历史失败')
  } finally {
    historyLoading.value = false
  }
}

/** 审批历史时间轴：状态对应图标 */
function historyStepIcon(status: string): string {
  if (status === 'REJECTED') return 'cross'
  if (status === 'APPROVED') return 'passed'
  return 'clock-o'
}

/** 审批历史时间轴：节点标题 */
function historyStepTitle(status: string): string {
  if (status === 'REJECTED') return '被退回'
  if (status === 'APPROVED') return '审批通过'
  return '提交归档'
}

/** 审批历史时间轴：展示时间（提交或操作时间），格式 YYYY-MM-DD HH:mm:ss */
function historyStepTime(item: ProjectArchiveHistoryVO): string {
  if (item.status === 'PENDING_APPROVAL') return formatDateTimeFull(item.submitTime)
  return formatDateTimeFull(item.operationTime)
}

/** 审批历史时间轴：操作人（申请人或审批人） */
function historyStepOperator(item: ProjectArchiveHistoryVO): string {
  if (item.status === 'PENDING_APPROVAL') return item.applicantDisplayName ? `申请人：${item.applicantDisplayName}` : ''
  return item.approverDisplayName ? `操作人：${item.approverDisplayName}` : ''
}

/** 申请归档：若有草稿则先弹窗列出草稿并确认，确认后再归档；无草稿则直接归档 */
async function handleArchive() {
  if (!projectId.value || !stageProgress.value?.canArchive || project.value?.status === 'archived') return
  try {
    const draftRes = await getEvidenceList(projectId.value, { evidenceStatus: 'DRAFT' })
    const drafts = (draftRes?.data ?? []) as EvidenceListItem[]
    if (drafts.length > 0) {
      draftListForArchive.value = drafts
      showDraftConfirmDialog.value = true
      return
    }
    await doArchive()
  } catch {
    showToast('获取证据列表失败')
  }
}

/** 草稿项展示路径：大阶段 > 子阶段（便于定位） */
function draftItemPathLabel(d: EvidenceListItem): string {
  const stage = d.stageName || d.stageCode || (d.stageId != null ? `阶段${d.stageId}` : '')
  const typeName = d.evidenceTypeDisplayName || d.evidenceTypeCode || ''
  if (stage && typeName) return `${stage} > ${typeName}`
  if (stage) return stage
  if (typeName) return typeName
  return '未分类'
}

/** 草稿确认弹窗：用户点击「确认归档」时执行归档并关闭弹窗 */
async function onDraftArchiveConfirm(action: string): Promise<boolean> {
  if (action !== 'confirm') return true
  const ok = await doArchive()
  if (ok) {
    draftListForArchive.value = []
    return true
  }
  return false
}

// 监听Tab切换
watch(activeTab, (newVal) => {
  if (newVal === 1) {
    loadStageProgress()
    if (evidenceList.value.length === 0) loadEvidenceList()
  }
})

// 路由 projectId 变化时重新加载详情
watch(() => route.params.id, () => {
  if (projectId.value) loadProject()
}, { immediate: false })

// 从证据详情返回时带 ?tab=evidence，需激活证据 Tab
watch(() => route.query.tab, (tab) => {
  if (tab === 'evidence') {
    activeTab.value = 1
    if (evidenceList.value.length === 0) loadEvidenceList()
  }
}, { immediate: true })

// 从证据详情返回时带 ?expandedStage=xxx，恢复展开对应阶段（含已加载 stageProgress 的情况）
watch(
  () => [route.query.expandedStage, route.query.scrollStage, stageProgress.value] as const,
  ([expanded, scrollStage, progress]) => {
    const codes: string[] = []
    if (expanded && progress?.stages?.some((s: { stageCode: string }) => s.stageCode === expanded)) {
      codes.push(expanded)
    }
    if (scrollStage && progress?.stages?.some((s: { stageCode: string }) => s.stageCode === scrollStage)) {
      codes.push(scrollStage)
    }
    if (codes.length) {
      const next = [...new Set([...expandedStages.value, ...codes])]
      if (next.length !== expandedStages.value.length || next.some((c, i) => c !== expandedStages.value[i])) {
        expandedStages.value = next
      }
    }
  },
  { immediate: true }
)

// 从全局搜索跳转：带 scrollStage + scrollType 时，在证据 Tab 展开后延时定位到具体证据项并清除 query
const scrollFromQueryDone = ref(false)
watch(
  () => [
    route.query.scrollStage,
    route.query.scrollType,
    route.params.id,
    activeTab.value,
    expandedStages.value,
    stageProgress.value
  ] as const,
  async ([scrollStage, scrollType, projectId, tab, expanded, progress]) => {
    if (!scrollStage || !scrollType || !projectId || scrollFromQueryDone.value) return
    if (tab !== EVIDENCE_TAB_INDEX) return
    if (!progress?.stages?.some((s: { stageCode: string }) => s.stageCode === scrollStage)) return
    if (!expanded.includes(scrollStage)) return

    scrollFromQueryDone.value = true
    try {
      await nextTick()
      // 等待 Tab 与折叠面板展开、证据卡片挂载（折叠动画约 300ms，再留余量）
      await new Promise((r) => setTimeout(r, 500))

      await scrollToEvidence({
        displayName: '',
        stageCode: scrollStage,
        evidenceTypeCode: scrollType
      })

      // 若首次未找到元素，折叠或列表可能仍在渲染，再试一次
      const el = document.getElementById(`evidence-card-${scrollStage}-${scrollType}`)
      if (!el) {
        await new Promise((r) => setTimeout(r, 300))
        await scrollToEvidence({
          displayName: '',
          stageCode: scrollStage,
          evidenceTypeCode: scrollType
        })
      }
    } finally {
      scrollFromQueryDone.value = false
    }
  },
  { immediate: true }
)

onMounted(() => {
  loadProject()
  loadStageProgress()
  if (route.query.tab === 'evidence') {
    activeTab.value = 1
  }
  if (activeTab.value === 1) {
    loadEvidenceList()
  }
})
</script>

<style scoped>
.project-detail {
  min-height: 100vh;
  background: var(--bg-body);
}

.content {
  padding: 0 16px 24px;
}

/* ---------- 顶部摘要卡片 ---------- */
.detail-header-card {
  background: var(--bg-card);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-card-shadow);
  padding: 20px 16px;
  margin-bottom: 12px;
}
.detail-header-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-main);
  margin: 0 0 12px 0;
  line-height: 1.3;
}
.detail-header-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.detail-header-badge {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
}
.detail-header-badge.badge--active {
  background: rgba(0, 122, 255, 0.12);
  color: var(--primary-color);
}
.detail-header-badge.badge--archived {
  background: #ebedf0;
  color: var(--app-text-secondary);
}
.detail-header-badge.badge--pending {
  background: #fff7e8;
  color: #ed6a0c;
}
.detail-header-badge.badge--returned {
  background: #fff1f0;
  color: #ee0a24;
}
.detail-notice-bar {
  margin-bottom: 12px;
}
.detail-header-responsible {
  font-size: 13px;
  color: var(--app-text-secondary);
}

/* ---------- 分段控制器 ---------- */
.segmented-control {
  display: flex;
  background: var(--bg-card);
  border-radius: var(--app-card-radius);
  padding: 4px;
  margin-bottom: 12px;
  box-shadow: var(--app-card-shadow);
}
.segmented-item {
  flex: 1;
  min-height: 44px;
  border: none;
  background: transparent;
  font-size: 15px;
  color: var(--app-text-secondary);
  position: relative;
  border-radius: 10px;
  transition: color 0.2s, font-weight 0.2s;
}
.segmented-item.active {
  color: var(--primary-color);
  font-weight: 600;
  background: rgba(0, 122, 255, 0.08);
}
.segmented-item.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 24px;
  height: 3px;
  border-radius: 2px;
  background: var(--primary-color);
}

/* 隐藏默认 Tab 栏，仅保留内容 */
.tabs-custom-nav :deep(.van-tabs__nav) {
  display: none;
}
.tabs-custom-nav :deep(.van-tabs__content) {
  margin-top: 0;
}

/* ---------- 信息列表区（左右布局、分割线） ---------- */
.info-card {
  background: var(--bg-card);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-card-shadow);
  overflow: hidden;
  margin-bottom: 16px;
}
.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  padding: 0 16px;
  border-bottom: 1px solid #ebedf0;
}
.info-row:last-child {
  border-bottom: none;
}
.info-label {
  font-size: 14px;
  color: var(--app-text-secondary);
  flex-shrink: 0;
  margin-right: 12px;
}
.info-value {
  font-size: 14px;
  color: var(--text-main);
  text-align: right;
  word-break: break-all;
}
.info-badge {
  padding: 2px 10px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
}
.info-badge.badge--active {
  background: rgba(0, 122, 255, 0.12);
  color: var(--primary-color);
}
.info-badge.badge--archived {
  background: #ebedf0;
  color: var(--app-text-secondary);
}
.info-badge.badge--pending {
  background: #fff7e8;
  color: #ed6a0c;
}
.info-badge.badge--returned {
  background: #fff1f0;
  color: #ee0a24;
}

.members-section {
  margin-top: 16px;
  padding: 0 16px 16px;
  text-align: center;
}
.members-section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--van-text-color);
  margin-bottom: 8px;
  text-align: center;
}
.members-loading {
  padding: 16px 0;
}

/* 成员角色以描述文字展示，避免像按钮 */
.member-role-desc {
  font-size: 14px;
  color: var(--van-text-color);
}

.evidence-section {
  padding: 0;
}

.upload-file-section {
  padding: 16px;
  margin: 16px 0;
  background: #f7f8fa;
  border-radius: 8px;
}

/* 证据列表 cell：自定义整行布局，右侧操作区紧贴右边缘无留白 */
.evidence-cell-content {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  gap: 8px;
  min-height: 0;
}

.evidence-cell-main {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.evidence-cell-title {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.evidence-cell-label {
  margin-top: 2px;
  font-size: 12px;
  color: var(--van-gray-6);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* value 区域占满剩余宽度，内容区左撑满，避免整块被挤到右侧 */
.evidence-list-cell :deep(.van-cell__value) {
  flex: 1;
  min-width: 0;
  display: flex;
  justify-content: stretch;
  text-align: left;
}

.cell-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.cell-actions .preview-btn {
  min-width: 36px;
  height: 28px;
  padding: 0 10px;
  border: none;
  border-radius: 4px;
  background: var(--van-primary-color);
}

.cell-actions .preview-btn :deep(.van-icon) {
  font-size: 16px;
  color: #fff;
}

.cell-arrow {
  color: var(--van-gray-5);
  font-size: 16px;
}

/* 窄屏：下载按钮仅显示图标，避免挤断行 */
@media (max-width: 360px) {
  .evidence-list-cell .cell-actions .download-btn :deep(.van-button__text) {
    display: none;
  }
}

.upload-dialog-footer-inner {
  padding: 12px 16px 16px;
}

.upload-result-block {
  padding-bottom: 8px;
}

.upload-result-actions {
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-loading {
  padding: 24px;
  display: flex;
  justify-content: center;
}

/* 成员管理入口：底部居中按钮 */
.member-entry-wrap {
  padding: 24px 16px 32px;
  display: flex;
  justify-content: center;
}

.member-entry-btn {
  min-width: 160px;
}

.picker-wheel-wrap {
  touch-action: pan-y;
}

/* 阶段进度顶部（证据 Tab + 详情 Tab 共用） */
.stage-progress-header {
  padding: 12px 16px;
  background: var(--bg-card);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-card-shadow);
  margin: 0 0 12px 0;
}
.detail-tab-progress {
  margin-top: 16px;
}
.completion-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.completion-label {
  font-size: 14px;
  color: var(--van-gray-7);
  min-width: 72px;
}
.completion-row .van-progress {
  flex: 1;
}
.completion-value {
  font-size: 14px;
  font-weight: 500;
  min-width: 36px;
}
/* 关键缺失：浅色警示卡片 + 分类标签 */
.key-missing-card {
  margin-top: 10px;
}
.key-missing-card-inner {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  background: linear-gradient(135deg, #fff5f5 0%, #fff8e6 100%);
  border-radius: 8px;
  border: 1px solid rgba(255, 77, 79, 0.15);
}
.key-missing-icon {
  flex-shrink: 0;
  margin-top: 2px;
  font-size: 18px;
  color: var(--van-orange);
}
.key-missing-body {
  flex: 1;
  min-width: 0;
}
.key-missing-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--van-gray-8);
  margin-bottom: 10px;
}
.key-missing-category {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 14px;
}
.key-missing-category:last-of-type {
  margin-bottom: 0;
}
.key-missing-category-label {
  flex-shrink: 0;
  width: 70px;
  font-size: 12px;
  color: var(--van-gray-6);
  text-align: right;
  line-height: 24px;
  padding-top: 2px;
}
.key-missing-tags {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.key-missing-tag {
  margin: 0;
}
.key-missing-shortfall-wrap {
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}
.key-missing-shortfall-item {
  font-size: 12px;
  color: var(--van-gray-8);
  line-height: 24px;
}
.key-missing-shortfall-num {
  color: var(--van-red);
  font-weight: 700;
}
.key-missing-toggle {
  margin-top: 8px;
  font-size: 12px;
  color: var(--van-blue);
  cursor: pointer;
  user-select: none;
}
.key-missing-toggle:active {
  opacity: 0.8;
}
.key-missing-tag--clickable,
.key-missing-shortfall-item--clickable {
  cursor: pointer;
}
.key-missing-shortfall-item--clickable {
  display: inline-block;
}
/* 点击关键缺失后滚动到的证据卡片高亮闪烁（约 1.5 秒） */
.evidence-card.highlight-flash {
  animation: highlight-flash 1.5s ease-out;
}
@keyframes highlight-flash {
  0% { background-color: rgba(255, 193, 7, 0.5); }
  30% { background-color: rgba(255, 193, 7, 0.35); }
  100% { background-color: transparent; }
}
/* 不符合项锚点定位时，网格项也使用同一高亮动画 */
.grid-item.highlight-flash {
  animation: highlight-flash 1.5s ease-out;
  border-radius: 8px;
}

/* 退回状态：红色横幅（文案 + 右侧「去处理(n)」文字按钮一体） */
.returned-banner {
  background: #fff1f0;
  color: #ee0a24;
  padding: 10px 16px;
  margin: 0;
}
.returned-banner__content {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 24px;
}
.returned-banner__icon {
  flex-shrink: 0;
  font-size: 18px;
}
.returned-banner__text {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  line-height: 1.4;
}
.returned-banner__action {
  flex-shrink: 0;
  padding: 0 4px;
  font-size: 14px;
  color: #1989fa;
  background: none;
  border: none;
  cursor: pointer;
  white-space: nowrap;
}
.returned-banner__action:active {
  opacity: 0.8;
}

/* 顶部入口：历次审批记录 */
.detail-header-history-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  padding: 4px 0;
  font-size: 13px;
  color: #1989fa;
  background: none;
  border: none;
  cursor: pointer;
}
.detail-header-history-btn .van-icon {
  font-size: 14px;
}
.detail-header-history-btn:active {
  opacity: 0.8;
}

/* 审批历史弹窗（右侧 85%） */
.archive-history-popup.van-popup {
  display: flex;
  flex-direction: column;
}
.archive-history-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--van-gray-2);
  background: var(--van-background);
}
.archive-history-title {
  font-size: 16px;
  font-weight: 600;
}
.archive-history-close {
  font-size: 20px;
  padding: 4px;
  cursor: pointer;
  color: var(--van-gray-7);
}
.archive-history-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  -webkit-overflow-scrolling: touch;
}
.archive-history-loading {
  padding: 40px 0;
}
.archive-history-empty {
  padding: 40px 0;
}

/* 时间轴 */
.archive-history-timeline {
  padding-bottom: 24px;
}
.timeline-item {
  position: relative;
}
.timeline-line {
  position: absolute;
  left: 11px;
  top: 36px;
  bottom: -12px;
  width: 2px;
  background: var(--van-gray-3);
}
.timeline-node {
  display: flex;
  gap: 12px;
  padding-bottom: 16px;
}
.timeline-node__icon {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
}
.timeline-node__icon--REJECTED {
  background: var(--van-danger-color);
}
.timeline-node__icon--APPROVED {
  background: var(--van-success-color);
}
.timeline-node__icon--PENDING_APPROVAL {
  background: var(--van-primary-color);
}
.timeline-node__content {
  flex: 1;
  min-width: 0;
}
.timeline-node__title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 4px;
}
.timeline-item--REJECTED .timeline-node__title { color: var(--van-danger-color); }
.timeline-item--APPROVED .timeline-node__title { color: var(--van-success-color); }
.timeline-item--PENDING_APPROVAL .timeline-node__title { color: var(--van-primary-color); }
.timeline-node__meta {
  font-size: 12px;
  color: var(--van-gray-6);
  margin-bottom: 6px;
}
.timeline-node__time {
  margin-right: 8px;
}
.timeline-reject-comment {
  margin-top: 8px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.5;
  background: var(--van-gray-1);
  border-radius: 8px;
  color: var(--van-gray-8);
}
.timeline-reject-evidences {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--van-gray-7);
  line-height: 1.6;
}
.timeline-reject-evidence-item {
  margin-bottom: 4px;
}
.timeline-reject-evidence-item:last-child {
  margin-bottom: 0;
}

.returned-banner__action:active {
  opacity: 0.8;
}

/* 不符合项列表弹窗 */
.rejected-list-popup-header {
  padding: 14px 16px;
  font-weight: 600;
  font-size: 15px;
  border-bottom: 1px solid #ebedf0;
}
.rejected-list-popup-body {
  padding: 8px 0;
  max-height: 50vh;
  overflow-y: auto;
}
.rejected-list-item {
  padding: 12px 16px;
  border-bottom: 1px solid #f7f8fa;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.rejected-list-item:active {
  background: #f7f8fa;
}
.rejected-list-item-name {
  font-weight: 500;
  color: #323233;
}
.rejected-list-item-reason {
  font-size: 12px;
  color: #ee0a24;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.stage-rejected-badge {
  flex-shrink: 0;
}

.archive-row {
  margin-top: 10px;
}
.archive-btn--disabled {
  opacity: 0.6;
  background: var(--van-gray-5) !important;
  border-color: var(--van-gray-5) !important;
  color: var(--van-gray-6) !important;
}
.archive-btn--disabled:active {
  opacity: 0.6;
}
.stage-progress-loading {
  padding: 24px;
}

/* 阶段折叠 */
.stage-collapse {
  margin: 0 0 16px 0;
}
.stage-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.stage-name {
  font-weight: 500;
}
.stage-count {
  font-size: 13px;
  color: var(--van-gray-6);
}
/* 平铺卡片模式 */
.stage-items-flat {
  padding: 4px 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.evidence-card {
  background: var(--bg-card);
  border-radius: var(--app-card-radius);
  box-shadow: var(--app-card-shadow);
  padding: 14px 16px;
  border: none;
}
.evidence-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  min-height: 44px;
}
.evidence-card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  flex: 1;
  min-width: 0;
}
.card-name {
  color: var(--text-main);
}
.card-required {
  flex-shrink: 0;
}
.evidence-card-status {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.card-status-text {
  font-size: 13px;
  color: var(--app-text-secondary);
}
.card-status-text.card-status-text--insufficient {
  color: var(--van-red);
  font-weight: 500;
}
.card-arrow {
  color: var(--app-text-secondary);
  font-size: 14px;
}
.card-count {
  font-size: 13px;
  color: var(--app-text-secondary);
}
/* 固定 112px 方块 + flex-wrap 自动排列 */
.evidence-card-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.grid-item {
  position: relative;
  width: 112px;
  height: 112px;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--van-gray-1);
  border: 1px solid var(--van-gray-2);
  flex-shrink: 0;
}
/* 已作废证据卡片整体弱化，突出旁边有效证据 */
.grid-item--voided {
  opacity: 0.6;
}
.voided-tag {
  color: var(--van-gray-6);
}
.grid-thumb {
  width: 100%;
  height: 100%;
  position: relative;
}
.grid-thumb {
  border-radius: 10px;
  overflow: hidden;
}
.grid-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.grid-thumb-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 4px 6px;
  background: linear-gradient(transparent, rgba(0,0,0,0.5));
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 8px;
}
.reject-tag-inline {
  margin-left: 4px;
}
.reject-reason-inline {
  font-size: 10px;
  color: #fff;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.grid-reject-btn {
  font-size: 10px;
  padding: 2px 6px;
  border: none;
  border-radius: 4px;
  background: rgba(255,255,255,0.9);
  color: var(--van-red);
  cursor: pointer;
  flex-shrink: 0;
}
.grid-reject-btn--mark {
  margin-left: auto;
  background: var(--van-red);
  color: #fff;
}
.grid-reject-btn--replace {
  margin-left: auto;
  background: #1989fa;
  color: #fff;
}
.grid-reject-btn--replace:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.replace-file-input-hidden {
  position: absolute;
  width: 0;
  height: 0;
  opacity: 0;
  pointer-events: none;
}
.grid-thumb--has-reject-comment,
.grid-file--has-reject-comment {
  box-shadow: 0 0 0 2px var(--van-red);
}
.grid-file-reject-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 4px 6px;
  background: rgba(0,0,0,0.6);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 6px;
  font-size: 10px;
  color: #fff;
}
.grid-file-reject-bar .reject-reason-inline {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.grid-file-mark-btn {
  position: absolute;
  bottom: 4px;
  right: 4px;
  font-size: 10px;
  padding: 2px 6px;
  border: none;
  border-radius: 4px;
  background: var(--van-red);
  color: #fff;
  cursor: pointer;
  z-index: 1;
}
.grid-thumb--rejected::after {
  content: '';
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  pointer-events: none;
}
.evidence-badge {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
  color: #fff;
  line-height: 1.2;
}
.evidence-badge--pending {
  background: var(--van-orange);
}
.evidence-badge--submitted {
  background: var(--van-primary-color);
}
.evidence-badge--rejected {
  background: var(--van-red);
}
.grid-file--rejected::before {
  content: '';
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  border-radius: 6px;
  pointer-events: none;
  z-index: 0;
}
.grid-file .evidence-badge--file {
  position: absolute;
  bottom: 4px;
  left: 4px;
  z-index: 1;
}
.grid-thumb-delete {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
  cursor: pointer;
}
.grid-thumb-delete:active {
  background: rgba(0, 0, 0, 0.7);
}
.grid-file {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 6px;
  width: 100%;
  height: 100%;
  border-radius: 6px;
  transition: background 0.2s;
}
.grid-file-type-badge {
  position: absolute;
  top: 4px;
  right: 4px;
  color: #fff;
  font-size: 9px;
  font-weight: 600;
  line-height: 1;
  padding: 2px 4px;
  border-radius: 3px;
  letter-spacing: 0.5px;
}
.grid-file-name {
  font-size: 10px;
  color: var(--text-main);
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
  display: block;
  margin-top: 2px;
  font-weight: 500;
}
.grid-file-delete {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.4);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
  cursor: pointer;
}
.grid-file-delete:active {
  background: rgba(0, 0, 0, 0.6);
}
/* 上传入口：自定义大加号 + 动态文案 */
.grid-upload-btn {
  border: 2px dashed var(--van-gray-4);
  background: transparent;
  flex-direction: column;
  gap: 0;
  transition: border-color 0.2s, background 0.2s;
}
.grid-upload-btn:active {
  border-color: var(--van-primary-color);
  background: rgba(25, 137, 250, 0.06);
}
.grid-upload-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
}
.grid-upload-plus {
  font-size: 28px;
  font-weight: 300;
  color: var(--van-gray-5);
  line-height: 1;
}
.grid-upload-label {
  font-size: 12px;
  color: var(--van-gray-7);
}
.grid-upload-hint {
  font-size: 11px;
  color: var(--van-red);
  font-weight: 500;
}

.upload-context-cell {
  background: var(--van-gray-1);
}
.upload-hint-cell .upload-hint-text {
  font-size: 12px;
  color: var(--van-gray-6);
}
.archive-block-content {
  padding: 16px;
  font-size: 14px;
}
.archive-block-reason {
  margin-bottom: 8px;
  color: var(--van-gray-8);
}
.archive-block-list {
  margin: 6px 0;
  font-size: 13px;
  color: var(--van-gray-7);
}
.draft-confirm-content {
  padding: 16px;
  font-size: 14px;
}
.draft-confirm-tip {
  margin-bottom: 12px;
  color: var(--van-gray-8);
  line-height: 1.5;
}
.draft-confirm-list {
  margin: 8px 0;
  padding-left: 20px;
  color: var(--van-gray-7);
  font-size: 13px;
  max-height: 160px;
  overflow-y: auto;
}
.draft-confirm-list li {
  margin: 4px 0;
}
.draft-confirm-ask {
  margin-top: 12px;
  color: var(--van-gray-8);
}

/* 审批操作区：固定底部 */
.approval-bar-wrap {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 12px 16px;
  padding-bottom: calc(12px + env(safe-area-inset-bottom));
  background: var(--bg-card);
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.08);
  z-index: 100;
}
.approval-bar {
  display: flex;
  gap: 12px;
  justify-content: center;
}
.approval-bar .approval-btn {
  flex: 1;
  max-width: 160px;
}
/* 底部有审批栏时给内容区留白，避免被遮挡 */
.project-detail.approval-bar-visible .content {
  padding-bottom: 80px;
}
.reject-reason-field {
  padding: 8px 0;
}
</style>
