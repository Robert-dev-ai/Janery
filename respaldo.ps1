# Respaldo logico del esquema de Janery en la base a la que apunte el .env (Neon). Solo lee: no cambia la base.
# Uso: .\respaldo.ps1                        guarda en respaldos\ (ignorada por Git)
#      .\respaldo.ps1 -Destino D:\respaldos  guarda en otra carpeta: mejor fuera de esta computadora o en un disco cifrado
# El archivo contiene datos personales y financieros. Restaurar: docs/operacion.md, seccion 4.
param([string]$Destino = (Join-Path $PSScriptRoot 'respaldos'))

$ErrorActionPreference = 'Stop'

$entorno = @{}
Get-Content (Join-Path $PSScriptRoot '.env') | Where-Object { $_ -match '^\s*[A-Z_]+=' } | ForEach-Object {
    $partes = $_ -split '=', 2
    $entorno[$partes[0].Trim()] = $partes[1].Trim()
}
foreach ($clave in 'JANERY_DB_HOST', 'JANERY_DB_NAME', 'JANERY_DB_USER', 'JANERY_DB_PASSWORD') {
    if (-not $entorno[$clave]) { throw "Falta $clave en .env" }
}
$hostBase = $entorno['JANERY_DB_HOST']
if ($hostBase -match '-pooler') {
    throw 'El host usa el pooler de Neon: pg_dump necesita la conexion directa (host sin -pooler).'
}
$puerto = if ($entorno['JANERY_DB_PORT']) { $entorno['JANERY_DB_PORT'] } else { '5432' }
$esquema = if ($entorno['JANERY_DB_SCHEMA']) { $entorno['JANERY_DB_SCHEMA'] } else { 'janery' }
$ssl = if ($entorno['JANERY_DB_SSLMODE']) { $entorno['JANERY_DB_SSLMODE'] } else { 'require' }

# pg_dump debe ser de la misma version mayor que el servidor, o mas nueva (Neon: PostgreSQL 18).
$pgDump = (Get-Command pg_dump -ErrorAction SilentlyContinue).Source
if (-not $pgDump) {
    $pgDump = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\pg_dump.exe' -ErrorAction SilentlyContinue |
        Sort-Object { [int]$_.Directory.Parent.Name } | Select-Object -Last 1 -ExpandProperty FullName
}
if (-not $pgDump) { throw 'No se encontro pg_dump: instale PostgreSQL 18 marcando solo Command Line Tools.' }
$pgRestore = Join-Path (Split-Path $pgDump) 'pg_restore.exe'

New-Item -ItemType Directory -Force $Destino | Out-Null
$archivo = Join-Path $Destino ("janery_{0}.dump" -f (Get-Date -Format 'yyyyMMdd-HHmm'))

$env:PGPASSWORD = $entorno['JANERY_DB_PASSWORD']
$env:PGSSLMODE = $ssl
try {
    & $pgDump -h $hostBase -p $puerto -U $entorno['JANERY_DB_USER'] -d $entorno['JANERY_DB_NAME'] -n $esquema -Fc --no-owner -f $archivo
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump fallo: no se creo el respaldo.' }
} finally {
    Remove-Item Env:PGPASSWORD, Env:PGSSLMODE -ErrorAction SilentlyContinue
}

# Un respaldo que no se puede leer no sirve: se comprueba al momento.
$tablas = & $pgRestore --list $archivo | Select-String ' TABLE DATA '
if ($LASTEXITCODE -ne 0 -or -not $tablas) {
    throw "El respaldo $archivo no se puede leer o no contiene datos."
}
$tamano = [math]::Round((Get-Item $archivo).Length / 1MB, 2)
Write-Output "Respaldo listo: $archivo ($tamano MB, $($tablas.Count) tablas con datos)."
