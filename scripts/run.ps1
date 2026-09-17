param([string]$JdkHome)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'java-env.ps1')
Initialize-ProjectJava -JdkHome $JdkHome
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & .\mvnw.cmd --batch-mode --no-transfer-progress javafx:run
    if ($LASTEXITCODE -ne 0) { throw "La aplicación terminó con código $LASTEXITCODE." }
} finally {
    Pop-Location
}
