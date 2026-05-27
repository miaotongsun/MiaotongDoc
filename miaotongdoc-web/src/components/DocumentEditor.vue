<template>
  <div id="onlyoffice-editor" class="document-editor"></div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue'

const props = defineProps<{
  serverUrl: string
  config: any
}>()

const emit = defineEmits(['ready', 'stateChange', 'remoteConnection', 'remoteChange'])

let editor: any = null

onMounted(async () => {
  // 彻底清除所有 Service Worker 和缓存
  if ('serviceWorker' in navigator) {
    const registrations = await navigator.serviceWorker.getRegistrations()
    for (const reg of registrations) {
      console.log('[DocEditor] Unregistering SW:', reg.scope)
      await reg.unregister()
    }
  }
  // 清除所有缓存
  if ('caches' in window) {
    const cacheNames = await caches.keys()
    for (const name of cacheNames) {
      console.log('[DocEditor] Deleting cache:', name)
      await caches.delete(name)
    }
  }
  loadEditorScript()
})

onBeforeUnmount(() => {
  destroyEditor()
})

function loadEditorScript() {
  const script = document.createElement('script')
  script.src = `${props.serverUrl}/web-apps/apps/api/documents/api.js`
  script.onload = () => initEditor()
  script.onerror = (e) => console.error('[DocEditor] Script load failed:', e)
  document.head.appendChild(script)
}

function initEditor() {
  if (!(window as any).DocsAPI) {
    console.error('[DocEditor] DocsAPI not available')
    return
  }

  const config = {
    ...props.config,
    events: {
      onAppReady: () => {
        console.log('[DocEditor] App ready, editor initialized')
        emit('ready')
      },
      onDocumentStateChange: (event: any) => {
        emit('stateChange', event.data ? 'editing' : 'saved')
      },
      onFirstRemoteConnection: (event: any) => {
        console.log('[DocEditor] First remote user connected:', event)
        emit('remoteConnection', { type: 'join', userId: event?.data?.userId })
      },
      onRemoteConnection: (event: any) => {
        console.log('[DocEditor] Remote user joined:', event)
        emit('remoteConnection', { type: 'join', userId: event?.data?.userId })
      },
      onRemoteDocumentChange: (event: any) => {
        console.log('[DocEditor] Remote document changed:', event)
        emit('remoteChange', event)
      },
      onError: (event: any) => console.error('[DocEditor] Error:', event)
    }
  }

  editor = new (window as any).DocsAPI.DocEditor('onlyoffice-editor', config)
}

function destroyEditor() {
  if (editor) {
    editor.destroyEditor()
    editor = null
  }
}
</script>

<style scoped>
.document-editor {
  width: 100%;
  height: 100%;
}
</style>
