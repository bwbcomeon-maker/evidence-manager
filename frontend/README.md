# 项目交付证据管理系统 - 前端

基于 Vue 3 + Vite + TypeScript + Vant 的移动端优先前端应用。

## 技术栈

- **框架**: Vue 3
- **构建工具**: Vite
- **语言**: TypeScript
- **UI 框架**: Vant（移动端优先）
- **路由**: vue-router
- **HTTP**: axios
- **状态管理**: Pinia
- **包管理**: npm

## 前置要求

请确保已安装 Node.js（推荐版本 >= 18.0.0）和 npm。

### 环境检查

如果遇到 "npm 无法识别" 错误，请先运行环境检查脚本：

```powershell
.\check-node.ps1
```

或查看详细的环境配置指南：`SETUP.md`

## 安装依赖

```bash
npm install
```

## 启动开发服务器

### 方式一：使用启动脚本（推荐）

```powershell
.\start.ps1
```

启动脚本会自动检查环境、安装依赖并启动服务器。

### 方式二：手动启动

```bash
npm install
npm run dev
```

启动后，访问 http://localhost:3000 即可看到项目列表页面。

## 项目结构

```
frontend/
├─ src/
│  ├─ main.ts              # 应用入口
│  ├─ App.vue              # 根组件
│  ├─ router/              # 路由配置
│  ├─ api/                 # API 请求封装
│  ├─ views/               # 页面组件
│  │  ├─ ProjectList.vue      # 项目列表页
│  │  ├─ ProjectDetail.vue    # 项目详情页
│  │  ├─ EvidenceUpload.vue  # 证据上传页
│  │  └─ EvidenceList.vue    # 证据列表页
│  └─ stores/              # Pinia 状态管理
├─ vite.config.ts          # Vite 配置
└─ package.json            # 项目配置
```

## 路由配置

- `/projects` - 项目列表页
- `/projects/:id` - 项目详情页
- `/projects/:id/upload` - 证据上传页
- `/projects/:id/evidences` - 证据列表页

## API 代理配置

前端请求 `/api/**` 会自动代理到后端服务器 `http://localhost:8081`。

## 开发说明

当前所有页面使用 Mock 数据，可以直接运行查看效果。后续可以替换为真实的 API 调用。
