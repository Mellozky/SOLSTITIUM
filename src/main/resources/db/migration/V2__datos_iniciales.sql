-- Datos SUPUESTOS de prueba, no tarifas ni cotizaciones reales.
-- Fuente: ERS v1.4, capítulo 10, tabla 30 (página 57).
-- Se ejecuta una sola vez con Flyway; no sobrescribe posteriores cambios administrativos.
-- Los porcentajes se guardan como porcentajes (5 = 5 %); pesos y factores como fracciones.
-- Los identificadores serial se generan en PostgreSQL para conservar sus secuencias.

INSERT INTO parametro (clave, valor, unidad, descripcion) VALUES
    ('costo_unitario_kwh', 1000, 'COP/kWh', 'Costo unitario supuesto para las pruebas del ERS'),
    ('consumo_subsistencia', 173, 'kWh/mes', 'Consumo mensual cubierto por el subsidio'),
    ('incremento_anual_tarifa', 5, '%', 'Incremento anual de la tarifa para la proyección'),
    ('hsp', 4.5, 'h/día', 'Horas de sol pico supuestas para las pruebas'),
    ('factor_rendimiento', 0.80, 'fracción', 'Factor de rendimiento del sistema'),
    ('factor_aprovechamiento', 0.75, 'fracción', 'Fracción del área libre aprovechable'),
    ('relacion_cc_ca', 1.2, 'relación', 'Relación entre potencia continua y alterna'),
    ('vida_util', 25, 'años', 'Horizonte de evaluación del sistema'),
    ('tope_sobredimensionamiento', 25, '%', 'Margen máximo para compensar la sombra'),
    ('amplificacion_serie', 3, 'factor', 'Amplificación de la pérdida por sombra en cada franja'),
    ('consumo_maximo', 2000, 'kWh/mes', 'Tope admitido de consumo mensual'),
    ('umbral_advertencia_consumo', 800, 'kWh/mes', 'Por encima de este consumo se muestra una advertencia'),
    ('area_minima', 5, 'm²', 'Área libre mínima admitida en el formulario'),
    ('area_maxima', 500, 'm²', 'Área libre máxima admitida en el formulario'),
    ('umbral_diferencia_factura', 20, '%', 'Diferencia máxima sin advertencia respecto de la factura estimada');

INSERT INTO estrato (numero, tipo_ajuste, porcentaje_ajuste) VALUES
    (1, 'subsidio', 50), (2, 'subsidio', 40), (3, 'subsidio', 15),
    (4, 'ninguno', 0), (5, 'contribucion', 20), (6, 'contribucion', 20);

INSERT INTO franja_horaria (id, nombre, orden, hora_inicio, hora_fin, peso) VALUES
    (1, 'Mañana', 1, '06:00', '10:00', 0.15),
    (2, 'Mediodía', 2, '10:00', '14:00', 0.70),
    (3, 'Tarde', 3, '14:00', '18:00', 0.15);

INSERT INTO porcion_afectada (id, etiqueta, orden, valor) VALUES
    (1, 'Nada', 1, 0), (2, 'Un borde o una esquina', 2, 0.10),
    (3, 'Cerca de una cuarta parte', 3, 0.25), (4, 'La mitad o más', 4, 0.50);

INSERT INTO configuracion (id, codigo, nombre, costo_kwp, coeficiente_recuperacion) VALUES
    (1, 'central', 'Inversor central', 4000000, 0),
    (2, 'mlpe', 'Electrónica a nivel de módulo', 4600000, 0.36);

-- Intervalos: [mínimo, máximo), salvo el punto sin sombra [1, 1].
-- Estos extremos se interpretarán en la calculadora/validador; los CHECK no lo hacen.
INSERT INTO tramo_sombreado (id, nombre, orden, factor_minimo, factor_maximo, muestra_advertencia) VALUES
    (1, 'Sin sombra', 1, 1, 1, false),
    (2, 'Leve', 2, 0.90, 1, false),
    (3, 'Moderada', 3, 0.75, 0.90, false),
    (4, 'Alta', 4, 0.55, 0.75, false),
    (5, 'Severa', 5, 0, 0.55, true);

INSERT INTO tramo_configuracion (tramo_id, configuracion_id) VALUES
    (1, 1), (2, 1), (3, 1), (4, 1), (4, 2), (5, 1), (5, 2);

