param([string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
if (!$OutputDirectory) { $OutputDirectory = Join-Path $repo ('.codex-reference/multiplayer-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
$testRoot = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $testRoot | Out-Null
$launches = Get-Content -LiteralPath (Join-Path $repo 'build/multiplayer-launches.json') -Raw | ConvertFrom-Json
$serverDirectory = $launches.runMultiServer.workingDirectory
New-Item -ItemType Directory -Force -Path $serverDirectory | Out-Null
'eula=true' | Set-Content -LiteralPath (Join-Path $serverDirectory 'eula.txt')
@"
server-ip=127.0.0.1
server-port=25576
online-mode=false
view-distance=4
simulation-distance=4
spawn-protection=0
gamemode=survival
difficulty=peaceful
level-type=minecraft:flat
level-name=mechanical-network-$(Get-Date -Format 'yyyyMMdd-HHmmss')
max-tick-time=120000
"@ | Set-Content -LiteralPath (Join-Path $serverDirectory 'server.properties')
$env:JDK_JAVA_OPTIONS = '-Djdk.net.unixdomain.tmpdir=' + (Join-Path $repo '.codex-reference/no-unix-sockets')
$processes = @()
foreach ($name in @('runMultiServer','runMultiClientA','runMultiClientB')) {
    $spec = $launches.$name
    New-Item -ItemType Directory -Force -Path $spec.workingDirectory | Out-Null
    $env:MOD_CLASSES = $spec.modClasses
    $argumentFile = Join-Path $testRoot ($name + '.args')
    function Quote-JavaArgument([string]$value) { return '"' + $value.Replace('\','\\').Replace('"','\"') + '"' }
    $lines = @(Get-Content -LiteralPath $spec.vmArgs)
    foreach ($jvmArgument in $spec.jvmArguments) { $lines += Quote-JavaArgument $jvmArgument }
    $lines += Quote-JavaArgument ('-Dsparkmotors.multiRoot=' + $testRoot)
    $lines += '-Xmx2G'
    $lines += '-classpath'
    $lines += Quote-JavaArgument $spec.classpath
    $lines += Get-Content -LiteralPath $spec.programArgs
    [IO.File]::WriteAllLines($argumentFile, $lines)
    $process = Start-Process -FilePath $spec.java -ArgumentList ('@"' + $argumentFile + '"') -WorkingDirectory $spec.workingDirectory -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $testRoot ($name+'.log')) -RedirectStandardError (Join-Path $testRoot ($name+'.err'))
    $processes += [pscustomobject]@{name=$name;pid=$process.Id}
}
$processes | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $testRoot 'processes.json')
Write-Output $testRoot
Write-Output ($processes | ConvertTo-Json -Compress)
