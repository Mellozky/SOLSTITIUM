# SOLSTITIUM

Simulador de viabilidad para la adopción de energía solar residencial en Montería.
Proyecto de aula de Ingeniería de Software — Universidad de Córdoba. Requisitos: ERS v1.4.

Equipo: Daniel Mendoza · Luis Hernández · Carlos Sandoval · Esteban Vidal

## Stack

- **Back-end** (raíz del repo): Java 21, Spring Boot 4.1, Maven, Spring Data JPA, Flyway, Spring Security.
- **Base de datos:** PostgreSQL (solo parámetros, catálogo de paneles, administradores y bitácora; las simulaciones no se guardan).
- **Front-end previsto** (carpeta `frontend/`): aplicación que consumirá la API REST en JSON; aún no está implementada.
- **Despliegue previsto:** Render (API) + Vercel (front-end).

## Estado al 20 de noviembre de 2026

Están implementados persistencia, autenticación administrativa, entrada y validación, tarifa, sombra, dimensionamiento, generación, ahorro, recuperación, ROI, recomendación y escenario sin sombra. La API pública `POST /api/simulaciones` permite calcular sin registro. La administración permite consultar y actualizar parámetros existentes y consultar su bitácora con sesión y CSRF. La suite incluye 333 pruebas y una regresión de los once escenarios del ERS. Faltan la interfaz web, el CRUD administrativo de paneles, el despliegue y la aceptación final.

El árbol siguiente muestra las capas implementadas; la interfaz web sigue pendiente.

## Patrón MVC

```
Vista (frontend/)  ──HTTP/JSON──▶  Controlador  ──▶  Servicio  ──▶  Repositorio  ──▶  PostgreSQL
                                   (controller)     (service)      (repository)
                                        └──────── Modelo: model/entity + model/dto ────────┘
```

```
src/main/java/com/solstitium/solstitium/
├── controller/        @RestController: recibe la petición, valida el DTO y delega en UN servicio
├── service/           reglas, transacciones y ValidadorParametros
│   └── calculo/       CalculadoraSombra, CalculadoraTarifa, CalculadoraTecnica, CalculadoraFinanciera
├── repository/        acceso a datos (Spring Data JPA)
├── model/
│   ├── entity/        entidades JPA (una por tabla)
│   └── dto/           ConjuntoParametros, SolicitudSimulacion, FactorSombra, ResultadoSimulacion...
├── config/            ConfiguracionSeguridad, CORS
└── exception/         manejador global de errores (mensajes claros en español)
src/main/resources/db/migration/   V1__esquema.sql, V1_1__restricciones.sql, V2__datos_iniciales.sql
```

## Cómo correrlo

Entorno del proyecto: JDK 21 y PostgreSQL 18 sobre una base existente. El CI usa PostgreSQL 18; la verificación local se realizó con PostgreSQL 18.7 en una base exclusiva de pruebas.

Crear la base `solstitium` desde pgAdmin o `createdb`. Flyway crea las tablas, pero no crea la base. En IntelliJ, definir `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` en la configuración de ejecución. El archivo `.env` no se carga automáticamente.

Ejemplo en PowerShell (sustituir el marcador por la contraseña local):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1' # ajustar a la instalación local
$env:DB_URL = 'jdbc:postgresql://localhost:5432/solstitium'
$env:DB_USERNAME = 'postgres'
$env:DB_PASSWORD = '<contraseña local>'
./mvnw.cmd spring-boot:run
```

En Git Bash/Linux/macOS:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/solstitium
export DB_USERNAME=postgres DB_PASSWORD='<contraseña local>'
./mvnw spring-boot:run
```

La aplicación expone `POST /api/simulaciones` sin sesión. El acceso administrativo usa Spring Security con sesión, BCrypt y CSRF. V2 no crea administradores ni contraseñas compartidas: se aprovisionarán por entorno. Ver `docs/contrato-autenticacion.md` y `docs/contrato-simulacion.md`.

Ejemplo de simulación en PowerShell, con la aplicación iniciada:

```powershell
$solicitud = @{
  consumos = @(160, 150, 140)
  estrato = 1
  areaLibre = 40
  respuestasSombra = @(
    @{franjaId = 1; porcionId = 1}
    @{franjaId = 2; porcionId = 1}
    @{franjaId = 3; porcionId = 1}
  )
} | ConvertTo-Json -Depth 4
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/simulaciones' -ContentType 'application/json' -Body $solicitud
```

La factura es opcional y se contrasta con el último mes, en orden cronológico. Errores de entrada devuelven 400; configuración o base no disponibles, 503; fallos inesperados, 500 con mensaje genérico. No se almacenan las simulaciones. Los contratos detallan redondeos, advertencias, ausencia de recuperación y salida sin espacio.

## Migraciones y datos iniciales

- **V1:** crea el tipo enumerado y las doce tablas (once entidades más `tramo_configuracion`).
- **V1.1:** añade restricciones de unicidad, comprobaciones de dominio e índices.
- **V2:** carga la tabla 30 del ERS: quince parámetros generales, seis estratos, tres franjas, cuatro porciones, dos configuraciones, cinco tramos con siete asociaciones, dos paneles, tres rangos y veintiocho límites de edición.

Flyway registra las versiones en `flyway_schema_history` y aplica solo las pendientes. No editar migraciones publicadas o aplicadas. V2 no se repite en cada inicio ni sobrescribe cambios posteriores.

