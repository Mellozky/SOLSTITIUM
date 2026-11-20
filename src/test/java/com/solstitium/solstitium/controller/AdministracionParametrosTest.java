package com.solstitium.solstitium.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import com.solstitium.solstitium.apoyo.SolicitudesPrueba;
import com.solstitium.solstitium.service.SimulacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Todos los cambios de estos casos se revierten; las llamadas recorren seguridad, MVC, servicio y PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdministracionParametrosTest {
    private static final String USUARIO = "prueba_parametros";
    @Autowired private MockMvc cliente;
    @Autowired private JdbcTemplate consultas;
    @Autowired private PasswordEncoder codificador;
    @Autowired private SimulacionService simulaciones;

    @BeforeEach
    void preparar() {
        consultas.update("insert into administrador (usuario, contrasena_hash, nombre, activo) values (?, ?, ?, true)",
                USUARIO, codificador.encode("ClaveTemporal-456"), "Prueba administrativa");
    }

    @ParameterizedTest
    @ValueSource(strings = {"tarifas", "tecnicos", "sombreado", "costos", "rangos"})
    void consultaCadaSeccionConLimitesYDto(String seccion) throws Exception {
        cliente.perform(get("/api/admin/parametros").param("seccion", seccion).with(user(USUARIO).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.seccion").value(seccion))
                .andExpect(jsonPath("$.registros").isNotEmpty()).andExpect(jsonPath("$.limites").isNotEmpty())
                .andExpect(jsonPath("$.registros[0].tabla").isString())
                .andExpect(jsonPath("$.registros[0].valores").isMap());
    }

    @ParameterizedTest
    @CsvSource({"tarifas,costo_unitario_kwh", "tarifas,consumo_subsistencia", "tarifas,incremento_anual_tarifa",
            "tecnicos,tope_sobredimensionamiento", "rangos,consumo_maximo", "rangos,umbral_advertencia_consumo",
            "rangos,area_minima", "rangos,area_maxima", "rangos,umbral_diferencia_factura"})
    void consultaParametroYLimiteEnLaSeccionDelErs(String seccion, String clave) throws Exception {
        cliente.perform(get("/api/admin/parametros").param("seccion", seccion).with(user(USUARIO).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registros[?(@.registroId == '" + clave + "')]").isNotEmpty())
                .andExpect(jsonPath("$.limites[?(@.clave == '" + clave + "')]").isNotEmpty());
    }

    @ParameterizedTest
    @CsvSource({"tarifas,consumo_maximo", "tarifas,umbral_advertencia_consumo", "tarifas,umbral_diferencia_factura",
            "tecnicos,area_minima", "tecnicos,area_maxima", "sombreado,tope_sobredimensionamiento"})
    void noReparteLimitesDeEntradaEnSeccionesAjenas(String seccion, String clave) throws Exception {
        cliente.perform(get("/api/admin/parametros").param("seccion", seccion).with(user(USUARIO).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registros[?(@.registroId == '" + clave + "')]").isEmpty())
                .andExpect(jsonPath("$.limites[?(@.clave == '" + clave + "')]").isEmpty());
    }

    @Test
    void rechazaRecuperacionCentralSinGuardarElLoteYConservaCp08() throws Exception {
        var parametrosAntes = consultas.queryForList("select * from parametro order by clave");
        actualizar(lote(cambio("parametro", "costo_unitario_kwh", "valor", "1100"),
                cambio("configuracion", "1", "coeficiente_recuperacion", "0.36")), 400, null);
        assertThat(consultas.queryForList("select * from parametro order by clave")).isEqualTo(parametrosAntes);
        assertThat(consultas.queryForObject("select coeficiente_recuperacion from configuracion where codigo='central'", BigDecimal.class))
                .isEqualByComparingTo("0");
        assertThat(cantidadBitacora()).isZero();
        var resultado = simulaciones.simular(SolicitudesPrueba.crear(1, "100", "40", 4, 4, 4));
        assertThat(resultado.getResultados().stream().filter(r -> r.getConfiguracion().equals("central")).findFirst().orElseThrow().getAhorroMensual())
                .isEqualByComparingTo("0");
        assertThat(resultado.getResultados().stream().filter(r -> r.getConfiguracion().equals("mlpe")).findFirst().orElseThrow().getAhorroMensual())
                .isEqualByComparingTo("32076");
        assertThat(resultado.getRecomendada()).isEqualTo("mlpe");
    }

    @Test
    void configuracionCentralIncoherenteImpideGuardarOtrosParametros() throws Exception {
        consultas.update("update configuracion set coeficiente_recuperacion=0.36 where codigo='central'");
        actualizar(lote(cambio("parametro", "hsp", "valor", "5")), 400, null);
        assertThat(consultas.queryForObject("select valor from parametro where clave='hsp'", BigDecimal.class)).isEqualByComparingTo("4.5");
        assertThat(cantidadBitacora()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000000000", "2147483647", "2147483648"})
    void rechazaVidaFueraDelMotorSinGuardarNiAuditar(String vida) throws Exception {
        var parametrosAntes = consultas.queryForList("select * from parametro order by clave");
        actualizar(lote(cambio("parametro", "costo_unitario_kwh", "valor", "1100"),
                cambio("parametro", "vida_util", "valor", vida)), 400, null);
        assertThat(consultas.queryForList("select * from parametro order by clave")).isEqualTo(parametrosAntes);
        assertThat(cantidadBitacora()).isZero();
        assertThat(simulaciones.simular(SolicitudesPrueba.crear(1, "150", "40", 1, 1, 1)).getResultados()).isNotEmpty();
    }

    @Test
    void permiteElLimiteTecnicoDeVidaConIncrementoCompatible() throws Exception {
        actualizar(lote(cambio("parametro", "vida_util", "valor", "999999999")), 200, 1);
        assertThat(consultas.queryForObject("select valor from parametro where clave='vida_util'", BigDecimal.class))
                .isEqualByComparingTo("999999999");
    }

    @Test
    void limiteTecnicoNoSustituyeElLimiteDeVidaDeLaBase() throws Exception {
        consultas.update("update limite_parametro set valor_maximo=30 where clave='vida_util'");
        actualizar(lote(cambio("parametro", "vida_util", "valor", "31")), 400, null);
        assertThat(consultas.queryForObject("select valor from parametro where clave='vida_util'", BigDecimal.class))
                .isEqualByComparingTo("25");
        assertThat(cantidadBitacora()).isZero();
    }

    @Test
    void rechazaUnHorizonteCuyaCombinacionDesbordaElCalculo() throws Exception {
        actualizar(lote(cambio("parametro", "vida_util", "valor", "999999999"),
                cambio("parametro", "incremento_anual_tarifa", "valor", "9999999999")), 400, null);
        assertThat(cantidadBitacora()).isZero();
    }

    @Test
    void incrementoAnualDeCincoASeisQuedaEnBitacoraCv27() throws Exception {
        actualizar(lote(cambio("parametro", "incremento_anual_tarifa", "valor", "6")), 200, 1);
        cliente.perform(get("/api/admin/bitacora").with(user(USUARIO).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].registroId").value("incremento_anual_tarifa"))
                .andExpect(jsonPath("$[0].valorAnterior").value("5"))
                .andExpect(jsonPath("$[0].valorNuevo").value("6"))
                .andExpect(jsonPath("$[0].administrador").value(USUARIO))
                .andExpect(jsonPath("$[0].fecha").isNotEmpty());
    }

    @ParameterizedTest
    @CsvSource({"parametro,costo_unitario_kwh,valor,3001", "franja_horaria,3,peso,0.10",
            "tramo_sombreado,3,factor_maximo,0.92", "tramo_sombreado,2,configuraciones,[2]"})
    void reproduceRechazosExactosCv20ACv23(String tabla, String id, String campo, String valor) throws Exception {
        actualizar(lote(cambio(tabla, id, campo, valor)), 400, null);
        assertThat(cantidadBitacora()).isZero();
    }

    @Test
    void rutasExigenSesionRolYCsrf() throws Exception {
        cliente.perform(get("/api/admin/parametros").param("seccion", "tarifas")).andExpect(status().isUnauthorized());
        cliente.perform(get("/api/admin/bitacora")).andExpect(status().isUnauthorized());
        cliente.perform(put("/api/admin/parametros").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(lote(cambio("parametro", "costo_unitario_kwh", "valor", "1100")))).andExpect(status().isUnauthorized());
        cliente.perform(get("/api/admin/bitacora").with(user(USUARIO).roles("RESIDENTE"))).andExpect(status().isForbidden());
        cliente.perform(put("/api/admin/parametros").with(user(USUARIO).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(lote(cambio("parametro", "costo_unitario_kwh", "valor", "1100"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void sesionRealActualizaYAuditaConSuAutor() throws Exception {
        var inicio = cliente.perform(post("/api/autenticacion/iniciar-sesion").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"prueba_parametros\",\"contrasena\":\"ClaveTemporal-456\"}"))
                .andExpect(status().isOk()).andReturn();
        var sesion = (MockHttpSession) inicio.getRequest().getSession(false);
        var fechaAnterior = consultas.queryForObject("select fecha_actualizacion from parametro where clave='costo_unitario_kwh'", java.time.LocalDateTime.class);
        cliente.perform(put("/api/admin/parametros").session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(lote(cambio("parametro", "costo_unitario_kwh", "valor", "1100"),
                        cambio("estrato", "1", "porcentaje_ajuste", "45"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modificados").value(2));
        assertThat(valorTarifa()).isEqualByComparingTo("1100");
        assertThat(consultas.queryForObject("select fecha_actualizacion from parametro where clave='costo_unitario_kwh'", java.time.LocalDateTime.class))
                .isAfter(fechaAnterior);
        var filas = consultas.queryForList("select b.*, a.usuario from bitacora_parametro b join administrador a on a.id=b.administrador_id where a.usuario=? order by b.id", USUARIO);
        assertThat(filas).hasSize(2);
        assertThat(filas.get(0)).containsEntry("usuario", USUARIO).containsEntry("valor_anterior", "1000").containsEntry("valor_nuevo", "1100");
        cliente.perform(get("/api/admin/bitacora").session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].administrador").value(USUARIO))
                .andExpect(jsonPath("$[0].campo").value("porcentaje_ajuste"))
                .andExpect(jsonPath("$[1].campo").value("valor"))
                .andExpect(jsonPath("$[0].administrador.contrasenaHash").doesNotExist());
    }

    @Test
    void mismaCantidadConOtraEscalaNoAuditaNiCambiaFecha() throws Exception {
        var antes = consultas.queryForMap("select valor, fecha_actualizacion from parametro where clave='costo_unitario_kwh'");
        actualizar(lote(cambio("parametro", "costo_unitario_kwh", "valor", "1000.0000")), 200, 0);
        assertThat(consultas.queryForMap("select valor, fecha_actualizacion from parametro where clave='costo_unitario_kwh'")).isEqualTo(antes);
        assertThat(cantidadBitacora()).isZero();
    }

    @Test
    void loteMixtoSoloAuditaElValorDistinto() throws Exception {
        actualizar(lote(cambio("parametro", "hsp", "valor", "4.5000"),
                cambio("estrato", "1", "porcentaje_ajuste", "45")), 200, 1);
        assertThat(cantidadBitacora()).isEqualTo(1);
        assertThat(consultas.queryForObject("select tabla_afectada from bitacora_parametro where administrador_id=(select id from administrador where usuario=?)", String.class, USUARIO))
                .isEqualTo("estrato");
    }

    @Test
    void bitacoraOrdenaPorFechaAntesQuePorIdentificador() throws Exception {
        consultas.update("""
                insert into bitacora_parametro (administrador_id, fecha, tabla_afectada, registro_id, campo, valor_anterior, valor_nuevo)
                select id, '2099-01-02 12:00:00+00'::timestamptz, 'parametro', 'hsp', 'valor', '4.5', '5'
                from administrador where usuario=?
                """, USUARIO);
        consultas.update("""
                insert into bitacora_parametro (administrador_id, fecha, tabla_afectada, registro_id, campo, valor_anterior, valor_nuevo)
                select id, '2099-01-01 12:00:00+00'::timestamptz, 'parametro', 'vida_util', 'valor', '25', '26'
                from administrador where usuario=?
                """, USUARIO);
        cliente.perform(get("/api/admin/bitacora").with(user(USUARIO).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].registroId").value("hsp"))
                .andExpect(jsonPath("$[1].registroId").value("vida_util"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"500", "3000"})
    void aceptaExtremosInclusivosCv20(String valor) throws Exception {
        actualizar(lote(cambio("parametro", "costo_unitario_kwh", "valor", valor)), 200, 1);
        assertThat(valorTarifa()).isEqualByComparingTo(valor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"499.9999", "3000.0001", "1100.00001", "1000.00000000000000000001", "\"1100\"", "true", "null", "{}", "[]"})
    void rechazaLimitesTiposYRedondeosSinGuardarCv20(String valor) throws Exception {
        actualizar(lote(cambio("parametro", "costo_unitario_kwh", "valor", valor)), 400, null);
        assertThat(valorTarifa()).isEqualByComparingTo("1000");
        assertThat(cantidadBitacora()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"limite_parametro,1,valor_maximo,3001", "estrato,1,tipo_ajuste,0", "panel,1,potencia_wp,500",
            "administrador,1,activo,false", "parametro,inexistente,valor,1", "parametro,costo_unitario_kwh,fecha_actualizacion,1",
            "parametro; drop table panel,1,valor,1", "estrato,1,porcentaje_ajuste = 0,1"})
    void rechazaDestinosFueraDeLista(String tabla, String id, String campo, String valor) throws Exception {
        actualizar(lote(cambio(tabla, id, campo, valor)), 400, null);
        assertThat(cantidadBitacora()).isZero();
        assertThat(consultas.queryForObject("select count(*) from panel", Integer.class)).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"cambios\":[]}", "{\"cambios\":[null]}", "{\"cambios\":[{}]}",
            "{\"administradorId\":1,\"cambios\":[{\"tabla\":\"parametro\",\"registroId\":\"hsp\",\"campo\":\"valor\",\"valorNuevo\":5}]}",
            "{\"cambios\":[{\"tabla\":\"parametro\",\"registroId\":\"hsp\",\"campo\":\"valor\",\"valorNuevo\":5,\"administrador\":1}]}"})
    void rechazaEstructuraEIdentidadEnCuerpo(String cuerpo) throws Exception {
        actualizar(cuerpo, 400, null);
        assertThat(cantidadBitacora()).isZero();
    }

    @Test
    void validaTodoElLoteAntesDePersistirYNiegaDuplicados() throws Exception {
        actualizar(lote(cambio("parametro", "costo_unitario_kwh", "valor", "1100"),
                cambio("estrato", "1", "porcentaje_ajuste", "101")), 400, null);
        actualizar(lote(cambio("parametro", "costo_unitario_kwh", "valor", "1100"),
                cambio("parametro", "costo_unitario_kwh", "valor", "1200")), 400, null);
        assertThat(valorTarifa()).isEqualByComparingTo("1000");
        assertThat(cantidadBitacora()).isZero();
    }

    @Test
    void rechazaPesosIncoherentesYAceptaCambioConjuntoCv21() throws Exception {
        actualizar(lote(cambio("franja_horaria", "1", "peso", "0.2")), 400, null);
        actualizar(lote(cambio("franja_horaria", "1", "peso", "0.2"),
                cambio("franja_horaria", "2", "peso", "0.65")), 200, 2);
    }

    @ParameterizedTest
    @CsvSource({"tramo_sombreado,3,factor_minimo,0.8", "tramo_sombreado,3,factor_maximo,0.95",
            "tramo_sombreado,1,factor_minimo,0.99", "rango_viabilidad,2,anios_minimo,7",
            "rango_viabilidad,1,anios_maximo,7", "rango_viabilidad,3,anios_maximo,30",
            "rango_viabilidad,1,anios_maximo,null", "franja_horaria,1,peso,-0.1",
            "porcion_afectada,2,valor,0.3", "configuracion,1,costo_kwp,0",
            "parametro,area_minima,valor,600", "parametro,consumo_maximo,valor,700",
            "parametro,vida_util,valor,25.5", "estrato,4,porcentaje_ajuste,1"})
    void rechazaConjuntoIncoherenteCv21ACv23(String tabla, String id, String campo, String valor) throws Exception {
        actualizar(lote(cambio(tabla, id, campo, valor)), 400, null);
        assertThat(cantidadBitacora()).isZero();
    }

    @Test
    void permiteMoverFronterasConjuntamenteCv22Cv23() throws Exception {
        actualizar(lote(cambio("tramo_sombreado", "3", "factor_minimo", "0.8"),
                cambio("tramo_sombreado", "4", "factor_maximo", "0.8"),
                cambio("rango_viabilidad", "1", "anios_maximo", "7"),
                cambio("rango_viabilidad", "2", "anios_minimo", "7")), 200, 4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[2]", "[1,1]", "[1,999]", "[]", "[1.5]", "[\"1\"]"})
    void exigeCentralYConfiguracionesExistentes(String ids) throws Exception {
        actualizar(lote(cambio("tramo_sombreado", "3", "configuraciones", ids)), 400, null);
        assertThat(cantidadBitacora()).isZero();
    }

    @Test
    void auditaAsociacionSoloCuandoCambia() throws Exception {
        actualizar(lote(cambio("tramo_sombreado", "3", "configuraciones", "[2,1]")), 200, 1);
        actualizar(lote(cambio("tramo_sombreado", "3", "configuraciones", "[1,2]")), 200, 0);
        assertThat(consultas.queryForObject("select valor_nuevo from bitacora_parametro where tabla_afectada='tramo_sombreado' and registro_id='3'", String.class))
                .isEqualTo("[1, 2]");
        assertThat(consultas.queryForObject("select count(*) from tramo_configuracion where tramo_id=3", Integer.class)).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"25:00\"", "\"9:00\"", "\"10:01\"", "600", "null"})
    void rechazaHorasInvalidasOCruzadas(String hora) throws Exception {
        actualizar(lote(cambio("franja_horaria", "1", "hora_fin", hora)), 400, null);
    }

    @Test
    void permiteHoraYAdvertencia() throws Exception {
        actualizar(lote(cambio("franja_horaria", "1", "hora_inicio", "\"06:30\""),
                cambio("tramo_sombreado", "4", "muestra_advertencia", "true")), 200, 2);
        assertThat(consultas.queryForObject("select hora_inicio::text from franja_horaria where id=1", String.class)).isEqualTo("06:30:00");
        assertThat(consultas.queryForObject("select muestra_advertencia from tramo_sombreado where id=4", Boolean.class)).isTrue();
    }

    @Test
    void modificaPorcionCoeficienteCostoYExtremoAbierto() throws Exception {
        actualizar(lote(cambio("porcion_afectada", "2", "valor", "0.12"),
                cambio("configuracion", "2", "coeficiente_recuperacion", "0.4"),
                cambio("configuracion", "2", "costo_kwp", "4700000"),
                cambio("rango_viabilidad", "3", "anios_maximo", "null")), 200, 3);
        assertThat(consultas.queryForObject("select valor from porcion_afectada where id=2", BigDecimal.class)).isEqualByComparingTo("0.12");
        var configuracion = consultas.queryForMap("select costo_kwp, coeficiente_recuperacion from configuracion where id=2");
        assertThat((BigDecimal) configuracion.get("costo_kwp")).isEqualByComparingTo("4700000");
        assertThat((BigDecimal) configuracion.get("coeficiente_recuperacion")).isEqualByComparingTo("0.4");
        assertThat(cantidadBitacora()).isEqualTo(3);
    }

    @Test
    void limiteAusenteImpideEscrituraSinExponerDetalles() throws Exception {
        consultas.update("delete from limite_parametro where clave='hsp'");
        actualizar(lote(cambio("parametro", "hsp", "valor", "5")), 503, null);
        assertThat(cantidadBitacora()).isZero();
    }

    @Test
    void sinReferenciaActivaNoGuardaNingunCambio() throws Exception {
        consultas.update("update panel set es_referencia=false where es_referencia");
        actualizar(lote(cambio("parametro", "hsp", "valor", "5")), 400, null);
        assertThat(cantidadBitacora()).isZero();
    }

    @Test
    void limiteDeBaseEsFuenteDeValidacionYNoSePuedeEditar() throws Exception {
        consultas.update("update limite_parametro set valor_maximo=1050 where clave='costo_unitario_kwh'");
        actualizar(lote(cambio("parametro", "costo_unitario_kwh", "valor", "1100")), 400, null);
        cliente.perform(get("/api/admin/parametros").param("seccion", "tarifas").with(user(USUARIO).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.limites[?(@.clave == 'costo_unitario_kwh')].valorMaximo").value(1050.0));
    }

    @Test
    void cuentaDesactivadaDespuesDeLoginNoPuedeConsultarNiGuardar() throws Exception {
        consultas.update("update administrador set activo=false where usuario=?", USUARIO);
        cliente.perform(get("/api/admin/bitacora").with(user(USUARIO).roles("ADMIN"))).andExpect(status().isForbidden());
        actualizar(lote(cambio("parametro", "hsp", "valor", "5")), 403, null);
    }

    @Test
    void rechazaSeccionAusenteODesconocida() throws Exception {
        cliente.perform(get("/api/admin/parametros").with(user(USUARIO).roles("ADMIN"))).andExpect(status().isBadRequest());
        cliente.perform(get("/api/admin/parametros").param("seccion", "paneles").with(user(USUARIO).roles("ADMIN"))).andExpect(status().isBadRequest());
    }

    private void actualizar(String cuerpo, int estado, Integer modificados) throws Exception {
        var resultado = cliente.perform(put("/api/admin/parametros").with(user(USUARIO).roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().is(estado));
        if (modificados != null) resultado.andExpect(jsonPath("$.modificados").value(modificados));
        if (estado == 400) resultado.andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    private BigDecimal valorTarifa() {
        return consultas.queryForObject("select valor from parametro where clave='costo_unitario_kwh'", BigDecimal.class);
    }
    private int cantidadBitacora() {
        return consultas.queryForObject("select count(*) from bitacora_parametro b join administrador a on a.id=b.administrador_id where a.usuario=?", Integer.class, USUARIO);
    }
    static String cambio(String tabla, String id, String campo, String valor) {
        return "{\"tabla\":\"" + tabla + "\",\"registroId\":\"" + id + "\",\"campo\":\"" + campo + "\",\"valorNuevo\":" + valor + "}";
    }
    static String lote(String... cambios) { return "{\"cambios\":[" + String.join(",", cambios) + "]}"; }
}
