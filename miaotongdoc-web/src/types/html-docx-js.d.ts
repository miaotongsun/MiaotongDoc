declare module 'html-docx-js' {
  interface HtmlDocxOptions {
    orientation?: 'portrait' | 'landscape'
    margins?: {
      top?: number
      right?: number
      bottom?: number
      left?: number
      header?: number
      footer?: number
      gutter?: number
    }
  }

  function asBlob(html: string, options?: HtmlDocxOptions): Blob

  export { asBlob }
  export default { asBlob }
}
