export type Theme = 'blue' | 'blue-purple' | 'green' | 'orange' | 'pink' | 'custom'

export interface ThemeConfig {
  name: string
  preview: string  // 预览颜色
  variables: Record<string, string>
}

export const themes: Record<Theme, ThemeConfig> = {
  blue: {
    name: '商务蓝',
    preview: '#409eff',
    variables: {
      '--el-color-primary': '#409eff',
      '--el-color-primary-light-3': '#66b1ff',
      '--el-color-primary-light-5': '#8cc5ff',
      '--el-color-primary-light-7': '#b3d8ff',
      '--el-color-primary-light-8': '#d9ecff',
      '--el-color-primary-light-9': '#ecf5ff',
      '--el-color-primary-dark-2': '#337ecc',
      '--primary-gradient': 'linear-gradient(135deg, #409eff, #66b1ff)',
      '--card-accent': '#409eff',
      '--hover-bg': '#ecf5ff',
      '--active-bg': '#e6f0ff',
    }
  },
  'blue-purple': {
    name: '蓝渐紫',
    preview: 'linear-gradient(135deg, #409eff, #8b5cf6)',
    variables: {
      '--el-color-primary': '#409eff',
      '--el-color-primary-light-3': '#66b1ff',
      '--el-color-primary-light-5': '#8cc5ff',
      '--el-color-primary-light-7': '#b3d8ff',
      '--el-color-primary-light-8': '#d9ecff',
      '--el-color-primary-light-9': '#f0f4ff',
      '--el-color-primary-dark-2': '#337ecc',
      '--primary-gradient': 'linear-gradient(135deg, #409eff, #8b5cf6)',
      '--card-accent': '#8b5cf6',
      '--hover-bg': '#f0f4ff',
      '--active-bg': '#e8e8ff',
    }
  },
  green: {
    name: '清新绿',
    preview: '#67c23a',
    variables: {
      '--el-color-primary': '#67c23a',
      '--el-color-primary-light-3': '#85ce61',
      '--el-color-primary-light-5': '#a2d369',
      '--el-color-primary-light-7': '#c2e7b0',
      '--el-color-primary-light-8': '#d3f0c0',
      '--el-color-primary-light-9': '#ecf8e4',
      '--el-color-primary-dark-2': '#5baf26',
      '--primary-gradient': 'linear-gradient(135deg, #67c23a, #95d475)',
      '--card-accent': '#67c23a',
      '--hover-bg': '#ecf8e4',
      '--active-bg': '#d3f0c0',
    }
  },
  orange: {
    name: '活力橙',
    preview: '#e6a23c',
    variables: {
      '--el-color-primary': '#e6a23c',
      '--el-color-primary-light-3': '#ebb563',
      '--el-color-primary-light-5': '#f0c87a',
      '--el-color-primary-light-7': '#f5dab1',
      '--el-color-primary-light-8': '#f9e8c9',
      '--el-color-primary-light-9': '#fdf6ec',
      '--el-color-primary-dark-2': '#c88f2d',
      '--primary-gradient': 'linear-gradient(135deg, #e6a23c, #f5c97a)',
      '--card-accent': '#e6a23c',
      '--hover-bg': '#fdf6ec',
      '--active-bg': '#f9e8c9',
    }
  },
  pink: {
    name: '少女粉',
    preview: '#f56c6c',
    variables: {
      '--el-color-primary': '#f56c6c',
      '--el-color-primary-light-3': '#f78989',
      '--el-color-primary-light-5': '#f9a5a5',
      '--el-color-primary-light-7': '#fbc4c4',
      '--el-color-primary-light-8': '#fcd8d8',
      '--el-color-primary-light-9': '#feeeee',
      '--el-color-primary-dark-2': '#cc5959',
      '--primary-gradient': 'linear-gradient(135deg, #f56c6c, #fb9a9a)',
      '--card-accent': '#f56c6c',
      '--hover-bg': '#feeeee',
      '--active-bg': '#fcd8d8',
    }
  },
  custom: {
    name: '自定义',
    preview: '#409eff',
    variables: {
      '--el-color-primary': '#409eff',
      '--el-color-primary-light-3': '#66b1ff',
      '--el-color-primary-light-5': '#8cc5ff',
      '--el-color-primary-light-7': '#b3d8ff',
      '--el-color-primary-light-8': '#d9ecff',
      '--el-color-primary-light-9': '#ecf5ff',
      '--el-color-primary-dark-2': '#337ecc',
      '--primary-gradient': 'linear-gradient(135deg, #409eff, #8b5cf6)',
      '--card-accent': '#409eff',
      '--hover-bg': '#ecf5ff',
      '--active-bg': '#e6f0ff',
    }
  }
}

