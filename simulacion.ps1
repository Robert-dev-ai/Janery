# Arranca el backend con el reloj simulado sobre la base janery_simulacion (docs/testing.md).
# Uso: .\simulacion.ps1             continua la simulacion anterior
#      .\simulacion.ps1 -Reiniciar  borra los datos simulados y vuelve a la fecha real
param([switch]$Reiniciar)

$base = 'janery_simulacion'

$entorno = @{}
Get-Content (Join-Path $PSScriptRoot '.env') -ErrorAction Stop | Where-Object { $_ -match '^\s*[A-Z_]+=' } | ForEach-Object {
    $partes = $_ -split '=', 2
    $entorno[$partes[0].Trim()] = $partes[1].Trim()
}
$esquema = if ($entorno['JANERY_DB_SCHEMA']) { $entorno['JANERY_DB_SCHEMA'] } else { 'janery' }
$puerto = if ($env:JANERY_SERVER_PORT) { $env:JANERY_SERVER_PORT } elseif ($entorno['JANERY_SERVER_PORT']) { $entorno['JANERY_SERVER_PORT'] } else { '8080' }

if (Get-NetTCPConnection -State Listen -LocalPort $puerto -ErrorAction SilentlyContinue) {
    throw "El puerto $puerto esta ocupado. Detenga primero el backend normal."
}

$psql = (Get-Command psql -ErrorAction SilentlyContinue).Source
if (-not $psql) {
    $psql = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' | Sort-Object FullName | Select-Object -Last 1 -ExpandProperty FullName
}
if (-not $psql) { throw 'No se encontro psql.' }

$env:PGPASSWORD = $entorno['JANERY_DB_PASSWORD']
$env:PGOPTIONS = '-c client_min_messages=warning'
$conexion = @('-h', $entorno['JANERY_DB_HOST'], '-p', $entorno['JANERY_DB_PORT'], '-U', $entorno['JANERY_DB_USER'], '-v', 'ON_ERROR_STOP=1', '-q', '-t', '-A')
# Solo para conectarse: este servidor no tiene la base "postgres".
$baseActual = $entorno['JANERY_DB_NAME']

$existe = & $psql @conexion -d $baseActual -c "select 1 from pg_database where datname = '$base'"
if ($LASTEXITCODE -ne 0) { throw 'No se pudo conectar a PostgreSQL.' }
if ($existe -ne '1') {
    & $psql @conexion -d $baseActual -c "create database $base"
    if ($LASTEXITCODE -ne 0) { throw "No se pudo crear la base $base." }
} elseif ($Reiniciar) {
    & $psql @conexion -d $base -c "drop schema if exists `"$esquema`" cascade"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron borrar los datos simulados.' }
}
Remove-Item Env:PGPASSWORD, Env:PGOPTIONS

Set-Location (Join-Path $PSScriptRoot 'backend')
& .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=simulacion'
