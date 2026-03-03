<template>
  <div class="todo-center">
    <van-tabs v-model:active="activeTab" shrink @change="onTabChange">
      <van-tab title="全部待办" name="all">
        <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
          <van-list
            v-model:loading="loading"
            :finished="finished"
            finished-text="没有更多了"
            @load="onLoad"
          >
            <van-cell-group v-if="list.length" inset>
              <van-cell
                v-for="item in list"
                :key="item.id"
                :title="item.title"
                :label="formatLabel(item)"
                is-link
                @click="onTodoClick(item)"
              >
                <template #title>
                  <span class="todo-title">
                    <van-badge v-if="!item.readAt" dot class="todo-unread-dot" />
                    {{ item.title || '待办' }}
                  </span>
                </template>
                <template #label>
                  <div class="todo-label">
                    <span v-if="item.body" class="todo-body">{{ truncateBody(item.body) }}</span>
                    <span class="todo-time">{{ formatTime(item.createdAt) }}</span>
                  </div>
                </template>
              </van-cell>
            </van-cell-group>
            <van-empty v-else-if="!loading && !refreshing" description="暂无待办" :image-size="80" />
          </van-list>
        </van-pull-refresh>
      </van-tab>
      <van-tab title="未读待办" name="unread">
        <van-pull-refresh v-model="refreshingUnread" @refresh="onRefreshUnread">
          <van-list
            v-model:loading="loadingUnread"
            :finished="finishedUnread"
            finished-text="没有更多了"
            @load="onLoadUnread"
          >
            <van-cell-group v-if="listUnread.length" inset>
              <van-cell
                v-for="item in listUnread"
                :key="item.id"
                :title="item.title"
                :label="formatLabel(item)"
                is-link
                @click="onTodoClick(item)"
              >
                <template #title>
                  <span class="todo-title">
                    <van-badge dot class="todo-unread-dot" />
                    {{ item.title || '待办' }}
                  </span>
                </template>
                <template #label>
                  <div class="todo-label">
                    <span v-if="item.body" class="todo-body">{{ truncateBody(item.body) }}</span>
                    <span class="todo-time">{{ formatTime(item.createdAt) }}</span>
                  </div>
                </template>
              </van-cell>
            </van-cell-group>
            <van-empty v-else-if="!loadingUnread && !refreshingUnread" description="暂无未读待办" :image-size="80" />
          </van-list>
        </van-pull-refresh>
      </van-tab>
    </van-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { getTodos, markTodoRead, type TodoItemVO } from '@/api/notifications'
import { showToast } from 'vant'
import { getFriendlyErrorMessage } from '@/utils/errorMessage'

const router = useRouter()
const activeTab = ref<'all' | 'unread'>('all')

const list = ref<TodoItemVO[]>([])
const loading = ref(false)
const refreshing = ref(false)
const finished = ref(false)
const listUnread = ref<TodoItemVO[]>([])
const loadingUnread = ref(false)
const refreshingUnread = ref(false)
const finishedUnread = ref(false)

const PAGE_SIZE = 50

/** 清空列表并重置状态（避免展示上一用户的待办） */
function clearLists() {
  list.value = []
  listUnread.value = []
  finished.value = false
  finishedUnread.value = false
}

/** 进入页面时强制按当前用户重新拉取，避免多标签/切账号后仍显示旧列表 */
function loadForCurrentUser() {
  clearLists()
  if (activeTab.value === 'all') {
    fetchAll(false)
  } else {
    fetchUnread(false)
  }
}

function formatLabel(item: TodoItemVO): string {
  const parts: string[] = []
  if (item.body) parts.push(truncateBody(item.body))
  parts.push(formatTime(item.createdAt))
  return parts.join(' · ')
}

function truncateBody(body: string, maxLen = 60): string {
  if (!body) return ''
  const s = body.trim()
  return s.length <= maxLen ? s : s.slice(0, maxLen) + '...'
}

