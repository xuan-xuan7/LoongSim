param(
    [string]$Java11Home,
    [string]$Java17Home,
    [string]$WixHome,
    [string]$AppVersion = '1.0.1',
    [string]$JarPath,
    [string]$CertificateThumbprint = $env:WINDOWS_CERTIFICATE_THUMBPRINT,
    [string]$SignToolPath,
    [switch]$RequireSignature
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = $PSScriptRoot
$tools = Join-Path $repo 'local\tools'
$work = Join-Path $repo ('local\windows-package\' + [Guid]::NewGuid().ToString('N'))
$dist = Join-Path $repo 'dist'
New-Item -ItemType Directory -Force $tools, $work, $dist | Out-Null

function Get-PortableTool {
    param([string]$Name, [string]$Url, [string]$Exe)
    $destination = Join-Path $tools $Name
    $result = Get-ChildItem $destination -Recurse -Filter (Split-Path $Exe -Leaf) -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName.EndsWith($Exe, [StringComparison]::OrdinalIgnoreCase) } | Select-Object -First 1
    if (-not $result) {
        $archive = Join-Path $tools "$Name.zip"
        if (-not (Test-Path -LiteralPath $archive)) { Invoke-WebRequest -Uri $Url -OutFile $archive }
        Expand-Archive -LiteralPath $archive -DestinationPath $destination -Force
        $result = Get-ChildItem $destination -Recurse -Filter (Split-Path $Exe -Leaf) |
            Where-Object { $_.FullName.EndsWith($Exe, [StringComparison]::OrdinalIgnoreCase) } | Select-Object -First 1
    }
    if (-not $result) { throw "Missing tool: $Exe" }
    return (Split-Path (Split-Path $result.FullName -Parent) -Parent)
}

function Sign-And-Verify {
    param([string]$Path)
    & $SignToolPath sign /sha1 $CertificateThumbprint /s My /fd SHA256 /tr 'http://timestamp.digicert.com' /td SHA256 $Path
    if ($LASTEXITCODE -ne 0) { throw "Signing failed: $Path" }
    & $SignToolPath verify /pa /all $Path
    if ($LASTEXITCODE -ne 0) { throw "Signature verification failed: $Path" }
    $signature = Get-AuthenticodeSignature -LiteralPath $Path
    if ($signature.Status -ne 'Valid' -or -not $signature.TimeStamperCertificate) { throw "A trusted, timestamped signature is required: $Path" }
}

if ($RequireSignature -and -not $CertificateThumbprint) { throw 'A Windows code-signing certificate is required.' }
if ($CertificateThumbprint) {
    if (-not $SignToolPath) {
        $SignToolPath = (Get-ChildItem 'C:\Program Files (x86)\Windows Kits\10\bin' -Recurse -Filter signtool.exe |
            Where-Object { $_.Directory.Name -eq 'x64' } | Sort-Object FullName -Descending | Select-Object -First 1).FullName
    }
    if (-not (Test-Path -LiteralPath $SignToolPath)) { throw 'Windows SDK SignTool is required.' }
    $cert = Get-Item -LiteralPath "Cert:\CurrentUser\My\$CertificateThumbprint"
    if (-not $cert.HasPrivateKey -or $cert.NotAfter -le (Get-Date)) { throw 'Signing certificate is unusable or expired.' }
}
if (-not $Java17Home) { $Java17Home = Get-PortableTool 'temurin17' 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' 'bin\jpackage.exe' }
if (-not $WixHome) {
    $WixHome = Join-Path $tools 'wix314'
    if (-not (Test-Path -LiteralPath (Join-Path $WixHome 'candle.exe'))) {
        $archive = Join-Path $tools 'wix314-binaries.zip'
        if (-not (Test-Path -LiteralPath $archive)) { Invoke-WebRequest -Uri 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip' -OutFile $archive }
        Expand-Archive -LiteralPath $archive -DestinationPath $WixHome -Force
    }
}
if (-not $JarPath) {
    if (-not $Java11Home) { $Java11Home = Get-PortableTool 'temurin11' 'https://api.adoptium.net/v3/binary/latest/11/ga/windows/x64/jdk/hotspot/normal/eclipse' 'bin\java.exe' }
    $env:JAVA_HOME = $Java11Home
    $env:PATH = "$Java11Home\bin;$WixHome;$env:PATH"
    Push-Location (Join-Path $repo 'logisim')
    try {
        & .\gradlew.bat --no-daemon shadowJar
        if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }
    } finally { Pop-Location }
    $JarPath = (Get-ChildItem (Join-Path $repo 'logisim\build\libs') -Filter '*-all.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}
$JarPath = (Resolve-Path -LiteralPath $JarPath).Path
$applicationVersion = & (Join-Path $Java17Home 'bin\java.exe') '-Djava.awt.headless=true' -jar $JarPath -version
if ($LASTEXITCODE -ne 0 -or ($applicationVersion -join "`n").Trim() -ne "LoongSim $AppVersion") {
    throw "Application JAR version does not match installer version $AppVersion. Rebuild the current source before packaging."
}
$env:PATH = "$WixHome;$env:PATH"
$jpackage = Join-Path $Java17Home 'bin\jpackage.exe'
$icon = Join-Path $repo 'logisim\src\main\resources\resources\logisim\img\LoongSim.ico'
$inputDir = Join-Path $work 'input'
$imageDir = Join-Path $work 'image'
$installerDir = Join-Path $work 'installer'
New-Item -ItemType Directory -Force $inputDir, $imageDir, $installerDir | Out-Null
Copy-Item -LiteralPath $JarPath -Destination $inputDir
$jarName = Split-Path $JarPath -Leaf
& $jpackage --type app-image --name LoongSim --app-version $AppVersion --vendor LoongSim `
    --input $inputDir --main-jar $jarName --main-class com.cburch.logisim.Main --icon $icon --dest $imageDir
if ($LASTEXITCODE -ne 0) { throw 'Application image build failed' }
$image = Join-Path $imageDir 'LoongSim'
$packagedJar = Join-Path $image ('app\' + $jarName)
if ((Get-FileHash -LiteralPath $packagedJar -Algorithm SHA256).Hash -ne
    (Get-FileHash -LiteralPath $JarPath -Algorithm SHA256).Hash) {
    throw 'Packaged application JAR does not match the verified build input.'
}
$launcher = Join-Path $image 'LoongSim.exe'
if ($CertificateThumbprint) { Sign-And-Verify $launcher }

# The MSI is signed before being embedded in the EXE bootstrapper.
& $jpackage --type msi --name LoongSim --app-version $AppVersion --vendor LoongSim --app-image $image `
    --dest $installerDir --win-menu --win-shortcut --win-dir-chooser --win-per-user-install `
    --win-upgrade-uuid 'e8a75d8d-0d16-4fde-89a9-4f2f562e7d35' --license-file (Join-Path $repo 'logisim\LICENSE.md')
if ($LASTEXITCODE -ne 0) { throw 'MSI build failed' }
$msi = (Get-ChildItem $installerDir -Filter '*.msi' | Select-Object -First 1).FullName
if ($CertificateThumbprint) { Sign-And-Verify $msi }
$msiXml = [Security.SecurityElement]::Escape($msi)
$iconXml = [Security.SecurityElement]::Escape($icon)
$bundleSource = Join-Path $work 'bundle.wxs'
@"
<?xml version="1.0" encoding="UTF-8"?>
<Wix xmlns="http://schemas.microsoft.com/wix/2006/wi" xmlns:bal="http://schemas.microsoft.com/wix/BalExtension">
  <Bundle Name="LoongSim" Version="$AppVersion" Manufacturer="LoongSim" UpgradeCode="fcd641dc-cb49-45ab-a741-165c31503f6b" IconSourceFile="$iconXml">
    <BootstrapperApplicationRef Id="WixStandardBootstrapperApplication.HyperlinkLicense">
      <bal:WixStandardBootstrapperApplication LicenseUrl="https://github.com/xuan-xuan7/LoongSim/blob/logisim-only/logisim/LICENSE.md" />
    </BootstrapperApplicationRef>
    <Chain><MsiPackage SourceFile="$msiXml" DisplayInternalUI="yes" Vital="yes" /></Chain>
  </Bundle>
</Wix>
"@ | Set-Content -LiteralPath $bundleSource -Encoding utf8
$bundleObject = Join-Path $work 'bundle.wixobj'
& (Join-Path $WixHome 'candle.exe') -nologo -ext WixBalExtension -out $bundleObject $bundleSource
if ($LASTEXITCODE -ne 0) { throw 'Bootstrapper compilation failed' }
$suffix = if ($CertificateThumbprint) { '' } else { '-unsigned' }
$prefix = "LoongSim-$AppVersion-logisim-only-windows-x64"
$exe = Join-Path $dist "$prefix-setup$suffix.exe"
& (Join-Path $WixHome 'light.exe') -nologo -ext WixBalExtension -out $exe $bundleObject
if ($LASTEXITCODE -ne 0) { throw 'Bootstrapper link failed' }
if ($CertificateThumbprint) {
    $engine = Join-Path $work 'engine.exe'
    & (Join-Path $WixHome 'insignia.exe') -ib $exe -o $engine
    if ($LASTEXITCODE -ne 0) { throw 'Burn engine extraction failed' }
    Sign-And-Verify $engine
    & (Join-Path $WixHome 'insignia.exe') -ab $engine $exe -o $exe
    if ($LASTEXITCODE -ne 0) { throw 'Signed Burn engine embedding failed' }
    Sign-And-Verify $exe
}
$msiDest = Join-Path $dist "$prefix$suffix.msi"
Copy-Item -LiteralPath $msi -Destination $msiDest -Force
foreach ($file in @($exe,$msiDest)) {
    $hash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $(Split-Path $file -Leaf)" | Set-Content -LiteralPath "$file.sha256" -Encoding ascii
}
$info = [ordered]@{ platform='windows-x64'; version=$AppVersion; sourceCommit=(git -C $repo rev-parse HEAD); jarSha256=(Get-FileHash $JarPath -Algorithm SHA256).Hash.ToLowerInvariant(); signed=[bool]$CertificateThumbprint; launcher=$launcher; image=$image; installers=@($exe,$msiDest) }
$info | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $dist "$prefix$suffix-build.json") -Encoding utf8
Get-Item $exe,$msiDest | Select-Object FullName,Length
