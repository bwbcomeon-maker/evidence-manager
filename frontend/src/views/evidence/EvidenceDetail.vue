<template>
  <div class="evidence-detail">
    <template v-if="evidence">
      <van-cell-group inset>
        <van-cell title="证据标题" :value="evidence.title" />
        <van-cell title="项目ID" :value="String(evidence.projectId)" />
        <van-cell title="业务类型" :value="evidence.bizType" />
        <van-cell title="文件类型" :value="evidence.contentType || '—'" />
        <van-cell title="状态">
          <template #value>
            <van-tag :type="evidence.status === 'invalid' ? 'danger' : 'success'">
              {{ evidence.status === 'invalid' ? '作废' : evidence.status === 'archived' ? '归档' : '有效' }}
            </van-tag>
          </template>
        </van-cell>
        <van-cell title="上传人" :value="evidence.createdBy || '—'" />
        <van-cell title="上传时间" :value="formatDateTime(evidence.createdAt)" />
        <van-cell v-if="evidence.latestVersion" title="文件名" :value="evidence.latestVersion.originalFilename" />
      </van-cell-group>

      <div class="actions">
        <van-button type="primary" block icon="eye-o" @click="handlePreview">预览</van-button>
        <van-button type="primary" block plain icon="down" @click="handleDownload">下载</van-button>
        <van-button
          v-if="canVoid"
          type="danger"
          block
          plain
          icon="warning-o"
          @click="handleVoid"
        >
          作废
        </van-button>
      </div>
    </template>
    <van-empty v-else description="无法加载详情，请从列表进入" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Cell, CellGroup, Button, Tag, Empty, showToast, showLoadingToast, showSuccessToast, closeToast, showConfirmDialog } from 'vant'
import { downloadVersionFile, type EvidenceListItem } from '@/api/evidence'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const evidence = ref<EvidenceListItem | null>(null)
const evidenceId = computed(() => Number(route.params.id))

const canVoid = computed(() => {
  return auth.isAdmin
})


function initFromState() {
  const state = history.state as { evidence?: EvidenceListItem } | undefined
  if (state?.evidence && state.evidence.evidenceId === evidenceId.value) {
    evidence.value = state.evidence
  }
}

async function handlePreview() {
  if (!evidence.value?.latestVersion) {
    showToast('暂无版本文件')
    return
  }
  const ct = (evidence.value.contentType || '').toLowerCase()
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
    const blob = await downloadVersionFile(evidence.value.latestVersion.versionId)
    const url = window.URL.createObjectURL(blob)
    window.open(url, '_blank', 'noopener,noreferrer')
    setTimeout(() => window.URL.revokeObjectURL(url), 60_000)
  } catch (e: any) {
    showToast(e.message || '预览失败')
  } finally {
    closeToast()
  }
}

async function handleDownload() {
  if (!evidence.value?.latestVersion) {
    showToast('暂无版本文件')
    return
  }
  try {
    showLoadingToast({ message: '下载中...', forbidClick: true, duration: 0 })
    const blob = await downloadVersionFile(evidence.value.latestVersion.versionId)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = evidence.value.latestVersion.originalFilename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    showSuccessToast('下载成功')
  } catch (e: any) {
    showToast(e.message || '下载失败')
  } finally {
    closeToast()
  }
}

function handleVoid() {
  showConfirmDialog({
    title: '确认作废',
    message: '确定要将该证据标记为作废吗？作废后仍可查看与下载，但会列入作废证据。'
  }).then(() => {
    showToast('作废功能需后端接口支持，敬请期待')
  }).catch(() => {})
}

onMounted(() => {
  initFromState()
})
</script>

<style scoped>
.evidence-detail {
  padding: 16px 0;
  min-height: 100%;
}
.actions {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