-- Marca/modelo ficticios: la ERS solo fija potencia y área para estos dos paneles.
INSERT INTO panel (marca, modelo, potencia_wp, area_m2, es_referencia, activo) VALUES
    ('DEMOSTRACIÓN', 'Referencia ERS 550', 550, 2.6, true, true),
    ('DEMOSTRACIÓN', 'Alterno ERS 450', 450, 2.1, false, true);

-- Viable [0, 6]; medianamente viable (6, 10]; no viable (10, infinito).
-- No recuperar la inversión también se clasifica como no viable en la calculadora.
INSERT INTO rango_viabilidad (id, clasificacion, orden, anios_minimo, anios_maximo) VALUES
    (1, 'Viable', 1, 0, 6),
    (2, 'Medianamente viable', 2, 6, 10),
    (3, 'No viable', 3, 10, NULL);

-- Límites de edición: solo 500..3000 para costo_unitario_kwh está fijado por el ERS (CV-20).
-- Los restantes son límites MATEMÁTICOS/TÉCNICOS, no rangos recomendados de ingeniería.
-- Se dejan amplios hasta acordar rangos operativos. El futuro ValidadorParametros
-- comprobará además coherencia, enteros donde aplique, divisores positivos y cobertura.
-- Para horas, el límite numérico representa minutos desde medianoche (0..1439).
-- Los booleanos, etiquetas y asociaciones se validan por su tipo y reglas, no como números.
INSERT INTO limite_parametro (tabla, campo, clave, valor_minimo, valor_maximo) VALUES
    ('parametro', 'valor', 'costo_unitario_kwh', 500, 3000),
    ('parametro', 'valor', 'consumo_subsistencia', 0, 9999999999.9999),
    ('parametro', 'valor', 'incremento_anual_tarifa', 0, 9999999999.9999),
    ('parametro', 'valor', 'hsp', 0.0001, 24),
    ('parametro', 'valor', 'factor_rendimiento', 0.0001, 1),
    ('parametro', 'valor', 'factor_aprovechamiento', 0.0001, 1),
    ('parametro', 'valor', 'relacion_cc_ca', 0.0001, 9999999999.9999),
    ('parametro', 'valor', 'vida_util', 1, 9999999999),
    ('parametro', 'valor', 'tope_sobredimensionamiento', 0, 9999999999.9999),
    ('parametro', 'valor', 'amplificacion_serie', 0, 9999999999.9999),
    ('parametro', 'valor', 'consumo_maximo', 0.0001, 9999999999.9999),
    ('parametro', 'valor', 'umbral_advertencia_consumo', 0, 9999999999.9999),
    ('parametro', 'valor', 'area_minima', 0.0001, 9999999999.9999),
    ('parametro', 'valor', 'area_maxima', 0.0001, 9999999999.9999),
    ('parametro', 'valor', 'umbral_diferencia_factura', 0, 9999999999.9999),
    ('estrato', 'porcentaje_ajuste', NULL, 0, 100),
    ('franja_horaria', 'hora_inicio', NULL, 0, 1439),
    ('franja_horaria', 'hora_fin', NULL, 0, 1439),
    ('franja_horaria', 'peso', NULL, 0, 1),
    ('porcion_afectada', 'valor', NULL, 0, 1),
    ('configuracion', 'costo_kwp', NULL, 0.01, 9999999999.99),
    ('configuracion', 'coeficiente_recuperacion', NULL, 0, 1),
    ('tramo_sombreado', 'factor_minimo', NULL, 0, 1),
    ('tramo_sombreado', 'factor_maximo', NULL, 0, 1),
    ('panel', 'potencia_wp', NULL, 0.01, 99999.99),
    ('panel', 'area_m2', NULL, 0.001, 99.999),
    ('rango_viabilidad', 'anios_minimo', NULL, 0, 999.99),
    ('rango_viabilidad', 'anios_maximo', NULL, 0, 999.99);

-- No crear credenciales compartidas en un repositorio público. El administrador
-- se aprovisionará con BCrypt mediante un procedimiento separado para cada entorno.
-- La bitácora queda vacía: la instalación no representa cambios hechos por un administrador.