Los valores son supuestos de prueba, no precios ni cotizaciones reales. Las marcas y modelos de los paneles son ficticios. No se crean administradores; se deberán aprovisionar por entorno con BCrypt. La bitácora queda vacía al instalar.

Convenciones para el código que consumirá estos datos:

- Los porcentajes generales se guardan como porcentajes: `5` significa 5 %. Pesos y factores son fracciones: `0.80` significa 80 %.
- Tramos de sombra: mínimo incluido y máximo excluido, salvo el punto sin sombra `[1, 1]`. Viabilidad: `[0, 6]`, `(6, 10]` y `(10, infinito)`; los servicios interpretarán estos extremos.
- El ERS fija el límite de edición del costo unitario en 500–3000 para CV-20. Los otros límites cargados son técnicos/matemáticos, no rangos de ingeniería aprobados. Deben acordarse los rangos operativos antes de habilitar su edición. Las horas se representan en límites como minutos desde medianoche (0–1439).

## Carga y pruebas de persistencia

`ParametroRepository.obtenerParametros()` delega en el fragmento `CargaParametros` y su implementación. Reúne los nueve grupos de la figura 5 del ERS en un DTO interno. Carga las configuraciones de cada tramo antes de cerrar la transacción, ordena las listas y rechaza grupos vacíos o la ausencia del panel de referencia activo.

La lectura inicia una transacción de solo lectura con aislamiento repetible; si el llamador ya abrió una transacción, participa en ella y hereda su aislamiento. La administración implementa validación de límites y coherencia antes de guardar cada valor modificado junto con su bitácora en una transacción.

Usar una **base exclusiva de pruebas**, con permisos para aplicar Flyway, y dirigir `DB_URL` a ella:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/solstitium_pruebas'
./mvnw.cmd verify
```

Hay 337 pruebas de arranque, persistencia, configuración, autenticación, validación, cálculo, administración, servicio y API. Las pruebas JPA y MVC usan rollback; las transaccionales administrativas sin transacción envolvente restauran los datos y las de autenticación eliminan su usuario de prueba. Flyway deja instaladas las migraciones y sus datos iniciales. Las pruebas de V2 esperan los valores fijos del ERS; no usar una base operativa personalizada.

La instalación limpia y la actualización de V1.1 a V2 se verificaron previamente. La regresión actual comprueba los quince resultados de CP-01–CP-11 y los fallos HTTP sin información interna. Ver `docs/informe-pruebas-motor-simulacion.md` para tolerancias, el orden de los meses en CP-10 y lo pendiente. Esto no acredita aceptación del sistema completo desde navegador ni en producción.

El job de interfaz del CI se omite sin `frontend/package-lock.json`; que salga verde no significa que exista o se haya probado el front-end.

## API administrativa de parámetros

- `GET /api/admin/parametros?seccion=tarifas`: consultar `tarifas`, `tecnicos`, `sombreado`, `costos` o `rangos`, con sus límites.
- `PUT /api/admin/parametros`: actualizar un lote de valores existentes, validando límites y coherencia; auditoría por valor realmente modificado y autor obtenido de la sesión.
- `GET /api/admin/bitacora`: consultar cambios del más reciente al más antiguo.

Las rutas requieren un administrador activo; PUT conserva la protección CSRF. Los límites
y el tipo de ajuste del estrato son de solo lectura. No incluyen pantallas ni CRUD de paneles.
Ver `docs/contrato-administracion-parametros.md` para cuerpos JSON y campos permitidos,
y `docs/informe-pruebas-administracion-parametros.md` para rollback real, concurrencia
y la correspondencia de CV-20–CV-23, CV-27 y CV-28 con el ERS original. La recuperación
de sombra solo es editable para MLPE; central conserva cero. La vida útil debe respetar
los límites de la base y la capacidad técnica compartida con el motor financiero.

## Cómo trabajamos en GitHub

- `main` es la rama principal: siempre compila, pasa las pruebas y es lo que se despliega.
- Cada tarea va en una rama corta desde `main`: `feature/…`, `fix/…`, `test/…`, `docs/…`, `chore/…`.
- Al terminar: actualizar la rama con `main`, ejecutar `./mvnw verify` contra una base exclusiva de pruebas, publicar la rama y abrir un Pull Request. Si el cambio incluye interfaz, ejecutar también sus comprobaciones y compilación. Verificar CI sobre el cambio final.
- Todos los PR, incluidos los de compañeros y agentes, quedan pendientes de la aprobación de Daniel; solo él los aprueba e integra a `main`. No subir cambios directamente a `main`. Este acuerdo requiere protección de rama en GitHub para imponerse automáticamente; su configuración no se da por hecha.
- Commits con *Conventional Commits* en español: `feat(calculo): calcular factor de sombra (RF-03)`.
- Nunca `push --force` sobre `main`.
- Código propio, comentarios, mensajes y documentación en español, conservando los nombres del ERS y los identificadores obligatorios de las herramientas.

El reparto por funcionalidad está en `docs/Plan_de_trabajo_SOLSTITIUM.pdf`. Sus fechas antiguas no se aplican: la entrega es el **22 de noviembre de 2026**. El flujo actualizado se acordó en el chat. El avance técnico se documenta en `docs/Documentacion_avance_SOLSTITIUM.docx`; los contratos y el informe de pruebas de esta carpeta detallan el motor y la API actuales.
