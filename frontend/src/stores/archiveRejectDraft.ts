import { defineStore } from 'pinia'

/**
 * 归档退回阶段的「不符合项」草稿仓库：
 * 在 pending_approval 阶段，PMO 在项目详情页内标记的附件级不符合原因，
 * 在整单退回前仅保存在前端，用于支持在详情页之间往返时状态不丢失。
 */
export const useArchiveRejectDraftStore = defineStore('archiveRejectDraft', {
  state: () => ({
    /**
     * drafts[projectId] = { [evidenceId]: comment }
     */
    drafts: {} as Record<number, Record<string, string>>
  }),
  actions: {
    /** 获取某项目下的草稿映射（不会返回原始引用，避免外部直接修改 state 对象） */
    getProjectDraft(projectId: number): Record<string, string> {
      const map = this.drafts[projectId]
      return map ? { ...map } : {}
    },

    /** 覆盖写入某项目下的草稿映射 */
    setProjectDraft(projectId: number, draft: Record<string, string>) {
      // 创建一个新的对象，避免外部引用影响 store
      this.drafts[projectId] = { ...draft }
    },

    /** 清空某项目下的草稿 */
    clearProjectDraft(projectId: number) {
      if (this.drafts[projectId]) {
        delete this.drafts[projectId]
      }
    }
  }
})

