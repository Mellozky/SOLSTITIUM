import { test } from 'node:test';
import assert from 'node:assert/strict';
import { debeConfirmarSalida, hayDatosIngresados, MENSAJE_DATOS_PERDIDOS } from './accesoAdministracion.js';

const vacio = () => ({ meses: Array(12).fill(''), factura: '', estrato: null, area: '', seleccionesSombra: {} });

test('un simulador recién abierto no tiene datos ingresados', () => {
  assert.equal(hayDatosIngresados(vacio()), false);
});

test('los espacios en blanco no cuentan como datos', () => {
  assert.equal(hayDatosIngresados({ ...vacio(), meses: ['  ', ...Array(11).fill('')], factura: ' ', area: '\t' }), false);
});

test('cualquier dato ingresado cuenta', () => {
  const casos = [
    { meses: ['150', ...Array(11).fill('')] },
    { factura: '80000' },
    { estrato: 3 },
    { area: '20' },
    { seleccionesSombra: { 1: 2 } },
  ];
  for (const caso of casos) assert.equal(hayDatosIngresados({ ...vacio(), ...caso }), true, JSON.stringify(caso));
});

test('sin datos se sale sin preguntar', () => {
  let preguntas = 0;
  assert.equal(debeConfirmarSalida(vacio(), () => { preguntas += 1; return false; }), true);
  assert.equal(preguntas, 0);
});

test('con datos se pregunta y se respeta la respuesta', () => {
  const datos = { ...vacio(), estrato: 2 };
  const mensajes = [];
  assert.equal(debeConfirmarSalida(datos, (mensaje) => { mensajes.push(mensaje); return true; }), true);
  assert.equal(debeConfirmarSalida(datos, () => false), false);
  assert.deepEqual(mensajes, [MENSAJE_DATOS_PERDIDOS]);
});
