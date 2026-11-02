param(
    [string]$Java17Home,
    [string]$WixHome,
    [string]$JarPath,
    [string]$AppImagePath,
    [string]$OutputDirectory,
    [string]$WorkDirectory
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = Split-Path $PSScriptRoot -Parent
$versionLine = Get-Content (Join-Path $repo 'logisim/gradle.properties') | Where-Object { $_ -match '^version\s*=' }
$jarVersion = ($versionLine -split '=',2)[1].Trim()
$appVersion = $jarVersion -replace '-.*$',''
if (-not $Java17Home) {
    $Java17Home = (Get-ChildItem (Join-Path $repo 'local/tools/temurin17') -Directory | Select-Object -First 1).FullName
}
if (-not $WixHome) { $WixHome = Join-Path $repo 'local/tools/wix314' }
$Java17Home = (Resolve-Path -LiteralPath $Java17Home).Path
$WixHome = (Resolve-Path -LiteralPath $WixHome).Path
$jpackage = Join-Path $Java17Home 'bin/jpackage.exe'
foreach ($required in @($jpackage, (Join-Path $WixHome 'candle.exe'), (Join-Path $WixHome 'light.exe'))) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Required packaging tool missing: $required" }
}
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $repo "dist/LoongSim-$appVersion" }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$prefix = "LoongSim-$appVersion-master-windows-x64"
$exe = Join-Path $OutputDirectory "$prefix-setup-unsigned.exe"
$msi = Join-Path $OutputDirectory "$prefix-unsigned.msi"
if ((Test-Path -LiteralPath $exe) -or (Test-Path -LiteralPath $msi)) { throw 'Installer already exists; use a new output directory.' }
if (-not $AppImagePath) {
    & (Join-Path $PSScriptRoot 'package-windows-fpga.ps1') -Java17Home $Java17Home -JarPath $JarPath -OutputDirectory $OutputDirectory
    $AppImagePath = Join-Path $OutputDirectory 'LoongSim'
}
$AppImagePath = (Resolve-Path -LiteralPath $AppImagePath).Path
$buildInfo = Get-Content (Join-Path $AppImagePath 'app/LoongSim-build-info.json') -Raw | ConvertFrom-Json
if ($buildInfo.version -ne $appVersion) { throw 'Application image and installer versions differ.' }
$jar = Join-Path $AppImagePath "app/LoongSim-$jarVersion-all.jar"
if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $buildInfo.jarSha256) {
    throw 'Packaged application JAR does not match its build record.'
}
# WiX 3 still resolves source files through MAX_PATH-limited APIs. jpackage adds
# images/win-msi.image/LoongSim/... below this root, so use a short, isolated stage.
if (-not $WorkDirectory) { $WorkDirectory = Join-Path ($env:SystemDrive + '/LS') ([Guid]::NewGuid().ToString('N').Substring(0,8)) }
$work = [IO.Path]::GetFullPath($WorkDirectory)
if (Test-Path -LiteralPath $work) { throw 'Installer staging directory already exists; select a new -WorkDirectory.' }
$resources = Join-Path $work 'resources'
$installer = Join-Path $work 'installer'
New-Item -ItemType Directory -Force $resources,$installer | Out-Null
# jpackage 17 defaults to one CAB. Split the 3+ GB toolchain into bounded, embedded cabinets.
# The template comes from the exact JDK used to build this installer.
Push-Location $work
try {
    & (Join-Path $Java17Home 'bin/jar.exe') xf (Join-Path $Java17Home 'jmods/jdk.jpackage.jmod') 'classes/jdk/jpackage/internal/resources/main.wxs'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot extract the jpackage WiX template.' }
} finally { Pop-Location }
$template = Get-Content (Join-Path $work 'classes/jdk/jpackage/internal/resources/main.wxs') -Raw
$media = '<Media Id="1" Cabinet="Data.cab" EmbedCab="yes" />'
if (-not $template.Contains($media)) { throw 'Unexpected jpackage WiX template; review cabinet configuration.' }
$template = $template.Replace($media, '<MediaTemplate EmbedCab="yes" MaximumUncompressedMediaSize="500" CompressionLevel="high" />')
[IO.File]::WriteAllText((Join-Path $resources 'main.wxs'),$template,[Text.UTF8Encoding]::new($false))
$oldPath = $env:PATH
try {
    $env:PATH = "$WixHome;$env:PATH"
    & $jpackage --type msi --name LoongSim --app-version $appVersion --vendor LoongSim `
        --description 'LoongSim circuit design, simulation and integrated A7 FPGA tools' `
        --app-image $AppImagePath --dest $installer --temp (Join-Path $work 'jpackage') `
        --resource-dir $resources --win-menu --win-shortcut --win-dir-chooser --win-per-user-install `
        --win-upgrade-uuid 'e8a75d8d-0d16-4fde-89a9-4f2f562e7d35' `
        --license-file (Join-Path $repo 'logisim/LICENSE.md')
    if ($LASTEXITCODE -ne 0) { throw "MSI packaging failed; intermediate files: $work" }
} finally { $env:PATH = $oldPath }
$generatedMsi = @(Get-ChildItem -LiteralPath $installer -Filter '*.msi')
if ($generatedMsi.Count -ne 1) { throw 'Expected exactly one generated MSI.' }
Copy-Item -LiteralPath $generatedMsi[0].FullName -Destination $msi
$icon = Join-Path $repo 'logisim/src/main/resources/resources/logisim/img/LoongSim.ico'
$msiXml = [Security.SecurityElement]::Escape($msi)
$iconXml = [Security.SecurityElement]::Escape($icon)
$bundleSource = Join-Path $work 'bundle.wxs'
$bundle = @"
<?xml version="1.0" encoding="UTF-8"?>
<Wix xmlns="http://schemas.microsoft.com/wix/2006/wi" xmlns:bal="http://schemas.microsoft.com/wix/BalExtension">
  <Bundle Name="LoongSim" Version="$appVersion" Manufacturer="LoongSim" UpgradeCode="fcd641dc-cb49-45ab-a741-165c31503f6b" IconSourceFile="$iconXml">
    <BootstrapperApplicationRef Id="WixStandardBootstrapperApplication.HyperlinkLicense">
      <bal:WixStandardBootstrapperApplication LicenseUrl="https://github.com/xuan-xuan7/LoongSim/blob/master/logisim/LICENSE.md" />
    </BootstrapperApplicationRef>
    <Chain><MsiPackage SourceFile="$msiXml" DisplayInternalUI="yes" Vital="yes" /></Chain>
  </Bundle>
</Wix>
"@
[IO.File]::WriteAllText($bundleSource,$bundle,[Text.UTF8Encoding]::new($false))
$bundleObject = Join-Path $work 'bundle.wixobj'
& (Join-Path $WixHome 'candle.exe') -nologo -ext (Join-Path $WixHome 'WixBalExtension.dll') -out $bundleObject $bundleSource
if ($LASTEXITCODE -ne 0) { throw 'Installer bootstrapper compilation failed.' }
& (Join-Path $WixHome 'light.exe') -nologo -ext (Join-Path $WixHome 'WixBalExtension.dll') -out $exe $bundleObject
if ($LASTEXITCODE -ne 0) { throw 'Installer bootstrapper linking failed.' }
foreach ($file in @($exe,$msi)) {
    $hash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText("$file.sha256","$hash  $(Split-Path $file -Leaf)`n",[Text.Encoding]::ASCII)
}
$info = [ordered]@{
    platform='windows-x64'; version=$appVersion; branch='master'; signed=$false
    sourceCommit=$buildInfo.sourceCommit; uncommittedChanges=$buildInfo.uncommittedChanges
    jarSha256=$buildInfo.jarSha256; tools=$buildInfo.tools; xilinxCableHelper=$buildInfo.xilinxCableHelper
    installers=@(@($exe,$msi) | ForEach-Object {
        [ordered]@{name=(Split-Path $_ -Leaf); size=(Get-Item -LiteralPath $_).Length; sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant()}
    })
}
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'LoongSim-windows-build-info.json'),($info | ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
Get-Item -LiteralPath $exe,$msi | Select-Object FullName,Length
