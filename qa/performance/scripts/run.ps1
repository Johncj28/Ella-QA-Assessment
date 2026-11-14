<#
.SYNOPSIS
  Run Ella API k6 tests, save an HTML + JSON report, print clickable links and open the report.

.EXAMPLE
  ./scripts/run.ps1 -Api user -Type smoke
  ./scripts/run.ps1 -Api transaction -Type load -Grafana
  ./scripts/run.ps1 -Api all -Type smoke -NoOpen          # links only, don't open the browser
#>
param(
  [ValidateSet('user', 'product', 'transaction', 'all')] [string] $Api = 'all',
  [ValidateSet('smoke', 'load', 'stress', 'soak')] [string] $Type = 'smoke',
  [string] $BaseUrl = '',
  [string] $InfluxUrl = 'http://localhost:8086/k6',
  [string] $GrafanaUrl = 'http://localhost:3000',
  [switch] $Grafana,
  [switch] $NoOpen
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$reports = Join-Path $root 'reports'
New-Item -ItemType Directory -Force $reports | Out-Null
Push-Location $root   # handleSummary writes reports/... relative to the working directory

$apis = if ($Api -eq 'all') { 'user', 'product', 'transaction' } else { @($Api) }
$results = @()

foreach ($a in $apis) {
  $testId = "$a-$Type-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
  $html = Join-Path $reports "$testId.html"
  $json = Join-Path $reports "$testId.json"
  $startMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

  $k6Args = @('run', '-e', "TEST_ID=$testId")
  if ($BaseUrl) { $k6Args += @('-e', "BASE_URL=$BaseUrl") }
  if ($Grafana) { $k6Args += @('--out', "influxdb=$InfluxUrl") }
  $k6Args += (Join-Path $root "tests/$a/$Type.js")

  # handleSummary (src/utils/report.js) writes reports/$testId.html and .json at the end of the run
  # k6's live web dashboard while the test runs
  $env:K6_WEB_DASHBOARD = 'true'

  Write-Host "`n=== $testId ===" -ForegroundColor Cyan
  Write-Host "Live view while running: http://localhost:5665"
  & k6 @k6Args
  $passed = ($LASTEXITCODE -eq 0)
  $endMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

  $grafanaLink = if ($Grafana) {
    "$GrafanaUrl/d/ella-k6-performance?var-testid=$testId&from=$($startMs - 10000)&to=$($endMs + 10000)"
  }
  $results += [pscustomobject]@{ Id = $testId; Passed = $passed; Html = $html; Json = $json; Grafana = $grafanaLink }

  if (-not $NoOpen -and (Test-Path $html)) { Start-Process $html }
  if (-not $NoOpen -and $grafanaLink) { Start-Process $grafanaLink }
}

Remove-Item Env:K6_WEB_DASHBOARD, Env:K6_WEB_DASHBOARD_EXPORT -ErrorAction SilentlyContinue
Pop-Location

# ---- clickable summary (Ctrl+click in the VS Code terminal) ----
$fileUrl = { param($p) 'file:///' + ($p -replace '\\', '/') }
Write-Host "`n==================== REPORTS ====================" -ForegroundColor Cyan
foreach ($r in $results) {
  $status = if ($r.Passed) { 'PASSED' } else { 'FAILED (thresholds breached)' }
  $color = if ($r.Passed) { 'Green' } else { 'Red' }
  Write-Host "`n$($r.Id)  $status" -ForegroundColor $color
  if (Test-Path $r.Html) { Write-Host "  HTML report : $(& $fileUrl $r.Html)" }
  else { Write-Host "  HTML report : not created (did k6 stop before the end?)" -ForegroundColor Yellow }
  if (Test-Path $r.Json) { Write-Host "  JSON summary: $(& $fileUrl $r.Json)" }
  if ($r.Grafana) { Write-Host "  Grafana     : $($r.Grafana)" }
}
Write-Host "`nAll reports : $(& $fileUrl $reports)"
Write-Host "Reopen one  : start reports\<name>.html`n"

if ($results | Where-Object { -not $_.Passed }) { exit 1 }
