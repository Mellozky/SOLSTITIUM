export const MAXIMO_MESES = 12;

export function numeroDecimal(valor) {
  const texto = valor.trim();
  if (!/^(?:\d+(?:[.,]\d+)?|[.,]\d+)$/.test(texto)) return NaN;
  const numero = Number(texto.replace(',', '.'));
  return Number.isFinite(numero) ? numero : NaN;
}

export function prepararConsumo(meses, factura) {
  const consumos = meses.map(numeroDecimal).toReversed();
  return factura.trim()
    ? { consumos, valorFactura: numeroDecimal(factura) }
    : { consumos };
}

export function revisarConsumo(meses, factura, parametros) {
  const errores = {};
  const advertencias = {};
  const ultimo = meses.findLastIndex((mes) => mes.trim() !== '');

  meses.slice(0, Math.max(ultimo + 1, 1)).forEach((valor, indice) => {
    const consumo = numeroDecimal(valor);
    if (!(consumo > 0)) {
      errores[indice] = valor.trim()
        ? 'Ingresa un consumo mayor que cero.'
        : 'Ingresa el consumo de este mes.';
    } else if (consumo > parametros.consumoMaximo) {
      errores[indice] = `El máximo es ${parametros.consumoMaximo} kWh.`;
    } else if (consumo > parametros.umbralAdvertenciaConsumo) {
      advertencias[indice] = 'Consumo poco común. Revisa el valor.';
    }
  });

  if (factura.trim() && !(numeroDecimal(factura) > 0)) {
    errores.factura = 'Ingresa un valor mayor que cero.';
  }

  return { mesesIngresados: meses.slice(0, ultimo + 1), errores, advertencias };
}
