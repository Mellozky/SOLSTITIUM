# Contrato del catálogo de paneles

Definido antes de implementar, 20 de noviembre de 2026. RF-04, RF-07 y CU-13–CU-15
del ERS v1.4, páginas 37–39; figuras 3–5, páginas 42, 44 y 45.

Todas las rutas requieren sesión de servidor con rol ADMIN y cuenta activa. Las
escrituras requieren `X-CSRF-TOKEN` obtenido mediante el flujo de autenticación
existente. JSON con campos desconocidos se rechaza; nunca se acepta un administrador
en la petición. No hay precio, eliminación ni rutas públicas del catálogo.

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| GET `/api/admin/paneles` | Sin cuerpo | 200: `{paneles: [...], limites: [...]}` |
| POST `/api/admin/paneles` | Datos del panel | 201, Location y panel creado, activo y sin referencia |
| PUT `/api/admin/paneles/{id}` | Datos completos del panel | 200 y panel editado; conserva estado y referencia |
| PUT `/api/admin/paneles/{id}/estado` | `{"activo":true}` o `{"activo":false}` | 200 y panel actualizado |
| PUT `/api/admin/paneles/{id}/referencia` | `{"confirmado":true}` | 200 y panel de referencia |

Datos del panel: `{"marca":"Marca","modelo":"Modelo","potenciaWp":550,"areaM2":2.6}`.
Respuesta de panel: `id`, `marca`, `modelo`, `potenciaWp`, `areaM2`, `activo`,
`esReferencia`. Listado ordenado por marca, modelo e id, incluye inactivos.
Cada límite contiene `campo` (nombre SQL), `valorMinimo`, `valorMaximo`; procede
de `limite_parametro`, tabla `panel`, clave nula y es de solo lectura.

Marca y modelo son obligatorios, sin espacios exteriores tras guardar, máximo 60
caracteres. Se rechaza la pareja duplicada incluso con diferencias de mayúsculas
o espacios exteriores, incluyendo paneles inactivos y excluyendo el propio id al
editar. Esta normalización precisa la igualdad del ERS para evitar duplicados
visuales; no cambia las migraciones ni los valores de paneles existentes.
Potencia y área son números JSON positivos y obligatorios, dentro de los límites
actuales de BD, sin redondeo: potencia NUMERIC(7,2), área NUMERIC(5,3).
Estas precisiones son capacidad del esquema, no límites de negocio nuevos.

El panel de referencia no se puede desactivar; uno inactivo no puede seleccionarse.
Primero debe activarse por la ruta de estado. La ruta de referencia representa la
confirmación de CU-14; `false`, omisión o cancelar no modifica nada. Repetir el
estado, la referencia o los mismos datos no genera auditoría ficticia.

Cada escritura valida valores individuales y después la coherencia global mediante
el mismo validador que los parámetros. Bloquea primero las filas de `parametro`
en orden de clave, como administración de parámetros, antes de leer el catálogo.
Las escrituras quedan serializadas también entre instancias del servidor. El cambio
de referencia desmarca y sincroniza la anterior antes de marcar la nueva, todo en
una transacción junto con la bitácora por campo. Un fallo revierte incluso esa
sincronización intermedia. En creación se auditan los seis campos con anterior
`(sin registro)`; cada modificación incluye fecha, autor, id, anterior y nuevo.

Las simulaciones ya cargan la referencia desde BD con lectura repetible: una
simulación en curso conserva su versión; la siguiente al commit usa la nueva
potencia y área. No se modifica el motor ni se persisten simulaciones.

| Estado | Significado |
|---|---|
| 400 | JSON, tipos, límites, confirmación o coherencia global inválidos |
| 401 | Sin sesión (con CSRF válido en escrituras) |
| 403 | Rol ajeno, cuenta inactiva o CSRF ausente/inválido |
| 404 | Id positivo de panel inexistente |
| 409 | Marca/modelo duplicados, desactivar referencia o seleccionar inactivo |
| 405 | Método no soportado, incluida eliminación |
| 503 | Configuración incompleta o fallo de persistencia/bloqueo/bitácora |

Errores de aplicación: `{ "mensaje": "...", "errores": ["..."] }`, en español,
sin detalles SQL. Los rechazos del filtro de seguridad conservan su contrato
existente. Los id deben ser enteros positivos.

Se mantienen `PanelController`, `PanelService`, `PanelRepository` y los métodos
`listar`, `registrar`, `editar`, `cambiarEstado`, `designarReferencia` del ERS.
Se adaptan las firmas de entidad a DTO y se obtiene el administrador de la sesión,
en concordancia con la separación MVC y la seguridad solicitadas. El listado
incluye límites para que el cliente pueda presentar los rangos vigentes.
