import assert from 'node:assert/strict';
import test from 'node:test';
import { jsPDF } from 'jspdf';
import { conRecomendacion, sinEspacio, sinRecuperacion } from './ejemplos-resultados.js';
import { crearDocumento, nombreArchivo } from './resumenPdf.js';

const fecha = new Date('2026-11-22T18:30:00Z');
// El PDF se genera sin compresión: su texto aparece literal en el contenido del archivo.
const contenido = (resultado) => crearDocumento(resultado, jsPDF, fecha).output();

test('nombra el archivo con la fecha de Montería', () => {
  assert.equal(nombreArchivo(fecha), 'simulacion-solar-monteria-2026-11-22.pdf');
  assert.equal(nombreArchivo(new Date('2026-11-23T03:00:00Z')), 'simulacion-solar-monteria-2026-11-22.pdf');
});

test('incluye los datos que exige el CU-05 en todos los casos', () => {
  for (const resultado of [conRecomendacion, sinRecuperacion, sinEspacio]) {
    const pdf = contenido(resultado);
    assert.ok(pdf.startsWith('%PDF-'));
    assert.match(pdf, new RegExp(`Sombra asumida: ${resultado.tramo.toLowerCase()}`));
    assert.match(pdf, /Panel de referencia: DEMOSTRACI/);
    assert.match(pdf, /Tarifa actualizada el 21 de noviembre de 2026/);
    assert.match(pdf, /Esta es una estimaci/);
  }
});

test('con recomendación lleva los mismos resultados de la pantalla', () => {
  const pdf = contenido(conRecomendacion);

  assert.match(pdf, /Inversor central .* Recomendada/);
  for (const valor of ['$15.400.000', '$17.710.000', '4,47 a', '4,50 a', '878,1 %', '$262.994', '$37.007', '3,4 a']) {
    assert.ok(pdf.includes(valor), `falta ${valor}`);
  }
  assert.match(pdf, /Lo que pagar/);
  assert.match(pdf, /Si quitaras la sombra/);
});

test('ninguna recupera: indica que no se recupera y no marca recomendada', () => {
  const pdf = contenido(sinRecuperacion);

  assert.match(pdf, /Ninguna configuraci/);
  assert.match(pdf, /No se recupera/);
  assert.doesNotMatch(pdf, /Recomendada/);
});

test('sin espacio: omite todos los indicadores financieros', () => {
  const pdf = contenido(sinEspacio);

  assert.match(pdf, /Sin espacio en tu techo/);
  for (const ausente of ['Recuperas en', 'ROI en la vida', 'Ahorro mensual', 'Lo que pagar', 'Si quitaras la sombra']) {
    assert.ok(!pdf.includes(ausente), `no debería incluir ${ausente}`);
  }
});
