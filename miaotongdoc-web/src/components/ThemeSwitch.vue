<template>
  <div class="theme-btn">
    <el-button text @click="open = true">
      <el-icon :size="20"><Brush /></el-icon>
    </el-button>

    <div v-if="open" class="mask" @click.self="open = false">
      <div class="panel">
        <h4>主题颜色</h4>
        <div class="presets">
          <div
            v-for="(t, k) in themes"
            :key="k"
            class="preset"
            :class="{ active: cur === k }"
            @click="cur = k; applyTheme(k)"
          >
            <div class="color" :style="{ background: t.preview }"></div>
            <span>{{ t.name }}</span>
          </div>
        </div>
        <div class="divider"></div>
        <div class="custom">
          <div class="row">
            <label>起始色</label>
            <input type="color" v-model="c1" @change="applyGrad" />
          </div>
          <div class="row">
            <label>结束色</label>
            <input type="color" v-model="c2" @change="applyGrad" />
          </div>
        </div>
        <div class="preview" :style="{ background: g }"></div>
        <el-button type="primary" style="width:100%" @click="open = false">关闭</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Brush } from '@element-plus/icons-vue'
import { themes, applyTheme, loadTheme } from '@/utils/theme'

const open = ref(false)
const cur = ref(loadTheme() as string)
const c1 = ref('#409eff')
const c2 = ref('#8b5cf6')

onMounted(() => {
  const saved = localStorage.getItem('theme-color-1')
  const saved2 = localStorage.getItem('theme-color-2')
  if (saved) c1.value = saved
  if (saved2) c2.value = saved2
  applyTheme(cur.value as any)
})

const g = computed(() => `linear-gradient(135deg, ${c1.value}, ${c2.value})`)

function applyGrad() {
  const root = document.documentElement
  const c1Hex = c1.value
  const c2Hex = c2.value

  // 计算透明度变量
  const r1 = parseInt(c1Hex.slice(1,3), 16)
  const g1 = parseInt(c1Hex.slice(3,5), 16)
  const b1 = parseInt(c1Hex.slice(5,7), 16)

  root.style.setProperty('--el-color-primary', c1Hex)
  root.style.setProperty('--el-color-primary-light-3', `rgba(${r1},${g1},${b1},0.6)`)
  root.style.setProperty('--el-color-primary-light-5', `rgba(${r1},${g1},${b1},0.4)`)
  root.style.setProperty('--el-color-primary-light-7', `rgba(${r1},${g1},${b1},0.2)`)
  root.style.setProperty('--el-color-primary-light-8', `rgba(${r1},${g1},${b1},0.1)`)
  root.style.setProperty('--el-color-primary-light-9', `rgba(${r1},${g1},${b1},0.05)`)
  root.style.setProperty('--primary-gradient', `linear-gradient(135deg, ${c1Hex}, ${c2Hex})`)
  root.style.setProperty('--card-accent', c2Hex)
  root.style.setProperty('--hover-bg', `rgba(${r1},${g1},${b1},0.08)`)
  root.style.setProperty('--active-bg', `rgba(${r1},${g1},${b1},0.12)`)

  localStorage.setItem('theme', 'custom')
  localStorage.setItem('theme-color-1', c1Hex)
  localStorage.setItem('theme-color-2', c2Hex)
  cur.value = 'custom'
}
</script>

<style scoped>
.theme-btn { display: inline-block; }
.mask {
  position: fixed; inset: 0; z-index: 9999;
}
.panel {
  position: fixed; top: 70px; right: 20px; width: 260px;
  background: #fff; border-radius: 12px; box-shadow: 0 4px 20px rgba(0,0,0,.15);
  padding: 20px;
}
.panel h4 { margin: 0 0 16px; font-size: 16px; color: #303133; }
.presets { display: flex; gap: 10px; flex-wrap: wrap; }
.preset {
  flex: 1; min-width: 45px; text-align: center; cursor: pointer; padding: 10px 6px;
  border-radius: 10px; border: 2px solid transparent;
}
.preset:hover { background: #f5f7fa; }
.preset.active { border-color: var(--el-color-primary); }
.color { width: 36px; height: 36px; border-radius: 50%; margin: 0 auto 6px; box-shadow: 0 2px 6px rgba(0,0,0,.12); }
.preset span { font-size: 12px; color: #606266; }
.divider { height: 1px; background: #ebeef5; margin: 16px 0; }
.custom { display: flex; gap: 16px; justify-content: center; }
.row { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.row label { font-size: 12px; color: #909399; }
.row input[type="color"] {
  width: 50px; height: 50px; border: none; border-radius: 8px; cursor: pointer; padding: 0;
}
.row input[type="color"]::-webkit-color-swatch-wrapper { padding: 0; }
.row input[type="color"]::-webkit-color-swatch { border-radius: 8px; border: none; }
.preview { height: 36px; border-radius: 8px; margin: 14px 0; }
</style>