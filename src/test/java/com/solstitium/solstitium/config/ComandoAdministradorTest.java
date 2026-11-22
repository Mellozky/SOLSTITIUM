package com.solstitium.solstitium.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.solstitium.solstitium.repository.AdministradorRepository;
import com.solstitium.solstitium.service.AprovisionamientoAdministrador;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** Prueba el comando sin terminar la JVM: ejecutar() devuelve el código de salida. */
@SpringBootTest
@Transactional
class ComandoAdministradorTest {

    @Autowired
    private AprovisionamientoAdministrador aprovisionamiento;

    @Autowired
    private AdministradorRepository administradores;

    @Autowired
    private PasswordEncoder codificadorContrasenas;

    @Autowired
    private ConfigurableApplicationContext contexto;

    @Autowired
    private ComandoAdministrador comandoRegistrado;

    private ComandoAdministrador comandoCon(MockEnvironment entorno) {
        return new ComandoAdministrador(aprovisionamiento, entorno, contexto);
    }

    @Test
    void sinOpcionesNoHaceNadaYLaAplicacionArrancaNormalmente() {
        comandoRegistrado.run(new DefaultApplicationArguments());
        assertThat(contexto.isActive()).isTrue();
    }

    @Test
    void creaElAdministradorConLasVariablesDeEntorno() {
        var entorno = new MockEnvironment()
                .withProperty("ADMIN_USUARIO", "admin_comando")
                .withProperty("ADMIN_NOMBRE", "Administrador del comando")
                .withProperty("ADMIN_CONTRASENA", "Clave-Del-Comando-1");

        assertThat(comandoCon(entorno).ejecutar(true, false)).isZero();
        var creado = administradores.buscarPorUsuario("admin_comando").orElseThrow();
        assertThat(codificadorContrasenas.matches("Clave-Del-Comando-1", creado.getContrasenaHash())).isTrue();
    }

    @Test
    void restableceLaContrasenaConLasVariablesDeEntorno() {
        aprovisionamiento.crear("admin_comando", "Administrador del comando", "Clave-Del-Comando-1");
        var entorno = new MockEnvironment()
                .withProperty("ADMIN_USUARIO", "admin_comando")
                .withProperty("ADMIN_CONTRASENA", "Clave-Nueva-Comando");

        assertThat(comandoCon(entorno).ejecutar(false, true)).isZero();
        var actualizado = administradores.buscarPorUsuario("admin_comando").orElseThrow();
        assertThat(codificadorContrasenas.matches("Clave-Nueva-Comando", actualizado.getContrasenaHash())).isTrue();
    }

    @Test
    void devuelveErrorSiFaltanDatosOSeUsanAmbasOpciones() {
        assertThat(comandoCon(new MockEnvironment()).ejecutar(true, false)).isEqualTo(1);
        assertThat(comandoCon(new MockEnvironment()).ejecutar(true, true)).isEqualTo(1);
        assertThat(administradores.buscarPorUsuario("admin_comando")).isEmpty();
    }
}
