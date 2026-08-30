/**
 * @mind-elixir/node-menu 类型声明
 * 包本身没带 .d.ts，手动声明。
 */
declare module '@mind-elixir/node-menu' {
  import type { MindElixirInstance } from 'mind-elixir'
  const NodeMenu: (mind: MindElixirInstance) => void
  export default NodeMenu
}

declare module '@mind-elixir/node-menu/dist/style.css'