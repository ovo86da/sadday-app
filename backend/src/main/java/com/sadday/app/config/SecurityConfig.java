package com.sadday.app.config;

import com.sadday.app.security.apikey.ApiKeyAuthFilter;
import com.sadday.app.security.jwt.JwtAuthFilter;
import com.sadday.app.security.ratelimit.RateLimitFilter;
import com.sadday.app.shared.util.ApiPaths;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import jakarta.annotation.Nullable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Configuración central de Spring Security.
 *
 * <p>Principios aplicados:
 * <ul>
 *   <li>Sin sesiones HTTP — completamente stateless (JWT)</li>
 *   <li>CSRF deshabilitado — no aplica con tokens Bearer</li>
 *   <li>Contraseñas con Argon2id (parámetros OWASP 2026)</li>
 *   <li>Autorización en dos capas: SecurityFilterChain (URL) + @PreAuthorize (método)</li>
 *   <li>Security headers: X-Content-Type-Options, X-Frame-Options, CSP, Referrer-Policy</li>
 *   <li>Rate limiting por IP en endpoints de autenticación (ver {@link RateLimitFilter})</li>
 *   <li>Swagger solo accesible sin auth en perfil non-prod; en prod requiere autenticación</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter    jwtAuthFilter;
    private final ApiKeyAuthFilter apiKeyAuthFilter;

    /** Null en el perfil test (desactivado con @Profile("!test")) */
    @Nullable
    private final RateLimitFilter  rateLimitFilter;

    /** true cuando el perfil activo es "prod" */
    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Autowired
    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          ApiKeyAuthFilter apiKeyAuthFilter,
                          @Nullable @Autowired(required = false) RateLimitFilter rateLimitFilter) {
        this.jwtAuthFilter    = jwtAuthFilter;
        this.apiKeyAuthFilter = apiKeyAuthFilter;
        this.rateLimitFilter  = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean isProd = "prod".equalsIgnoreCase(activeProfile);

        http
                // Sin estado — no crear ni usar sesiones HTTP
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CSRF no aplica con tokens Bearer
                .csrf(AbstractHttpConfigurer::disable)

                // CORS gestionado por CorsConfig
                .cors(cors -> {})

                // ── Security headers ──────────────────────────────────────
                .headers(headers -> {
                    headers
                        // Evita MIME-type sniffing
                        .contentTypeOptions(opt -> {})
                        // Bloquea embeber en iframes (clickjacking)
                        .frameOptions(frame -> frame.deny())
                        // CSP estricto para una API pura (sin HTML de usuario)
                        .contentSecurityPolicy(csp ->
                                csp.policyDirectives(
                                        "default-src 'none'; " +
                                        "frame-ancestors 'none'"))
                        // No enviar Referer a orígenes cruzados
                        .referrerPolicy(ref ->
                                ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));
                    // HSTS solo en producción (requiere HTTPS)
                    if (isProd) {
                        headers.httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000));
                    }
                })

                // ── Reglas de autorización ────────────────────────────────
                .authorizeHttpRequests(auth -> auth
                        // Endpoints públicos de auth
                        .requestMatchers(
                                ApiPaths.AUTH_LOGIN,
                                ApiPaths.AUTH_REFRESH,
                                ApiPaths.AUTH_FORGOT,
                                ApiPaths.AUTH_RESET,
                                ApiPaths.AUTH_MFA + "/login",
                                ApiPaths.AUTH + "/country-challenge/verify",
                                ApiPaths.REGISTRO + "/**"
                        ).permitAll()

                        // Logout y setup/disable de MFA requieren autenticación
                        .requestMatchers(
                                ApiPaths.AUTH_LOGOUT,
                                ApiPaths.AUTH_LOGOUT + "-all",
                                ApiPaths.AUTH_MFA + "/setup",
                                ApiPaths.AUTH_MFA + "/confirm",
                                ApiPaths.AUTH_MFA
                        ).authenticated()

                        // Swagger/OpenAPI: abierto en local/test, protegido en prod
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**")
                                .access((authentication, context) -> {
                                    if (!isProd) {
                                        return new org.springframework.security.authorization.AuthorizationDecision(true);
                                    }
                                    // En prod solo Admin y Secretaria pueden ver la documentación
                                    return new org.springframework.security.authorization.AuthorizationDecision(
                                            authentication.get().getAuthorities().stream()
                                                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                                                            || a.getAuthority().equals("ROLE_SECRETARIA")));
                                })

                        // Actuator: health público (para Docker/load-balancer health checks)
                        //   info: público en local (útil en dev), protegido en prod
                        //   resto: siempre denegado (los endpoints ni están habilitados, doble seguro)
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/info")
                                .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(!isProd))
                        .requestMatchers("/actuator/**").denyAll()

                        // Portal de administración: Admin, Secretaria y Directivo
                        // (desbloquear cuenta está adicionalmente protegido con @PreAuthorize("hasRole('ADMIN')"))
                        .requestMatchers(ApiPaths.ADMIN + "/**")
                                .hasAnyRole("ADMIN", "SECRETARIA", "DIRECTIVO")

                                // Todo lo demás requiere autenticación
                        .anyRequest().authenticated()
                )

                // ── Filtros custom ────────────────────────────────────────
                // Orden deseado (de primero a último):
                //   RateLimitFilter → ApiKeyAuthFilter → JwtAuthFilter → UsernamePasswordAuthenticationFilter
                //
                // Spring Security 7 solo permite referenciar filtros built-in
                // (con orden registrado) en addFilterBefore/addFilterAfter.
                // Registramos cada filtro ANTES de UsernamePasswordAuthenticationFilter;
                // cada addFilterBefore subsiguiente se inserta delante del anterior.
                .addFilterBefore(jwtAuthFilter,    UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiKeyAuthFilter,  UsernamePasswordAuthenticationFilter.class)

                // Manejo de errores de seguridad — sin exponer detalles
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(
                                (req, res, e) -> res.setStatus(HttpStatus.FORBIDDEN.value()))
                );

        // Rate limit solo cuando está disponible (no en tests)
        if (rateLimitFilter != null) {
            http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    /**
     * Argon2id con parámetros OWASP recomendados (2026):
     * salt=16 bytes, hash=32 bytes, parallelism=1, memory=19MB, iterations=2
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
