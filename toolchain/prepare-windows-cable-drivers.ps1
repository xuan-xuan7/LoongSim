param(
    [Parameter(Mandatory=$true)][string]$WorkDirectory,
    [string]$FtdiDriverDirectory,
    [string]$XilinxDriverDirectory
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = Split-Path $PSScriptRoot -Parent
$compiler = Join-Path $repo 'local/tools/w64devkit/bin/g++.exe'
if (!(Test-Path -LiteralPath $compiler -PathType Leaf)) {
    throw 'Install w64devkit to build the Windows cable driver installer helper.'
}

$stage = Join-Path $WorkDirectory 'cable-drivers'
$exe = Join-Path $stage 'LoongSimCableDrivers.exe'
New-Item -ItemType Directory -Path $stage | Out-Null
& $compiler -O2 -municode -static (Join-Path $PSScriptRoot 'native/windows-cable-drivers.cpp') -o $exe
if ($LASTEXITCODE -ne 0) { throw 'Windows cable driver setup compilation failed.' }

if (!$FtdiDriverDirectory) {
    $cache = Join-Path $repo 'local/toolchain-cache'
    New-Item -ItemType Directory -Path $cache -Force | Out-Null
    $driverZip = Join-Path $cache 'CDM-v2.12.36.20-WHQL-Certified.zip'
    if (!(Test-Path -LiteralPath $driverZip -PathType Leaf)) {
        $url = 'https://ftdichip.com/wp-content/uploads/2025/03/CDM-v2.12.36.20-WHQL-Certified.zip'
        $newDownload = Join-Path $stage 'ftdi-download.zip'
        Invoke-WebRequest -Uri $url -OutFile $newDownload -TimeoutSec 45
        Move-Item -LiteralPath $newDownload -Destination $driverZip
    }
    $expanded = Join-Path $stage 'ftdi-expanded'
    Expand-Archive -LiteralPath $driverZip -DestinationPath $expanded
    $inf = @(Get-ChildItem -LiteralPath $expanded -Filter 'ftdibus.inf' -File -Recurse)
    if ($inf.Count -ne 1) { throw 'Expected one ftdibus.inf in the official FTDI driver archive.' }
    $FtdiDriverDirectory = $inf[0].DirectoryName
}

$payloads = [Collections.Generic.List[string]]::new()
function Add-DriverPayloads([string]$Directory, [string]$Family, [string]$InfFile, [string]$CatalogFile) {
    $root = (Resolve-Path -LiteralPath $Directory).Path
    if (!(Test-Path -LiteralPath (Join-Path $root $InfFile) -PathType Leaf)) {
        throw "Missing driver manifest: $InfFile"
    }
    $catalog = Get-AuthenticodeSignature -LiteralPath (Join-Path $root $CatalogFile)
    if ($catalog.Status -ne 'Valid') { throw "Invalid signature on $CatalogFile ($($catalog.Status))" }
    # Do not distribute the vendor's interactive installer scripts.
    foreach ($file in (Get-ChildItem -LiteralPath $root -File -Recurse | Where-Object Extension -NotIn '.cmd','.bat','.exe')) {
        $relative = $file.FullName.Substring($root.Length + 1)
        $name = [Security.SecurityElement]::Escape("drivers\$Family\$relative")
        $source = [Security.SecurityElement]::Escape($file.FullName)
        $payloads.Add("<Payload Name=""$name"" SourceFile=""$source"" />")
    }
}

Add-DriverPayloads $FtdiDriverDirectory 'ftdi' 'ftdibus.inf' 'ftdibus.cat'
if ($XilinxDriverDirectory) {
    Add-DriverPayloads $XilinxDriverDirectory 'xilinx' 'xpcwinusb.inf' 'xpcwinusb.cat'
}
$package = [Security.SecurityElement]::Escape($exe)
$payloadXml = $payloads -join [Environment]::NewLine
@"
<ExePackage Id="LoongSimCableDrivers" SourceFile="$package" PerMachine="yes"
            DetectCondition="0" Permanent="yes" Vital="yes" Cache="no">
    <ExitCode Value="3010" Behavior="scheduleReboot" />
    $payloadXml
</ExePackage>
"@
