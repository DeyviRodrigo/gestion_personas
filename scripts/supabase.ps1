param([ValidateSet('setup', 'start', 'stop', 'status')][string]$Action = 'start')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$dockerCommand = Get-Command docker.exe -ErrorAction SilentlyContinue
if (-not $dockerCommand) {
    foreach ($candidate in @((Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin'), 'C:\Program Files\Docker\Docker\resources\bin')) {
        if (Test-Path -LiteralPath (Join-Path $candidate 'docker.exe')) { $env:PATH = "$candidate;$env:PATH"; break }
    }
}
if (-not (Get-Command docker.exe -ErrorAction SilentlyContinue)) { throw 'Instala e inicia Docker Desktop con el motor WSL 2.' }
Push-Location $projectRoot
try {
    & docker.exe info --format '{{.OSType}}' 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Docker no está disponible. Inicia Docker Desktop y espera a que arranque el motor.' }
    if (-not (Test-Path -LiteralPath 'node_modules/.bin/supabase.cmd')) {
        & npm.cmd ci --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo instalar la CLI de Supabase.' }
    }
    if ($Action -eq 'stop') {
        & npx.cmd --no-install supabase stop
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo detener Supabase.' }
        return
    }
    if ($Action -eq 'status') {
        & docker.exe ps --filter 'name=supabase_' --format '{{.Names}}  {{.Status}}  {{.Ports}}'
        Write-Host 'Studio: http://127.0.0.1:54323 | PostgreSQL: 127.0.0.1:54322'
        return
    }
    & docker.exe network inspect gestion-personas-local *> $null
    if ($LASTEXITCODE -ne 0) {
        & docker.exe network create -o 'com.docker.network.bridge.host_binding_ipv4=127.0.0.1' gestion-personas-local | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la red local de Docker.' }
    }
    New-Item -ItemType Directory -Path 'target' -Force | Out-Null
    Write-Host 'Iniciando Supabase local. La primera descarga puede tardar varios minutos.'
    & npx.cmd --no-install supabase start --network-id gestion-personas-local *> 'target/supabase-start.log'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar Supabase. Consulta target/supabase-start.log.' }
    if ($Action -eq 'setup') {
        $configDirectory = Join-Path $env:USERPROFILE '.gestion-personas'
        New-Item -ItemType Directory -Path $configDirectory -Force | Out-Null
        $configPath = Join-Path $configDirectory 'database.properties'
        $password = $null
        if (Test-Path -LiteralPath $configPath) {
            $line = Get-Content -LiteralPath $configPath | Where-Object { $_ -match '^app.local-password=[a-f0-9]{64}$' } | Select-Object -First 1
            if ($line) { $password = $line.Substring('app.local-password='.Length) }
            else { throw 'Ya existe database.properties con otra configuración. Revísala antes de ejecutar setup; no se ha sobrescrito.' }
        }
        if (-not $password) {
            $bytes = New-Object byte[] 32
            $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
            try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
            $password = [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
        }
        $sql = @'
DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'gestion_personas_app') THEN
    CREATE ROLE gestion_personas_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
  END IF;
END $$;
ALTER ROLE gestion_personas_app PASSWORD '__PASSWORD__';
CREATE SCHEMA IF NOT EXISTS gestion_personas AUTHORIZATION postgres;
REVOKE ALL ON SCHEMA gestion_personas FROM PUBLIC;
GRANT USAGE ON SCHEMA gestion_personas TO gestion_personas_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA gestion_personas TO gestion_personas_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA gestion_personas TO gestion_personas_app;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA gestion_personas GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO gestion_personas_app;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA gestion_personas GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO gestion_personas_app;
'@
        $sql.Replace('__PASSWORD__', $password) | & docker.exe exec -i supabase_db_gestion-personas psql -U postgres -d postgres -v ON_ERROR_STOP=1 *> 'target/supabase-roles.log'
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo preparar el usuario de PostgreSQL. Consulta target/supabase-roles.log.' }
        $configuration = @'
# Credenciales locales: no copiar al repositorio ni al instalador.
app.local-password=__PASSWORD__
spring.datasource.url=${DB_URL:jdbc:postgresql://127.0.0.1:54322/postgres?currentSchema=gestion_personas}
spring.datasource.username=${DB_USER:gestion_personas_app}
spring.datasource.password=${DB_PASSWORD:${app.local-password}}
spring.flyway.user=${FLYWAY_USER:postgres}
spring.flyway.password=${FLYWAY_PASSWORD:postgres}
'@
        [System.IO.File]::WriteAllText($configPath, $configuration.Replace('__PASSWORD__', $password), [System.Text.UTF8Encoding]::new($false))
        Write-Host "Configuración de la aplicación creada en $configPath"
    }
    Write-Host 'Supabase disponible. Studio: http://127.0.0.1:54323 | PostgreSQL: 127.0.0.1:54322'
} finally {
    Pop-Location
}
