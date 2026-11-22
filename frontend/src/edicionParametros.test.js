import assert from 'node:assert/strict';
import test from 'node:test';
import { camposDe, claveCampo, prepararCambios } from './edicionParametros.js';

function registro(tabla, registroId, valores) {
  return { tabla, registroId, valores };
}

test('envía juntos solo los valores modificados con los tipos del contrato', () => {
  const tarifa = registro('parametro', 'incremento_anual_tarifa', { valor: 5 });
  const estrato = registro('estrato', '1', { porcentaje_ajuste: 52, tipo_ajuste: 'subsidio' });
  const borrador = {
    [claveCampo(tarifa, 'valor')]: '06,00',
    [claveCampo(estrato, 'porcentaje_ajuste')]: '52,0',
    [claveCampo(estrato, 'tipo_ajuste')]: 'contribucion',
  };
  assert.deepEqual(prepararCambios([tarifa, estrato], 'tarifas', borrador), {
    cambios: [{ tabla: 'parametro', registroId: 'incremento_anual_tarifa', campo: 'valor', valorNuevo: 6 }],
    errores: {},
  });
});

test('conserva horas, booleanos y listas, y omite la recuperación de central', () => {
  const central = registro('configuracion', '1', { codigo: 'central', coeficiente_recuperacion: 0 });
  const mlpe = registro('configuracion', '2', { codigo: 'mlpe', coeficiente_recuperacion: 0.2 });
  const franja = registro('franja_horaria', '1', { hora_inicio: '08:00:00' });
  const tramo = registro('tramo_sombreado', '1', { muestra_advertencia: true, configuraciones: [1, 2] });
  const borrador = {
    [claveCampo(central, 'coeficiente_recuperacion')]: '0.1',
    [claveCampo(mlpe, 'coeficiente_recuperacion')]: '0,25',
    [claveCampo(franja, 'hora_inicio')]: '08:30',
    [claveCampo(tramo, 'muestra_advertencia')]: false,
    [claveCampo(tramo, 'configuraciones')]: [2],
  };
  const { cambios, errores } = prepararCambios([central, mlpe, franja, tramo], 'sombreado', borrador);
  assert.deepEqual(errores, {});
  assert.deepEqual(cambios.map(({ campo, valorNuevo }) => [campo, valorNuevo]), [
    ['coeficiente_recuperacion', 0.25], ['hora_inicio', '08:30'],
    ['muestra_advertencia', false], ['configuraciones', [2]],
  ]);
  assert.deepEqual(camposDe(mlpe, 'costos'), ['costo_kwp']);
});

test('deja al backend validar los límites y rechaza la pérdida de precisión local', () => {
  const tarifa = registro('parametro', 'costo_unitario_kwh', { valor: 1000 });
  const clave = claveCampo(tarifa, 'valor');
  assert.equal(prepararCambios([tarifa], 'tarifas', { [clave]: '3001' }).cambios[0].valorNuevo, 3001);
  assert.deepEqual(prepararCambios([tarifa], 'tarifas', { [clave]: '1000.00000000000001' }).errores,
    { [clave]: 'Ingresa un número con menos dígitos.' });
});

test('acepta un máximo nulo solo para el último rango', () => {
  const primero = registro('rango_viabilidad', '1', { orden: 1, anios_maximo: 6 });
  const ultimo = registro('rango_viabilidad', '2', { orden: 2, anios_maximo: 12 });
  const clavePrimero = claveCampo(primero, 'anios_maximo');
  const { cambios, errores } = prepararCambios([primero, ultimo], 'rangos', {
    [clavePrimero]: '', [claveCampo(ultimo, 'anios_maximo')]: '',
  });
  assert.deepEqual(errores, { [clavePrimero]: 'Ingresa un número válido.' });
  assert.deepEqual(cambios, [{ tabla: 'rango_viabilidad', registroId: '2', campo: 'anios_maximo', valorNuevo: null }]);
});
