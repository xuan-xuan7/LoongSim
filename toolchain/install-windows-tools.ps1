param(
    [string]$Destination,
    [string]$CacheDirectory,
    [string]$CompilerDirectory,
    [string]$CMakeExe
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = Split-Path $PSScriptRoot -Parent
if (-not $Destination) { $Destination = Join-Path $repo 'fpga/runtime/windows-x64' }
if (-not $CacheDirectory) { $CacheDirectory = Join-Path $repo 'local/toolchain-cache' }
$Destination = [IO.Path]::GetFullPath($Destination)
$CacheDirectory = [IO.Path]::GetFullPath($CacheDirectory)
$lock = Get-Content (Join-Path $PSScriptRoot 'windows-tools.lock.json') -Raw | ConvertFrom-Json
if ($lock.openxc7.requiresOssCadSuite -ne $lock.ossCadSuite.version) { throw 'Incompatible locked tool versions' }
if (Test-Path -LiteralPath $Destination) { throw "Destination already exists; select a new directory: $Destination" }
New-Item -ItemType Directory -Force $CacheDirectory, (Split-Path $Destination -Parent) | Out-Null
$stage = Join-Path (Split-Path $Destination -Parent) ('windows-x64-stage-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $stage | Out-Null
foreach ($entry in @($lock.ossCadSuite, $lock.openxc7)) {
    $archive = Join-Path $CacheDirectory $entry.file
    if (-not (Test-Path -LiteralPath $archive)) {
        Write-Output "Downloading $($entry.file)"
        & curl.exe --fail --location --retry 3 --connect-timeout 30 --output ($archive + '.partial') $entry.url
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $($entry.file)" }
        Move-Item -LiteralPath ($archive + '.partial') -Destination $archive
    }
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) {
        throw "SHA256 mismatch; remove the incomplete archive and retry: $archive"
    }
    $target = if ($entry -eq $lock.openxc7) { Join-Path $stage 'openxc7' } else { $stage }
    New-Item -ItemType Directory -Force $target | Out-Null
    & tar.exe -xzf $archive -C $target
    if ($LASTEXITCODE -ne 0) { throw "Extraction failed: $($entry.file)" }
}
$required = @('oss-cad-suite/bin/yosys.exe', 'oss-cad-suite/bin/openFPGALoader.exe',
    'oss-cad-suite/lib/python3.exe', 'oss-cad-suite/bin/yosys-abc.exe', 'oss-cad-suite/share/yosys/xilinx/cells_sim.v',
    'openxc7/bin/nextpnr-xilinx.exe', 'openxc7/bin/xc7frames2bit.exe',
    'openxc7/libexec/fasm2frames', 'openxc7/share/nextpnr/himbaechel/xilinx/chipdb-xc7a200t.bin',
    'openxc7/share/nextpnr/external/prjxray-db/artix7/xc7a200tfbg676-1/part.yaml')
foreach ($file in $required) {
    if (-not (Test-Path -LiteralPath (Join-Path $stage $file) -PathType Leaf)) { throw "Missing runtime file: $file" }
}
$build = Get-Content (Join-Path $stage 'openxc7/BUILD-INFO.json') -Raw | ConvertFrom-Json
if ($build.'yosys-release-tag' -ne $lock.ossCadSuite.version -or $build.'release-tag' -ne $lock.openxc7.version) {
    throw 'Unexpected openXC7 build identity'
}
# Preserve the distributions, libraries, license files and build metadata together.
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'windows-tools.lock.json') -Destination (Join-Path $stage 'windows-tools.lock.json')
@('platform=windows-x64', "ossCadSuite.version=$($lock.ossCadSuite.version)",
    "openxc7.version=$($lock.openxc7.version)", "device=$($lock.device)") |
    Set-Content -LiteralPath (Join-Path $stage 'runtime.properties') -Encoding ascii
& (Join-Path $PSScriptRoot 'build-windows-xpcu.ps1') -RuntimeDirectory $stage -CompilerDirectory $CompilerDirectory -CMakeExe $CMakeExe
Move-Item -LiteralPath $stage -Destination $Destination
Write-Output "Installed LoongSim FPGA tools: $Destination"
Write-Output 'No system PATH, USB driver, or board configuration was changed.'
