package com.solstitium.solstitium.config;

import com.solstitium.solstitium.exception.DatosAdministradorInvalidosException;
import com.solstitium.solstitium.service.AprovisionamientoAdministrador;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Comando de instalación para cargar administradores por script (ERS: ningún caso de uso los crea).
 * Solo actúa si la aplicación arranca con una de sus opciones, y termina al acabar:
 * <ul>
 *   <li>{@code --crear-administrador}: usa ADMIN_USUARIO, ADMIN_NOMBRE y ADMIN_CONTRASENA.</li>
 *   <li>{@code --restablecer-contrasena-administrador}: usa ADMIN_USUARIO y ADMIN_CONTRASENA.</li>
 * </ul>
 * Los datos llegan por variables de entorno para que la contraseña no quede en la línea de órdenes.
 * Los scripts de {@code scripts/} piden los datos de forma interactiva.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComandoAdministrador implements ApplicationRunner {

    static final String OPCION_CREAR = "crear-administrador";
    static final String OPCION_RESTABLECER = "restablecer-contrasena-administrador";

    private final AprovisionamientoAdministrador aprovisionamiento;
    private final Environment entorno;
    private final ConfigurableApplicationContext contexto;

    @Override
    public void run(ApplicationArguments argumentos) {
        boolean crear = argumentos.containsOption(OPCION_CREAR);
        boolean restablecer = argumentos.containsOption(OPCION_RESTABLECER);
        if (!crear && !restablecer) return;
        int codigo = ejecutar(crear, restablecer);
        System.exit(SpringApplication.exit(contexto, () -> codigo));
    }

    /** Devuelve 0 si la operación se completó y 1 si los datos fueron rechazados. */
    int ejecutar(boolean crear, boolean restablecer) {
        if (crear && restablecer) {
            log.error("Use solo una opción: --{} o --{}.", OPCION_CREAR, OPCION_RESTABLECER);
            return 1;
        }
        String usuario = entorno.getProperty("ADMIN_USUARIO");
        String contrasena = entorno.getProperty("ADMIN_CONTRASENA");
        try {
            if (crear) {
                aprovisionamiento.crear(usuario, entorno.getProperty("ADMIN_NOMBRE"), contrasena);
                log.info("Administrador «{}» creado y activo.", usuario);
            } else {
                aprovisionamiento.restablecerContrasena(usuario, contrasena);
                log.info("Contraseña del administrador «{}» restablecida; la cuenta queda activa.", usuario);
            }
            return 0;
        } catch (DatosAdministradorInvalidosException excepcion) {
            log.error("No se guardó ningún cambio: {}", excepcion.getMessage());
            return 1;
        }
    }
}
