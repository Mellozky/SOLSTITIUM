const camposPorTabla = {
  parametro: ['valor'],
  estrato: ['porcentaje_ajuste'],
  franja_horaria: ['hora_inicio', 'hora_fin', 'peso'],
  porcion_afectada: ['valor'],
  configuracion: ['costo_kwp', 'coeficiente_recuperacion'],
  tramo_sombreado: ['factor_minimo', 'factor_maximo', 'muestra_advertencia', 'configuraciones'],
  rango_viabilidad: ['anios_minimo', 'anios_maximo'],
};

export function camposDe(registro, seccion) {
  const campos = camposPorTabla[registro.tabla] ?? [];
  if (registro.tabla !== 'configuracion') return campos;
  return seccion === 'costos' ? ['costo_kwp'] : ['coeficiente_recuperacion'];
}

export function esEditable(registro, campo) {
  return campo !== 'coeficiente_recuperacion' || registro.valores.codigo === 'mlpe';
}

export function valorDeEntrada(campo, valor) {
  if (campo === 'hora_inicio' || campo === 'hora_fin') return valor?.slice(0, 5) ?? '';
  return typeof valor === 'number' ? String(valor) : valor ?? '';
}

export function claveCampo(registro, campo) {
  return JSON.stringify([registro.tabla, registro.registroId, campo]);
}

function convertir(campo, valor, admiteNulo) {
  if (typeof valor === 'boolean' || Array.isArray(valor)) return valor;
  if (campo === 'hora_inicio' || campo === 'hora_fin') {
    if (!/^\d{2}:\d{2}$/.test(valor)) throw new Error('Ingresa una hora válida.');
    return valor;
  }
  const texto = valor.trim().replace(',', '.');
  if (admiteNulo && texto === '') return null;
  if (!/^-?(?:\d+(?:\.\d+)?|\.\d+)$/.test(texto)) throw new Error('Ingresa un número válido.');
  const numero = Number(texto);
  const normalizado = texto.replace(/^(-?)0+(?=\d)/, '$1').replace(/^(-?)\./, (_, signo) => signo + '0.')
    .replace(/(\.\d*?)0+$/, '$1').replace(/\.$/, '');
  if (!Number.isFinite(numero) || (normalizado !== '-0'
    && normalizado !== numero.toLocaleString('en-US', { useGrouping: false, maximumFractionDigits: 20 }))) {
    throw new Error('Ingresa un número con menos dígitos.');
  }
  return numero;
}

export function prepararCambios(registros, seccion, borrador) {
  const cambios = [];
  const errores = {};
  const ultimoOrden = Math.max(...registros.filter(({ tabla }) => tabla === 'rango_viabilidad')
    .map(({ valores }) => valores.orden));
  for (const registro of registros) {
    for (const campo of camposDe(registro, seccion)) {
      const clave = claveCampo(registro, campo);
      if (!esEditable(registro, campo) || !Object.hasOwn(borrador, clave)) continue;
      const anterior = registro.valores[campo];
      const admiteNulo = campo === 'anios_maximo' && registro.valores.orden === ultimoOrden;
      try {
        const nuevo = convertir(campo, borrador[clave], admiteNulo);
        const coincide = Array.isArray(nuevo) ? nuevo.length === anterior.length
          && nuevo.every((id) => anterior.includes(id)) : nuevo === (campo.startsWith('hora_')
          ? valorDeEntrada(campo, anterior) : anterior);
        if (!coincide) cambios.push({ tabla: registro.tabla, campo, registroId: registro.registroId, valorNuevo: nuevo });
      } catch (error) {
        errores[clave] = error.message;
      }
    }
  }
  return { cambios, errores };
}
