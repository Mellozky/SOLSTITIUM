# Coordinación de simulación (RF-01 a RF-06)

`SimulacionService.simular(solicitud)` conserva el nombre del ERS, carga los parámetros una vez y coordina validación, sombra, dimensionamiento y finanzas en una transacción de lectura repetible. No guarda datos del residente.

Devuelve `ResultadoSimulacion`, con tramo, resultados, recomendación y motivo, escenario sin sombra, advertencias, panel de referencia y fecha de actualización de la tarifa. Se añaden `factorSombra` y `gastoMensualSinSistema`; cada configuración incluye `gastoMensualConSistema`, para que el gráfico no replique reglas tarifarias en el navegador. No se exponen entidades JPA.

La recomendación compara tiempos de recuperación sin redondear y excluye los que no recuperan en la vida útil. En un empate exacto conserva el orden de configuraciones del catálogo. Si ninguna recupera, devuelve `recomendada: null` y explica el motivo. Una configuración que recupera en 14 años puede recomendarse aunque su rango sea “No viable”, como en CP-08: no se confunde la clasificación con la ausencia de recuperación.

El escenario sin sombra se calcula únicamente cuando el factor original es menor que uno: factor uno, pérdida cero, inversor central y nuevo dimensionamiento sin margen de sombra. No modifica la solicitud original ni depende de que el catálogo conserve una porción etiquetada como “Nada”.

Con cero paneles devuelve `sinEspacio: true`, resultados vacíos y ningún indicador financiero. Las advertencias y metadatos se conservan. En todos los casos se indica que es una estimación, no una cotización; el tramo puede añadir una advertencia de baja confianza y sugerir evaluación en sitio.

## API pública

`POST /api/simulaciones`, con `Content-Type: application/json`. La entrada se describe en `contrato-entrada-simulacion.md`. No requiere registro, sesión ni token CSRF: realiza exclusivamente cálculos de lectura. Esa excepción no afecta autenticación ni administración, que conservan CSRF. Los números de estrato e identificadores deben ser enteros; no se truncan decimales.

- **200**: `ResultadoSimulacion`. Un resultado sin espacio también es 200 con `sinEspacio: true` y lista vacía. Un `tiempoRecuperacion: null` en una configuración calculada indica que no recupera durante su vida útil; no es un error HTTP.
- **400**: datos o JSON inválidos; `{ "mensaje": "...", "errores": ["..."] }`, en español.
- **503**: configuración o acceso a la base no disponibles. No se devuelve un resultado parcial ni información interna.
- **500**: error inesperado, con mensaje genérico. Las trazas se registran únicamente en el servidor.

Ejemplo desde JavaScript: `fetch('/api/simulaciones', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(solicitud)})`. En desarrollo CORS permite `http://localhost:5173`; ajustar `APP_FRONTEND_ORIGIN` en despliegue. No hay todavía una interfaz web ni un despliegue operativo.

La fecha de tarifa usa `LocalDateTime`, según el ERS, por lo que se serializa sin sufijo de zona. El despliegue debe mantener una política de zona horaria coherente con PostgreSQL; no interpretar ese dato como UTC con offset implícito.
