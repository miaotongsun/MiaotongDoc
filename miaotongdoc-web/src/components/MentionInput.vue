<template>
  <div class="mention-input" ref="inputRef">
    <el-input
      v-model="inputValue"
      type="textarea"
      :rows="rows"
      :placeholder="placeholder"
      @input="handleInput"
      @keydown="handleKeydown"
    />

    <!-- @提及下拉列表 -->
    <div v-if="showDropdown" class="mention-dropdown">
      <div
        v-for="user in filteredUsers"
        :key="user.id"
        class="mention-item"
        :class="{ active: user.id === activeUserId }"
        @click="selectUser(user)"
      >
        <el-avatar :size="28">{{ user.realName?.charAt(0) }}</el-avatar>
        <div class="user-info">
          <span class="name">{{ user.realName }}</span>
          <span class="emp-id">{{ user.employeeId }}</span>
        </div>
      </div>
      <div v-if="filteredUsers.length === 0" class="no-results">
        无匹配用户
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'

interface User {
  id: number
  realName: string
  employeeId: string
}

const props = withDefaults(defineProps<{
  modelValue: string
  rows?: number
  placeholder?: string
  users?: User[]
}>(), {
  rows: 2,
  placeholder: '输入评论，输入 @ 提及同事...',
  users: () => []
})

const emit = defineEmits(['update:modelValue', 'mention'])

const inputRef = ref<HTMLElement>()
const inputValue = ref(props.modelValue)
const showDropdown = ref(false)
const searchQuery = ref('')
const activeUserId = ref<number | null>(null)
const mentionStartIndex = ref(-1)

watch(() => props.modelValue, (val) => {
  inputValue.value = val
})

const filteredUsers = computed(() => {
  if (!searchQuery.value) return props.users
  const query = searchQuery.value.toLowerCase()
  return props.users.filter(u =>
    u.realName.toLowerCase().includes(query) ||
    u.employeeId.includes(query)
  )
})

function handleInput(value: string) {
  emit('update:modelValue', value)

  // 检测 @ 符号
  const cursorPosition = getCursorPosition()
  const textBeforeCursor = value.substring(0, cursorPosition)
  const lastAtIndex = textBeforeCursor.lastIndexOf('@')

  if (lastAtIndex >= 0) {
    const textAfterAt = textBeforeCursor.substring(lastAtIndex + 1)
    // 检查 @ 后面是否有空格（如果有则表示提及已结束）
    if (!textAfterAt.includes(' ') && !textAfterAt.includes('\n')) {
      mentionStartIndex.value = lastAtIndex
      searchQuery.value = textAfterAt
      showDropdown.value = true

      // 设置默认激活用户
      if (filteredUsers.value.length > 0 && !activeUserId.value) {
        activeUserId.value = filteredUsers.value[0].id
      }
      return
    }
  }

  closeDropdown()
}

function handleKeydown(e: KeyboardEvent) {
  if (!showDropdown.value) return

  if (e.key === 'ArrowDown') {
    e.preventDefault()
    navigateDropdown(1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    navigateDropdown(-1)
  } else if (e.key === 'Enter' && showDropdown.value) {
    e.preventDefault()
    const user = filteredUsers.value.find(u => u.id === activeUserId.value)
    if (user) selectUser(user)
  } else if (e.key === 'Escape') {
    closeDropdown()
  }
}

function navigateDropdown(direction: number) {
  const users = filteredUsers.value
  if (users.length === 0) return

  const currentIndex = users.findIndex(u => u.id === activeUserId.value)
  let nextIndex = currentIndex + direction

  if (nextIndex < 0) nextIndex = users.length - 1
  if (nextIndex >= users.length) nextIndex = 0

  activeUserId.value = users[nextIndex].id
}

function selectUser(user: User) {
  if (mentionStartIndex.value < 0) return

  const beforeMention = inputValue.value.substring(0, mentionStartIndex.value)
  const afterMention = inputValue.value.substring(getCursorPosition())
  const mentionText = `@{userId:${user.id}:${user.realName}} `

  inputValue.value = beforeMention + mentionText + afterMention
  emit('update:modelValue', inputValue.value)
  emit('mention', user)

  closeDropdown()

  // 重新聚焦输入框
  setTimeout(() => {
    const textarea = inputRef.value?.querySelector('textarea')
    if (textarea) {
      const newCursorPos = (beforeMention + mentionText).length
      textarea.setSelectionRange(newCursorPos, newCursorPos)
      textarea.focus()
    }
  }, 0)
}

function closeDropdown() {
  showDropdown.value = false
  searchQuery.value = ''
  activeUserId.value = null
  mentionStartIndex.value = -1
}

function getCursorPosition(): number {
  const textarea = inputRef.value?.querySelector('textarea')
  return textarea?.selectionStart || 0
}

// 点击外部关闭下拉框
function handleClickOutside(e: MouseEvent) {
  if (inputRef.value && !inputRef.value.contains(e.target as Node)) {
    closeDropdown()
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style scoped>
.mention-input {
  position: relative;
}

.mention-dropdown {
  position: absolute;
  bottom: 100%;
  left: 0;
  right: 0;
  max-height: 200px;
  overflow-y: auto;
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  z-index: 100;
}

.mention-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.mention-item:hover,
.mention-item.active {
  background: #f5f7fa;
}

.user-info {
  display: flex;
  flex-direction: column;
}

.user-info .name {
  font-size: 14px;
  color: #303133;
}

.user-info .emp-id {
  font-size: 12px;
  color: #909399;
}

.no-results {
  padding: 12px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
</style>