export function applyTheme(theme: Theme) {
  const config = themes[theme]
  const root = document.documentElement

  // 如果是 custom 主题且有自定义颜色，不覆盖
  if (theme === 'custom' && localStorage.getItem('theme-color-1')) {
    return
  }

  Object.entries(config.variables).forEach(([key, value]) => {
    root.style.setProperty(key, value)
  })

  localStorage.setItem('theme', theme)
}

export function loadTheme(): Theme {
  const saved = localStorage.getItem('theme') as Theme
  const theme = saved && themes[saved] ? saved : 'blue'

  // 加载自定义颜色
  const c1 = localStorage.getItem('theme-color-1')
  const c2 = localStorage.getItem('theme-color-2')

  if (theme === 'custom' && c1 && c2) {
    const root = document.documentElement
    const r = parseInt(c1.slice(1,3), 16)
    const g = parseInt(c1.slice(3,5), 16)
    const b = parseInt(c1.slice(5,7), 16)

    root.style.setProperty('--el-color-primary', c1)
    root.style.setProperty('--el-color-primary-light-3', `rgba(${r},${g},${b},0.6)`)
    root.style.setProperty('--el-color-primary-light-5', `rgba(${r},${g},${b},0.4)`)
    root.style.setProperty('--el-color-primary-light-7', `rgba(${r},${g},${b},0.2)`)
    root.style.setProperty('--el-color-primary-light-8', `rgba(${r},${g},${b},0.1)`)
    root.style.setProperty('--el-color-primary-light-9', `rgba(${r},${g},${b},0.05)`)
    root.style.setProperty('--primary-gradient', `linear-gradient(135deg, ${c1}, ${c2})`)
    root.style.setProperty('--card-accent', c2)
    root.style.setProperty('--hover-bg', `rgba(${r},${g},${b},0.08)`)
    root.style.setProperty('--active-bg', `rgba(${r},${g},${b},0.12)`)
  }

  applyTheme(theme)
  return theme
}

export function applyCustomColor(color: string) {
  const hex = color.startsWith('#') ? color : `#${color}`
  const root = document.documentElement

  root.style.setProperty('--el-color-primary', hex)
  root.style.setProperty('--primary-gradient', `linear-gradient(135deg, ${hex}, ${adjustColor(hex, 30)})`)
  root.style.setProperty('--card-accent', hex)
  root.style.setProperty('--hover-bg', hexToRgba(hex, 0.1))
  root.style.setProperty('--active-bg', hexToRgba(hex, 0.15))

  localStorage.setItem('custom-color', hex)
}

function adjustColor(hex: string, amount: number): string {
  const num = parseInt(hex.replace('#', ''), 16)
  const r = Math.min(255, ((num >> 16) & 0xff) + amount)
  const g = Math.min(255, ((num >> 8) & 0xff) + amount)
  const b = Math.min(255, (num & 0xff) + amount)
  return `#${(1 << 24 | r << 16 | g << 8 | b).toString(16).slice(1)}`
}

function hexToRgba(hex: string, alpha: number): string {
  const num = parseInt(hex.replace('#', ''), 16)
  const r = (num >> 16) & 0xff
  const g = (num >> 8) & 0xff
  const b = num & 0xff
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}