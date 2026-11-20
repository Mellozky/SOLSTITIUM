package com.solstitium.solstitium.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ControladorAutenticacionTest {

    private static final String USUARIO = "administrador_prueba_acceso";
    private static final String CONTRASENA = "ClaveTemporal-123";

    @Autowired
    private MockMvc clientePruebas;

    @Autowired
    private JdbcTemplate consultas;

    @Autowired
    private PasswordEncoder codificadorContrasenas;

    @BeforeEach
    void crearAdministradorDePrueba() {
        consultas.update("delete from administrador where usuario = ?", USUARIO);
        consultas.update(
                "insert into administrador (usuario, contrasena_hash, nombre, activo) values (?, ?, ?, true)",
                USUARIO,
                codificadorContrasenas.encode(CONTRASENA),
                "Administrador de pruebas");
    }

    @AfterEach
    void eliminarAdministradorDePrueba() {
        consultas.update("delete from administrador where usuario = ?", USUARIO);
    }

    @Test
    void administradorPuedeIniciarSesionConsultarSesionYSalir() throws Exception {
        MvcResult inicioSesion = clientePruebas.perform(post("/api/autenticacion/iniciar-sesion")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuario":"administrador_prueba_acceso","contrasena":"ClaveTemporal-123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value(USUARIO))
                .andReturn();

        MockHttpSession sesion = (MockHttpSession) inicioSesion.getRequest().getSession(false);

        clientePruebas.perform(get("/api/autenticacion/sesion").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value(USUARIO));

        clientePruebas.perform(post("/api/autenticacion/cerrar-sesion").session(sesion).with(csrf()))
                .andExpect(status().isNoContent());

        clientePruebas.perform(get("/api/autenticacion/sesion").session(sesion))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void credencialesIncorrectasNoInicianSesion() throws Exception {
        clientePruebas.perform(post("/api/autenticacion/iniciar-sesion")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuario":"administrador_prueba_acceso","contrasena":"incorrecta"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void rutasDeAdministracionExigenSesion() throws Exception {
        clientePruebas.perform(get("/api/admin/prueba"))
                .andExpect(status().isUnauthorized());
        clientePruebas.perform(get("/api/autenticacion/sesion"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administradorInactivoNoPuedeIniciarSesion() throws Exception {
        consultas.update("update administrador set activo = false where usuario = ?", USUARIO);

        clientePruebas.perform(post("/api/autenticacion/iniciar-sesion")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuario":"administrador_prueba_acceso","contrasena":"ClaveTemporal-123"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inicioDeSesionSinTokenCsrfEsRechazado() throws Exception {
        clientePruebas.perform(post("/api/autenticacion/iniciar-sesion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuario":"administrador_prueba_acceso","contrasena":"ClaveTemporal-123"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void obtieneYRenuevaTokenCsrfDuranteElFlujoRealDeSesion() throws Exception {
        // No se usa with(csrf()): se prueba el contrato que consumirá el navegador.
        MvcResult entregaInicial = clientePruebas.perform(get("/api/autenticacion/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) entregaInicial.getRequest().getSession(false);
        String tokenInicial = leerToken(entregaInicial);
        String identificadorAnterior = sesion.getId();

        clientePruebas.perform(post("/api/autenticacion/iniciar-sesion").session(sesion)
                        .header("X-CSRF-TOKEN", tokenInicial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuario":"administrador_prueba_acceso","contrasena":"ClaveTemporal-123"}
                                """))
                .andExpect(status().isOk());
        assertThat(sesion.getId()).isNotEqualTo(identificadorAnterior);

        clientePruebas.perform(get("/api/autenticacion/sesion").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value(USUARIO));
        MvcResult entregaAutenticada = clientePruebas.perform(get("/api/autenticacion/csrf").session(sesion))
                .andExpect(status().isOk()).andReturn();
        String tokenAutenticado = leerToken(entregaAutenticada);
        assertThat(tokenAutenticado).isNotEqualTo(tokenInicial);

        clientePruebas.perform(post("/api/autenticacion/cerrar-sesion").session(sesion)
                        .header("X-CSRF-TOKEN", tokenInicial))
                .andExpect(status().isForbidden());
        clientePruebas.perform(post("/api/autenticacion/cerrar-sesion").session(sesion)
                        .header("X-CSRF-TOKEN", tokenAutenticado))
                .andExpect(status().isNoContent());
        clientePruebas.perform(get("/api/autenticacion/sesion"))
                .andExpect(status().isUnauthorized());
    }

    private String leerToken(MvcResult resultado) throws Exception {
        JsonNode contenido = JsonMapper.builder().build()
                .readTree(resultado.getResponse().getContentAsString());
        return contenido.get("token").asText();
    }
}
