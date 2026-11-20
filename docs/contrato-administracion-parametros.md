# Administración de parámetros y bitácora

Contrato definido antes de implementar los controladores. Alcance: CU-08–CU-12 y CU-15,
consulta de bitácora CU-07. No incluye pantallas, creación/eliminación de registros,
administradores ni CRUD de paneles. Referencia de aceptación: CV-20–CV-23, CV-27 y CV-28,
contrastados con la tabla 37 del ERS original (página 62 del PDF).

## Sesión y seguridad

Las tres rutas requieren la sesión administrativa descrita en `contrato-autenticacion.md`.
`PUT` exige además `X-CSRF-TOKEN`. El administrador se obtiene exclusivamente de la
sesión y se comprueba que continúe activo. El cuerpo nunca identifica al autor.
Sin sesión: 401; sin permiso o CSRF: 403.

La consulta utiliza una transacción de lectura repetible. Las actualizaciones bloquean
en orden las filas de parámetros generales antes de cargar y validar el conjunto,
serializando los lotes de este módulo para impedir incoherencias por escrituras concurrentes.
Un futuro servicio de paneles debe coordinar sus escrituras si también modifica invariantes
compartidas; este contrato no implementa ese servicio.

## Consulta

`GET /api/admin/parametros?seccion=tarifas`

Sección obligatoria: `tarifas`, `tecnicos`, `sombreado`, `costos` o `rangos`.
Respuesta 200: `{ "seccion": "tarifas", "registros": [...], "limites": [...] }`.
Cada registro contiene `tabla`, `registroId` y `valores` (campos planos, nunca entidades).
Los límites contienen `tabla`, `campo`, `clave`, `valorMinimo` y `valorMaximo`.
`clave` distingue parámetros generales; null aplica a toda una columna.

Distribución de parámetros generales:

- tarifas: costo_unitario_kwh, consumo_subsistencia, incremento_anual_tarifa; también estratos (CU-08).
- tecnicos: hsp, factor_rendimiento, factor_aprovechamiento, relacion_cc_ca, vida_util,
  tope_sobredimensionamiento (CU-09).
- sombreado: amplificacion_serie; franjas, porciones, tramos y coeficiente de recuperación MLPE (CU-10).
- costos: costo_kwp de las configuraciones.
- rangos: rangos de viabilidad, consumo_maximo, umbral_advertencia_consumo,
  area_minima, area_maxima y umbral_diferencia_factura (CU-12).

La consulta incluye metadatos de identificación y campos de solo lectura, como
tipo_ajuste y fecha_actualizacion. No implica permiso para modificarlos.

## Actualización atómica

`PUT /api/admin/parametros`, `Content-Type: application/json`.

```json
{
  "cambios": [
    {"tabla":"parametro","campo":"valor","registroId":"costo_unitario_kwh","valorNuevo":1100},
    {"tabla":"estrato","campo":"porcentaje_ajuste","registroId":"1","valorNuevo":45}
  ]
}
```

`CambioParametro` tiene exactamente tabla, campo, registroId y valorNuevo.
La lista no puede estar vacía ni repetir el mismo destino. Solo modifica registros
existentes. Un destino no permitido, desconocido o un dato inválido rechaza todo el lote.
Los atributos JSON desconocidos se rechazan, incluida cualquier identidad de administrador.

Lista explícita de destinos editables:

| Tabla | Campos |
|---|---|
| parametro | valor (solo las claves enumeradas en las secciones) |
| estrato | porcentaje_ajuste |
| franja_horaria | hora_inicio, hora_fin, peso |
| porcion_afectada | valor |
| configuracion | costo_kwp; coeficiente_recuperacion únicamente para código mlpe |
| tramo_sombreado | factor_minimo, factor_maximo, muestra_advertencia, configuraciones |
| rango_viabilidad | anios_minimo, anios_maximo |

Paneles, límites, identificadores, nombres, orden, códigos y tipo_ajuste son de solo
lectura en este módulo. No se construye SQL con nombres enviados por el cliente.
Los números deben ser números JSON exactos, sin redondeo silencioso a la escala de BD;
vida_util debe ser entero. Horas: cadena `HH:mm`, límites en minutos desde medianoche.
La vida útil no puede superar 999999999 años, límite técnico de BigDecimal.pow,
compartido por administración y motor en LimitesCalculoFinanciero. No es un rango de
ingeniería: los límites operativos siguen procediendo de limite_parametro. Se comprueba
además que vida_util e incremento_anual_tarifa puedan evaluarse juntos sin desbordamiento.
Booleanos: true/false. `configuraciones`: lista no vacía de identificadores enteros existentes,
sin duplicados; siempre debe incluir la configuración central.
`anios_maximo` admite null únicamente en el último rango, como extremo sin límite.

Primero se validan tipos, escala y límites inclusivos leídos de limite_parametro;
después se comprueba el conjunto resultante: pesos suman exactamente 1, horarios sin
cruces, porciones crecientes, tramos cubren [0,1) sin huecos ni cruces y un único punto
[1,1], central en cada tramo, rangos continuos desde cero con último extremo abierto,
áreas y umbrales coherentes, coeficiente de central igual a cero y exactamente un panel
de referencia activo. El coeficiente central se muestra como dato de solo lectura;
una petición para editarlo se rechaza, incluso si propone cero. El motor rechaza una
configuración central incoherente, en lugar de aplicarle recuperación MLPE.

Respuesta 200: `{ "modificados": 2 }`. Valores numéricamente iguales no generan
bitácora ni cambian fecha_actualizacion. Esta fecha se actualiza solo si cambia el
valor de un parámetro general. Cada valor distinto genera un registro con anterior,
nuevo y autor de sesión. Asociaciones se auditan como listas ordenadas de ids;
el extremo nulo se representa como `null` en texto en la bitácora.
Todo se guarda en una sola transacción; cualquier fallo de auditoría revierte todos
los cambios. Las escrituras administrativas se serializan mediante bloqueo de
los parámetros generales antes de leer el conjunto para evitar validaciones concurrentes
sobre estados incompatibles. Los futuros escritores del catálogo deben coordinar ese bloqueo.

## Bitácora

`GET /api/admin/bitacora` devuelve 200 y una lista de DTO con id, administrador
(nombre de usuario), fecha, tabla, campo, registroId, valorAnterior y valorNuevo.
Orden: fecha descendente y, para empates, id descendente. No expone hashes ni entidades.

## Errores y fechas

400: solicitud, sección, destino, límite o coherencia inválidos.
503: configuración/base no disponibles o conflicto concurrente; se puede reintentar.
500: fallo inesperado. Respuesta: `{ "mensaje": "...", "errores": [...] }`, en español
y sin detalles internos. Los errores previos del filtro de seguridad conservan su contrato.
Fechas LocalDateTime, sin offset, según el modelo existente. Se usa la zona del proceso
y la misma política JDBC del resto de la aplicación; cada entorno debe mantener coherencia
con timestamptz. No se modifica la política temporal global desde este módulo.
