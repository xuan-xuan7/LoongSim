param(
    [string]$Java11Home,
    [string]$Java17Home,
    [string]$WixHome
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repo = $PSScriptRoot
$tools = Join-Path $repo 'local\tools'
$work = Join-Path $repo 'local\windows-package'
$dist = Join-Path $repo 'dist'
New-Item -ItemType Directory -Force $tools, $work, $dist | Out-Null

function Get-PortableTool {
    param([string]$Name, [string]$Url, [string]$Exe)

    $destination = Join-Path $tools $Name
    $existing = if (Test-Path $destination) {
        Get-ChildItem $destination -Recurse -Filter (Split-Path $Exe -Leaf) -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName.EndsWith($Exe, [StringComparison]::OrdinalIgnoreCase) } |
            Select-Object -First 1
    }
    if (-not $existing) {
        $archive = Join-Path $tools "$Name.zip"
        if (-not (Test-Path $archive)) {
            Write-Host "Downloading $Name..."
            Invoke-WebRequest -Uri $Url -OutFile $archive
        }
        New-Item -ItemType Directory -Force $destination | Out-Null
        Expand-Archive -LiteralPath $archive -DestinationPath $destination -Force
    }
    $result = Get-ChildItem $destination -Recurse -Filter (Split-Path $Exe -Leaf) |
        Where-Object { $_.FullName.EndsWith($Exe, [StringComparison]::OrdinalIgnoreCase) } |
        Select-Object -First 1
    if (-not $result) { throw "Could not find $Exe in $destination" }
    return (Split-Path (Split-Path $result.FullName -Parent) -Parent)
}

if (-not $Java11Home) {
    $Java11Home = Get-PortableTool 'temurin11' 'https://api.adoptium.net/v3/binary/latest/11/ga/windows/x64/jdk/hotspot/normal/eclipse' 'bin\java.exe'
}
if (-not $Java17Home) {
    $Java17Home = Get-PortableTool 'temurin17' 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' 'bin\jpackage.exe'
}
if (-not $WixHome) {
    $wixArchive = Join-Path $tools 'wix314-binaries.zip'
    $WixHome = Join-Path $tools 'wix314'
    if (-not (Test-Path (Join-Path $WixHome 'candle.exe'))) {
        if (-not (Test-Path $wixArchive)) {
            Write-Host 'Downloading WiX 3.14.1...'
            Invoke-WebRequest -Uri 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip' -OutFile $wixArchive
        }
        Expand-Archive -LiteralPath $wixArchive -DestinationPath $WixHome -Force
    }
}

foreach ($file in @((Join-Path $Java11Home 'bin\java.exe'), (Join-Path $Java17Home 'bin\jpackage.exe'), (Join-Path $WixHome 'candle.exe'), (Join-Path $WixHome 'light.exe'))) {
    if (-not (Test-Path $file)) { throw "Required tool missing: $file" }
}

$env:JAVA_HOME = $Java11Home
$env:PATH = "$(Join-Path $Java11Home 'bin');$WixHome;$env:PATH"
Push-Location (Join-Path $repo 'logisim')
try {
    & .\gradlew.bat --no-daemon shadowJar
    if ($LASTEXITCODE -ne 0) { throw 'Gradle shadowJar failed' }
} finally {
    Pop-Location
}

$jar = Get-ChildItem (Join-Path $repo 'logisim\build\libs') -Filter '*-all.jar' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { throw 'Shadow JAR not found' }

# Use the checked-in multi-resolution icon for the native launcher and shortcuts.
$icon = Join-Path $repo 'logisim\src\main\resources\resources\logisim\img\LoongSim.ico'
if (-not (Test-Path $icon)) { throw "Application icon missing: $icon" }

$imageDest = Join-Path $work 'image'
if (Test-Path $imageDest) { Remove-Item -LiteralPath $imageDest -Recurse -Force }
New-Item -ItemType Directory -Force $imageDest | Out-Null
$jpackage = Join-Path $Java17Home 'bin\jpackage.exe'
& $jpackage --type app-image --name LoongSim --app-version 1.0.0 --vendor 'LoongSim' `
    --description 'LoongSim digital logic circuit editor and simulator' `
    --input $jar.DirectoryName --main-jar $jar.Name --main-class com.cburch.logisim.Main `
    --icon $icon --dest $imageDest
if ($LASTEXITCODE -ne 0) { throw 'jpackage app-image failed' }

$image = Join-Path $imageDest 'LoongSim'
& $jpackage --type exe --name LoongSim --app-version 1.0.0 --vendor 'LoongSim' `
    --app-image $image --icon $icon --dest $dist --temp (Join-Path $work 'installer-temp') `
    --win-menu --win-shortcut --win-dir-chooser --win-per-user-install `
    --license-file (Join-Path $repo 'logisim\LICENSE.md')
if ($LASTEXITCODE -ne 0) { throw 'jpackage installer failed' }

Get-ChildItem $dist -Filter 'LoongSim*.exe' | Select-Object FullName, Length
