// La base se instala con los supuestos de prueba del ERS (migración V2). Mientras no se carguen
// valores reales desde la administración, la interfaz lo advierte. Para ocultar el aviso, compilar
// con VITE_AVISO_DATOS_PRUEBA=false.
const configuracion = import.meta.env?.VITE_AVISO_DATOS_PRUEBA;

export const MOSTRAR_AVISO_DATOS_PRUEBA = configuracion !== 'false';

export const TITULO_AVISO_DATOS_PRUEBA = 'Versión de demostración';
export const TEXTO_AVISO_DATOS_PRUEBA = 'Las tarifas, los costos y los demás parámetros son supuestos '
  + 'de prueba, no valores reales del mercado. Usa los resultados solo como ejemplo.';
