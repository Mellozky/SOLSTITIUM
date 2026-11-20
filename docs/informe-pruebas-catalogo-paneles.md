# Backend del catálogo de paneles: implementación y verificación

20 de noviembre de 2026, America/Bogota. Rama `codex/catalogo-paneles`, basada
en `main` actualizada a `22c442b`. Contrato definido antes del código en
`docs/contrato-catalogo-paneles.md`.

## Correspondencia con el ERS

Se consultó el original v1.4 de 66 páginas en la ubicación local facilitada por
el usuario. RF-04: página 21; RF-07: páginas 23–24; CU-13–CU-15: páginas 37–39;
diagramas de clases, figuras 4 y 5: páginas 44–45; CV-24–CV-28: página 62.

| Requisito | Implementación y evidencia |
|---|---|
| RF-07, CU-13 | Listado con límites; alta, edición y estado mediante DTO; sin precio ni eliminación |
| CU-13 A1, CV-25 | Rechaza marca/modelo duplicados en alta y edición, incluso inactivos y diferencias de mayúsculas o espacios exteriores |
| CU-13 A3, CV-24 | Devuelve 409 al intentar desactivar la referencia; explica que primero debe elegirse otra |
| CU-14, CV-26 | Exige confirmación explícita; rechaza panel inactivo; cambia y audita las dos marcas de referencia |
| CU-15, CV-28 | Límites de BD, coherencia global, identidad de sesión, auditoría por campo y rollback real ante fallo de bitácora |
| RF-04, CU-14 | Tras el commit, la siguiente simulación utiliza potencia y área de la nueva referencia |

## Código y responsabilidades

- `controller/PanelController.java`: cinco rutas administrativas, valida DTO y delega.
- `model/dto/SolicitudPanel.java`, `SolicitudEstadoPanel.java`,
  `SolicitudReferenciaPanel.java`: contratos separados para datos, estado y
  confirmación. `DeserializadoresCatalogo.java` rechaza coerciones de tipos.
  `PanelCatalogo.java` y `ConsultaPaneles.java` evitan exponer entidades JPA.
- `service/PanelService.java`: métodos `registrar`, `editar`, `cambiarEstado` y
  `designarReferencia` del ERS; añade consulta `listar`. Obtiene el administrador
  con `AdministradorSesion`, valida límites y reutiliza `ValidadorParametros` para
  coherencia global. Cada escritura incluye bitácora por campo en la transacción.
- `repository/PanelRepository.java`: conserva los métodos del ERS; añade id como
  desempate al orden existente de marca/modelo.
- `exception/ManejadorErroresAdministracion.java`, `ConflictoCatalogoException.java`
  y `PanelNoEncontradoException.java`: errores comprensibles, sin exponer SQL.
- `controller/CatalogoPanelesTest.java` y `service/TransaccionPanelesTest.java`:
  pruebas del recorrido completo y de transacciones confirmadas/fallidas.

Las firmas de la figura 4 se adaptan a DTO y a identidad obtenida desde la sesión,
en lugar de recibir entidades o un administrador del cliente. El contrato documenta
esta adaptación y la normalización de duplicados. No se modificaron entidades,
calculadoras financieras, migraciones publicadas, seguridad de sesión ni pantallas.

## Concurrencia y atomicidad

`PanelService` llama a `AdministracionParametrosRepository.cargar(true)`, exactamente
como `ParametroService`: bloquea las filas de `parametro` por clave antes de leer
el catálogo y los límites. Todos los escritores de ambos módulos usan ese orden.
La prueba observa en `pg_stat_activity` y `pg_blocking_pids` que la conexión del
catálogo espera el bloqueo mantenido por una actualización real de parámetros.
Tras liberarlo, ambos commits conservan el conjunto coherente.

El cambio de referencia retira la anterior y ejecuta `flush` antes de marcar la nueva,
respetando el índice único parcial existente. La transacción conserva el bloqueo
hasta terminar ambas auditorías. Se verificaron carreras entre dos referencias,
entre referencia y desactivación y entre dos altas duplicadas. No hay bloqueos
locales en memoria ni cambios de esquema necesarios.

