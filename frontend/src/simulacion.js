/**
 * Envía la solicitud completa a POST /api/simulaciones (contrato-simulacion.md) y devuelve
 * el ResultadoSimulacion. Si la API la rechaza, lanza un error con los mensajes en español.
 */
export async function solicitarSimulacion(solicitud, { senal } = {}) {
  let respuesta;
  try {
    respuesta = await fetch('/api/simulaciones', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(solicitud),
      signal: senal,
    });
  } catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new ErrorSimulacion('No pudimos conectar con el simulador. Revisa tu conexión e inténtalo de nuevo.');
  }

  if (respuesta.ok) return respuesta.json();
  if (respuesta.status === 400) {
    const cuerpo = await respuesta.json().catch(() => ({}));
    throw new ErrorSimulacion(cuerpo.mensaje ?? 'Revisa los datos ingresados.', cuerpo.errores ?? []);
  }
  if (respuesta.status === 503) {
    throw new ErrorSimulacion('El simulador no está disponible en este momento. Inténtalo más tarde.');
  }
  throw new ErrorSimulacion('Ocurrió un error inesperado. Inténtalo de nuevo.');
}

export class ErrorSimulacion extends Error {
  constructor(mensaje, errores = []) {
    super(mensaje);
    this.name = 'ErrorSimulacion';
    this.errores = errores;
  }
}
