function Initialize-ProjectJava {
    param([string]$JdkHome)

    $candidates = @($JdkHome, $env:JAVA_HOME)
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $candidates += Split-Path (Split-Path $javaCommand.Source -Parent) -Parent
    }
    $candidates += Get-ChildItem -Path (Join-Path $env:USERPROFILE '.jdks') -Directory -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty FullName

    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        $release = Join-Path $candidate 'release'
        if ((Test-Path -LiteralPath $release) -and (Test-Path -LiteralPath (Join-Path $candidate 'bin\javac.exe'))) {
            $version = Get-Content -LiteralPath $release | Select-String '^JAVA_VERSION="(17|21)(\.|\")'
            if ($version) {
                $env:JAVA_HOME = (Resolve-Path -LiteralPath $candidate).Path
                $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
                Write-Host "JDK: $env:JAVA_HOME"
                return
            }
        }
    }
    throw 'Configura JAVA_HOME con un JDK 17 o 21, o usa -JdkHome. Son las versiones soportadas por estas dependencias.'
}
