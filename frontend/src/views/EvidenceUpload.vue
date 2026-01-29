<template>
  <div class="evidence-upload">
    <div class="content">
      <van-form @submit="onSubmit">
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
          >
            <van-button icon="plus" type="primary">选择文件</van-button>
          </van-uploader>
        </div>

        <div class="actions">
          <van-button
            round
            block
            type="primary"
            native-type="submit"
            :loading="submitting"
          >
            提交
          </van-button>
        </div>
      </van-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NavBar, Form, Field, CellGroup, Uploader, Button, showToast } from 'vant'
import type { UploaderFileListItem } from 'vant'

const route = useRoute()
const router = useRouter()
const projectId = route.params.id as string

const form = ref({
  title: '',
  note: ''
})

const fileList = ref<UploaderFileListItem[]>([])
const submitting = ref(false)

const afterRead = (file: UploaderFileListItem) => {
  console.log('File selected:', file)
}

const onSubmit = async () => {
  if (!form.value.title) {
    showToast('请输入证据标题')
    return
  }

  if (fileList.value.length === 0) {
    showToast('请选择要上传的文件')
    return
  }

  submitting.value = true

  // 模拟 API 调用
  setTimeout(() => {
    submitting.value = false
    showToast.success('上传成功')
    setTimeout(() => {
      router.back()
    }, 1500)
  }, 1500)
}
</script>

<style scoped>
.evidence-upload {
  min-height: 100vh;
}

.content {
  padding: 16px;
}

.upload-section {
  margin: 16px 0;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
}

.actions {
  margin-top: 24px;
}
</style>
