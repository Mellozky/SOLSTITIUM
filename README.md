# SOLSTITIUM

Simulador de viabilidad para la adopción de energía solar residencial en Montería.
Proyecto de aula de Ingeniería de Software — Universidad de Córdoba. Requisitos: ERS v1.4.

Equipo: Daniel Mendoza · Luis Hernández · Carlos Sandoval · Esteban Vidal

## Stack

- **Back-end** (raíz del repo): Java 21, Spring Boot 4.1, Maven, Spring Data JPA, Flyway, Spring Security.
- **Base de datos:** PostgreSQL (solo parámetros, catálogo de paneles, administradores y bitácora; las simulaciones no se guardan).
- **Front-end** (carpeta `frontend/`): SPA que consume la API REST en JSON.
- **Despliegue:** Render (API) + Vercel (front-end).

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
│   └── dto/           SolicitudSimulacion, ResultadoSimulacion, CambioParametro...
├── config/            SecurityConfig, CORS
└── exception/         manejador global de errores (mensajes claros en español)
src/main/resources/db/migration/   V1__esquema.sql, V2__datos_iniciales.sql (Flyway)
```

## Cómo correrlo

Requisitos: JDK 21 y PostgreSQL con una base `solstitium`.

```bash
# Variables opcionales (por defecto: localhost:5432/solstitium, postgres/postgres)
export DB_URL=jdbc:postgresql://localhost:5432/solstitium DB_USERNAME=postgres DB_PASSWORD=postgres
./mvnw spring-boot:run     # levanta la API
./mvnw verify              # compila y corre las pruebas
```

## Cómo trabajamos en GitHub

- `main` es la rama principal: siempre compila, pasa las pruebas y es lo que se despliega.
- Cada tarea va en una rama corta desde `main`: `feature/…`, `fix/…`, `test/…`, `docs/…`, `chore/…`.
- Al terminar: actualizar `main`, unir la rama, correr `./mvnw verify`, hacer push y avisar en el grupo. No se usan Pull Requests.
- Commits con *Conventional Commits* en español: `feat(calculo): calcular factor de sombra (RF-03)`.
- Nunca `push --force` sobre `main`.
- Cada sprint termina con una etiqueta de versión en `main` (`v0.1.0`, `v0.2.0`, `v1.0.0`).

El plan de trabajo completo está en `docs/Plan_de_trabajo_SOLSTITIUM.pdf`.
