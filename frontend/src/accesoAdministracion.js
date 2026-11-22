export const RUTA_ADMINISTRACION = '/admin';
export const RUTA_SIMULADOR = '/';
export const MENSAJE_DATOS_PERDIDOS =
  'Al ir a la administración se perderán los datos ingresados en la simulación. ¿Deseas continuar?';

const tieneTexto = (valor) => typeof valor === 'string' && valor.trim() !== '';

// Las simulaciones no se guardan: salir del simulador descarta lo ingresado.
export function hayDatosIngresados({ meses, factura, estrato, area, seleccionesSombra }) {
  return meses.some(tieneTexto)
    || tieneTexto(factura)
    || estrato !== null
    || tieneTexto(area)
    || Object.keys(seleccionesSombra).length > 0;
}

export function debeConfirmarSalida(datos, confirmar) {
  return !hayDatosIngresados(datos) || confirmar(MENSAJE_DATOS_PERDIDOS);
}
