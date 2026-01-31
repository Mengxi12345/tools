/**
 * 高亮搜索结果中的关键词
 */
export function highlightText(text: string, keyword: string): string {
  if (!keyword || !text) return text;

  const regex = new RegExp(`(${escapeRegex(keyword)})`, 'gi');
  return text.replace(regex, '<mark>$1</mark>');
}

/**
 * 高亮多个关键词
 */
export function highlightMultipleKeywords(text: string, keywords: string[]): string {
  if (!keywords || keywords.length === 0 || !text) return text;

  const escapedKeywords = keywords.map(k => escapeRegex(k)).join('|');
  const regex = new RegExp(`(${escapedKeywords})`, 'gi');
  return text.replace(regex, '<mark>$1</mark>');
}

/**
 * 转义正则表达式特殊字符
 */
function escapeRegex(str: string): string {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * React 组件中使用的高亮函数
 */
export function HighlightText({ text, keyword }: { text: string; keyword: string }) {
  const highlighted = highlightText(text, keyword);
  return <span dangerouslySetInnerHTML={{ __html: highlighted }} />;
}
