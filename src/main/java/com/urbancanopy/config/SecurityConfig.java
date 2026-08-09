package com.urbancanopy.config;

import com.urbancanopy.common.dto.ErrorResponse;
import com.urbancanopy.user.service.LoginAttemptHandler;
import com.urbancanopy.user.service.UrbanCanopyUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * The application's security policy (FR-AUTH-01 … FR-AUTH-12, FR-ADM-08).
 *
 * <h2>Pages are open; the API is not</h2>
 * <p>Static HTML is served to anyone. That is not an oversight — every page fetches its data
 * through the API, so an unauthenticated visitor who loads {@code /plans.html} receives a
 * shell, gets 401 from the endpoint, and is redirected to the login page by {@code api.js}.
 * Guarding the HTML as well would add a second, redundant place for the rule to live, and the
 * public catalogue would then need carving back out of it.</p>
 *
 * <h2>Errors are JSON, never a redirect</h2>
 * <p>A {@code fetch} that is answered with a 302 to a login page produces a confusing success:
 * the browser follows it and hands the caller an HTML page where JSON was expected. Both the
 * unauthenticated and forbidden cases therefore return a status code with an
 * {@link ErrorResponse} body — the same envelope every other error in the application uses —
 * and the client decides what to do (FR-ADM-08: a styled refusal, not a redirect loop).</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * BCrypt, at the default strength of 10.
     *
     * <p>Deliberately a slow hash. The property that matters is not that it is hard to reverse
     * but that it is expensive to try: a leaked table of BCrypt hashes cannot be run through a
     * dictionary at the millions-per-second a fast digest permits (FR-AUTH-02, BR-02).</p>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           UrbanCanopyUserDetailsService userDetailsService,
                                           LoginAttemptHandler attempts) throws Exception {
        http
            // ------------------------------------------------------------------ CSRF
            /*
             * Enabled on every state-changing request (FR-AUTH-12). The token is issued in a
             * cookie the browser's JavaScript can read and echo back in a header — the standard
             * pattern for an API driven by fetch, and safe because the same-origin policy stops
             * another site from reading that cookie. GET requests are exempt by definition:
             * they change nothing.
             */
            .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))

            // ------------------------------------------------------------ authorisation
            .authorizeHttpRequests(auth -> auth
                    // Static assets and pages.
                    .requestMatchers("/", "/*.html", "/admin/*.html",
                            "/css/**", "/js/**", "/img/**", "/uploads/**", "/favicon.ico").permitAll()

                    // Registering and signing in must be reachable without being signed in.
                    .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()

                    // The species catalogue is reference material; browsing it before signing
                    // up is the behaviour the requirements describe (FR-SPEC-08).
                    .requestMatchers(org.springframework.http.HttpMethod.GET,
                            "/api/v1/tree-species", "/api/v1/tree-species/*",
                            "/api/v1/tree-species/filters").permitAll()

                    .requestMatchers("/actuator/health").permitAll()

                    // Everything under /admin requires the role, in one rule rather than one
                    // annotation per method — a rule that cannot be forgotten on a new endpoint.
                    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                    .anyRequest().authenticated())

            // -------------------------------------------------------------- form login
            .formLogin(form -> form
                    .loginProcessingUrl("/api/v1/auth/login")
                    .usernameParameter("email")
                    .passwordParameter("password")
                    .successHandler(attempts::onSuccess)
                    .failureHandler(this::onLoginFailure)
                    .permitAll())

            // ------------------------------------------------------------------ logout
            // POST only, which Spring Security enforces by default while CSRF is enabled —
            // a logout reachable by GET can be triggered by any image tag on any site.
            .logout(logout -> logout
                    .logoutUrl("/api/v1/auth/logout")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("JSESSIONID")
                    .logoutSuccessHandler((request, response, authentication) ->
                            writeJson(response, HttpStatus.OK, "{\"success\":true,\"message\":\"You have been signed out.\"}")))

            // ----------------------------------------------------------------- session
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    // A new session id on login, so a session fixed by an attacker before
                    // sign-in is not the one that ends up authenticated.
                    .sessionFixation(fixation -> fixation.changeSessionId()))

            // ------------------------------------------------------- unauthenticated/403
            .exceptionHandling(handling -> handling
                    .authenticationEntryPoint((request, response, exception) ->
                            writeError(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                                    "Please sign in to continue."))
                    // A missing or stale CSRF token is not a permission problem, and telling
                    // the user it is sends them to ask an administrator for rights they
                    // already have. It means their page has been open longer than the
                    // session — a reload fixes it.
                    .accessDeniedHandler((request, response, exception) -> {
                        boolean csrf = exception instanceof CsrfException;
                        writeError(request, response, HttpStatus.FORBIDDEN,
                                csrf ? "CSRF_TOKEN_INVALID" : "ACCESS_DENIED",
                                csrf
                                        ? "Your session security token has expired. Reload the page and try again."
                                        : "You do not have permission to perform this action.");
                    }))

            .userDetailsService(userDetailsService)

            // See csrfCookieFilter() for why this is necessary.
            .addFilterAfter(csrfCookieFilter(), CsrfFilter.class);

        return http.build();
    }

    /**
     * Forces the CSRF token to be materialised on every request, so its cookie is actually sent.
     *
     * <h2>Why this filter has to exist</h2>
     * <p>Spring Security 6 resolves the CSRF token <em>lazily</em>: the token is only generated,
     * and therefore the cookie only written, if something asks for it during the request. A
     * browser client that has never made a state-changing request has consequently never
     * received a token — so its first {@code POST} arrives without one and is rejected with 403.
     * The failure is confusing because it looks like the CSRF configuration is wrong when it is
     * in fact working exactly as configured.</p>
     *
     * <p>Reading {@code token.getToken()} is what triggers generation and the {@code Set-Cookie}.
     * The value is deliberately discarded — the call is made for its effect.</p>
     */
    private OncePerRequestFilter csrfCookieFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                if (token != null) {
                    token.getToken();
                }
                chain.doFilter(request, response);
            }
        };
    }

    /**
     * Turns an authentication failure into a message the user can act on.
     *
     * <p>A wrong password and an unknown email give the same answer, so the form cannot be used
     * to discover which addresses have accounts. A disabled or locked account is told the truth,
     * because reaching either of those requires having already supplied the right password.</p>
     */
    private void onLoginFailure(HttpServletRequest request, HttpServletResponse response,
                                AuthenticationException exception) throws IOException {
        String message;
        if (exception instanceof DisabledException) {
            message = "This account has been disabled. Contact an administrator.";
        } else if (exception instanceof LockedException) {
            message = "Too many failed attempts. This account is temporarily locked — try again shortly.";
        } else {
            message = "Invalid email address or password.";
        }

        writeError(request, response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", message);
    }

    /** Writes the application's standard error envelope, so clients parse one shape. */
    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            HttpStatus status, String code, String message) throws IOException {
        ErrorResponse body = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .errorCode(code)
                .message(message)
                .path(request.getRequestURI())
                .build();

        writeJson(response, status, objectMapper.writeValueAsString(body));
    }

    private void writeJson(HttpServletResponse response, HttpStatus status, String json) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json);
    }
}
