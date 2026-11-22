import assert from 'node:assert/strict';
import test from 'node:test';
import { navegarPaso } from './rutasSimulacion.js';

function crearHistorial(rutas) {
  const entradas = rutas.map((ruta) => ({ ruta, estado: null }));
  let posicion = entradas.length - 1;
  return {
    get state() { return entradas[posicion].estado; },
    get ruta() { return entradas[posicion].ruta; },
    get length() { return entradas.length; },
    pushState(estado, _, ruta) {
      entradas.splice(posicion + 1, entradas.length, { ruta, estado });
      posicion++;
    },
    replaceState(estado, _, ruta) {
      entradas[posicion] = { ruta, estado };
    },
    back() { posicion = Math.max(0, posicion - 1); },
    forward() { posicion = Math.min(entradas.length - 1, posicion + 1); },
  };
}

test('Atrás en Sombra retrocede a Vivienda y Atrás del navegador vuelve a Consumo', () => {
  const historial = crearHistorial(['/']);

  navegarPaso(historial, 0, 1);
  navegarPaso(historial, 1, 2);
  assert.equal(historial.ruta, '/simulacion/sombra');

  assert.equal(navegarPaso(historial, 2, 1), false);
  assert.equal(historial.ruta, '/simulacion/vivienda');
  assert.equal(historial.length, 3);

  historial.back();
  assert.equal(historial.ruta, '/');
  historial.forward();
  assert.equal(historial.ruta, '/simulacion/vivienda');
  historial.forward();
  assert.equal(historial.ruta, '/simulacion/sombra');
});

test('Atrás sin un paso previo registrado reemplaza la entrada y conserva el historial ajeno', () => {
  const historial = crearHistorial(['/admin/ingreso', '/simulacion/sombra']);

  assert.equal(navegarPaso(historial, 2, 1), true);
  assert.equal(historial.ruta, '/simulacion/vivienda');
  assert.equal(historial.length, 2);
  historial.back();
  assert.equal(historial.ruta, '/admin/ingreso');
});

test('Resultados tiene su propia ruta y Atrás regresa a Sombra', () => {
  const historial = crearHistorial(['/']);

  navegarPaso(historial, 0, 1);
  navegarPaso(historial, 1, 2);
  navegarPaso(historial, 2, 3);
  assert.equal(historial.ruta, '/simulacion/resultados');

  assert.equal(navegarPaso(historial, 3, 2), false);
  assert.equal(historial.ruta, '/simulacion/sombra');
});

test('Nueva simulación desde Resultados reemplaza la entrada y vuelve a Consumo', () => {
  const historial = crearHistorial(['/simulacion/resultados']);

  assert.equal(navegarPaso(historial, 3, 0), true);
  assert.equal(historial.ruta, '/simulacion/consumo');
  assert.equal(historial.length, 1);
});
