# Verificación del motor y la API de simulación

Fecha de corte: 20 de noviembre de 2026. Datos: supuestos de la tabla 30 del ERS v1.4; no son precios ni cotizaciones reales.

## Alcance automatizado

La suite verifica persistencia, datos iniciales, autenticación con sesión y CSRF, entrada, tarifa, sombra, dimensionamiento, generación, ahorro, recuperación, ROI, recomendación, escenario sin sombra y API pública. El comando es `./mvnw.cmd verify` (Windows) o `./mvnw verify`, con JDK 21 y una base PostgreSQL exclusiva de prueba.

`RegresionErsTest` contrasta los quince resultados de los once escenarios CP-01 a CP-11 de las tablas 32–34. Paneles, potencia, inversor e inversión se verifican exactamente. Para cifras redondeadas del documento se usa media unidad de su último decimal: 0,5 COP en ahorro, 0,05 puntos en cobertura/ROI y 0,005 años en recuperación. Se mantiene mayor precisión interna para clasificar y recomendar. Estas tolerancias son más estrictas que el margen global de 1 % del ERS.

CP-09 cambia la referencia al panel alterno; CP-11 cambia el costo unitario a 1.200. Se prueban también ausencia de recuperación, empate exacto, escenario sin sombra y ausencia de modificaciones en las doce tablas al simular.

### Orden de los meses en CP-10

La tabla de entradas muestra 900 y 850 kWh, pero el aviso esperado de 1.080.000 COP corresponde al mes de 900. El contrato de la API define orden cronológico, con el más reciente al final. La regresión usa `[850, 900]`: conserva el promedio 875 y todos los resultados financieros, y hace que el aviso esperado corresponda al último mes. Con `[900, 850]`, la factura estimada del último mes sería 1.020.000 COP; no se cambia la regla del ERS para forzar otro resultado.

## Validación y seguridad comprobadas

- Entrada: CV-01–CV-15 mediante validación, bordes configurables y comparación de factura; CV-16 mediante fallo simulado del servicio o base, devolviendo 503 sin resultados.
- Autenticación: rechazo de usuario inactivo y credenciales erróneas, sesión, cierre de sesión, renovación CSRF y bloqueo de acceso sin autenticar (CV-17–CV-19).
- CV-29: cero paneles en el componente técnico con 3 m²; también se prueba la salida del servicio sin espacio con un panel de mayor área y una entrada permitida por el formulario.
- CORS: origen local permitido y origen ajeno rechazado. Estratos decimales no se truncan. La simulación no requiere sesión ni CSRF; el login conserva esa protección.
- Fallos: 400 para entrada inválida, 503 para servicio/configuración no disponibles y 500 genérico para errores inesperados. Las respuestas no incluyen trazas ni detalles internos.

Los cambios de datos en pruebas se revierten. Las migraciones y sus datos iniciales sí permanecen instalados. Las pruebas de V2 esperan los valores originales: no ejecutarlas sobre una base operativa personalizada.

## Pendiente de aceptación del sistema

Las pruebas actuales no acreditan una interfaz web ni un despliegue. Faltan el front-end, los flujos administrativos de parámetros/paneles/bitácora y las validaciones CV-20–CV-28 desde esos flujos. Las restricciones de base no sustituyen esos casos de uso.

Siguen pendientes la medición desde navegador de los 3 segundos, HTTPS en producción, compatibilidad en navegadores/móvil, disponibilidad medida, cinco usuarios piloto, manuales finales y contraste con dos cotizaciones reales. La actualización de parámetros se comprueba técnicamente, pero el mantenimiento desde una pantalla administrativa todavía no existe. No declarar aprobación integral de NF-01–NF-08 ni aceptación final del proyecto.
