<template>
  <div class="collaboration-bar">
    <div class="online-users">
      <el-tooltip v-for="user in onlineUsers" :key="user.userId"
        :content="`${user.userName} 正在编辑`" placement="bottom">
        <el-avatar :size="32" :style="{ border: `2px solid ${user.color}` }">
          {{ user.userName.charAt(0) }}
        </el-avatar>
      </el-tooltip>
      <span class="online-count" v-if="onlineUsers.length > 1">
        {{ onlineUsers.length }} 人协作中
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount } from 'vue'
import { usePresenceStore } from '@/stores/presence'

const props = defineProps<{ docId: number }>()
const presenceStore = usePresenceStore()

const currentUserId = Number(sessionStorage.getItem('userId'))
const isAdmin = sessionStorage.getItem('role') === 'admin'

const onlineUsers = computed(() =>
  presenceStore.getOnlineUsers(props.docId).filter(
    u => u.userId !== currentUserId && !(isAdmin && u.userName?.includes('管理员'))
  )
)

onMounted(() => presenceStore.connect(props.docId))
onBeforeUnmount(() => presenceStore.disconnect(props.docId))
</script>

<style scoped>
.collaboration-bar {
  display: flex;
  align-items: center;
}

.online-users {
  display: flex;
  align-items: center;
  gap: -8px;
}

.online-users .el-avatar {
  margin-left: -8px;
}

.online-users .el-avatar:first-child {
  margin-left: 0;
}

.online-count {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}
</style>
