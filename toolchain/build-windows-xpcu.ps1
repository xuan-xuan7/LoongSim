param(
    [string]$RuntimeDirectory,
    [string]$CompilerDirectory,
    [string]$CMakeExe
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = Split-Path $PSScriptRoot -Parent
if (-not $RuntimeDirectory) { $RuntimeDirectory = Join-Path $repo 'fpga/runtime/windows-x64' }
if (-not $CompilerDirectory) { $CompilerDirectory = Join-Path $repo 'local/tools/w64devkit/bin' }
if (-not $CMakeExe) { $CMakeExe = Join-Path $repo 'local/tools/native-build/cmake/data/bin/cmake.exe' }
if (-not (Test-Path (Join-Path $CompilerDirectory 'g++.exe')) -or -not (Test-Path $CMakeExe)) {
    throw 'Supply -CompilerDirectory (modern MinGW-w64 bin) and -CMakeExe (CMake 3.31+). See WINDOWS_FPGA_FLOW.md.'
}
$compiler = (Resolve-Path $CompilerDirectory).Path
$CMakeExe = (Resolve-Path $CMakeExe).Path
$runtime = (Resolve-Path $RuntimeDirectory).Path
$source = Join-Path $repo 'local/openfpgaloader-windows-source'
$build = Join-Path $repo 'local/openfpgaloader-windows-build'
$dependency = Join-Path $repo 'local/tools/native-usb/include'
$patch = Join-Path $PSScriptRoot 'patches/openfpgaloader-v1.2.0-xpcu.patch'
$commit = 'befbab4f646207dbaf1daf8b144dd980ad283dd5'
if (-not (Test-Path $source)) {
    & git clone --depth 1 --branch v1.2.0 https://github.com/trabucayre/openFPGALoader.git $source
    if ($LASTEXITCODE -ne 0) { throw 'openFPGALoader clone failed' }
}
if ((& git -C $source rev-parse HEAD) -ne $commit) { throw 'Unexpected openFPGALoader source revision' }
& git -C $source apply --reverse --check $patch 2>$null
if ($LASTEXITCODE -ne 0) {
    & git -C $source apply --check $patch
    if ($LASTEXITCODE -ne 0) { throw 'Source does not match the locked patch' }
    & git -C $source apply $patch
    if ($LASTEXITCODE -ne 0) { throw 'Failed to apply the XPCU compatibility patch' }
}
New-Item -ItemType Directory -Force $dependency | Out-Null
$header = Join-Path $dependency 'libusb.h'
if (-not (Test-Path $header)) {
    Invoke-WebRequest https://raw.githubusercontent.com/libusb/libusb/v1.0.29/libusb/libusb.h -OutFile $header
}
if ((Get-FileHash $header -Algorithm SHA256).Hash -ne '8C77E192D53960966C42D5F6EF204E3BE88C9FF3CA431ACDD5C6039689B3FE0B') {
    throw 'Unexpected libusb header SHA256'
}
$previousPath = $env:PATH
try {
    $env:PATH = $compiler + ';' + $previousPath
    & $CMakeExe -S $source -B $build -G 'MinGW Makefiles' `
        "-DCMAKE_CXX_COMPILER=$compiler/g++.exe" "-DCMAKE_MAKE_PROGRAM=$compiler/make.exe" `
        '-DENABLE_CABLE_ALL=OFF' '-DENABLE_VENDORS_ALL=OFF' '-DENABLE_XILINX_SUPPORT=ON' `
        '-DENABLE_XILINX_PLATFORM_CABLE_USB=ON' '-DENABLE_USB_SCAN=ON' '-DUSE_PKGCONFIG=OFF' `
        '-DISE_PATH=' '-DVIVADO_PATH=' '-DCMAKE_EXE_LINKER_FLAGS=-static-libgcc -static-libstdc++' `
        "-DLIBUSB_INCLUDE_DIRS=$dependency" "-DLIBUSB_LIBRARIES=$runtime/oss-cad-suite/lib/libusb-1.0.dll"
    if ($LASTEXITCODE -ne 0) { throw 'XPCU configure failed' }
    & $CMakeExe --build $build -j 6
    if ($LASTEXITCODE -ne 0) { throw 'XPCU build failed' }
} finally { $env:PATH = $previousPath }
$target = Join-Path $runtime 'loongsim'
New-Item -ItemType Directory -Force (Join-Path $target 'bin'), (Join-Path $target 'licenses') | Out-Null
$exe = Join-Path $target 'bin/openFPGALoader-xpcu.exe'
Copy-Item (Join-Path $build 'openFPGALoader.exe') $exe -Force
Copy-Item (Join-Path $source 'LICENSE') (Join-Path $target 'licenses/openFPGALoader-Apache-2.0.txt') -Force
Copy-Item (Join-Path $PSScriptRoot 'licenses/*.txt') (Join-Path $target 'licenses') -Force
Copy-Item $patch (Join-Path $target 'openfpgaloader-v1.2.0-xpcu.patch') -Force
@{
    name='LoongSim XPCU compatibility helper'; upstream='https://github.com/trabucayre/openFPGALoader'
    sourceCommit=$commit; patchSha256=(Get-FileHash $patch).Hash.ToLowerInvariant()
    binarySha256=(Get-FileHash $exe).Hash.ToLowerInvariant()
    compiler=(& (Join-Path $compiler 'g++.exe') -dumpfullversion)
    features='Xilinx SRAM programming, USB scan; no compressed bitstreams'
} | ConvertTo-Json | Set-Content (Join-Path $target 'BUILD-INFO.json') -Encoding utf8
@'
openFPGALoader v1.2.0, Apache-2.0, https://github.com/trabucayre/openFPGALoader
Modified by LoongSim: add 03fd:0007/0008 cable identities; retain running FX2
firmware after validating the Xilinx protocol, including reopening the same PID
after a RAM firmware load. Firmware is not redistributed.
This helper dynamically links the libusb DLL from the original OSS CAD Suite.
See the included patch, upstream license and BUILD-INFO.json.
'@ | Set-Content (Join-Path $target 'NOTICE.txt') -Encoding utf8
Write-Output "Built $exe"
