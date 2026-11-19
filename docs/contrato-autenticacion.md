# Contrato de autenticación de administración

El back-end mantiene la sesión con una cookie HTTP (`JSESSIONID`). La SPA debe enviar
`credentials: "include"` en todas las llamadas a la API.

## Flujo

1. `GET /api/autenticacion/csrf` devuelve `{ "token": "..." }` y establece una sesión CSRF.
2. Enviar ese token en `X-CSRF-TOKEN` en las peticiones `POST`, `PUT`, `PATCH` y `DELETE`.
3. `POST /api/autenticacion/iniciar-sesion` con JSON `{ "usuario": "...", "contrasena": "..." }`.
   Si es válido devuelve `200` con `{ "usuario": "..." }`; si no, devuelve `401` con
   `{ "mensaje": "Credenciales incorrectas o cuenta inactiva." }`.
4. Tras iniciar sesión, volver a pedir `GET /api/autenticacion/csrf` para obtener el token de la
   sesión autenticada.
5. `GET /api/autenticacion/sesion` devuelve el usuario autenticado o `401` si no hay sesión.
6. `POST /api/autenticacion/cerrar-sesion` requiere el token CSRF y cierra la sesión (`204`).

Las rutas bajo `/api/admin/**` exigen una sesión de administrador. Los usuarios deben
existir en `administrador`, tener `activo = true` y guardar un hash BCrypt en
`contrasena_hash`. No se crea ningún administrador con contraseña predeterminada.

En local, el origen permitido por CORS es `http://localhost:5173`. En producción se debe
definir `APP_FRONTEND_ORIGIN`, `SESSION_COOKIE_SAME_SITE=none` y
`SESSION_COOKIE_SECURE=true` para que Vercel pueda enviar la cookie al API en Render.
