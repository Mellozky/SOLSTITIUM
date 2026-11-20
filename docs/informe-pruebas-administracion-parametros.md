# Verificación de parámetros y bitácora administrativos

Fecha: 20 de noviembre de 2026. Alcance solicitado: consulta y actualización de registros
existentes, sesión, límites, coherencia y auditoría. CU-07–CU-12 y CU-15. Sin pantallas
administrativas ni CRUD de paneles. Contrato: `contrato-administracion-parametros.md`.

## Ejecución local

`./mvnw.cmd verify`, con JDK 21.0.10 y PostgreSQL 17.11, contra una base exclusiva
`solstitium_pruebas_administracion`, instalada con Flyway V1, V1.1 y V2.
Resultado: **293 pruebas, cero fallos, cero errores y cero omitidas; BUILD SUCCESS**.
De ellas, 74 corresponden a esta funcionalidad: 71 de MVC/integración y tres de
transacción/concurrencia. Las otras 219 conservan la regresión del motor y la API pública.

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

El ERS original no estaba disponible en la ruta de AGENTS.md ni se proporcionó otra.
Los criterios administrativos solicitados están verificados, pero la correspondencia
exacta de cada fila CV-21–CV-23, CV-27 y CV-28 requiere contrastar ese PDF; no se declara
certificación documental de esos identificadores. No se verificaron pantallas,
aceptación desde navegador ni producción. Los límites amplios de V2 siguen siendo
técnicos/matemáticos: no equivalen a rangos operativos de ingeniería aprobados.
