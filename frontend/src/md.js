import { marked } from 'marked'
import DOMPurify from 'dompurify'

// 聊天回复的 markdown 渲染：单换行也换行（LLM 输出习惯），输出经 DOMPurify 消毒防 XSS
marked.use({ breaks: true, gfm: true })

export function renderMd(text) {
  if (!text) return ''
  return DOMPurify.sanitize(marked.parse(text))
}
