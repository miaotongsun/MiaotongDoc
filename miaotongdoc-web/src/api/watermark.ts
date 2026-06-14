import api from './index'

export interface WatermarkConfig {
  id: number
  name: string
  isEnabled: boolean
  textTemplate: string
  fontSize: number
  fontColor: string
  rotation: number
  opacity: number
  position: string
}

export const watermarkApi = {
  getConfig() {
    return api.get<any, WatermarkConfig>('/watermark/config')
  },

  updateConfig(updates: Partial<WatermarkConfig>) {
    return api.put<any, WatermarkConfig>('/watermark/config', updates)
  }
}
