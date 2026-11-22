export async function solicitarJson(ruta, { datos, signal } = {}) {
  const cabeceras = { Accept: 'application/json' };
  const opciones = { headers: cabeceras, signal, cache: 'no-store' };

  if (datos !== undefined) {
    cabeceras['Content-Type'] = 'application/json';
    opciones.method = 'POST';
    opciones.body = JSON.stringify(datos);
  }

  let respuesta;
  try {
    respuesta = await fetch(`/api/${ruta}`, opciones);
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new Error('No se pudo conectar con el servidor. Intenta nuevamente.');
  }

  if (respuesta.status === 204) return null;

  let contenido;
  try {
    contenido = await respuesta.json();
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new Error('El servidor devolvió una respuesta que no se pudo leer.');
  }

  if (!respuesta.ok) {
    const mensaje = typeof contenido?.mensaje === 'string' && contenido.mensaje.trim();
    const error = new Error(mensaje || 'El servidor rechazó la solicitud. Intenta nuevamente.');
    error.detalles = Array.isArray(contenido?.errores)
      ? contenido.errores.filter((detalle) => typeof detalle === 'string' && detalle.trim())
      : [];
    throw error;
  }

  return contenido;
}
