# 前端项目启动脚本
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  项目交付证据管理系统 - 前端启动脚本" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查 Node.js
Write-Host "步骤 1: 检查 Node.js 环境..." -ForegroundColor Yellow
$nodeFound = $false

try {
    $nodeVersion = node --version 2>$null
    if ($nodeVersion) {
        Write-Host "✓ Node.js: $nodeVersion" -ForegroundColor Green
        $nodeFound = $true
    }
} catch {
    # 尝试查找 Node.js
    $commonPaths = @(
        "C:\Program Files\nodejs\node.exe",
        "C:\Program Files (x86)\nodejs\node.exe"
    )
    
    foreach ($path in $commonPaths) {
        if (Test-Path $path) {
            $nodeDir = Split-Path $path -Parent
            $env:PATH = "$nodeDir;$env:PATH"
            try {
                $nodeVersion = node --version 2>$null
                if ($nodeVersion) {
                    Write-Host "✓ Node.js: $nodeVersion (已添加到 PATH)" -ForegroundColor Green
                    $nodeFound = $true
                    break
                }
            } catch {
                continue
            }
        }
    }
}

if (-not $nodeFound) {
    Write-Host "✗ 未找到 Node.js" -ForegroundColor Red
    Write-Host ""
    Write-Host "请先安装 Node.js：" -ForegroundColor Yellow
    Write-Host "1. 访问 https://nodejs.org/" -ForegroundColor White
    Write-Host "2. 下载并安装 LTS 版本" -ForegroundColor White
    Write-Host "3. 安装时勾选 'Add to PATH'" -ForegroundColor White
    Write-Host ""
    Write-Host "或者运行检查脚本：" -ForegroundColor Cyan
    Write-Host "  .\check-node.ps1" -ForegroundColor White
    exit 1
}

# 检查依赖
Write-Host ""
Write-Host "步骤 2: 检查项目依赖..." -ForegroundColor Yellow
if (-not (Test-Path "node_modules")) {
    Write-Host "依赖未安装，开始安装..." -ForegroundColor Yellow
    npm install
    if ($LASTEXITCODE -ne 0) {
        Write-Host "✗ 依赖安装失败" -ForegroundColor Red
        exit 1
    }
    Write-Host "✓ 依赖安装完成" -ForegroundColor Green
} else {
    Write-Host "✓ 依赖已安装" -ForegroundColor Green
}

# 启动开发服务器
Write-Host ""
Write-Host "步骤 3: 启动开发服务器..." -ForegroundColor Yellow
Write-Host ""
Write-Host "Frontend will start at http://localhost:3000" -ForegroundColor Cyan
Write-Host "Press Ctrl+C to stop the server" -ForegroundColor Yellow
Write-Host ""

npm run dev
