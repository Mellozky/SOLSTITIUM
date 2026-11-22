export async function solicitarJson(ruta, {
  datos,
  metodo = datos === undefined ? 'GET' : 'POST',
  tokenCsrf,
  signal,
} = {}) {
  const cabeceras = new Headers({ Accept: 'application/json' });
  const opciones = {
    method: metodo,
    headers: cabeceras,
    credentials: 'include',
    cache: 'no-store',
    signal,
  };

  if (tokenCsrf) cabeceras.set('X-CSRF-TOKEN', tokenCsrf);

  if (datos !== undefined) {
    cabeceras.set('Content-Type', 'application/json');
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
    if (respuesta.ok) throw new Error('El servidor devolvió una respuesta que no se pudo leer.');
  }

  if (!respuesta.ok) {
    const mensaje = typeof contenido?.mensaje === 'string' && contenido.mensaje.trim();
    const error = new Error(mensaje || 'El servidor rechazó la solicitud. Intenta nuevamente.');
    error.estado = respuesta.status;
    error.detalles = Array.isArray(contenido?.errores)
      ? contenido.errores.filter((detalle) => typeof detalle === 'string' && detalle.trim())
      : [];
    throw error;
  }

  return contenido;
}
