/**
 * 代码块语言列表 + 搜索
 * - value: highlight.js / lowlight 官方 alias（Tiptap CodeBlockLowlight 直接吃）
 * - label: 显示名
 * - aliases: 搜索时匹配的扩展关键词（不传给 lowlight）
 */

export interface CodeLang {
  value: string
  label: string
  aliases?: string[]
}

export const CODE_LANGS: CodeLang[] = [
  { value: 'plaintext', label: 'Plain Text' },
  { value: 'abap', label: 'ABAP' },
  { value: 'ada', label: 'Ada' },
  { value: 'apache', label: 'Apache', aliases: ['apacheconf'] },
  { value: 'apex', label: 'Apex' },
  { value: 'x86asm', label: 'Assembly language', aliases: ['asm', 'assembly'] },
  { value: 'bash', label: 'Bash', aliases: ['sh', 'shell', 'zsh'] },
  { value: 'csharp', label: 'C#', aliases: ['cs', 'c-sharp'] },
  { value: 'cpp', label: 'C++', aliases: ['cxx', 'c-plus-plus'] },
  { value: 'c', label: 'C' },
  { value: 'cmake', label: 'CMake' },
  { value: 'cobol', label: 'COBOL', aliases: ['cob'] },
  { value: 'css', label: 'CSS' },
  { value: 'coffeescript', label: 'CoffeeScript', aliases: ['coffee'] },
  { value: 'd', label: 'D' },
  { value: 'dart', label: 'Dart' },
  { value: 'delphi', label: 'Delphi', aliases: ['pascal', 'pas'] },
  { value: 'diff', label: 'Diff', aliases: ['patch'] },
  { value: 'django', label: 'Django', aliases: ['djangohtml'] },
  { value: 'dockerfile', label: 'Dockerfile', aliases: ['docker'] },
  { value: 'erlang', label: 'Erlang', aliases: ['erl'] },
  { value: 'fortran', label: 'Fortran', aliases: ['f90', 'f95'] },
  { value: 'gherkin', label: 'Gherkin', aliases: ['cucumber'] },
  { value: 'go', label: 'Go', aliases: ['golang'] },
  { value: 'graphql', label: 'GraphQL', aliases: ['gql'] },
  { value: 'groovy', label: 'Groovy' },
  { value: 'xml', label: 'HTML', aliases: ['html', 'htm'] },
  { value: 'htmlbars', label: 'HTMLBars' },
  { value: 'http', label: 'HTTP' },
  { value: 'haskell', label: 'Haskell', aliases: ['hs'] },
  { value: 'json', label: 'JSON', aliases: ['jsonc'] },
  { value: 'java', label: 'Java' },
  { value: 'javascript', label: 'JavaScript', aliases: ['js', 'jsx'] },
  { value: 'julia', label: 'Julia', aliases: ['jl'] },
  { value: 'kotlin', label: 'Kotlin', aliases: ['kt'] },
  { value: 'latex', label: 'LaTeX', aliases: ['tex'] },
  { value: 'lisp', label: 'Lisp' },
  { value: 'lua', label: 'Lua' },
  { value: 'matlab', label: 'MATLAB' },
  { value: 'makefile', label: 'Makefile', aliases: ['mk', 'mak'] },
  { value: 'markdown', label: 'Markdown', aliases: ['md', 'mdx'] },
  { value: 'nginx', label: 'Nginx', aliases: ['nginxconf'] },
  { value: 'objectivec', label: 'Objective-C', aliases: ['objc', 'obj-c'] },
  { value: 'glsl', label: 'OpenGL Shading Language', aliases: ['opengl'] },
  { value: 'php', label: 'PHP' },
  { value: 'perl', label: 'Perl', aliases: ['pl'] },
  { value: 'powershell', label: 'PowerShell', aliases: ['ps1', 'pwsh'] },
  { value: 'prolog', label: 'Prolog' },
  { value: 'properties', label: 'Properties' },
  { value: 'protobuf', label: 'ProtoBuf', aliases: ['proto'] },
  { value: 'python', label: 'Python', aliases: ['py'] },
  { value: 'r', label: 'R' },
  { value: 'ruby', label: 'Ruby', aliases: ['rb'] },
  { value: 'rust', label: 'Rust', aliases: ['rs'] },
  { value: 'sas', label: 'SAS' },
  { value: 'scss', label: 'SCSS', aliases: ['sass'] },
  { value: 'sql', label: 'SQL' },
  { value: 'scala', label: 'Scala' },
  { value: 'scheme', label: 'Scheme' },
  { value: 'shell', label: 'Shell' },
  { value: 'solidity', label: 'Solidity', aliases: ['sol'] },
  { value: 'swift', label: 'Swift' },
  { value: 'toml', label: 'TOML' },
  { value: 'thrift', label: 'Thrift' },
  { value: 'typescript', label: 'TypeScript', aliases: ['ts', 'tsx'] },
  { value: 'vbscript', label: 'VBScript', aliases: ['vbs'] },
  { value: 'vhdl', label: 'VHDL' },
  { value: 'verilog', label: 'Verilog', aliases: ['v'] },
  { value: 'vb', label: 'Visual Basic', aliases: ['vbnet'] },
  { value: 'xml', label: 'XML' },
  { value: 'yaml', label: 'YAML', aliases: ['yml'] },
]

/**
 * 按查询字符串模糊匹配语言（label / value / aliases 三段）
 */
export function searchLangs(query: string): CodeLang[] {
  const q = (query || '').trim().toLowerCase()
  if (!q) return CODE_LANGS
  return CODE_LANGS.filter(l => {
    if (l.label.toLowerCase().includes(q)) return true
    if (l.value.toLowerCase().includes(q)) return true
    return l.aliases?.some(a => a.toLowerCase().includes(q)) ?? false
  })
}