# Verificación de parámetros y bitácora administrativos

Fecha: 20 de noviembre de 2026. Alcance solicitado: consulta y actualización de registros
existentes, sesión, límites, coherencia y auditoría. CU-07–CU-12 y CU-15. Sin pantallas
administrativas ni CRUD de paneles. Contrato: `contrato-administracion-parametros.md`.

## Ejecución local

`./mvnw.cmd -B -ntp verify`, con JDK 21.0.10 y PostgreSQL 18.7, contra una base exclusiva
`solstitium_pruebas_correcciones`, instalada con Flyway V1, V1.1 y V2.
Resultado: **333 pruebas, cero fallos, cero errores y cero omitidas; BUILD SUCCESS**.
De ellas, 102 corresponden a administración: 99 de MVC/integración y tres de
transacción/concurrencia. Las otras 231 incluyen el motor, sus límites técnicos y la API pública.

Antes de corregir el código se ejecutaron 21 regresiones seleccionadas: 17 fallaron
por los dos defectos o por la distribución de secciones, y cuatro ya pasaban. La
ejecución previa de 293 pruebas no detectaba los cambios inválidos de central ni
el horizonte aceptado por administración y rechazado por el motor.

## Correcciones verificadas

- El coeficiente solo es editable para MLPE. Cambiar central a 0,36 devuelve 400 y
  revierte todo el lote propuesto, incluso otros valores válidos. CP-08 conserva
  ahorro central cero, ahorro MLPE 32.076 COP y recomendación MLPE.
- La coherencia global exige coeficiente central cero incluso cuando el lote modifica
  otro parámetro. El motor también rechaza datos central incoherentes sin calcular ahorro.
- Administración y motor comparten LimitesCalculoFinanciero: vida útil entera positiva,
  máximo técnico 999999999 y potencia representable para el incremento anual propuesto.
  Los valores 1000000000, 2147483647 y 2147483648 se rechazan sin cambios ni bitácora;
  después del rechazo, una simulación con los valores anteriores sigue funcionando.
- Se prueba el máximo técnico y la combinación extrema que desborda la potencia.
  Esta comprobación evita expandir un resultado intermedio gigantesco. El máximo
  técnico no sustituye los límites operativos de limite_parametro ni recomienda
  una vida útil de esa magnitud; también se comprueba que prevalezca un límite de BD menor.
- Las consultas y sus límites se distribuyen según CU-08–CU-12. CU-12 incluye consumo,
  área y diferencia de factura; CU-09 incluye el tope de sobredimensionamiento.
- CI se alinea con PostgreSQL 18 y JDK 21. La base existente del usuario no se utilizó
  para estas pruebas ni se modificaron migraciones publicadas.

## Evidencia funcional

- Las cinco secciones se consultan con valores y límites, mediante DTO.
- Sesión real: login, actualización y consulta de auditoría del mismo administrador.
- Acceso anónimo, rol ajeno, escritura sin CSRF y cuenta desactivada son rechazados.
- No se aceptan identidades administrativas ni atributos desconocidos en el cuerpo.
- CV-20: costo unitario admite 500 y 3000; rechaza valores fuera de esos extremos.
  Se comprueba además que cambiar el límite en la BD modifica la validación.
- Se rechazan redondeo silencioso, tipos incorrectos, destinos duplicados, registros
  inexistentes y nombres de tabla/campo ajenos a la lista explícita.
- Límites, paneles y tipo de ajuste del estrato no se pueden editar por estas rutas.
- Pesos: rechaza una suma incorrecta; acepta un lote que cambia dos pesos manteniendo 1.
- Tramos y rangos: rechaza huecos, cruces y extremos inválidos; acepta mover fronteras
  conjuntamente. Cada tramo debe incluir central y solo configuraciones existentes.
- Horarios, porciones, costos, coeficientes, áreas, umbrales y referencia activa se validan.
- Un valor numéricamente igual no genera registro ni actualiza la fecha. Un lote mixto
  solo audita los valores distintos; los parámetros generales modificados cambian su fecha.
- Bitácora con valor anterior/nuevo y autor de sesión; orden fecha DESC e id DESC.

## Correspondencia exacta con el ERS original

Se contrastó la tabla 37 (página 62 del PDF facilitado por el usuario):

| Caso | Entrada o acción verificada |
|---|---|
| CV-20 | Costo unitario 3001: rechazo sin auditoría |
| CV-21 | Pesos 0,15 / 0,70 / 0,10: rechazo |
| CV-22 | Máximo del tramo moderado 0,92: rechazo del cruce con leve |
| CV-23 | Quitar central del tramo leve dejando MLPE: rechazo |
| CV-27 | Incremento anual de 5 a 6: bitácora con fecha, usuario, anterior y nuevo |
| CV-28 | Error real al insertar la segunda auditoría: rollback completo |

## Rollback real

`TransaccionParametrosTest` no tiene una transacción envolvente de prueba. Crea
temporalmente un trigger PostgreSQL que deja insertar el primer registro de auditoría
y falla al insertar el segundo. El servicio ya sincronizó la tarifa y el porcentaje
del estrato antes de auditar. La excepción revierte la transacción completa.

Después del fallo se leen nuevamente parámetros (incluidas sus fechas), estratos y
bitácora desde fuera de la transacción fallida y se comparan con las filas originales:
no permanece ninguna de esas modificaciones ni el primer registro de auditoría.
El trigger y su función se retiran al terminar. Se verifica también una transacción
exitosa con parámetros y auditoría confirmados.

## Concurrencia y limpieza

Dos transacciones intentan, simultáneamente, fijar el área mínima en 450 y la máxima
en 100. Cada cambio sería válido contra la configuración inicial; juntos serían
incoherentes. El bloqueo serializa los lotes: solo uno confirma y el otro se rechaza.

Los casos MVC usan rollback. Los casos sin transacción envolvente restauran los valores
y fechas iniciales y eliminan su administrador/auditoría de prueba. No usar una base
operativa: esta suite y la regresión del ERS esperan los datos originales de V2.

## Límites de la evidencia

El ERS original está disponible en la ruta proporcionada por el usuario, actualizada
en AGENTS.md. Los casos anteriores se verifican a nivel de API/integración; no se
verificaron pantallas, aceptación desde navegador ni producción. La ejecución local
acredita PostgreSQL 18 de pruebas, no el estado ni los datos de la base existente del
usuario. Los límites amplios de V2 siguen siendo técnicos/matemáticos: no equivalen
a rangos operativos de ingeniería aprobados.

## Revisión del horizonte financiero — 20 de noviembre de 2026

Ejecutado `mvnw.cmd -o -B verify` con Java 21 y PostgreSQL 18 en la base exclusiva `solstitium_pruebas_v2`: 337 pruebas, cero fallos, errores u omitidas.

La combinación vida 999999999 e incremento 9000 ahora devuelve 400 sin modificar parámetros ni bitácora, y la simulación posterior conserva su funcionamiento. También se prueba el rechazo con incremento 5, el máximo de años con incremento cero y la frontera de 100 dígitos del crecimiento compuesto. Este presupuesto técnico evita expansiones desmesuradas; los rangos operativos siguen en la base. Las restas del acumulado y ROI usan DECIMAL128. La regresión CP-01–CP-11 conserva sus tolerancias.
