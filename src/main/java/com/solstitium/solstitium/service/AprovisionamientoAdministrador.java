package com.solstitium.solstitium.service;

import com.solstitium.solstitium.exception.DatosAdministradorInvalidosException;
import com.solstitium.solstitium.model.entity.Administrador;
import com.solstitium.solstitium.repository.AdministradorRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea administradores y restablece su contraseña. Lo usa solo el comando de instalación
 * (ComandoAdministrador): el ERS no tiene caso de uso para crearlos desde la aplicación.
 * Guarda únicamente el hash BCrypt (NF-03); la contraseña nunca se registra ni se devuelve.
 */
@Service
@RequiredArgsConstructor
public class AprovisionamientoAdministrador {

    static final int LONGITUD_MINIMA_CONTRASENA = 10;
    /** BCrypt solo usa los primeros 72 bytes; una contraseña más larga se rechaza. */
    static final int BYTES_MAXIMOS_CONTRASENA = 72;
    private static final Pattern FORMATO_USUARIO = Pattern.compile("[A-Za-z0-9._-]{3,50}");
    private static final int LONGITUD_MAXIMA_NOMBRE = 100;

    private final AdministradorRepository administradores;
    private final PasswordEncoder codificadorContrasenas;

    @Transactional
    public void crear(String usuario, String nombre, String contrasena) {
        String usuarioValido = validarUsuario(usuario);
        String nombreValido = validarNombre(nombre);
        validarContrasena(contrasena);
        if (administradores.buscarPorUsuario(usuarioValido).isPresent()) {
            throw new DatosAdministradorInvalidosException(
                    "Ya existe un administrador con el usuario «" + usuarioValido + "». "
                            + "Para cambiar su contraseña, use la opción de restablecer.");
        }
        Administrador administrador = new Administrador();
        administrador.setUsuario(usuarioValido);
        administrador.setNombre(nombreValido);
        administrador.setContrasenaHash(codificadorContrasenas.encode(contrasena));
        administrador.setActivo(true);
        administrador.setFechaCreacion(LocalDateTime.now());
        administradores.save(administrador);
    }

    /** Cambia la contraseña de un administrador existente y lo deja activo. */
    @Transactional
    public void restablecerContrasena(String usuario, String contrasena) {
        String usuarioValido = validarUsuario(usuario);
        validarContrasena(contrasena);
        Administrador administrador = administradores.buscarPorUsuario(usuarioValido)
                .orElseThrow(() -> new DatosAdministradorInvalidosException(
                        "No existe un administrador con el usuario «" + usuarioValido + "»."));
        administrador.setContrasenaHash(codificadorContrasenas.encode(contrasena));
        administrador.setActivo(true);
        administradores.save(administrador);
    }

    private static String validarUsuario(String usuario) {
        String valor = usuario == null ? "" : usuario.strip();
        if (!FORMATO_USUARIO.matcher(valor).matches()) {
            throw new DatosAdministradorInvalidosException(
                    "El usuario debe tener entre 3 y 50 caracteres: letras sin tilde, números, punto, guion o guion bajo.");
        }
        return valor;
    }

    private static String validarNombre(String nombre) {
        String valor = nombre == null ? "" : nombre.strip();
        if (valor.isEmpty() || valor.length() > LONGITUD_MAXIMA_NOMBRE) {
            throw new DatosAdministradorInvalidosException("El nombre es obligatorio y admite hasta 100 caracteres.");
        }
        return valor;
    }

    private static void validarContrasena(String contrasena) {
        if (contrasena == null || contrasena.length() < LONGITUD_MINIMA_CONTRASENA) {
            throw new DatosAdministradorInvalidosException(
                    "La contraseña debe tener al menos " + LONGITUD_MINIMA_CONTRASENA + " caracteres.");
        }
        if (contrasena.getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS_CONTRASENA) {
            throw new DatosAdministradorInvalidosException(
                    "La contraseña es demasiado larga: admite hasta 72 bytes (unos 72 caracteres sin tildes).");
        }
    }
}
