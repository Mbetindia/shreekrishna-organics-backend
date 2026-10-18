
package com.shreekrishna.organics.config;

import com.shreekrishna.organics.security.JwtAuthenticationFilter;
import com.shreekrishna.organics.security.RestAccessDeniedHandler;
import com.shreekrishna.organics.security.RestAuthenticationEntryPoint;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .cors(cors ->
                        cors.configurationSource(
                                corsConfigurationSource()
                        )
                )
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(auth -> auth

                        // Public authentication and health checks
                        .requestMatchers(
                                "/api/v1/health",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login"
                        ).permitAll()

                        // Razorpay webhook
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/payments/razorpay/webhook"
                        ).permitAll()

                        // Admin APIs - ADMIN role required
                        // Includes /api/v1/admin/blogs/**
                        .requestMatchers(
                                "/api/v1/admin/**"
                        ).hasRole("ADMIN")

                        // Public Website Testimonials
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/testimonials"
                        ).permitAll()

                        // Public YouTube Videos
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/videos"
                        ).permitAll()

                        // NEW: Public Published Blogs
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/blogs",
                                "/api/v1/blogs/**"
                        ).permitAll()

                        // Public Customer Reviews
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/products/*/reviews",
                                "/api/v1/products/*/reviews/summary"
                        ).permitAll()

                        // Public storefront product browsing
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/products",
                                "/api/v1/products/*",
                                "/api/v1/products/featured",
                                "/api/v1/products/category/*",
                                "/api/v1/products/*/variants",
                                "/api/v1/products/*/images",
                                "/api/v1/product-variants/*"
                        ).permitAll()

                        // Public category browsing
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/categories",
                                "/api/v1/categories/*"
                        ).permitAll()

                        // Public active combos
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/combos",
                                "/api/v1/combos/*"
                        ).permitAll()

                        // Public current product offer
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/products/*/offer"
                        ).permitAll()

                        // All other APIs require authentication
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(
                                authenticationEntryPoint
                        )
                        .accessDeniedHandler(
                                accessDeniedHandler
                        )
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        // Allow local development and deployed Vercel frontend
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "https://shree-krishna-organics-frontend.vercel.app"
        ));

        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept"
        ));

        // Required for frontend invoice PDF downloads
        configuration.setExposedHeaders(List.of(
                "Content-Disposition"
        ));

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/api/**",
                configuration
        );

        return source;
    }
}
