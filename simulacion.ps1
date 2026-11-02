# Arranca el simulador: el backend con el reloj simulado sobre la base janery_simulacion, en el puerto 8081 (docs/testing.md).
# Puede correr junto al backend normal. Frontend del simulador: cd frontend; npm run simulador (http://localhost:4201).
# Uso: .\simulacion.ps1             continua la simulacion anterior
#      .\simulacion.ps1 -Reiniciar  borra los datos simulados y vuelve a la fecha real
#      .\simulacion.ps1 -SegundoPlano  arranca sin ventana: cerrar una consola no lo detiene (salida en simulacion.log)
#      .\simulacion.ps1 -Detener    detiene el simulador
param([switch]$Reiniciar, [switch]$SegundoPlano, [switch]$Detener)

if ($Detener) {
    $escucha = Get-NetTCPConnection -State Listen -LocalPort 8081 -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($escucha) {
        # Sube hasta el proceso de Maven para no dejarlo huerfano.
        $id = $escucha.OwningProcess
        $raiz = $id
        while ($id) {
            $proceso = Get-CimInstance Win32_Process -Filter "ProcessId=$id"
            if (-not $proceso -or $proceso.CommandLine -notmatch 'JaneryApplication|plexus-classworlds|mvnw') { break }
            $raiz = $id
            $id = $proceso.ParentProcessId
        }
        taskkill /PID $raiz /T /F | Out-Null
    }
    return
}

if ($SegundoPlano) {
    # Sin ventana: nadie puede cerrarla ni interrumpirla con Ctrl+C por accidente.
    $registro = Join-Path $PSScriptRoot 'simulacion.log'
    $argumentos = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$PSCommandPath`"")
    if ($Reiniciar) { $argumentos += '-Reiniciar' }
    Start-Process powershell.exe -ArgumentList $argumentos -WindowStyle Hidden -WorkingDirectory $PSScriptRoot `
        -RedirectStandardOutput $registro -RedirectStandardError "$registro.err"
    Write-Output "Simulacion arrancando. Salida en $registro. Para detenerla: .\simulacion.ps1 -Detener"
    return
}

$base = 'janery_simulacion'

$entorno = @{}
Get-Content (Join-Path $PSScriptRoot '.env') -ErrorAction Stop | Where-Object { $_ -match '^\s*[A-Z_]+=' } | ForEach-Object {
    $partes = $_ -split '=', 2
    $entorno[$partes[0].Trim()] = $partes[1].Trim()
}
$esquema = if ($entorno['JANERY_DB_SCHEMA']) { $entorno['JANERY_DB_SCHEMA'] } else { 'janery' }
$puerto = if ($env:JANERY_SIMULADOR_PUERTO) { $env:JANERY_SIMULADOR_PUERTO } else { '8081' }

if (Get-NetTCPConnection -State Listen -LocalPort $puerto -ErrorAction SilentlyContinue) {
    throw "El puerto $puerto esta ocupado: el simulador ya esta corriendo (.\simulacion.ps1 -Detener)."
}

$psql = (Get-Command psql -ErrorAction SilentlyContinue).Source
if (-not $psql) {
    $psql = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' | Sort-Object FullName | Select-Object -Last 1 -ExpandProperty FullName
}
if (-not $psql) {
    # Sin psql no se puede crear la base: se supone creada. Reiniciar se hace desde la barra del simulador.
    Write-Warning 'No se encontro psql: se usa la base janery_simulacion existente.'
    Set-Location (Join-Path $PSScriptRoot 'backend')
    & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=simulacion'
    return
}

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
