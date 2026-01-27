# 前端项目环境配置指南

## 问题诊断

如果遇到 "npm 无法识别" 或 "Node.js not found" 错误，说明系统中未安装 Node.js 或 Node.js 不在 PATH 环境变量中。

## 解决方案

### 方案一：安装 Node.js（推荐）

1. **下载 Node.js**
   - 访问官网：https://nodejs.org/
   - 下载 LTS 版本（长期支持版，推荐 18.x 或更高版本）
   - Windows 系统下载 `.msi` 安装包

2. **安装 Node.js**
   - 双击下载的安装包
   - 按照安装向导操作
   - **重要**：安装时务必勾选 "Add to PATH" 选项
   - 完成安装

3. **验证安装**
   - 重新打开 PowerShell 或命令提示符
   - 运行以下命令验证：
     ```powershell
     node --version
     npm --version
     ```
   - 如果显示版本号，说明安装成功

4. **启动项目**
   ```powershell
   cd d:\evidence-manager\frontend
   npm install
   npm run dev
   ```

### 方案二：使用 Chocolatey（如果已安装）

```powershell
choco install nodejs-lts
```

### 方案三：使用提供的启动脚本

项目根目录提供了两个 PowerShell 脚本：

1. **检查环境脚本** (`check-node.ps1`)
   ```powershell
   .\check-node.ps1
   ```
   此脚本会：
   - 检查 Node.js 是否已安装
   - 查找 Node.js 的安装位置
   - 提供安装指导

2. **一键启动脚本** (`start.ps1`)
   ```powershell
   .\start.ps1
   ```
   此脚本会：
   - 自动检查 Node.js 环境
   - 自动安装依赖（如果未安装）
   - 启动开发服务器

### 方案四：手动配置 PATH（如果 Node.js 已安装但不在 PATH 中）

1. **找到 Node.js 安装目录**
   - 通常在：`C:\Program Files\nodejs\`
   - 或：`C:\Program Files (x86)\nodejs\`

2. **添加到 PATH**
   - 右键"此电脑" → 属性 → 高级系统设置
   - 点击"环境变量"
   - 在"系统变量"中找到 `Path`
   - 点击"编辑" → "新建"
   - 添加 Node.js 安装目录路径
   - 确定保存

3. **重新打开终端**
   - 关闭当前 PowerShell/CMD
   - 重新打开并验证

## 常见问题

### Q: 安装后仍然提示找不到 node？
A: 
- 确保安装时勾选了 "Add to PATH"
- 重新打开终端窗口（PATH 更新需要重启终端）
- 运行 `check-node.ps1` 脚本检查

### Q: npm install 很慢？
A: 
- 可以使用国内镜像源：
  ```powershell
  npm config set registry https://registry.npmmirror.com
  ```

### Q: 端口 3000 被占用？
A: 
- 修改 `vite.config.ts` 中的 `server.port` 配置
- 或关闭占用 3000 端口的程序

## 验证安装

安装完成后，运行以下命令验证：

```powershell
node --version    # 应显示版本号，如 v18.17.0
npm --version     # 应显示版本号，如 9.6.7
```

## 下一步

环境配置完成后，执行：

```powershell
cd d:\evidence-manager\frontend
npm install
npm run dev
```

项目将在 http://localhost:3000 启动。
