param(
    [string]$JavaHome,
    [string]$RuntimeDirectory,
    [string]$SimulationToolsDirectory,
    [string]$ResultsDirectory,
    [switch]$UnitOnly
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repo=Split-Path $PSScriptRoot -Parent
if(!$JavaHome){
    $candidate=Get-ChildItem -LiteralPath (Join-Path $repo 'local/tools/temurin11') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    $JavaHome=if($candidate){$candidate.FullName}else{$env:JAVA_HOME}
}
if(!$JavaHome -or !(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/javac.exe'))){throw 'Pass -JavaHome with a JDK 11 installation.'}
if(!$RuntimeDirectory){$RuntimeDirectory=Join-Path $repo 'fpga/runtime/windows-x64'}
if(!$SimulationToolsDirectory){$SimulationToolsDirectory=Join-Path $repo 'fpga/runtime/windows-x64/oss-cad-suite'}
if(!$ResultsDirectory){$ResultsDirectory=Join-Path $repo ('local/fpga-regression-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))}
$ResultsDirectory=[IO.Path]::GetFullPath($ResultsDirectory)
if(Test-Path -LiteralPath $ResultsDirectory){throw 'Select a new results directory so previous evidence is preserved.'}
if(!$UnitOnly){
    $RuntimeDirectory=(Resolve-Path -LiteralPath $RuntimeDirectory).Path
    $SimulationToolsDirectory=(Resolve-Path -LiteralPath $SimulationToolsDirectory).Path
    foreach($path in @('oss-cad-suite/bin/yosys.exe','openxc7/bin/nextpnr-xilinx.exe','openxc7/bin/xc7frames2bit.exe')){
        if(!(Test-Path -LiteralPath (Join-Path $RuntimeDirectory $path))){throw "Missing native runtime tool: $path"}
    }
    foreach($name in @('iverilog.exe','vvp.exe')){
        if(!(Test-Path -LiteralPath (Join-Path $SimulationToolsDirectory "bin/$name"))){throw 'Prepare development-side Icarus or pass -SimulationToolsDirectory. It is not part of the application package.'}
    }
}
New-Item -ItemType Directory -Path $ResultsDirectory | Out-Null
$keys=@('JAVA_HOME','LOONGSIM_FPGA_RUNTIME','LOONGSIM_FPGA_TEST_SIMULATOR','LOONGSIM_RUN_FPGA_INTEGRATION','LOONGSIM_FPGA_SMOKE_DIRECTORY','PYTHONDONTWRITEBYTECODE')
$previous=@{};foreach($key in $keys){$previous[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
$started=Get-Date
Push-Location $repo
try {
    $env:JAVA_HOME=(Resolve-Path -LiteralPath $JavaHome).Path
    $env:LOONGSIM_FPGA_RUNTIME=$RuntimeDirectory
    $env:LOONGSIM_FPGA_TEST_SIMULATOR=$SimulationToolsDirectory
    $env:LOONGSIM_RUN_FPGA_INTEGRATION=if($UnitOnly){'0'}else{'1'}
    $env:LOONGSIM_FPGA_SMOKE_DIRECTORY=Join-Path $ResultsDirectory 'projects'
    $env:PYTHONDONTWRITEBYTECODE='1'
    # Only simulation, synthesis, place/route and .bit generation. Never JTAG/program/flash.
    & (Join-Path $repo 'logisim/gradlew.bat') -p logisim --no-daemon cleanTest test 2>&1 |
        Tee-Object -FilePath (Join-Path $ResultsDirectory 'gradle.log')
    $code=$LASTEXITCODE
    $reports=@(Get-ChildItem -LiteralPath (Join-Path $repo 'logisim/build/test-results/test') -Filter 'TEST-*.xml')
    $tests=0;$failures=0;$errors=0;$skipped=0;$failed=[Collections.Generic.List[string]]::new()
    foreach($file in $reports){
        [xml]$xml=Get-Content -LiteralPath $file.FullName -Raw
        $tests += [int]$xml.testsuite.tests;$failures += [int]$xml.testsuite.failures
        $errors += [int]$xml.testsuite.errors;$skipped += [int]$xml.testsuite.skipped
        foreach($case in $xml.testsuite.SelectNodes('testcase')){
            if($case.SelectSingleNode('failure') -or $case.SelectSingleNode('error')){$failed.Add($xml.testsuite.name+'.'+$case.name)}
        }
    }
    $summary=[ordered]@{
        started=$started.ToString('o');finished=(Get-Date).ToString('o')
        sourceCommit=(git rev-parse HEAD).Trim();uncommittedChanges=[bool](git status --porcelain)
        nativeToolsEnabled=(!$UnitOnly);runtime=$RuntimeDirectory;simulationTools=$SimulationToolsDirectory
        tests=$tests;failures=$failures;errors=$errors;skipped=$skipped;failedTests=@($failed.ToArray())
        gradleExitCode=$code;hardwareProgrammed=$false
    }
    [IO.File]::WriteAllText((Join-Path $ResultsDirectory 'summary.json'),($summary|ConvertTo-Json -Depth 5),[Text.UTF8Encoding]::new($false))
    Copy-Item -LiteralPath (Join-Path $repo 'logisim/build/test-results/test') -Destination (Join-Path $ResultsDirectory 'junit') -Recurse
    Copy-Item -LiteralPath (Join-Path $repo 'logisim/build/reports/tests/test') -Destination (Join-Path $ResultsDirectory 'html') -Recurse
    Get-ChildItem -LiteralPath (Join-Path $repo 'logisim/src') -Recurse -File | ForEach-Object {
        [pscustomobject]@{path=$_.FullName.Substring($repo.Length+1);sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
    } | Export-Csv -LiteralPath (Join-Path $ResultsDirectory 'source-sha256.csv') -NoTypeInformation -Encoding utf8
    Get-ChildItem -LiteralPath $ResultsDirectory -Filter design.bit -Recurse -File | ForEach-Object {
        [pscustomobject]@{path=$_.FullName.Substring($ResultsDirectory.Length+1);bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
    } | Export-Csv -LiteralPath (Join-Path $ResultsDirectory 'bitstreams-sha256.csv') -NoTypeInformation -Encoding utf8
    $summary|ConvertTo-Json -Depth 5
    if($code -ne 0 -or $failures -gt 0 -or $errors -gt 0 -or (!$UnitOnly -and $skipped -gt 0) -or $tests -eq 0){
        throw "Regression checks failed or unexpectedly skipped. See $ResultsDirectory"
    }
} finally {
    Pop-Location
    foreach($key in $keys){[Environment]::SetEnvironmentVariable($key,$previous[$key],'Process')}
}
