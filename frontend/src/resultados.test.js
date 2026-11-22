import assert from 'node:assert/strict';
import test from 'node:test';
import { conRecomendacion, sinEspacio, sinRecuperacion } from './ejemplos-resultados.js';
import { decimalesAnios, formatearAnios, formatearFecha, formatearPesos, prepararResultados } from './resultados.js';

const etiquetasIndicadores = (tarjeta) => tarjeta.indicadores.map(({ etiqueta }) => etiqueta);
const valor = (tarjeta, etiqueta) => tarjeta.indicadores.find((indicador) => indicador.etiqueta === etiqueta).valor;

test('da formato colombiano a pesos, años y fechas sin zona horaria', () => {
  assert.equal(formatearPesos(262993.5), '$262.994');
  assert.equal(formatearPesos(15400000), '$15.400.000');
  assert.equal(formatearAnios(4.468603122890204), '4,5 años');
  assert.equal(formatearAnios(4.468603122890204, 2), '4,47 años');
  assert.equal(formatearAnios(null), 'No se recupera');
  assert.equal(formatearFecha('2026-11-21T23:54:24.620574'), '21 de noviembre de 2026');
});

test('usa dos decimales solo si dos tiempos distintos se verían iguales', () => {
  assert.equal(decimalesAnios([4.468603122890204, 4.501288614883259]), 2);
  assert.equal(decimalesAnios([4.6, 4.8]), 1);
  assert.equal(decimalesAnios([4.5, 4.5]), 1);
  assert.equal(decimalesAnios([null, 14.06]), 1);
});

test('con recomendación: muestra la recomendada, sus indicadores y el escenario sin sombra (CP-04)', () => {
  const vista = prepararResultados(conRecomendacion);

  assert.equal(vista.caso, 'recomendada');
  assert.equal(vista.encabezado.insignia, 'Viable');
  assert.equal(vista.encabezado.titulo, 'Instalar paneles solares es viable para tu hogar');
  assert.match(vista.encabezado.texto, /el inversor central: recuperas tu inversión en 4,47 años, antes que con optimizadores \(4,50 años\)/);
  assert.equal(vista.encabezado.ahorro.valor, '$262.994');
  assert.equal(vista.encabezado.ahorro.detalle, 'Cubre el 87,7 % de tu consumo');

  assert.deepEqual(vista.tarjetas.map(({ nombre, recomendada }) => [nombre, recomendada]),
    [['Inversor central', true], ['Con optimizadores (MLPE)', false]]);
  const [central, mlpe] = vista.tarjetas;
  assert.deepEqual(etiquetasIndicadores(central), ['Paneles', 'Potencia instalada', 'Inversor', 'Inversión estimada',
    'Ahorro mensual', 'Cobertura del consumo', 'Recuperas en', 'ROI en la vida útil']);
  assert.equal(valor(central, 'Paneles'), '7');
  assert.equal(valor(central, 'Potencia instalada'), '3,85 kWp');
  assert.equal(valor(central, 'Inversor'), '3,5 kW');
  assert.equal(valor(central, 'Inversión estimada'), '$15.400.000');
  assert.equal(valor(central, 'ROI en la vida útil'), '878,1 %');
  assert.equal(valor(mlpe, 'Inversión estimada'), '$17.710.000');
  assert.equal(valor(mlpe, 'Ahorro mensual'), '$300.000');

  assert.match(vista.explicacion.titulo, /el inversor central/);
  assert.equal(vista.explicacion.texto, 'Con optimizadores ahorrarías $300.000 al mes con una inversión de $17.710.000; '
    + 'con el inversor central ahorras $262.994 con $15.400.000. Con el inversor central recuperas antes lo que inviertes.');
  assert.deepEqual(vista.gastoMensual.filas.map(({ etiqueta, valor: monto }) => [etiqueta, monto]),
    [['Hoy, sin paneles', '$300.000'], ['Con inversor central', '$37.007'], ['Con optimizadores', '$0']]);
  assert.deepEqual(vista.escenarioSinSombra,
    { ahorro: '$300.000', recuperacion: '3,4 años', actual: { ahorro: '$262.994', recuperacion: '4,47 años' } });
  assert.equal(vista.etiquetas[0].texto, 'Sombra asumida: alta');
});

test('ninguna recupera: no recomienda, conserva indicadores y explica el motivo del back-end', () => {
  const vista = prepararResultados(sinRecuperacion);

  assert.equal(vista.caso, 'sinRecuperacion');
  assert.equal(vista.encabezado.titulo, 'Con estos datos, la inversión no se recupera');
  assert.equal(vista.encabezado.texto, 'Ninguna configuración recupera la inversión durante la vida útil.');
  assert.equal(vista.encabezado.ahorro, undefined);
  assert.ok(vista.tarjetas.every(({ recomendada }) => !recomendada));
  assert.ok(vista.tarjetas.every((tarjeta) => valor(tarjeta, 'Recuperas en') === 'No se recupera'));
  assert.equal(valor(vista.tarjetas[0], 'ROI en la vida útil'), '-100 %');
  assert.equal(vista.explicacion, null);
  assert.equal(vista.escenarioSinSombra.recuperacion, 'No se recupera');
  assert.equal(vista.escenarioSinSombra.actual, null);
  assert.ok(vista.advertencias.some((aviso) => aviso.includes('Baja confianza')));
});

test('sin espacio: indica «Sin espacio» y omite todos los indicadores financieros', () => {
  const vista = prepararResultados(sinEspacio);

  assert.equal(vista.caso, 'sinEspacio');
  assert.equal(vista.encabezado.insignia, 'Sin espacio');
  assert.equal(vista.encabezado.texto, 'El techo no dispone de espacio suficiente para instalar un panel.');
  assert.equal(vista.encabezado.ahorro, undefined);
  assert.deepEqual(vista.tarjetas, []);
  assert.equal(vista.explicacion, null);
  assert.equal(vista.gastoMensual, null);
  assert.equal(vista.escenarioSinSombra, null);
  assert.deepEqual(vista.advertencias, ['Esta simulación es una estimación, no una cotización.']);
});
