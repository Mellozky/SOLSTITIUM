package com.solstitium.solstitium.controller;

import com.solstitium.solstitium.model.dto.SesionAdministradorResponse;
import com.solstitium.solstitium.model.dto.SolicitudLogin;
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
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          SessionAuthenticationStrategy sessionAuthenticationStrategy,
                          CsrfTokenRepository csrfTokenRepository) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    /** Entrega un token CSRF para las peticiones de escritura de la SPA. */
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getToken());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody SolicitudLogin solicitud,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        try {
            Authentication autenticacion = authenticationManager.authenticate(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            solicitud.usuario(), solicitud.contrasena()));
            // Asegura que exista una sesión antes de aplicar la protección contra fijación de sesión.
            request.getSession(true);
            sessionAuthenticationStrategy.onAuthentication(autenticacion, request, response);

            SecurityContext contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(autenticacion);
            SecurityContextHolder.setContext(contexto);
            securityContextRepository.saveContext(contexto, request, response);
            // Obliga a la SPA a obtener un token ligado a la sesión autenticada.
            csrfTokenRepository.saveToken(null, request, response);

            return ResponseEntity.ok(new SesionAdministradorResponse(autenticacion.getName()));
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorAuthResponse("Credenciales incorrectas o cuenta inactiva."));
        }
    }

    @GetMapping("/sesion")
    public SesionAdministradorResponse sesion(Authentication autenticacion) {
        return new SesionAdministradorResponse(autenticacion.getName());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication autenticacion,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        logoutHandler.logout(request, response, autenticacion);
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    public record CsrfResponse(String token) {}

    public record ErrorAuthResponse(String mensaje) {}
}
