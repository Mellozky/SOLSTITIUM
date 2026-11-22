#!/usr/bin/env bash
# Crea un administrador de SOLSTITIUM o restablece su contraseña (Git Bash, Linux o macOS).
#
# El ERS indica que los administradores se cargan por script al instalar el sistema.
# La contraseña se pide sin mostrarla, no se guarda en archivos ni en el historial y solo
# se entrega al proceso que crea el usuario.
#
# Uso:
#   scripts/crear-administrador.sh               crear un administrador
#   scripts/crear-administrador.sh --restablecer cambiar la contraseña de uno existente
#
# Conexión: usa DB_URL, DB_USERNAME y DB_PASSWORD si ya existen. Si no, se conecta a
# jdbc:postgresql://localhost:5432/solstitium con el usuario postgres y pide la contraseña.
set -euo pipefail

raiz="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
opcion="--crear-administrador"
if [[ "${1:-}" == "--restablecer" ]]; then
  opcion="--restablecer-contrasena-administrador"
elif [[ $# -gt 0 ]]; then
  echo "Opción desconocida: $1. Use --restablecer o ninguna." >&2
  exit 1
fi

leer_secreto() {
  local valor
  read -r -s -p "$1: " valor
  echo >&2
  printf '%s' "$valor"
}

url="${DB_URL:-jdbc:postgresql://localhost:5432/solstitium}"
usuario_bd="${DB_USERNAME:-postgres}"
echo "Base de datos: $url (usuario $usuario_bd)"
clave_bd="${DB_PASSWORD:-}"
if [[ -z "$clave_bd" ]]; then clave_bd="$(leer_secreto 'Contraseña de PostgreSQL')"; fi

read -r -p "Usuario del administrador: " admin_usuario
admin_nombre=""
if [[ "$opcion" == "--crear-administrador" ]]; then read -r -p "Nombre completo: " admin_nombre; fi

contrasena="$(leer_secreto 'Contraseña del administrador (mínimo 10 caracteres)')"
if [[ "$contrasena" != "$(leer_secreto 'Repite la contraseña')" ]]; then
  echo "Las contraseñas no coinciden. No se guardó ningún cambio." >&2
  exit 1
fi

argumentos="$opcion --server.port=0 --spring.main.banner-mode=off"
argumentos+=" --logging.level.root=WARN --logging.level.com.solstitium.solstitium.config.ComandoAdministrador=INFO"

# Las variables se pasan solo a este proceso; no quedan definidas en la terminal.
if DB_URL="$url" DB_USERNAME="$usuario_bd" DB_PASSWORD="$clave_bd" \
   ADMIN_USUARIO="$admin_usuario" ADMIN_NOMBRE="$admin_nombre" ADMIN_CONTRASENA="$contrasena" \
   "$raiz/mvnw" -q -f "$raiz/pom.xml" spring-boot:run "-Dspring-boot.run.arguments=$argumentos"; then
  echo "Listo."
else
  echo "No se completó la operación. Revisa el mensaje anterior." >&2
  exit 1
fi
