// src/main/java/com/example/demo/config/SecurityConfig.java
package com.example.demo.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults()) // Spring ativa o CORS usando o bean corsConfigurationSource abaixo
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Liberação obrigatória do Preflight (OPTIONS)
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Endpoints de Autenticação e Públicos
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()

                // Acesso público para leituras específicas se necessário
                .requestMatchers(HttpMethod.GET,
                    "/api/clergy/popes",
                    "/api/clergy/bishops",
                    "/api/clergy/stats"
                ).permitAll()

                // Qualquer outra rota exige o token JWT válido
                .anyRequest().authenticated()
            );

        // Adiciona o filtro JWT para processar o header de Authorization
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // ✅ A mágica está aqui: permitindo Vercel e Localhost
        configuration.setAllowedOrigins(Arrays.asList(
            "https://apostolic-chain.vercel.app",
            "http://localhost:5173"
        ));
        
        // Métodos HTTP permitidos
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        
        // Headers permitidos (incluindo o que o seu interceptor de API envia)
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", 
            "Content-Type", 
            "X-Requested-With", 
            "Accept", 
            "Origin"
        ));
        
        // Permite o envio de cookies/auth headers se necessário
        configuration.setAllowCredentials(true);
        
        // Cache da resposta do Preflight (reduz requisições OPTIONS desnecessárias)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
