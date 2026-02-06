# 前端布局与 Tabbar 规范

本文档沉淀「Tabbar 空态点击失效 + 白屏」类问题的根因与修复经验，供新增/改版页面时对照自检，避免内容区覆盖底栏、层级错误导致点击被吞或白屏。

---

## 一、背景与问题现象

在带底部 Tabbar 的一级页（如首页、项目列表、证据、我的）中，曾出现：

- **有数据时**：点击底部 Tab，高亮与路由切换正常。
- **无数据空态时**：点击 Tab 不变蓝、不跳转，疑似点击未生效；部分场景下从子页返回一级页后出现白屏。

根因可归纳为两类：**内容区/空态覆盖 Tabbar**（布局未预留底栏空间），以及 **Tabbar 与内容层 stacking context / z-index 不当**（底栏被压在下方或点击被上层拦截）。以下规范与自检清单用于从布局、路由联动、空态、遮罩、z-index、safe-area 等维度规避同类问题。

---

## 二、根因总结

| 类型 | 说明 |
|------|------|
| **A. 覆盖** | 内容区未为固定底栏预留高度，空态/列表占满视口，从布局上占满 Tabbar 区域，导致底部点击落在内容层而非 Tabbar。 |
| **B. stacking context / z-index** | Tabbar 默认 z-index 较低（如 Vant 的 1），与内容层同级或低于内容层时，底部点击被内容层接住；或透明/遮罩层覆盖底栏并拦截事件。 |

---

## 三、规范条款

### A. Tabbar 必须「路由驱动高亮」

- **推荐**：使用 Vant Tabbar 的 **route 模式**（`<van-tabbar route>`，每个 `<van-tabbar-item to="/path">`），由当前路由决定高亮，单一真相源。
- **禁止**：使用 `v-model(active)` + `watch(route)` + `@change` 双源同步，易产生竞态（如 URL 已变但高亮未更新）。
- **若确需手写**：仅允许「路由 → active」单向映射（如 `computed` 由 `route.path` 推导 active），禁止在点击时再写一套与路由无关的 active 更新。

### B. 内容区必须为固定底栏预留空间（关键）

- 当 **显示 Tabbar**（如 `route.meta.showTabbar === true`）时，**内容区必须预留底部高度**，避免空态/列表占满视口后覆盖 Tabbar 区域导致点击被吞。
- **推荐写法**（在布局层，如 MainLayout 的 main 容器）：
  ```css
  .layout-content--with-tabbar {
    padding-bottom: calc(var(--van-tabbar-height, 50px) + env(safe-area-inset-bottom, 0));
  }
  ```
  - 仅在 `showTabbar === true` 时为该容器加上对应 class（或 data 属性），避免无 Tabbar 页面多出一块空白。
- 若项目使用其他底栏高度变量（如 `--van-tabbar-item-height`），可写成：
  `calc(var(--van-tabbar-height, var(--van-tabbar-item-height, 50px)) + env(safe-area-inset-bottom, 0))`。

### C. 层级与点击拦截规范

- **Tabbar 必须位于内容层之上**。建议在布局层对 Tabbar 根节点覆盖 CSS 变量或 class，例如：
  ```css
  .main-tabbar {
    z-index: 10;
  }
  ```
  或 `--van-tabbar-z-index: 10`，避免被内容/空态层盖住。
- **禁止**：空态/遮罩层使用 `position: fixed` 或 `absolute` 占满视口（如 `height: 100vh`、`bottom: 0`）且压住底栏；若必须全屏遮罩，需明确该层是否需要接收点击（不需要则设 `pointer-events: none`，或缩小覆盖范围不包含底栏）。
- **注意**：透明层（`opacity: 0`）仍会拦截点击，需避免无意义的透明全屏层盖住 Tabbar。

### D. 空态与白屏处理规范

