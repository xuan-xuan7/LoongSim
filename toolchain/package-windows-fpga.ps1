param(
    [string]$Java17Home,
    [string]$JarPath,
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = Split-Path $PSScriptRoot -Parent
$runtime = Join-Path $repo 'fpga/runtime/windows-x64'
if (-not (Test-Path -LiteralPath (Join-Path $runtime 'runtime.properties'))) {
    throw 'First install the tools using toolchain/install-windows-tools.ps1'
}
if (-not (Test-Path -LiteralPath (Join-Path $runtime 'loongsim/BUILD-INFO.json')) -or
    -not (Test-Path -LiteralPath (Join-Path $runtime 'loongsim/bin/openFPGALoader-xpcu.exe'))) {
    throw 'Build the Xilinx cable compatibility helper using toolchain/build-windows-xpcu.ps1 first'
}
$versionLine = Get-Content (Join-Path $repo 'logisim/gradle.properties') | Where-Object { $_ -match '^version\s*=' }
$jarVersion = ($versionLine -split '=',2)[1].Trim()
$appVersion = $jarVersion -replace '-.*$',''
if (-not $JarPath) { $JarPath = Join-Path $repo "logisim/build/libs/LoongSim-$jarVersion-all.jar" }
$JarPath = (Resolve-Path -LiteralPath $JarPath).Path
if (-not $Java17Home) {
    $candidate = Get-ChildItem (Join-Path $repo 'local/tools/temurin17') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidate) { $Java17Home=$candidate.FullName } else { $Java17Home=$env:JAVA_HOME }
}
$jpackage = Join-Path $Java17Home 'bin/jpackage.exe'
if (-not (Test-Path -LiteralPath $jpackage)) { throw 'Pass -Java17Home with a JDK 17 installation' }
$work = Join-Path $repo ('local/fpga-pkg/' + [Guid]::NewGuid().ToString('N').Substring(0,8))
$inputDirectory = Join-Path $work 'input'
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $repo ('dist/Windows-FPGA-' + [Guid]::NewGuid().ToString('N')) }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath (Join-Path $OutputDirectory 'LoongSim')) { throw 'Output already exists; select a new output directory' }
New-Item -ItemType Directory -Force $inputDirectory, (Join-Path $inputDirectory 'fpga/runtime'), $OutputDirectory | Out-Null
Copy-Item -LiteralPath $JarPath -Destination $inputDirectory
Copy-Item -LiteralPath (Join-Path $repo 'fpga/boards') -Destination (Join-Path $inputDirectory 'fpga/boards') -Recurse
Copy-Item -LiteralPath (Join-Path $repo 'fpga/examples') -Destination (Join-Path $inputDirectory 'fpga/examples') -Recurse
Copy-Item -LiteralPath $runtime -Destination (Join-Path $inputDirectory 'fpga/runtime/windows-x64') -Recurse
$info = @{
    sourceCommit=(git -C $repo rev-parse HEAD)
    uncommittedChanges=[bool](git -C $repo status --porcelain --untracked-files=normal)
    jarSha256=(Get-FileHash -LiteralPath $JarPath -Algorithm SHA256).Hash.ToLowerInvariant()
    version=$appVersion
    tools=(Get-Content (Join-Path $PSScriptRoot 'windows-tools.lock.json') -Raw | ConvertFrom-Json)
    xilinxCableHelper=(Get-Content (Join-Path $runtime 'loongsim/BUILD-INFO.json') -Raw | ConvertFrom-Json)
}
$info | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $inputDirectory 'LoongSim-build-info.json') -Encoding utf8
& $jpackage --type app-image --name LoongSim --app-version $appVersion --vendor LoongSim `
    --input $inputDirectory --main-jar (Split-Path $JarPath -Leaf) --main-class com.cburch.logisim.Main `
    --icon (Join-Path $repo 'logisim/src/main/resources/resources/logisim/img/LoongSim.ico') `
    --java-options '-Dfile.encoding=UTF-8' --dest $OutputDirectory
if ($LASTEXITCODE -ne 0) { throw 'Windows FPGA application packaging failed' }
Copy-Item -LiteralPath (Join-Path $repo 'logisim/LICENSE.md') -Destination (Join-Path $OutputDirectory 'LoongSim/LICENSE-LoongSim.md')
Copy-Item -LiteralPath (Join-Path $repo 'toolchain/release/windows-fpga/README.txt') -Destination (Join-Path $OutputDirectory 'LoongSim/README.txt')
Write-Output (Join-Path $OutputDirectory 'LoongSim/LoongSim.exe')
