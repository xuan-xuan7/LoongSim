param(
    [Parameter(Mandatory=$true)][string]$SourceDirectory,
    [Parameter(Mandatory=$true)][string]$DestinationDirectory
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$source = (Resolve-Path -LiteralPath $SourceDirectory).Path.TrimEnd('\','/')
$destination = [IO.Path]::GetFullPath($DestinationDirectory).TrimEnd('\','/')
if (Test-Path -LiteralPath $destination) { throw 'Select a new destination; existing runtimes are never modified.' }
if ($destination.StartsWith($source + '\', [StringComparison]::OrdinalIgnoreCase) -or
    $source.StartsWith($destination + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Source and destination must be separate runtime directories.'
}
$required = @(
    'runtime.properties', 'windows-tools.lock.json',
    'oss-cad-suite/bin/yosys.exe', 'oss-cad-suite/bin/yosys-abc.exe',
    'oss-cad-suite/bin/openFPGALoader.exe', 'oss-cad-suite/lib/python3.exe',
    'oss-cad-suite/share/yosys/xilinx/cells_sim.v',
    'openxc7/bin/nextpnr-xilinx.exe', 'openxc7/bin/xc7frames2bit.exe',
    'openxc7/libexec/fasm2frames',
    'openxc7/share/nextpnr/himbaechel/xilinx/chipdb-xc7a200t.bin',
    'openxc7/share/nextpnr/external/prjxray-db/artix7/xc7a200t/tilegrid.json',
    'openxc7/share/nextpnr/external/prjxray-db/artix7/xc7a200tfbg676-1/part.yaml',
    'openxc7/share/nextpnr/external/prjxray-db/artix7/mapping/parts.yaml',
    'openxc7/share/nextpnr/external/prjxray-db/artix7/mapping/devices.yaml',
    'loongsim/bin/openFPGALoader-xpcu.exe', 'loongsim/BUILD-INFO.json'
)
foreach ($path in $required) {
    if (!(Test-Path -LiteralPath (Join-Path $source $path) -PathType Leaf)) {
        throw "Incomplete source runtime: $path"
    }
}
if (!(Get-Content -LiteralPath (Join-Path $source 'runtime.properties') |
    Where-Object { $_ -eq 'device=xc7a200tfbg676-1' })) { throw 'Runtime is not for the A7 target.' }

$otherYosysFamilies = @('achronix', 'analogdevices', 'anlogic', 'ecp5', 'efinix',
    'fabulous', 'gatemate', 'gowin', 'ice40', 'intel', 'intel_alm', 'lattice',
    'microchip', 'nanoxplore', 'nexus', 'quicklogic', 'sf2')
$ossPrograms = @('yosys.exe', 'yosys-abc.exe', 'openFPGALoader.exe')
$openxc7Programs = @('nextpnr-xilinx.exe', 'xc7frames2bit.exe')
function Get-ExclusionReason([string]$path) {
    # Preserve license notices even inside an excluded vendor data directory.
    if ([IO.Path]::GetFileName($path) -match '^(LICENSE|COPYING|NOTICE|COPYRIGHT)([._-].*)?$') { return $null }
    if ($path -match '^oss-cad-suite/bin/(nextpnr-|ice|ecp|gowin_|gmpack\.|gmunpack\.|fujprog\.|prjoxide\.)') {
        return 'Other FPGA placement, bitstream or programming tool'
    }
    if ($path -match '^oss-cad-suite/share/(icebox|trellis|nextpnr)/') {
        return 'Other FPGA device database'
    }
    if ($path -match '^oss-cad-suite/share/yosys/([^/]+)/' -and $Matches[1] -in $otherYosysFamilies) {
        return 'Other FPGA synthesis technology library'
    }
    if ($path -match '^openxc7/share/nextpnr/himbaechel/xilinx/(chipdb-[^/]+\.bin)$' -and
        $Matches[1] -ne 'chipdb-xc7a200t.bin') { return 'Other Xilinx device chip database' }
    if ($path -match '^openxc7/share/nextpnr/external/prjxray-db/(spartan7|zynq7)/') {
        return 'Other Xilinx family bitstream database'
    }
    if ($path -match '^openxc7/share/nextpnr/external/prjxray-db/artix7/(xc7[^/]+)/' -and
        $Matches[1] -notin @('xc7a200t', 'xc7a200tfbg676-1')) {
        return 'Other Artix device or package database'
    }
    if ($path -match '^openxc7/share/nextpnr/external/prjxray-db/artix7/gridinfo/grid-(?!xc7a200tfbg676-1-db\.txt$)[^/]+') {
        return 'Other Artix device grid data'
    }
    # LoongSim invokes these programs directly; DLLs are retained for the shared
    # dependency pass. Do not use this rule for Python or X-Ray module directories.
    if ($path -match '^oss-cad-suite/bin/([^/]+)$' -and
        $Matches[1] -notin $ossPrograms -and [IO.Path]::GetExtension($path) -ne '.dll') {
        return 'Unused standalone tool or launcher'
    }
    if ($path -match '^openxc7/bin/([^/]+)$' -and $Matches[1] -notin $openxc7Programs -and
        [IO.Path]::GetExtension($path) -ne '.dll') {
        return 'Unused Xilinx inspection or patch tool'
    }
    if ($path -match '^openxc7/libexec/([^/]+)$' -and $Matches[1] -ne 'fasm2frames') {
        return 'Unused Xilinx inspection or patch script'
    }
    if ($path -match '^oss-cad-suite/lib/ivl/' -or
        $path -match '^oss-cad-suite/include/iverilog/') {
        return 'Unused standalone Verilog simulator backend'
    }
    if ($path -match '^oss-cad-suite/share/(cvc4|gtkwave3|mcy|openocd|verilator)/') {
        return 'Unused standalone tool resources'
    }
    if ($path -match '^oss-cad-suite/share/yosys/plugins/(eqy_[^/]+|slang)\.so$' -or
        $path -match '^oss-cad-suite/share/yosys/python3/') {
        return 'Unused formal verification or alternate HDL frontend extension'
    }
    if ($path -match '^oss-cad-suite/examples/') { return 'Upstream standalone tool examples' }
    if ($path -match '^oss-cad-suite/lib/python3\.11/(test|idlelib|tkinter|turtledemo|distutils|lib2to3|unittest|ensurepip|pydoc_data|venv|site-packages)/') {
        return 'Unused Python test, GUI, packaging or third-party modules'
    }
    if ($path -match '(^|/)__pycache__/') { return 'Regenerable Python bytecode cache' }
    if ($path -match '^oss-cad-suite/lib/(tcl8\.6|qt6|gdk-pixbuf-2\.0|pkgconfig)/') {
        return 'Unused Tcl script library, GUI plugin or pkg-config metadata'
    }
    if ($path -match '^oss-cad-suite/include/python3\.11/') { return 'Unused Python C development headers' }
    if ($path -match '^oss-cad-suite/share/(icons|man|glib-2\.0|mime|doc)/') {
        return 'Unused GUI icons, manpages or desktop metadata'
    }
    return $null
}

$files = @(Get-ChildItem -LiteralPath $source -File -Recurse)
$removed = [Collections.Generic.List[object]]::new()
$created = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
[long]$sourceBytes = 0
[long]$keptBytes = 0
[int]$keptCount = 0
New-Item -ItemType Directory -Path $destination -Force | Out-Null
foreach ($file in $files) {
    $relative = $file.FullName.Substring($source.Length + 1).Replace('\','/')
    $sourceBytes += $file.Length
    $reason = Get-ExclusionReason $relative
    if ($reason) {
        $removed.Add([ordered]@{path=$relative; bytes=$file.Length; reason=$reason})
        continue
    }
    $target = Join-Path $destination $relative
    $parent = Split-Path $target -Parent
    if ($created.Add($parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
    Copy-Item -LiteralPath $file.FullName -Destination $target
    $keptBytes += $file.Length
    $keptCount++
}
foreach ($path in $required) {
    if (!(Test-Path -LiteralPath (Join-Path $destination $path) -PathType Leaf)) {
        throw "A7 packaging removed a required runtime file: $path"
    }
}
$summary = [ordered]@{
    schema=1; profile='loongson-a7-flow-tools-v3'; device='xc7a200tfbg676-1'
    rulesSha256=(Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant()
    sourceFileCount=$files.Count; keptFileCount=$keptCount; removedFileCount=$removed.Count
    sourceBytes=$sourceBytes; keptBytes=$keptBytes; removedBytes=($sourceBytes-$keptBytes)
}
$manifest = [ordered]@{summary=$summary; removedFiles=@($removed.ToArray())}
[IO.File]::WriteAllText((Join-Path $destination 'loongsim/A7-PACKAGING.json'),
    ($manifest | ConvertTo-Json -Depth 6),[Text.UTF8Encoding]::new($false))
Write-Host ('A7 runtime: {0:N1} MiB -> {1:N1} MiB; excluded {2} files.' -f
    ($sourceBytes/1MB),($keptBytes/1MB),$removed.Count)
[pscustomobject]$summary
