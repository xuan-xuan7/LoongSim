$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repo = Split-Path $PSScriptRoot -Parent
$jar = Get-ChildItem (Join-Path $repo 'logisim\build\libs') -Filter '*-all.jar' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) {
    throw 'Build the preview JAR first: cd logisim; .\gradlew.bat shadowJar'
}

# Prefer the portable Java already used to build this workspace.
$portableJava = Get-ChildItem (Join-Path $repo 'local\tools\temurin11') -Directory -ErrorAction SilentlyContinue |
    ForEach-Object { Join-Path $_.FullName 'bin\javaw.exe' } |
    Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
$java = $portableJava
if (-not $java -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\javaw.exe'
    if (Test-Path -LiteralPath $candidate) { $java = $candidate }
}
if (-not $java) {
    $command = Get-Command javaw.exe -ErrorAction SilentlyContinue
    if ($command) { $java = $command.Source }
}
if (-not $java) { throw 'Java 11 or newer is required. Set JAVA_HOME or install Java.' }

Start-Process -FilePath $java -ArgumentList @('-jar', ('"{0}"' -f $jar.FullName)) `
    -WorkingDirectory $repo -WindowStyle Normal | Out-Null
