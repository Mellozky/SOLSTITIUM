import assert from 'node:assert/strict';
import test from 'node:test';
import { prepararConsumo, revisarConsumo } from './entrada.js';

const parametros = { consumoMaximo: 2000, umbralAdvertenciaConsumo: 800 };

test('octubre va arriba y su factura se compara con el último consumo de la API', () => {
  const octubreYSeptiembre = ['150', '120'];
  const entrada = prepararConsumo(octubreYSeptiembre, '75000');

  assert.deepEqual(entrada, { consumos: [120, 150], valorFactura: 75000 });
  assert.deepEqual(octubreYSeptiembre, ['150', '120']);
});

test('acepta de uno a doce meses y omite la factura vacía', () => {
  const unMes = revisarConsumo(['150,5', ...Array(11).fill('')], '', parametros);
  assert.deepEqual(unMes.errores, {});
  assert.deepEqual(prepararConsumo(unMes.mesesIngresados, ''), { consumos: [150.5] });

  const doceMeses = revisarConsumo(Array(12).fill('100'), '', parametros);
  assert.equal(doceMeses.mesesIngresados.length, 12);
  assert.deepEqual(doceMeses.errores, {});
});

test('valida meses consecutivos, factura y límites configurados', () => {
  assert.deepEqual(Object.keys(revisarConsumo(['', '', '20'], '0', parametros).errores),
    ['0', '1', 'factura']);
  assert.deepEqual(Object.keys(revisarConsumo(['800', '801', '2000', '2001'], '', parametros).errores),
    ['3']);
  assert.deepEqual(Object.keys(revisarConsumo(['800', '801', '2000'], '', parametros).advertencias),
    ['1', '2']);
});
