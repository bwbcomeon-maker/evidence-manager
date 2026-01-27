# Node.js Download and Launch Script
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Node.js Download Assistant" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if already installed
$nodeCmd = Get-Command node -ErrorAction SilentlyContinue
if ($nodeCmd) {
    $nodeVersion = node --version 2>$null
    if ($nodeVersion) {
        Write-Host "Node.js already installed: $nodeVersion" -ForegroundColor Green
        exit 0
    }
}

# Node.js LTS version (Windows x64)
$nodeVersion = "20.11.1"
$downloadUrl = "https://nodejs.org/dist/v$nodeVersion/node-v$nodeVersion-x64.msi"
$installerPath = "$env:TEMP\nodejs-installer.msi"

Write-Host "Downloading Node.js v$nodeVersion (LTS)..." -ForegroundColor Yellow
Write-Host "URL: $downloadUrl" -ForegroundColor Gray
Write-Host ""

try {
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -Uri $downloadUrl -OutFile $installerPath -UseBasicParsing
    Write-Host "Download completed!" -ForegroundColor Green
    Write-Host "File saved to: $installerPath" -ForegroundColor Gray
    Write-Host ""
    
    Write-Host "Launching installer..." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "IMPORTANT: During installation, make sure to check 'Add to PATH' option!" -ForegroundColor Red
    Write-Host ""
    
    Start-Process msiexec.exe -ArgumentList "/i `"$installerPath`""
    
    Write-Host "Installer launched successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "After installation completes:" -ForegroundColor Cyan
    Write-Host "1. Close this window and restart PowerShell" -ForegroundColor White
    Write-Host "2. Run: node --version" -ForegroundColor Yellow
    Write-Host "3. Run: cd d:\evidence-manager\frontend" -ForegroundColor Yellow
    Write-Host "4. Run: .\start.ps1" -ForegroundColor Yellow
    
} catch {
    Write-Host "Download failed: $_" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please download manually from:" -ForegroundColor Yellow
    Write-Host "https://nodejs.org/" -ForegroundColor Cyan
    exit 1
}
