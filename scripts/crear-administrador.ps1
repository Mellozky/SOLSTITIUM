<#
.SYNOPSIS
  Crea un administrador de SOLSTITIUM o restablece su contraseña.

.DESCRIPTION
  El ERS indica que los administradores se cargan por script al instalar el sistema.
  Este script pide los datos de forma interactiva: la contraseña no se muestra, no se guarda
  en archivos ni en el historial, y solo se entrega al proceso que crea el usuario.

  Conexión: usa DB_URL, DB_USERNAME y DB_PASSWORD si ya existen. Si no, se conecta a
  jdbc:postgresql://localhost:5432/solstitium con el usuario postgres y pide la contraseña
  de PostgreSQL. Las migraciones pendientes de Flyway se aplican antes de crear el usuario.

.PARAMETER Restablecer
  Cambia la contraseña de un administrador existente y lo deja activo.

.EXAMPLE
  .\scripts\crear-administrador.ps1

.EXAMPLE
  .\scripts\crear-administrador.ps1 -Restablecer
#>
param([switch]$Restablecer)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot

function Leer-Secreto([string]$mensaje) {
    $seguro = Read-Host -AsSecureString $mensaje
    $puntero = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($puntero) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($puntero) }
}

# Variables que el script crea solo durante su ejecución; al final se restauran.
$nombresVariables = 'ADMIN_USUARIO', 'ADMIN_NOMBRE', 'ADMIN_CONTRASENA', 'DB_URL', 'DB_USERNAME', 'DB_PASSWORD'
$anteriores = @{}
foreach ($nombre in $nombresVariables) { $anteriores[$nombre] = [Environment]::GetEnvironmentVariable($nombre, 'Process') }

try {
    if (-not $env:DB_URL) { $env:DB_URL = 'jdbc:postgresql://localhost:5432/solstitium' }
    if (-not $env:DB_USERNAME) { $env:DB_USERNAME = 'postgres' }
    Write-Host "Base de datos: $env:DB_URL (usuario $env:DB_USERNAME)"
    if (-not $env:DB_PASSWORD) { $env:DB_PASSWORD = Leer-Secreto 'Contraseña de PostgreSQL' }

    $env:ADMIN_USUARIO = Read-Host 'Usuario del administrador'
    if ($Restablecer) {
        $opcion = '--restablecer-contrasena-administrador'
    } else {
        $opcion = '--crear-administrador'
        $env:ADMIN_NOMBRE = Read-Host 'Nombre completo'
    }

    $contrasena = Leer-Secreto 'Contraseña del administrador (mínimo 10 caracteres)'
    if ($contrasena -cne (Leer-Secreto 'Repite la contraseña')) {
        Write-Host 'Las contraseñas no coinciden. No se guardó ningún cambio.' -ForegroundColor Red
        exit 1
    }
    $env:ADMIN_CONTRASENA = $contrasena
    $contrasena = $null

    $argumentos = "$opcion --server.port=0 --spring.main.banner-mode=off " +
        '--logging.level.root=WARN --logging.level.com.solstitium.solstitium.config.ComandoAdministrador=INFO'
    & (Join-Path $raiz 'mvnw.cmd') -q -f (Join-Path $raiz 'pom.xml') spring-boot:run "-Dspring-boot.run.arguments=$argumentos"
    $codigo = $LASTEXITCODE
    if ($codigo -eq 0) { Write-Host 'Listo.' -ForegroundColor Green }
    else { Write-Host 'No se completó la operación. Revisa el mensaje anterior.' -ForegroundColor Red }
    exit $codigo
}
finally {
    foreach ($nombre in $nombresVariables) {
        [Environment]::SetEnvironmentVariable($nombre, $anteriores[$nombre], 'Process')
    }
}
