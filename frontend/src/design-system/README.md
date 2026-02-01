# 设计系统

与 [docs/UI_DESIGN_SPEC.md](../../../docs/UI_DESIGN_SPEC.md) 配套的 CSS 设计令牌与工具类。

## 文件

- **design-tokens.css**：配色（浅/深主题）、字号、间距、圆角、阴影、毛玻璃与动效类。

## 主题

- 默认浅色（莫兰迪）：无需 class。
- 深色（霓虹）：在 `document.documentElement` 或 `document.body` 上添加 `theme-dark`。

## 使用示例

```css
.my-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
  box-shadow: var(--shadow-card);
  transition: box-shadow var(--transition-normal);
}
.my-card:hover {
  box-shadow: var(--shadow-card-hover);
}
```

```html
<header class="ds-nav-glass">...</header>
<div class="ds-card">...</div>
<div class="ds-skeleton" style="height: 80px;">...</div>
```
