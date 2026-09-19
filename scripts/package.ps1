param(
    [ValidateSet('app-image', 'exe', 'msi')][string]$Type = 'exe',
    [string]$JdkHome,
    [string]$WixHome,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'java-env.ps1')
Initialize-ProjectJava -JdkHome $JdkHome
$projectRoot = Split-Path $PSScriptRoot -Parent
$jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
if (-not (Test-Path -LiteralPath $jpackage)) { throw 'Este JDK no incluye jpackage.' }

if ($Type -ne 'app-image') {
    if (-not $WixHome) { $WixHome = Join-Path $projectRoot '.tools\wix311' }
    if (Test-Path -LiteralPath $WixHome) { $env:PATH = "$WixHome;$env:PATH" }
    if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue) -or
        -not (Get-Command light.exe -ErrorAction SilentlyContinue)) {
        throw 'Se necesita WiX 3.11 para EXE/MSI. Usa -WixHome con la carpeta de candle.exe y light.exe, o -Type app-image.'
    }
}

Push-Location $projectRoot
try {
    if (-not $SkipBuild) {
        & .\mvnw.cmd --batch-mode --no-transfer-progress clean verify
        if ($LASTEXITCODE -ne 0) { throw 'La compilación o las pruebas fallaron.' }
    }
    [xml]$pom = Get-Content -LiteralPath (Join-Path $projectRoot 'pom.xml')
    $version = $pom.project.version
    $jarName = "$($pom.project.artifactId)-$version.jar"
    $jar = Join-Path $projectRoot "target\$jarName"
    if (-not (Test-Path -LiteralPath $jar)) { throw "No se encontró $jar. Ejecuta el script sin -SkipBuild." }

    # Una carpeta de entrada nueva impide incluir bases de datos o archivos de pruebas.
    $inputDirectory = Join-Path $projectRoot ("target\jpackage-input-" + [guid]::NewGuid())
    New-Item -ItemType Directory -Path $inputDirectory | Out-Null
    Copy-Item -LiteralPath $jar -Destination $inputDirectory
    $destination = if ($Type -eq 'app-image') {
        Join-Path $projectRoot "instalador\portable-$version"
    } else { Join-Path $projectRoot 'instalador' }
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    $outputName = if ($Type -eq 'app-image') { 'GestionPersonas' } else { "GestionPersonas-$version.$Type" }
    if (Test-Path -LiteralPath (Join-Path $destination $outputName)) {
        throw "Ya existe $(Join-Path $destination $outputName). Mueve el paquete anterior antes de generar otro."
    }
    $arguments = @('--type', $Type, '--name', 'GestionPersonas', '--app-version', $version,
        '--input', $inputDirectory, '--main-jar', $jarName, '--dest', $destination,
        '--description', 'Gestión de personas con JavaFX y Supabase PostgreSQL',
        '--java-options', '-Dfile.encoding=UTF-8')
    # El JAR Spring Boot usa JarLauncher en Main-Class y Launcher en Start-Class.
    # jpackage toma Main-Class directamente del manifiesto.
    if ($Type -ne 'app-image') {
        $arguments += @('--win-shortcut', '--win-menu', '--win-dir-chooser', '--win-per-user-install',
            '--win-upgrade-uuid', 'f07556d0-7fc7-416d-9dd5-ab7c8e9ee092')
    }
    & $jpackage @arguments
    if ($LASTEXITCODE -ne 0) { throw "jpackage terminó con código $LASTEXITCODE." }
    Write-Host "Paquete generado: $(Join-Path $destination $outputName)"
} finally {
    Pop-Location
}
