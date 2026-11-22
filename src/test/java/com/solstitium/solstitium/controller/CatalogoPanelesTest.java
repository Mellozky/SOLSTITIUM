package com.solstitium.solstitium.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Seguridad real, MVC, JPA y PostgreSQL; los cambios se revierten en cada caso. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogoPanelesTest {
    private static final String USUARIO = "prueba_catalogo";
    private static final String DATOS = """
            {"marca":"Nueva marca","modelo":"Nuevo modelo","potenciaWp":600,"areaM2":2.8}
            """;
    private static final String CONTRASENA = "TemporalCatalogo-789";
    /** Cada hash BCrypt cuesta unos 130 ms: se calcula una vez por clase y no antes de cada prueba. */
    private static String hashContrasena;
    @Autowired private MockMvc cliente;
    @Autowired private JdbcTemplate consultas;
    @Autowired private PasswordEncoder codificador;

    @BeforeEach
    void preparar() {
        if (hashContrasena == null) hashContrasena = codificador.encode(CONTRASENA);
        consultas.update("insert into administrador (usuario, contrasena_hash, nombre, activo) values (?, ?, ?, true)",
                USUARIO, hashContrasena, "Prueba catálogo");
    }

    @Test
    void listaActivosEInactivosConLimitesSinPrecio() throws Exception {
        consultas.update("update panel set activo=false where id=2");
        cliente.perform(get("/api/admin/paneles").with(user(USUARIO).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paneles.length()").value(2))
                .andExpect(jsonPath("$.paneles[0].modelo").value("Alterno ERS 450"))
                .andExpect(jsonPath("$.paneles[0].activo").value(false))
                .andExpect(jsonPath("$.paneles[1].esReferencia").value(true))
                .andExpect(jsonPath("$.paneles[0].precio").doesNotExist())
                .andExpect(jsonPath("$.limites.length()").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"listar", "registrar", "editar", "estado", "referencia"})
    void requiereSesionYRolAdministrador(String operacion) throws Exception {
        cliente.perform(peticion(operacion).with(csrf())).andExpect(status().isUnauthorized());
        cliente.perform(peticion(operacion).with(csrf()).with(user("ajeno").roles("OTRO")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"registrar", "editar", "estado", "referencia"})
    void requiereCsrfValido(String operacion) throws Exception {
        cliente.perform(peticion(operacion).with(user(USUARIO).roles("ADMIN"))).andExpect(status().isForbidden());
        cliente.perform(peticion(operacion).with(user(USUARIO).roles("ADMIN")).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        assertThat(cantidadBitacora()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"listar", "registrar", "editar", "estado", "referencia"})
    void rechazaCuentaInactivaAunqueConserveSesion(String operacion) throws Exception {
        consultas.update("update administrador set activo=false where usuario=?", USUARIO);
        cliente.perform(peticion(operacion).with(csrf()).with(user(USUARIO).roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void registraConSesionRealYAuditaCadaCampoDelAutor() throws Exception {
        var sesion = new MockHttpSession();
        cliente.perform(post("/api/autenticacion/iniciar-sesion").session(sesion).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + USUARIO + "\",\"contrasena\":\"" + CONTRASENA + "\"}"))
                .andExpect(status().isOk());
        cliente.perform(post("/api/admin/paneles").session(sesion).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.activo").value(true)).andExpect(jsonPath("$.esReferencia").value(false));
        assertThat(cantidadBitacora()).isEqualTo(6);
        var campos = consultas.queryForList("select campo from bitacora_parametro where administrador_id=(select id from administrador where usuario=?)", String.class, USUARIO);
        assertThat(campos).containsExactlyInAnyOrder("marca", "modelo", "potencia_wp", "area_m2", "activo", "es_referencia");
        assertThat(consultas.queryForObject("select count(*) from bitacora_parametro where valor_anterior='(sin registro)' and fecha is not null", Integer.class)).isEqualTo(6);
    }

    @Test
    void editaSinCambiarEstadoNiReferenciaYAuditaSoloDiferencias() throws Exception {
        escribir(put("/api/admin/paneles/2"), DATOS, 200);
        escribir(put("/api/admin/paneles/2"), DATOS, 200);
        assertThat(cantidadBitacora()).isEqualTo(4);
        assertThat(consultas.queryForMap("select activo, es_referencia from panel where id=2"))
                .isEqualTo(Map.of("activo", true, "es_referencia", false));
        assertThat(consultas.queryForObject("select valor_anterior from bitacora_parametro where campo='potencia_wp'", String.class)).isEqualTo("450");
        assertThat(consultas.queryForObject("select valor_nuevo from bitacora_parametro where campo='potencia_wp'", String.class)).isEqualTo("600");
    }

    @Test
    void activaDesactivaSinEliminarYNoAuditaRepeticiones() throws Exception {
        escribir(put("/api/admin/paneles/2/estado"), "{\"activo\":false}", 200);
        escribir(put("/api/admin/paneles/2/estado"), "{\"activo\":false}", 200);
        escribir(put("/api/admin/paneles/2/estado"), "{\"activo\":true}", 200);
        assertThat(cantidadBitacora()).isEqualTo(2);
        assertThat(consultas.queryForObject("select count(*) from panel", Integer.class)).isEqualTo(2);
        cliente.perform(delete("/api/admin/paneles/2").with(user(USUARIO).roles("ADMIN")).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void cambiaReferenciaEnAmbosSentidosYRegistraAmbosPaneles() throws Exception {
        escribir(put("/api/admin/paneles/2/referencia"), "{\"confirmado\":true}", 200);
        escribir(put("/api/admin/paneles/2/referencia"), "{\"confirmado\":true}", 200);
        assertThat(cantidadBitacora()).isEqualTo(2);
        assertThat(consultas.queryForObject("select id from panel where es_referencia and activo", Long.class)).isEqualTo(2);
        escribir(put("/api/admin/paneles/1/referencia"), "{\"confirmado\":true}", 200);
        assertThat(consultas.queryForObject("select id from panel where es_referencia and activo", Long.class)).isEqualTo(1);
        assertThat(cantidadBitacora()).isEqualTo(4);
    }

    @Test
    void noDesactivaReferenciaCv24() throws Exception {
        escribir(put("/api/admin/paneles/1/estado"), "{\"activo\":false}", 409);
        assertThat(cantidadBitacora()).isZero();
        assertThat(consultas.queryForObject("select id from panel where es_referencia and activo", Long.class)).isEqualTo(1);
    }

    @Test
    void noSeleccionaPanelInactivoCv26() throws Exception {
        consultas.update("update panel set activo=false where id=2");
        escribir(put("/api/admin/paneles/2/referencia"), "{\"confirmado\":true}", 409);
        assertThat(cantidadBitacora()).isZero();
        assertThat(consultas.queryForObject("select id from panel where es_referencia and activo", Long.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"confirmado\":false}", "{\"confirmado\":null}", "{\"confirmado\":\"true\"}", "{\"confirmado\":1}"})
    void exigeConfirmacionExplicitaSinModificar(String cuerpo) throws Exception {
        escribir(put("/api/admin/paneles/2/referencia"), cuerpo, 400);
        assertThat(cantidadBitacora()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"activo\":null}", "{\"activo\":\"false\"}", "{\"activo\":0}", "{\"activo\":true,\"administrador\":1}"})
    void rechazaEstadoAmbiguoOCamposAjenos(String cuerpo) throws Exception {
        escribir(put("/api/admin/paneles/2/estado"), cuerpo, 400);
        assertThat(cantidadBitacora()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "{", "{\"marca\":\" \",\"modelo\":\"M\",\"potenciaWp\":550,\"areaM2\":2.6}",
            "{\"marca\":\"X\",\"modelo\":null,\"potenciaWp\":550,\"areaM2\":2.6}",
            "{\"marca\":55,\"modelo\":\"M\",\"potenciaWp\":550,\"areaM2\":2.6}"})
    void rechazaEstructurasInvalidas(String cuerpo) throws Exception {
        escribir(post("/api/admin/paneles"), cuerpo, 400);
        assertThat(cantidadBitacora()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "null", "0.001", "100000", "550.001", "\"550\"", "true"})
    void rechazaPotenciaInvalidaSinRedondear(String valor) throws Exception {
        escribir(post("/api/admin/paneles"), DATOS.replace("600", valor), 400);
        assertThat(cantidadBitacora()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "null", "0.0001", "100", "2.6001", "\"2.6\"", "false"})
    void rechazaAreaInvalidaSinRedondear(String valor) throws Exception {
        escribir(put("/api/admin/paneles/2"), DATOS.replace("2.8", valor), 400);
        assertThat(cantidadBitacora()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"precio", "id", "activo", "esReferencia", "administrador", "limites"})
    void noAceptaPrecioIdentidadEstadoReferenciaNiLimitesEnDatos(String campo) throws Exception {
        escribir(post("/api/admin/paneles"), DATOS.strip().replace("}", ",\"" + campo + "\":1}"), 400);
    }

    @Test
    void respetaLongitudYNormalizaEspacios() throws Exception {
        escribir(post("/api/admin/paneles"), DATOS.replace("Nueva marca", "X".repeat(61)), 400);
        escribir(post("/api/admin/paneles"), DATOS.replace("Nuevo modelo", "X".repeat(61)), 400);
        escribir(post("/api/admin/paneles"), DATOS.replace("Nueva marca", "  Marca  "), 201);
        assertThat(consultas.queryForObject("select marca from panel where modelo='Nuevo modelo'", String.class)).isEqualTo("Marca");
    }

    @Test
    void rechazaDuplicadosEnAltaYEdicionInclusoInactivosCv25() throws Exception {
        var duplicado = DATOS.replace("Nueva marca", " demostración ").replace("Nuevo modelo", "Referencia ERS 550");
        escribir(post("/api/admin/paneles"), duplicado, 409);
        escribir(put("/api/admin/paneles/2"), duplicado, 409);
        consultas.update("update panel set activo=false where id=2");
        escribir(post("/api/admin/paneles"), duplicado.replace("Referencia ERS 550", "Alterno ERS 450"), 409);
        assertThat(cantidadBitacora()).isZero();
    }

    @Test
    void permiteConservarParejaPropiaYUsarOtroModelo() throws Exception {
        escribir(put("/api/admin/paneles/1"), DATOS.replace("Nueva marca", "DEMOSTRACIÓN")
                .replace("Nuevo modelo", "Referencia ERS 550"), 200);
        escribir(post("/api/admin/paneles"), DATOS.replace("Nueva marca", "DEMOSTRACIÓN"), 201);
    }

    @Test
    void tomaLimitesDeBaseYExplicaRangoSinGuardar() throws Exception {
        consultas.update("update limite_parametro set valor_minimo=500, valor_maximo=650 where tabla='panel' and campo='potencia_wp'");
        consultas.update("update limite_parametro set valor_minimo=2.5, valor_maximo=3 where tabla='panel' and campo='area_m2'");
        escribir(post("/api/admin/paneles"), DATOS.replace("600", "499"), 400);
        escribir(post("/api/admin/paneles"), DATOS.replace("2.8", "3.001"), 400);
        assertThat(cantidadBitacora()).isZero();
        escribir(post("/api/admin/paneles"), DATOS.replace("600", "500").replace("2.8", "2.5"), 201);
        escribir(post("/api/admin/paneles"), DATOS.replace("Nuevo modelo", "Extremo superior").replace("600", "650").replace("2.8", "3"), 201);
        assertThat(consultas.queryForObject("select potencia_wp from panel where modelo='Nuevo modelo'", BigDecimal.class)).isEqualByComparingTo("500");
    }

    @Test
    void rechazaSinLimitesSinCrear() throws Exception {
        consultas.update("delete from limite_parametro where tabla='panel' and campo='area_m2'");
        escribir(post("/api/admin/paneles"), DATOS, 503);
        assertThat(cantidadBitacora()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"registrar", "editar", "estado", "referencia"})
    void validaCoherenciaGlobalEnCadaEscrituraDeCatalogo(String operacion) throws Exception {
        consultas.update("update franja_horaria set peso=0.10 where id=1");
        var antes = consultas.queryForList("select * from panel order by id");
        cliente.perform(peticion(operacion).with(user(USUARIO).roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        assertThat(cantidadBitacora()).isZero();
        assertThat(consultas.queryForList("select * from panel order by id")).isEqualTo(antes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"registrar", "editar", "estado", "referencia"})
    void noGuardaUnCatalogoSinReferenciaInicial(String operacion) throws Exception {
        consultas.update("update panel set es_referencia=false where es_referencia");
        cliente.perform(peticion(operacion).with(user(USUARIO).roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        assertThat(cantidadBitacora()).isZero();
    }

    @Test
    void muestraRangoVigenteEnErrorDeLimites() throws Exception {
        consultas.update("update limite_parametro set valor_minimo=500, valor_maximo=650 where tabla='panel' and campo='potencia_wp'");
        cliente.perform(post("/api/admin/paneles").with(user(USUARIO).roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(DATOS.replace("600", "499")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0]").value(
                        "El valor de panel.potencia_wp debe estar entre 500.0000 y 650.0000."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/999999", "/999999/estado", "/999999/referencia"})
    void informaPanelInexistente(String ruta) throws Exception {
        escribir(put("/api/admin/paneles" + ruta), ruta.endsWith("estado") ? "{\"activo\":true}"
                : ruta.endsWith("referencia") ? "{\"confirmado\":true}" : DATOS, 404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "abc", "999999999999999999999999"})
    void rechazaIdentificadoresInvalidos(String id) throws Exception {
        escribir(put("/api/admin/paneles/" + id), DATOS, 400);
    }

    private MockHttpServletRequestBuilder peticion(String operacion) {
        return switch (operacion) {
            case "listar" -> get("/api/admin/paneles");
            case "registrar" -> post("/api/admin/paneles").contentType(MediaType.APPLICATION_JSON).content(DATOS);
            case "editar" -> put("/api/admin/paneles/2").contentType(MediaType.APPLICATION_JSON).content(DATOS);
            case "estado" -> put("/api/admin/paneles/2/estado").contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}");
            default -> put("/api/admin/paneles/2/referencia").contentType(MediaType.APPLICATION_JSON).content("{\"confirmado\":true}");
        };
    }

    private void escribir(MockHttpServletRequestBuilder peticion, String cuerpo, int estado) throws Exception {
        cliente.perform(peticion.with(user(USUARIO).roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().is(estado));
    }

    private int cantidadBitacora() {
        return consultas.queryForObject("select count(*) from bitacora_parametro where administrador_id=(select id from administrador where usuario=?)", Integer.class, USUARIO);
    }
}
