# Node.js Auto Installation Script
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Node.js Installation Assistant" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if already installed
$nodeCmd = Get-Command node -ErrorAction SilentlyContinue
if ($nodeCmd) {
    $nodeVersion = node --version 2>$null
    if ($nodeVersion) {
        Write-Host "Node.js already installed: $nodeVersion" -ForegroundColor Green
        Write-Host ""
        Write-Host "You can start the project now:" -ForegroundColor Cyan
        Write-Host "  cd d:\evidence-manager\frontend" -ForegroundColor White
        Write-Host "  .\start.ps1" -ForegroundColor White
        exit 0
    }
}

Write-Host "Preparing to download Node.js..." -ForegroundColor Yellow
Write-Host ""

# Node.js LTS version download URL (Windows x64)
$nodeVersion = "20.11.1"
$downloadUrl = "https://nodejs.org/dist/v$nodeVersion/node-v$nodeVersion-x64.msi"
$installerPath = "$env:TEMP\nodejs-installer.msi"

Write-Host "Download Information:" -ForegroundColor Cyan
Write-Host "  Version: v$nodeVersion (LTS)" -ForegroundColor White
Write-Host "  Download URL: $downloadUrl" -ForegroundColor White
Write-Host "  Save Location: $installerPath" -ForegroundColor White
Write-Host ""

# Ask user to continue
$response = Read-Host "Start download and installation? (Y/N)"
if ($response -ne "Y" -and $response -ne "y") {
    Write-Host "Installation cancelled." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Manual installation steps:" -ForegroundColor Cyan
    Write-Host "1. Visit https://nodejs.org/" -ForegroundColor White
    Write-Host "2. Download LTS version" -ForegroundColor White
    Write-Host "3. Run installer, make sure to check 'Add to PATH'" -ForegroundColor White
    exit 0
}

# Download installer
Write-Host ""
Write-Host "Downloading..." -ForegroundColor Yellow
try {
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -Uri $downloadUrl -OutFile $installerPath -UseBasicParsing
    Write-Host "Download completed!" -ForegroundColor Green
} catch {
    Write-Host "Download failed: $_" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please download manually:" -ForegroundColor Yellow
    Write-Host "1. Visit https://nodejs.org/" -ForegroundColor White
    Write-Host "2. Download LTS version" -ForegroundColor White
    Write-Host "3. Run installer" -ForegroundColor White
    exit 1
}

Write-Host ""
Write-Host "Installer downloaded to: $installerPath" -ForegroundColor Green
Write-Host ""
Write-Host "Important:" -ForegroundColor Yellow
Write-Host "1. Installer will launch automatically" -ForegroundColor White
Write-Host "2. During installation, make sure to check 'Add to PATH' option" -ForegroundColor White
Write-Host "3. After installation, restart PowerShell and run verification" -ForegroundColor White
Write-Host ""

# Launch installer
Write-Host "Launching installer..." -ForegroundColor Yellow
Start-Process msiexec.exe -ArgumentList "/i `"$installerPath`"" -Wait

Write-Host ""
Write-Host "Installer launched!" -ForegroundColor Green
Write-Host ""
Write-Host "After installation:" -ForegroundColor Cyan
Write-Host "1. Restart PowerShell" -ForegroundColor White
Write-Host "2. Run verification:" -ForegroundColor White
Write-Host "   node --version" -ForegroundColor Yellow
Write-Host "   npm --version" -ForegroundColor Yellow
Write-Host "3. Then start the project:" -ForegroundColor White
Write-Host "   cd d:\evidence-manager\frontend" -ForegroundColor Yellow
Write-Host "   .\start.ps1" -ForegroundColor Yellow
