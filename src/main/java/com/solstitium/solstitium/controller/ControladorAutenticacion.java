package com.solstitium.solstitium.controller;

import com.solstitium.solstitium.model.dto.RespuestaSesionAdministrador;
import com.solstitium.solstitium.model.dto.SolicitudInicioSesion;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/autenticacion")
public class ControladorAutenticacion {

    private final AuthenticationManager gestorAutenticacion;
    private final SecurityContextRepository repositorioContextoSeguridad;
    private final SessionAuthenticationStrategy estrategiaAutenticacionSesion;
    private final CsrfTokenRepository repositorioTokenCsrf;
    private final SecurityContextLogoutHandler manejadorCierreSesion = new SecurityContextLogoutHandler();

    public ControladorAutenticacion(AuthenticationManager gestorAutenticacion,
                                     SecurityContextRepository repositorioContextoSeguridad,
                                     SessionAuthenticationStrategy estrategiaAutenticacionSesion,
                                     CsrfTokenRepository repositorioTokenCsrf) {
        this.gestorAutenticacion = gestorAutenticacion;
        this.repositorioContextoSeguridad = repositorioContextoSeguridad;
        this.estrategiaAutenticacionSesion = estrategiaAutenticacionSesion;
        this.repositorioTokenCsrf = repositorioTokenCsrf;
    }

    /** Entrega un token CSRF para las peticiones de escritura de la SPA. */
    @GetMapping("/csrf")
    public RespuestaTokenCsrf obtenerTokenCsrf(CsrfToken tokenCsrf) {
        return new RespuestaTokenCsrf(tokenCsrf.getToken());
    }

    @PostMapping("/iniciar-sesion")
    public ResponseEntity<?> iniciarSesion(@Valid @RequestBody SolicitudInicioSesion solicitud,
                                   HttpServletRequest solicitudHttp,
                                   HttpServletResponse respuestaHttp) {
        try {
            Authentication autenticacion = gestorAutenticacion.authenticate(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            solicitud.usuario(), solicitud.contrasena()));
            // Asegura que exista una sesión antes de aplicar la protección contra fijación de sesión.
            solicitudHttp.getSession(true);
            estrategiaAutenticacionSesion.onAuthentication(autenticacion, solicitudHttp, respuestaHttp);

            SecurityContext contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(autenticacion);
            SecurityContextHolder.setContext(contexto);
            repositorioContextoSeguridad.saveContext(contexto, solicitudHttp, respuestaHttp);
            // Obliga a la SPA a obtener un token ligado a la sesión autenticada.
            repositorioTokenCsrf.saveToken(null, solicitudHttp, respuestaHttp);

            return ResponseEntity.ok(new RespuestaSesionAdministrador(autenticacion.getName()));
        } catch (AuthenticationException excepcion) {
            SecurityContextHolder.clearContext();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new RespuestaErrorAutenticacion("Credenciales incorrectas o cuenta inactiva."));
        }
    }

    @GetMapping("/sesion")
    public RespuestaSesionAdministrador consultarSesion(Authentication autenticacion) {
        return new RespuestaSesionAdministrador(autenticacion.getName());
    }

    @PostMapping("/cerrar-sesion")
    public ResponseEntity<Void> cerrarSesion(Authentication autenticacion,
                                       HttpServletRequest solicitudHttp,
                                       HttpServletResponse respuestaHttp) {
        manejadorCierreSesion.logout(solicitudHttp, respuestaHttp, autenticacion);
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    public record RespuestaTokenCsrf(String token) {}

    public record RespuestaErrorAutenticacion(String mensaje) {}
}
