# Node.js Environment Check Script
Write-Host "Checking Node.js environment..." -ForegroundColor Cyan

# Check Node.js
$nodeFound = $false
$nodePath = $null

# Method 1: Check node in PATH
$nodeCmd = Get-Command node -ErrorAction SilentlyContinue
if ($nodeCmd) {
    $nodeVersion = node --version 2>$null
    if ($nodeVersion) {
        Write-Host "Found Node.js: $nodeVersion" -ForegroundColor Green
        $nodeFound = $true
        $nodePath = "node"
    }
}

# Method 2: Check common installation paths
if (-not $nodeFound) {
    $commonPaths = @(
        "C:\Program Files\nodejs\node.exe",
        "C:\Program Files (x86)\nodejs\node.exe",
        "$env:LOCALAPPDATA\Programs\nodejs\node.exe"
    )
    
    foreach ($path in $commonPaths) {
        if (Test-Path $path) {
            Write-Host "Found Node.js: $path" -ForegroundColor Green
            $nodeFound = $true
            $nodePath = $path
            break
        }
    }
}

if ($nodeFound) {
    Write-Host ""
    Write-Host "Node.js environment is ready!" -ForegroundColor Green
    Write-Host ""
    Write-Host "You can run the following commands to start the project:" -ForegroundColor Cyan
    Write-Host "  npm install" -ForegroundColor White
    Write-Host "  npm run dev" -ForegroundColor White
    
    # If found but not in PATH, provide temporary solution
    if ($nodePath -ne "node") {
        Write-Host ""
        Write-Host "Note: Node.js is not in PATH, you can use full path:" -ForegroundColor Yellow
        $nodeDir = Split-Path $nodePath -Parent
        Write-Host "  `$env:PATH = `"$nodeDir;`$env:PATH`"" -ForegroundColor White
        Write-Host "  npm install" -ForegroundColor White
    }
    
    # Check npm
    Write-Host ""
    Write-Host "Checking npm..." -ForegroundColor Cyan
    $npmCmd = Get-Command npm -ErrorAction SilentlyContinue
    if ($npmCmd) {
        $npmVersion = npm --version 2>$null
        if ($npmVersion) {
            Write-Host "npm version: $npmVersion" -ForegroundColor Green
        }
    } else {
        Write-Host "npm not found" -ForegroundColor Red
    }
} else {
    Write-Host ""
    Write-Host "Node.js not found" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please install Node.js:" -ForegroundColor Yellow
    Write-Host "1. Visit https://nodejs.org/" -ForegroundColor White
    Write-Host "2. Download and install LTS version (18.x or higher recommended)" -ForegroundColor White
    Write-Host "3. Make sure to check 'Add to PATH' during installation" -ForegroundColor White
    Write-Host "4. Restart terminal after installation and run this script again" -ForegroundColor White
    Write-Host ""
    Write-Host "Or use Chocolatey (if installed):" -ForegroundColor Cyan
    Write-Host "  choco install nodejs-lts" -ForegroundColor White
    exit 1
}
