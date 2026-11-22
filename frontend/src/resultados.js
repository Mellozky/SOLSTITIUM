// Prepara la respuesta de POST /api/simulaciones para la pantalla de resultados.
// Solo da formato y elige textos: todos los valores provienen del back-end (contrato-simulacion.md).

const formatoEntero = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });
const formatoUnDecimal = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const formatoDosDecimales = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const formatoHastaDosDecimales = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 });
const formatoHastaUnDecimal = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 1 });
const formatoFecha = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });

/**
 * Textos de interfaz del prototipo para cada código de configuración. La API devuelve el código;
 * si aparece uno nuevo en el catálogo, se muestra el código tal cual.
 */
const TEXTOS_CONFIGURACION = {
  central: {
    nombre: 'Inversor central',
    descripcion: 'Configuración convencional',
    conArticulo: 'el inversor central',
    con: 'con inversor central',
  },
  mlpe: {
    nombre: 'Con optimizadores (MLPE)',
    descripcion: 'Cada panel trabaja por separado',
    conArticulo: 'los optimizadores (MLPE)',
    con: 'con optimizadores',
  },
};

export function textosConfiguracion(codigo) {
  return TEXTOS_CONFIGURACION[codigo]
    ?? { nombre: codigo, descripcion: '', conArticulo: codigo, con: `con ${codigo}` };
}

export function formatearPesos(valor) {
  return `$${formatoEntero.format(valor)}`;
}

export function formatearAnios(valor, decimales = 1) {
  if (valor == null) return 'No se recupera';
  return `${(decimales === 2 ? formatoDosDecimales : formatoUnDecimal).format(valor)} años`;
}

/**
 * Un decimal, como el prototipo, salvo que dos tiempos distintos se vean iguales así redondeados:
 * entonces se usan dos para que la comparación no parezca un empate.
 */
export function decimalesAnios(tiempos) {
  const distintos = tiempos.filter((tiempo) => tiempo != null);
  const parecenIguales = distintos.some((uno, i) => distintos.some((otro, j) => i !== j && uno !== otro
    && formatoUnDecimal.format(uno) === formatoUnDecimal.format(otro)));
  return parecenIguales ? 2 : 1;
}

export function formatearPorcentaje(valor) {
  return `${formatoHastaUnDecimal.format(valor)} %`;
}

export function formatearPotencia(valor, unidad) {
  return `${formatoHastaDosDecimales.format(valor)} ${unidad}`;
}

export function formatearInversor(valor) {
  return `${formatoUnDecimal.format(valor)} kW`;
}

/** La fecha llega como LocalDateTime sin zona; solo se muestra su parte de calendario. */
export function formatearFecha(valor) {
  if (!valor) return 'sin fecha';
  const [anio, mes, dia] = valor.slice(0, 10).split('-').map(Number);
  return formatoFecha.format(new Date(Date.UTC(anio, mes - 1, dia)));
}

/** Indicadores de una configuración, en el orden del prototipo. */
export function indicadores(resultado, decimales = 1) {
  return [
    { etiqueta: 'Paneles', valor: formatoEntero.format(resultado.paneles) },
    { etiqueta: 'Potencia instalada', valor: formatearPotencia(resultado.potenciaKwp, 'kWp') },
    { etiqueta: 'Inversor', valor: formatearInversor(resultado.inversorKw) },
    { etiqueta: 'Inversión estimada', valor: formatearPesos(resultado.costoTotal) },
    { etiqueta: 'Ahorro mensual', valor: formatearPesos(resultado.ahorroMensual) },
    { etiqueta: 'Cobertura del consumo', valor: formatearPorcentaje(resultado.cobertura) },
    { etiqueta: 'Recuperas en', valor: formatearAnios(resultado.tiempoRecuperacion, decimales) },
    { etiqueta: 'ROI en la vida útil', valor: formatearPorcentaje(resultado.roi) },
  ];
}

function frasePara(clasificacion) {
  if (!clasificacion) return 'Revisa los resultados para tu hogar';
  const texto = clasificacion.toLowerCase();
  return texto.startsWith('no ')
    ? `Instalar paneles solares ${texto.replace(/^no /, 'no es ')} para tu hogar`
    : `Instalar paneles solares es ${texto} para tu hogar`;
}

function resumenRecomendacion(recomendado, otros, decimales) {
  const textos = textosConfiguracion(recomendado.configuracion);
  const inicio = `Te recomendamos ${textos.conArticulo}: recuperas tu inversión en ${formatearAnios(recomendado.tiempoRecuperacion, decimales)}`;
  const [otro] = otros;
  if (!otro) return `${inicio}.`;
  const textosOtro = textosConfiguracion(otro.configuracion);
  if (otro.tiempoRecuperacion == null) {
    return `${inicio}. ${textosOtro.con[0].toUpperCase()}${textosOtro.con.slice(1)} no se recupera durante la vida útil.`;
  }
  if (otro.tiempoRecuperacion === recomendado.tiempoRecuperacion) {
    return `${inicio}, lo mismo que ${textosOtro.con} (${formatearAnios(otro.tiempoRecuperacion, decimales)}).`;
  }
  return `${inicio}, antes que ${textosOtro.con} (${formatearAnios(otro.tiempoRecuperacion, decimales)}).`;
}

