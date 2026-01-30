<template>
  <div class="evidence-upload">
    <div class="content">
      <!-- 状态区块 -->
      <van-cell-group inset class="status-block">
        <van-cell title="当前状态">
          <template #value>
            <van-tag v-if="displayStatus" :type="statusTagType(displayStatus)">
              {{ mapStatusToText(displayStatus) }}
            </van-tag>
            <span v-else class="status-placeholder">—</span>
          </template>
        </van-cell>
      </van-cell-group>

      <van-form @submit="onSaveDraft">
        <van-cell-group inset>
          <van-field
            v-model="form.title"
            name="title"
            label="证据标题"
            placeholder="请输入证据标题"
            :rules="[{ required: true, message: '请输入证据标题' }]"
          />
          <van-field
            v-model="form.note"
            name="note"
            label="证据说明"
            type="textarea"
            placeholder="请输入证据说明（可选）"
            rows="4"
          />
        </van-cell-group>

        <div class="upload-section">
          <van-uploader
            v-model="fileList"
            :after-read="afterRead"
            :max-count="1"
            accept="*/*"
            :disabled="!!evidenceId"
          >
            <van-button icon="plus" type="primary" plain :disabled="!!evidenceId">
              {{ evidenceId ? '已上传' : '选择文件' }}
            </van-button>
          </van-uploader>
          <p v-if="evidenceId" class="upload-hint">已创建证据后不可更换文件，可到详情页查看</p>
        </div>

        <div class="actions">
          <van-button
            round
            block
            :type="evidenceId && displayStatus === 'DRAFT' ? 'default' : 'primary'"
            :disabled="!canSaveDraft"
            :loading="saving"
            @click="onSaveDraft"
          >
            保存草稿
          </van-button>
          <van-button
            v-if="canSubmit"
            round
            block
            type="primary"
            :loading="submitting"
            @click="onSubmit"
          >
            提交
          </van-button>
          <van-button
            v-if="showViewDetail"
            round
            block
            type="primary"
            plain
            @click="goToDetail"
          >
            查看详情
          </van-button>
        </div>
      </van-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Form,
  Field,
  CellGroup,
  Cell,
  Tag,
  Uploader,
  Button,
  showToast,
  showLoadingToast,
  showSuccessToast,
  closeToast,
  showConfirmDialog
} from 'vant'
import type { UploaderFileListItem } from 'vant'
import {
  uploadEvidence,
  getEvidenceById,
  submitEvidence,
  type EvidenceListItem
} from '@/api/evidence'
import { getEffectiveEvidenceStatus, mapStatusToText, statusTagType } from '@/utils/evidenceStatus'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.id || ''))

const form = ref({ title: '', note: '' })
const fileList = ref<UploaderFileListItem[]>([])
const evidenceId = ref<number | null>(null)
const evidenceStatus = ref<string | null>(null)
const saving = ref(false)
const submitting = ref(false)

const displayStatus = computed(() => evidenceStatus.value || null)

const canSaveDraft = computed(() => {
  if (evidenceId.value) return true
  return !!form.value.title.trim() && fileList.value.length > 0
})

const canSubmit = computed(
  () => !!evidenceId.value && getEffectiveEvidenceStatus({ evidenceStatus: evidenceStatus.value, status: null }) === 'DRAFT'
)

const showViewDetail = computed(() => {
  const s = evidenceStatus.value
  return evidenceId.value && s && ['SUBMITTED', 'ARCHIVED', 'INVALID'].includes(s)
})

function afterRead(_file: UploaderFileListItem) {
  // 仅用于触发 van-uploader 的 v-model
}

async function loadByEvidenceId(id: number) {
  try {
    const res = (await getEvidenceById(id)) as { code: number; data?: EvidenceListItem }
    if (res?.code === 0 && res.data) {
      evidenceId.value = res.data.evidenceId
      evidenceStatus.value = res.data.evidenceStatus ?? res.data.status ?? null
      form.value.title = res.data.title || ''
    }
  } catch {
    showToast('加载证据失败')
  }
}

onMounted(() => {
  const id = route.query.evidenceId
  if (id) {
    const num = Number(id)
    if (!Number.isNaN(num)) loadByEvidenceId(num)
  }
})

async function onSaveDraft() {
  if (!canSaveDraft.value) {
    if (!evidenceId.value) {
      showToast('请填写证据标题并选择文件')
    }
    return
  }

  if (evidenceId.value) {
    if (evidenceStatus.value === 'DRAFT') {
      showToast('当前已是草稿状态')
    }
    return
  }

  const file = fileList.value[0]?.file as File | undefined
  if (!file) {
    showToast('请选择要上传的文件')
    return
  }

  saving.value = true
  try {
    const formData = new FormData()
    formData.append('name', form.value.title.trim())
    formData.append('type', 'OTHER')
    if (form.value.note.trim()) formData.append('remark', form.value.note.trim())
    formData.append('file', file)

    const res = (await uploadEvidence(Number(projectId.value), formData)) as {
      code: number
      message?: string
      data?: { id: number; evidenceStatus?: string }
    }
    if (res?.code === 0 && res.data) {
      evidenceId.value = res.data.id
      evidenceStatus.value = res.data.evidenceStatus ?? 'DRAFT'
      showSuccessToast('已保存为草稿')
    } else {
      showToast(res?.message || '保存失败')
    }
  } catch (e: any) {
    showToast(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onSubmit() {
  if (!canSubmit.value) {
    showToast('请先保存草稿')
    return
  }

  try {
    await showConfirmDialog({
      title: '确认提交',
      message: '提交后将进入管理流程，是否继续？'
    })
  } catch {
    return
  }

  submitting.value = true
  try {
    const res = (await submitEvidence(evidenceId.value!)) as { code: number; message?: string }
    if (res?.code === 0) {
      evidenceStatus.value = 'SUBMITTED'
      showSuccessToast('已提交')
    } else {
      showToast(res?.message || '提交失败')
    }
  } catch (e: any) {
    showToast(e?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

function goToDetail() {
  if (evidenceId.value) {
    router.push({ path: `/evidence/detail/${evidenceId.value}` })
  }
}
</script>

<style scoped>
.evidence-upload {
  min-height: 100vh;
}

.content {
  padding: 16px;
}

.status-block {
  margin-bottom: 12px;
}

.status-placeholder {
  color: var(--van-gray-6);
}

.upload-section {
  margin: 16px 0;
  padding: 16px;
  background: var(--van-background-2);
  border-radius: 8px;
}

.upload-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--van-gray-6);
}

.actions {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