- 列表为空时**必须**展示明确空态（如 `van-empty`）和可操作入口（如「新建」「导入」），不得仅留白。
- **禁止**：空态区域占满整屏且未为底栏预留空间，导致底栏区域被内容层覆盖、无法点击。
- 页面应在 **loading / empty / error** 三态下均能正常渲染，避免因数据为空或接口异常导致整页白屏；列表页需区分「加载中」「无数据」「加载失败」并分别展示。

---

## 四、新增页面自检 Checklist

新增或改版「带底部 Tabbar」的一级页或存在空态/弹窗的页面时，请按下列项自检（可勾选）。

### 布局与底栏

- [ ] 内容区在「显示 Tabbar」时已预留底部高度（如通过 class 控制 `padding-bottom`），且与 `--van-tabbar-height` / safe-area 一致。
- [ ] Tabbar 使用 **route 模式**（`route` + `to`），或手写时仅用「路由 → active」单向 computed，无 v-model + watch + @change 双源同步。
- [ ] Tabbar 层级高于内容层（如 z-index 10 或覆盖 `--van-tabbar-z-index`），且无全屏 fixed/absolute 层压住底栏。

### 空态与三态

- [ ] 列表无数据时展示空态组件（如 `van-empty`）和可操作入口（新建/导入等）。
- [ ] 空态区域不占满视口覆盖 Tabbar（依赖布局预留 padding-bottom）。
- [ ] loading / empty / error 三态均有 UI 展示，无白屏。

### 遮罩与弹窗

- [ ] 全屏或大面积遮罩不覆盖 Tabbar 区域，或该层设 `pointer-events: none`（仅在不需点击时）。
- [ ] 弹窗（Popup/Dialog/Overlay）关闭后，Tabbar 仍可正常点击并切换路由。

### 回归验证（必跑）

- [ ] **空数据页面**：点击四个 Tab，每次对应 Tab 变蓝且路由切换正确。
- [ ] **有数据页面**：点击四个 Tab，行为与空数据一致。
- [ ] **空态 → 切换 → 返回 → 再切换**：从空态页切走再返回再切 Tab，高亮与路由一致。
- [ ] **刷新 + 前进/后退**：刷新或浏览器前进/后退后，当前页对应 Tab 高亮正确，Tab 点击正常。
- [ ] **弹窗/遮罩**：打开并关闭 Popup/Dialog 后，底部 Tab 仍可点击并切换。

---

## 五、示例代码片段

### Tabbar route 模式（布局中）

```vue
<template>
  <div class="main-layout">
    <main class="layout-content" :class="{ 'layout-content--with-tabbar': showTabbar }">
      <router-view :key="route.fullPath" />
    </main>
    <van-tabbar v-if="showTabbar" route placeholder class="main-tabbar">
      <van-tabbar-item to="/home" icon="home-o">首页</van-tabbar-item>
      <van-tabbar-item to="/projects" icon="apps-o">项目</van-tabbar-item>
      <van-tabbar-item to="/evidence" icon="description">证据</van-tabbar-item>
      <van-tabbar-item to="/me" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>
```

### 内容区预留 padding + Tabbar z-index（样式）

```css
.layout-content {
  flex: 1;
  min-height: 0;
  padding-bottom: env(safe-area-inset-bottom, 0);
}
/* 有 Tabbar 时预留底部高度，避免空态/内容覆盖 Tabbar 导致点击被吞 */
.layout-content--with-tabbar {
  padding-bottom: calc(var(--van-tabbar-height, 50px) + env(safe-area-inset-bottom, 0));
}
/* 保证 Tabbar 在内容层之上 */
.main-tabbar {
  z-index: 10;
}
```

### showTabbar 仅与路由 meta 绑定（逻辑）

```ts
const showTabbar = computed(() => !!route.meta.showTabbar)
```

---

以上规范与 checklist 供前端同学在新增/改版页面时对照使用，减少「空态点击失效」与「白屏」类问题复现。
