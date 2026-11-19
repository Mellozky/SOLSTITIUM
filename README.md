# SOLSTITIUM

Simulador de viabilidad para la adopción de energía solar residencial en Montería.
Proyecto de aula de Ingeniería de Software — Universidad de Córdoba. Requisitos: ERS v1.4.

Equipo: Daniel Mendoza · Luis Hernández · Carlos Sandoval · Esteban Vidal

## Stack

- **Back-end** (raíz del repo): Java 21, Spring Boot 4.1, Maven, Spring Data JPA, Flyway, Spring Security.
- **Base de datos:** PostgreSQL (solo parámetros, catálogo de paneles, administradores y bitácora; las simulaciones no se guardan).
- **Front-end previsto** (carpeta `frontend/`): aplicación que consumirá la API REST en JSON; aún no está implementada.
- **Despliegue previsto:** Render (API) + Vercel (front-end).

## Estado al 18 de noviembre de 2026

En `main` están el CI, las migraciones V1, V1.1 y V2, las once entidades JPA, los cuatro repositorios y la carga de `ConjuntoParametros`. La suite tiene 17 pruebas. Todavía faltan la solicitud de simulación, las calculadoras, los servicios y controladores funcionales, la autenticación administrativa y la interfaz web.

El árbol siguiente muestra la arquitectura prevista; varias carpetas todavía no existen.

## Patrón MVC

```
Vista (frontend/)  ──HTTP/JSON──▶  Controlador  ──▶  Servicio  ──▶  Repositorio  ──▶  PostgreSQL
                                   (controller)     (service)      (repository)
                                        └──────── Modelo: model/entity + model/dto ────────┘
```

```
src/main/java/com/solstitium/solstitium/
├── controller/        @RestController: recibe la petición, valida el DTO y delega en UN servicio
├── service/           reglas de negocio y transacciones
│   └── calculo/       CalculadoraSombra, CalculadoraTecnica, CalculadoraFinanciera, ValidadorParametros
├── repository/        acceso a datos (Spring Data JPA)
├── model/
│   ├── entity/        entidades JPA (una por tabla)
│   └── dto/           ConjuntoParametros; previstos: SolicitudSimulacion, ResultadoSimulacion...
├── config/            SecurityConfig, CORS
└── exception/         manejador global de errores (mensajes claros en español)
src/main/resources/db/migration/   V1__esquema.sql, V1_1__restricciones.sql, V2__datos_iniciales.sql
```

## Cómo correrlo

Requisitos: JDK 21 y PostgreSQL 15 o superior (por `NULLS NOT DISTINCT`). El CI usa PostgreSQL 17; la verificación local se realizó con PostgreSQL 18.

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

La aplicación todavía no expone una API de simulación. Spring Security conserva su configuración automática de desarrollo; V2 no crea administradores ni contraseñas compartidas.

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

La lectura inicia una transacción de solo lectura con aislamiento repetible; si el llamador ya abrió una transacción, participa en ella y hereda su aislamiento. La coherencia completa y el guardado con bitácora corresponden a funcionalidades futuras.

Usar una **base exclusiva de pruebas**, con permisos para aplicar Flyway, y dirigir `DB_URL` a ella:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/solstitium_pruebas'
./mvnw.cmd verify
```

Hay 17 pruebas: arranque (1), persistencia (4), carga de parámetros (6) y datos iniciales (6). Las pruebas JPA revierten los datos que modifican, pero Flyway deja instaladas las migraciones y sus datos iniciales. Las pruebas de V2 esperan los valores fijos del ERS; no usar una base operativa cuyos parámetros ya fueron personalizados.

La verificación local del 18 de noviembre terminó con `BUILD SUCCESS`: 17 pruebas aprobadas desde una base vacía y una prueba adicional de arranque para actualizar una base existente de V1.1 a V2. Esto no acredita todavía los CP/CV/NF del simulador completo.

El job de interfaz del CI se omite sin `frontend/package-lock.json`; que salga verde no significa que exista o se haya probado el front-end.

## Cómo trabajamos en GitHub

- `main` es la rama principal: siempre compila, pasa las pruebas y es lo que se despliega.
- Cada tarea va en una rama corta desde `main`: `feature/…`, `fix/…`, `test/…`, `docs/…`, `chore/…`.
- Al terminar: actualizar `main`, unir la rama, correr `./mvnw verify`, hacer push y avisar en el grupo. No se usan Pull Requests.
- Commits con *Conventional Commits* en español: `feat(calculo): calcular factor de sombra (RF-03)`.
- Nunca `push --force` sobre `main`.
- Código propio, comentarios, mensajes y documentación en español, conservando los nombres del ERS y los identificadores obligatorios de las herramientas.

El reparto por funcionalidad está en `docs/Plan_de_trabajo_SOLSTITIUM.pdf`. Sus fechas antiguas no se aplican: la entrega es el **22 de noviembre de 2026**. El flujo actualizado se acordó en el chat. El avance técnico se documenta en `docs/Documentacion_avance_SOLSTITIUM.docx`.
