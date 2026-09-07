---
title: 安卓 Markdown 阅读器 · 渲染样例
tags: [typora, theme, preview]
date: 2026-09-06
---

# 让排版回到 Typora

> 这篇样例用来检验 **phycat 主题** 在「纯查看」场景下的还原度。
> 下面的每种元素都是 Markdown 里最常用的那部分。

## 文字与强调

正文段落——这一段用于观察**行高、字距、正文字色**。它含有*斜体*、~~删除线~~、`行内代码`，以及一个[链接](https://github.com)。汉字与英文混排也会出现，用来检验 HarmonyOS Sans / 思源宋体与 Cascadia 的搭配效果：Markdown 0.5 alpha。

### 三级标题

三级及以下标题会带小图标与自动编号，注意标题编号与图标的显示效果。

#### 四级标题

#### 四级标题（再次出现，验证编号递增）

## 列表

有序列表与任务清单都演示一下：

1. 第一步：把 App 装到平板上
2. 第二步：导入 md 文件
3. 第三步：切换主题

- 无序项 A
- 无序项 B
  - 嵌套项 B1
  - 嵌套项 B2

任务清单（Typora 风格的复选框）：

- [x] 已通读这份文档
- [ ] 把文档导入平板
- [ ] 挑一套喜欢的主题

## 引用

> 好的引用是正文的留白。这一段用来检验引用块的
> 边框、底色与内边距是否与原主题一致。

## 表格

| 主题名   | 基调       | 说明                       |
| -------- | ---------- | -------------------------- |
| Caramel  | 焦糖暖橙   | 温暖，阅读友好             |
| Prussian | 普鲁士蓝   | 沉稳，适合技术文档         |
| Dark     | 夜间暗色   | 暗光环境、更省电           |

## 代码块

```kotlin
data class Book(
    val title: String,
    val theme: String = "phycat-caramel",
)

fun main() {
    println("Hello, Typora reader!")
}
```

没有标注语言的代码块也能正常显示：

```
这一段没有指定语言。
```

渲染时会被包进带 `md-fences` 类的 `<pre>`，代码用 Cascadia Code 显示。

## 图片

开启 HTML 透传后，下面这张**内嵌 SVG**（data URI）在离线时也能显示：

<img src="data:image/svg+xml;utf8,%3Csvg xmlns='http://www.w3.org/2000/svg' width='520' height='92'%3E%3Crect width='520' height='92' rx='10' fill='%23f4e3d3'/%3E%3Ctext x='18' y='54' font-family='sans-serif' font-size='21' fill='%23553a25'%3Einline SVG via data URI - works offline%3C/text%3E%3C/svg%3E" style="max-width:100%">

## 分隔线

---

到这里，样例覆盖了：标题层级与自动编号、正文、强调、行内/块级代码、引用、有序/无序列表、任务清单、表格、图片。

想用你自己的文档测试？点顶部工具栏的 **打开 md 文件…**。
