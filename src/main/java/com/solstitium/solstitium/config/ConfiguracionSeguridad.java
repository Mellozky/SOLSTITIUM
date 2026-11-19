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
                                             SecurityContextRepository contextRepository,
                                             CsrfTokenRepository repositorioTokenCsrf) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
                .cors(cors -> {})
                .csrf(csrf -> csrf
                        .repositorioTokenCsrf(repositorioTokenCsrf)
                        .csrfTokenRequestHandler(csrfHandler))
                .securityContext(context -> context.repositorioContextoSeguridad(contextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(authorize -> authorize
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
    UserDetailsService servicioUsuariosAdministradores(JdbcTemplate jdbcTemplate) {
        return usuario -> jdbcTemplate.query(
                        "select usuario, contrasena_hash, activo from administrador where usuario = ?",
                        resultSet -> {
                            if (!resultSet.next()) {
                                throw new UsernameNotFoundException("Credenciales incorrectas");
                            }
                            return User.withUsername(resultSet.getString("usuario"))
                                    .password(resultSet.getString("contrasena_hash"))
                                    .roles("ADMIN")
                                    .disabled(!resultSet.getBoolean("activo"))
                                    .build();
                        },
                        usuario);
    }

    @Bean
    AuthenticationProvider proveedorAutenticacion(UserDetailsService detailsService, PasswordEncoder codificadorContrasenas) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(detailsService);
        provider.setPasswordEncoder(codificadorContrasenas);
        return provider;
    }

    @Bean
    AuthenticationManager gestorAutenticacion(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
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
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(origenInterfaz));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