Cuatro casos instalan temporalmente un trigger real en PostgreSQL que provoca
un error al insertar auditoría de alta, edición, estado o referencia. Alta/edición
fallan en el segundo campo; referencia falla al auditar la nueva después de
insertar la auditoría de la anterior. Desde fuera de la transacción fallida se
comparan todas las filas del catálogo y bitácora con sus originales: nada permanece
guardado. El trigger se retira después de cada caso.

## Ejecución y evidencia

JDK **21.0.10**, PostgreSQL **18.7**, base exclusiva creada para esta tarea:
`solstitium_pruebas_catalogo`, servidor local `127.0.0.1:55438`. Nunca se usó la
base operativa. Flyway V1, V1.1 y V2 aparecen aplicadas correctamente.

Con `JAVA_HOME` apuntando al JDK 21, `DB_URL` apuntando a esa base y las variables
`DB_USERNAME` y `DB_PASSWORD` del entorno de pruebas:

```text
./mvnw.cmd -o -B -ntp verify
[INFO] Tests run: 428, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  01:04 min
[INFO] Finished at: 2026-11-20T16:04:28-05:00
```

Desglose: **82** pruebas MVC del catálogo, **9** de transacciones/concurrencia y
**337** existentes, incluidas regresiones del motor y administración de parámetros.
Se ejecutaron realmente las pruebas, no solo compilación. El modo `-o` reutilizó
dependencias ya presentes; no omite pruebas.

Log completo local: `tmp/verificacion/verify-catalogo.log` (excluido de Git).
SHA-256: `33D8DBA6C94595FB4019399C6A1580DCB273A93A067F83F479FE52AE198B10A9`.
Reportes individuales: `target/surefire-reports/` (excluidos de Git).

La primera ejecución aislada de MVC detectó un problema de preparación de un caso:
se modificaba estado por JDBC después de cargar el mismo panel en el contexto JPA.
Se separaron CV-24 y CV-26 para preparar cada escenario antes de cargar entidades;
ambos pasan en la ejecución completa anterior.

## Evidencia funcional concreta

- Las cinco operaciones rechazan anónimos, roles ajenos y cuentas desactivadas.
  Las cuatro escrituras rechazan CSRF ausente o inválido. Un login real permite
  registrar y atribuye los seis campos iniciales al administrador de esa sesión.
- Se rechazan campos desconocidos, precio, identidad enviada por el cliente,
  estado/referencia incluidos en datos de panel, tipos ambiguos, nulos, blancos,
  longitudes y precisiones fuera del esquema, ids inválidos y paneles inexistentes.
- Cambiar los límites de prueba en BD modifica la aceptación del servicio. Acepta
  ambos extremos y rechaza valores fuera de ellos mostrando el rango vigente.
- Las cuatro escrituras rechazan pesos incoherentes o un catálogo inicial sin
  referencia. Las repeticiones no generan cambios ficticios en bitácora.
- Simulación sin sombra, consumo 600 kWh, techo 10 m²: con referencia 550 Wp / 2,6 m²
  produce 2 paneles y 1,10 kWp. Tras confirmar referencia 450 Wp / 2,1 m² produce
  3 paneles y 1,35 kWp. Tras editar esta a 600 Wp / 3 m² produce 2 paneles y 1,20 kWp.
  Son supuestos de prueba del modelo, no cotizaciones.
- Tras la suite se consultó la base: conserva paneles 1 y 2 con referencia en 1,
  cero administradores y cero filas de bitácora de prueba. Las secuencias pueden
  avanzar por inserts revertidos, comportamiento normal de PostgreSQL.

## Límites de la verificación

No se implementaron ni probaron pantallas. HTTPS de despliegue, rendimiento desde
navegador, disponibilidad y aceptación con usuarios/cotizaciones reales quedan fuera
de esta tarea. Los límites cargados por V2 son supuestos técnicos de prueba; no se
presentan como rangos comerciales aprobados. Los bloqueos coordinan las escrituras
de estos servicios; scripts externos deben respetar el mismo protocolo. La base
existente solo garantiza por restricciones como máximo una referencia, y los
servicios preservan exactamente una activa. La aprobación e integración del PR
quedan exclusivamente a cargo de Daniel.
