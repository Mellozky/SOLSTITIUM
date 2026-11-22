import assert from 'node:assert/strict';
import test from 'node:test';
import { conRecomendacion } from './ejemplos-resultados.js';
import { ErrorSimulacion, solicitarSimulacion } from './simulacion.js';

function simularFetch(respuesta) {
  const llamadas = [];
  globalThis.fetch = async (url, opciones) => {
    llamadas.push({ url, opciones });
    if (respuesta instanceof Error) throw respuesta;
    return respuesta;
  };
  return llamadas;
}

const json = (estado, cuerpo) => new Response(JSON.stringify(cuerpo), {
  status: estado, headers: { 'Content-Type': 'application/json' },
});

test('envía la solicitud como JSON y devuelve el resultado del back-end', async () => {
  const llamadas = simularFetch(json(200, conRecomendacion));
  const solicitud = { consumos: [280, 320, 300], estrato: 4, areaLibre: 40, respuestasSombra: [] };

  const resultado = await solicitarSimulacion(solicitud);

  assert.equal(llamadas[0].url, '/api/simulaciones');
  assert.equal(llamadas[0].opciones.method, 'POST');
  assert.deepEqual(JSON.parse(llamadas[0].opciones.body), solicitud);
  assert.equal(resultado.recomendada, 'central');
});

test('un 400 conserva el mensaje y los errores en español de la API', async () => {
  simularFetch(json(400, { mensaje: 'Datos inválidos.', errores: ['El área está fuera del rango.'] }));

  await assert.rejects(solicitarSimulacion({}), (error) => error instanceof ErrorSimulacion
    && error.message === 'Datos inválidos.' && error.errores[0] === 'El área está fuera del rango.');
});

test('503, 500 y falla de red dan mensajes comprensibles sin detalles internos', async () => {
  simularFetch(json(503, {}));
  await assert.rejects(solicitarSimulacion({}), /no está disponible/);
  simularFetch(json(500, { traza: 'interna' }));
  await assert.rejects(solicitarSimulacion({}), /error inesperado/);
  simularFetch(new TypeError('Failed to fetch'));
  await assert.rejects(solicitarSimulacion({}), /No pudimos conectar/);
});
