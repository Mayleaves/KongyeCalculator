param([string]$Node='node')
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$tests=@(
    'verify-drawer-arrow-position.cjs',
    'verify-grouped-graph-responsive-solver.cjs',
    'verify-history-graph-performance.cjs',
    'verify-append-toolbar-graph.cjs',
    'verify-manager-history-math-pages.cjs',
    'verify-defaults-white-solver.cjs'
)
foreach($test in $tests){
    & $Node --experimental-websocket (Join-Path $project "tests/browser/$test")
    if($LASTEXITCODE -ne 0){throw "Browser regression failed: $test"}
}