/** Explica la recomendación con los valores calculados por el back-end, sin hacer cuentas. */
function explicacion(recomendado, otros) {
  const [otro] = otros;
  const textos = textosConfiguracion(recomendado.configuracion);
  if (!otro) return null;
  const textosOtro = textosConfiguracion(otro.configuracion);
  return {
    titulo: `¿Por qué ${textos.conArticulo}?`,
    texto: `${textosOtro.con[0].toUpperCase()}${textosOtro.con.slice(1)} ahorrarías ${formatearPesos(otro.ahorroMensual)} al mes `
      + `con una inversión de ${formatearPesos(otro.costoTotal)}; con ${textos.conArticulo} ahorras ${formatearPesos(recomendado.ahorroMensual)} `
      + `con ${formatearPesos(recomendado.costoTotal)}. `
      + (otro.tiempoRecuperacion === recomendado.tiempoRecuperacion
        ? 'Ambas recuperan la inversión en el mismo tiempo.'
        : `Con ${textos.conArticulo} recuperas antes lo que inviertes.`),
  };
}

/** Une los datos de la respuesta en lo que necesita cada bloque de la pantalla. */
export function prepararResultados(respuesta) {
  const etiquetas = [
    { texto: `Sombra asumida: ${respuesta.tramo.toLowerCase()}`, tipo: 'sombra' },
    { texto: `Panel de referencia: ${respuesta.panelReferencia}` },
    { texto: `Tarifa actualizada el ${formatearFecha(respuesta.fechaTarifa)}` },
    { texto: 'Estimación, no cotización' },
  ];
  const comunes = { etiquetas, advertencias: respuesta.advertencias ?? [] };

  if (respuesta.sinEspacio) {
    return {
      ...comunes,
      caso: 'sinEspacio',
      encabezado: {
        insignia: 'Sin espacio',
        titulo: 'Sin espacio en tu techo para instalar paneles',
        texto: respuesta.motivoRecomendacion,
      },
      tarjetas: [],
      explicacion: null,
      gastoMensual: null,
      escenarioSinSombra: null,
    };
  }

  const recomendado = respuesta.resultados.find(({ configuracion }) => configuracion === respuesta.recomendada);
  const decimales = decimalesAnios(respuesta.resultados.map(({ tiempoRecuperacion }) => tiempoRecuperacion));
  const tarjetas = respuesta.resultados.map((resultado) => ({
    codigo: resultado.configuracion,
    ...textosConfiguracion(resultado.configuracion),
    recomendada: resultado === recomendado,
    indicadores: indicadores(resultado, decimales),
    clasificacion: resultado.tiempoRecuperacion == null
      ? `${resultado.clasificacion} · no recupera la inversión`
      : resultado.clasificacion,
  }));
  const otros = respuesta.resultados.filter((resultado) => resultado !== recomendado);

  const encabezado = recomendado
    ? {
      insignia: recomendado.clasificacion,
      titulo: frasePara(recomendado.clasificacion),
      texto: resumenRecomendacion(recomendado, otros, decimales),
      ahorro: {
        valor: formatearPesos(recomendado.ahorroMensual),
        detalle: `Cubre el ${formatearPorcentaje(recomendado.cobertura)} de tu consumo`,
      },
    }
    : {
      insignia: 'Sin recuperación',
      titulo: 'Con estos datos, la inversión no se recupera',
      texto: respuesta.motivoRecomendacion,
    };

  const maximo = respuesta.gastoMensualSinSistema;
  const proporcion = (valor) => (maximo > 0 ? Math.min(100, Math.max(0, (valor / maximo) * 100)) : 0);
  const gastoMensual = {
    filas: [
      { etiqueta: 'Hoy, sin paneles', valor: formatearPesos(maximo), ancho: proporcion(maximo), tipo: 'actual' },
      ...respuesta.resultados.map((resultado) => ({
        etiqueta: textosConfiguracion(resultado.configuracion).con.replace(/^c/, 'C'),
        valor: formatearPesos(resultado.gastoMensualConSistema),
        ancho: proporcion(resultado.gastoMensualConSistema),
        tipo: resultado === recomendado ? 'recomendada' : 'alternativa',
      })),
    ],
  };

  const sinSombra = respuesta.escenarioSinSombra;
  return {
    ...comunes,
    caso: recomendado ? 'recomendada' : 'sinRecuperacion',
    encabezado,
    tarjetas,
    explicacion: recomendado ? explicacion(recomendado, otros) : null,
    gastoMensual,
    escenarioSinSombra: sinSombra && {
      ahorro: formatearPesos(sinSombra.ahorroMensual),
      recuperacion: formatearAnios(sinSombra.tiempoRecuperacion),
      actual: recomendado
        ? {
          ahorro: formatearPesos(recomendado.ahorroMensual),
          recuperacion: formatearAnios(recomendado.tiempoRecuperacion, decimales),
        }
        : null,
    },
  };
}
