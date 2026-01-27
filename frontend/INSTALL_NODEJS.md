# Node.js 安装指南

## 🚀 快速安装（推荐）

### 方法一：官方网站下载（最简单）

1. **访问 Node.js 官网**
   - 打开浏览器访问：https://nodejs.org/
   - 页面会自动显示推荐的 LTS 版本

2. **下载安装包**
   - 点击绿色的 "LTS" 按钮下载（推荐版本）
   - 或访问：https://nodejs.org/dist/v20.11.1/node-v20.11.1-x64.msi

3. **安装 Node.js**
   - 双击下载的 `.msi` 文件
   - 按照安装向导操作
   - **⚠️ 重要：** 安装时务必勾选 **"Add to PATH"** 选项
   - 完成安装

4. **验证安装**
   - 关闭当前 PowerShell 窗口
   - 重新打开 PowerShell
   - 运行以下命令：
     ```powershell
     node --version
     npm --version
     ```
   - 如果显示版本号，说明安装成功

5. **启动前端项目**
   ```powershell
   cd d:\evidence-manager\frontend
   .\start.ps1
   ```

### 方法二：使用安装脚本

如果网络正常，可以运行自动下载脚本：

```powershell
cd d:\evidence-manager\frontend
.\download-nodejs.ps1
```

脚本会自动下载并启动安装程序。

## 📋 安装步骤详解

### 步骤 1: 下载

访问 https://nodejs.org/ 下载 Windows Installer (.msi) 文件。

**推荐版本：**
- LTS 版本（长期支持版）
- 当前推荐：v20.11.1 或更高

### 步骤 2: 安装

1. 双击下载的 `.msi` 文件
2. 点击 "Next" 继续
3. 接受许可协议
4. **重要：** 在 "Custom Setup" 页面，确保勾选：
   - ✅ Node.js runtime
   - ✅ npm package manager
   - ✅ **Add to PATH**（最重要！）
5. 点击 "Install"
6. 等待安装完成
7. 点击 "Finish"

### 步骤 3: 验证

重新打开 PowerShell（必须重新打开才能加载新的 PATH），运行：

```powershell
node --version
# 应显示：v20.11.1 或类似版本号

npm --version
# 应显示：10.2.4 或类似版本号
```

### 步骤 4: 启动项目

```powershell
cd d:\evidence-manager\frontend
.\start.ps1
```

## 🔧 常见问题

### Q: 安装后仍然提示找不到 node？

**解决方案：**
1. 确保安装时勾选了 "Add to PATH"
2. **必须重新打开 PowerShell**（PATH 更新需要重启终端）
3. 运行 `.\check-node.ps1` 检查环境

### Q: 如何手动添加到 PATH？

如果安装时忘记勾选 "Add to PATH"：

1. 找到 Node.js 安装目录（通常在 `C:\Program Files\nodejs\`）
2. 右键"此电脑" → 属性 → 高级系统设置
3. 点击"环境变量"
4. 在"系统变量"中找到 `Path`
5. 点击"编辑" → "新建"
6. 添加：`C:\Program Files\nodejs`
7. 确定保存
8. 重新打开 PowerShell

### Q: 下载很慢？

可以使用国内镜像或使用下载工具。

### Q: 安装需要管理员权限？

是的，安装 Node.js 需要管理员权限。右键安装程序选择"以管理员身份运行"。

## ✅ 安装完成后的验证

运行以下命令验证安装：

```powershell
# 检查 Node.js
node --version

# 检查 npm
npm --version

# 检查环境（使用项目提供的脚本）
cd d:\evidence-manager\frontend
.\check-node.ps1
```

## 🎯 下一步

安装完成后：

1. **验证安装**
   ```powershell
   node --version
   npm --version
   ```

2. **启动前端项目**
   ```powershell
   cd d:\evidence-manager\frontend
   .\start.ps1
   ```

3. **访问应用**
   - 前端：http://localhost:3000
   - 后端：http://localhost:8081

## 📞 需要帮助？

如果遇到问题：
1. 查看 `SETUP.md` 获取详细指导
2. 运行 `.\check-node.ps1` 检查环境
3. 查看 `SOLUTION.md` 了解常见问题
