package com.solstitium.solstitium.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.solstitium.solstitium.exception.DatosAdministradorInvalidosException;
import com.solstitium.solstitium.model.entity.Administrador;
import com.solstitium.solstitium.repository.AdministradorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Cada prueba corre en una transacción que se revierte: no deja administradores en la base. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AprovisionamientoAdministradorTest {

    private static final String USUARIO = "admin.prueba_script";
    private static final String CONTRASENA = "Clave-Segura-2026";

    @Autowired
    private AprovisionamientoAdministrador aprovisionamiento;

    @Autowired
    private AdministradorRepository administradores;

    @Autowired
    private PasswordEncoder codificadorContrasenas;

    @Autowired
    private MockMvc cliente;

    private Administrador buscar(String usuario) {
        return administradores.buscarPorUsuario(usuario).orElseThrow();
    }

    @Test
    void creaUnAdministradorActivoConHashBcryptSinGuardarLaContrasena() {
        aprovisionamiento.crear("  " + USUARIO + "  ", "  Administrador de pruebas ", CONTRASENA);

        Administrador creado = buscar(USUARIO);
        assertThat(creado.getNombre()).isEqualTo("Administrador de pruebas");
        assertThat(creado.isActivo()).isTrue();
        assertThat(creado.getFechaCreacion()).isNotNull();
        assertThat(creado.getContrasenaHash()).startsWith("$2").doesNotContain(CONTRASENA);
        assertThat(codificadorContrasenas.matches(CONTRASENA, creado.getContrasenaHash())).isTrue();
    }

    @Test
    void elAdministradorCreadoPuedeIniciarSesionPorLaApi() throws Exception {
        aprovisionamiento.crear(USUARIO, "Administrador de pruebas", CONTRASENA);

        cliente.perform(post("/api/autenticacion/iniciar-sesion").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + USUARIO + "\",\"contrasena\":\"" + CONTRASENA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value(USUARIO));
    }

    @Test
    void noDuplicaUsuariosNiCambiaLaContrasenaExistente() {
        aprovisionamiento.crear(USUARIO, "Original", CONTRASENA);
        String hashOriginal = buscar(USUARIO).getContrasenaHash();

        assertThatThrownBy(() -> aprovisionamiento.crear(USUARIO, "Otro", "Otra-Clave-Larga"))
                .isInstanceOf(DatosAdministradorInvalidosException.class)
                .hasMessageContaining("Ya existe");
        assertThat(buscar(USUARIO).getContrasenaHash()).isEqualTo(hashOriginal);
        assertThat(buscar(USUARIO).getNombre()).isEqualTo("Original");
    }

    @Test
    void restablecerCambiaLaContrasenaYReactivaLaCuenta() {
        aprovisionamiento.crear(USUARIO, "Administrador de pruebas", CONTRASENA);
        Administrador administrador = buscar(USUARIO);
        administrador.setActivo(false);
        administradores.save(administrador);

        aprovisionamiento.restablecerContrasena(USUARIO, "Nueva-Clave-2026");

        Administrador restablecido = buscar(USUARIO);
        assertThat(restablecido.isActivo()).isTrue();
        assertThat(codificadorContrasenas.matches("Nueva-Clave-2026", restablecido.getContrasenaHash())).isTrue();
        assertThat(codificadorContrasenas.matches(CONTRASENA, restablecido.getContrasenaHash())).isFalse();
    }

    @Test
    void noRestableceUnUsuarioInexistente() {
        assertThatThrownBy(() -> aprovisionamiento.restablecerContrasena("no_existe_en_la_base", CONTRASENA))
                .isInstanceOf(DatosAdministradorInvalidosException.class)
                .hasMessageContaining("No existe");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ab", "con espacio", "tildé", "usuario@correo", "u23456789012345678901234567890123456789012345678901"})
    void rechazaUsuariosConFormatoInvalido(String usuario) {
        assertThatThrownBy(() -> aprovisionamiento.crear(usuario, "Nombre", CONTRASENA))
                .isInstanceOf(DatosAdministradorInvalidosException.class)
                .hasMessageContaining("usuario");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void exigeElNombre(String nombre) {
        assertThatThrownBy(() -> aprovisionamiento.crear(USUARIO, nombre, CONTRASENA))
                .isInstanceOf(DatosAdministradorInvalidosException.class)
                .hasMessageContaining("nombre");
    }

    @Test
    void rechazaContrasenasCortasODeMasDe72Bytes() {
        assertThatThrownBy(() -> aprovisionamiento.crear(USUARIO, "Nombre", "Corta-123"))
                .isInstanceOf(DatosAdministradorInvalidosException.class)
                .hasMessageContaining("al menos 10");
        assertThatThrownBy(() -> aprovisionamiento.crear(USUARIO, "Nombre", "ñ".repeat(37)))
                .isInstanceOf(DatosAdministradorInvalidosException.class)
                .hasMessageContaining("72 bytes");
        assertThat(administradores.buscarPorUsuario(USUARIO)).isEmpty();
    }
}
