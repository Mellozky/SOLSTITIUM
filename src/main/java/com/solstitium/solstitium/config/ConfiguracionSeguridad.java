package com.solstitium.solstitium.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class ConfiguracionSeguridad {

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http,
                                      SecurityContextRepository repositorioContexto,
                                      CsrfTokenRepository repositorioTokenCsrf) throws Exception {
        CsrfTokenRequestAttributeHandler manejadorCsrf = new CsrfTokenRequestAttributeHandler();
        manejadorCsrf.setCsrfRequestAttributeName(null);

        http
                .cors(cors -> {})
                .csrf(csrf -> csrf
                        .csrfTokenRepository(repositorioTokenCsrf)
                        .csrfTokenRequestHandler(manejadorCsrf))
                .securityContext(contexto -> contexto.securityContextRepository(repositorioContexto))
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(excepciones -> excepciones
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(autorizacion -> autorizacion
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/autenticacion/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/autenticacion/iniciar-sesion").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/autenticacion/sesion").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/autenticacion/cerrar-sesion").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll());

        return http.build();
    }

    @Bean
    UserDetailsService servicioUsuariosAdministradores(JdbcTemplate consultas) {
        return usuario -> consultas.query(
                        "select usuario, contrasena_hash, activo from administrador where usuario = ?",
                        resultado -> {
                            if (!resultado.next()) {
                                throw new UsernameNotFoundException("Credenciales incorrectas");
                            }
                            return User.withUsername(resultado.getString("usuario"))
                                    .password(resultado.getString("contrasena_hash"))
                                    .roles("ADMIN")
                                    .disabled(!resultado.getBoolean("activo"))
                                    .build();
                        },
                        usuario);
    }

    @Bean
    AuthenticationProvider proveedorAutenticacion(UserDetailsService servicioUsuarios, PasswordEncoder codificadorContrasenas) {
        DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider(servicioUsuarios);
        proveedor.setPasswordEncoder(codificadorContrasenas);
        return proveedor;
    }

    @Bean
    AuthenticationManager gestorAutenticacion(AuthenticationConfiguration configuracion) throws Exception {
        return configuracion.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder codificadorContrasenas() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityContextRepository repositorioContextoSeguridad() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionAuthenticationStrategy estrategiaAutenticacionSesion() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    CsrfTokenRepository repositorioTokenCsrf() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    CorsConfigurationSource origenConfiguracionCors(
            @Value("${app.frontend.origin:http://localhost:5173}") String origenInterfaz) {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(List.of(origenInterfaz));
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
        configuracion.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource origen = new UrlBasedCorsConfigurationSource();
        origen.registerCorsConfiguration("/**", configuracion);
        return origen;
    }
}
