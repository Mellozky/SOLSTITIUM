package com.solstitium.solstitium.controller;

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

@SpringBootTest
@AutoConfigureMockMvc
class ControladorAutenticacionTest {

    private static final String USUARIO = "admin_auth_test";
    private static final String CONTRASENA = "ClaveTemporal-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void crearAdministradorDePrueba() {
        jdbcTemplate.update("delete from administrador where usuario = ?", USUARIO);
        jdbcTemplate.update(
                "insert into administrador (usuario, contrasena_hash, nombre, activo) values (?, ?, ?, true)",
                USUARIO,
                passwordEncoder.encode(CONTRASENA),
                "Administrador de pruebas");
    }

    @AfterEach
    void eliminarAdministradorDePrueba() {
        jdbcTemplate.update("delete from administrador where usuario = ?", USUARIO);
    }

    @Test
    void administradorPuedeIniciarSesionConsultarSesionYSalir() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/autenticacion/iniciar-sesion")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuario":"admin_auth_test","contrasena":"ClaveTemporal-123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value(USUARIO))
                .andReturn();

        MockHttpSession sesion = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(get("/api/autenticacion/sesion").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value(USUARIO));

        mockMvc.perform(post("/api/autenticacion/cerrar-sesion").session(sesion).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/autenticacion/sesion").session(sesion))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void credencialesIncorrectasNoInicianSesion() throws Exception {
        mockMvc.perform(post("/api/autenticacion/iniciar-sesion")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuario":"admin_auth_test","contrasena":"incorrecta"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void rutasDeAdministracionExigenSesion() throws Exception {
        mockMvc.perform(get("/api/admin/prueba"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administradorInactivoNoPuedeIniciarSesion() throws Exception {
        jdbcTemplate.update("update administrador set activo = false where usuario = ?", USUARIO);

        mockMvc.perform(post("/api/autenticacion/iniciar-sesion")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuario":"admin_auth_test","contrasena":"ClaveTemporal-123"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
