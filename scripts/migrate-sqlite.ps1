param([string]$Source = (Join-Path $env:USERPROFILE '.gestion-personas\persona.db'), [string]$JdkHome)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'java-env.ps1')
Initialize-ProjectJava -JdkHome $JdkHome
$projectRoot = Split-Path $PSScriptRoot -Parent
$sourcePath = (Resolve-Path -LiteralPath $Source).Path
[xml]$pom = Get-Content -LiteralPath (Join-Path $projectRoot 'pom.xml')
$jar = Join-Path $projectRoot "target\$($pom.project.artifactId)-$($pom.project.version).jar"
if (-not (Test-Path -LiteralPath $jar)) { throw 'Primero compila el proyecto con .\mvnw.cmd verify.' }
& (Join-Path $env:JAVA_HOME 'bin\java.exe') -jar $jar "--migrate-sqlite=$sourcePath"
if ($LASTEXITCODE -ne 0) { throw 'La importación falló. Revisa el error; el archivo SQLite original se conserva.' }