function formatTime(createdAt: string): string {
  if (!createdAt) return ''
  const date = new Date(createdAt)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  if (diff < 60_000) return '刚刚'
  if (diff < 3600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86400_000) return `${Math.floor(diff / 3600_000)} 小时前`
  if (diff < 604800_000) return `${Math.floor(diff / 86400_000)} 天前`
  return `${date.getMonth() + 1}-${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

async function fetchAll(append: boolean) {
  if (loading.value && !append) return
  loading.value = true
  try {
    const limit = append ? list.value.length + PAGE_SIZE : PAGE_SIZE
    const res = await getTodos({ limit })
    if (res?.code === 0 && Array.isArray(res.data)) {
      list.value = res.data
      finished.value = res.data.length < limit
    } else {
      list.value = []
      finished.value = true
    }
  } catch (e) {
    showToast(getFriendlyErrorMessage(e, '加载失败'))
    finished.value = true
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

async function fetchUnread(append: boolean) {
  if (loadingUnread.value && !append) return
  loadingUnread.value = true
  try {
    const limit = append ? listUnread.value.length + PAGE_SIZE : PAGE_SIZE
    const res = await getTodos({ unreadOnly: true, limit })
    if (res?.code === 0 && Array.isArray(res.data)) {
      listUnread.value = res.data
      finishedUnread.value = res.data.length < limit
    } else {
      listUnread.value = []
      finishedUnread.value = true
    }
  } catch (e) {
    showToast(getFriendlyErrorMessage(e, '加载失败'))
    finishedUnread.value = true
  } finally {
    loadingUnread.value = false
    refreshingUnread.value = false
  }
}

function onLoad() {
  if (list.value.length === 0) fetchAll(false)
  else finished.value = true
}

function onLoadUnread() {
  if (listUnread.value.length === 0) fetchUnread(false)
  else finishedUnread.value = true
}

function onRefresh() {
  finished.value = false
  list.value = []
  fetchAll(false)
}

function onRefreshUnread() {
  finishedUnread.value = false
  listUnread.value = []
  fetchUnread(false)
}

function onTabChange() {
  if (activeTab.value === 'unread' && listUnread.value.length === 0 && !loadingUnread.value) {
    fetchUnread(false)
  }
}

/** 从其他标签切回本页时重新拉取，避免另一标签已切换账号导致列表与当前 Session 不一致 */
function onVisibilityChange() {
  if (document.visibilityState === 'visible') {
    loadForCurrentUser()
  }
}

onMounted(() => {
  loadForCurrentUser()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
})

async function onTodoClick(item: TodoItemVO) {
  const path = item.linkPath?.trim()
  const id = Number(item.id)
  if (Number.isNaN(id)) {
    showToast('无法标记已读')
    if (path) {
      const toPath = path.startsWith('/') ? path : '/' + path
      router.push(toPath)
    }
    return
  }
  let markReadOk = false
  try {
    await markTodoRead(id)
    markReadOk = true
  } catch (e) {
    if (path) {
      const toPath = path.startsWith('/') ? path : '/' + path
      router.push(toPath)
      showToast('已跳转')
    } else {
      showToast(getFriendlyErrorMessage(e, '操作失败'))
    }
    return
  }
  if (path) {
    const toPath = path.startsWith('/') ? path : '/' + path
    router.push(toPath)
  }
  if (!item.readAt && markReadOk) {
    list.value = list.value.map((t) => (t.id === item.id ? { ...t, readAt: new Date().toISOString() } : t))
    listUnread.value = listUnread.value.filter((t) => t.id !== item.id)
  }
}

watch(activeTab, (name) => {
  if (name === 'all' && list.value.length === 0 && !loading.value) {
    fetchAll(false)
  }
}, { immediate: false })
</script>

<style scoped>
.todo-center {
  min-height: 100vh;
  background: var(--app-bg, #f7f8fa);
}

.todo-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.todo-unread-dot {
  flex-shrink: 0;
}

.todo-label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 4px;
}

.todo-body {
  font-size: 13px;
  color: var(--van-gray-6);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-time {
  font-size: 12px;
  color: var(--van-gray-5);
}
</style>
