package se.flowkeeper.api.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

	@Value("${app.cors.allowed-origin}")
	private String allowedOrigin;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			// Stateless bearer-token API, no cookie-based session — CSRF is not
			// applicable the way it was for the legacy Thymeleaf apps. Do not
			// carry this disable-CSRF pattern into anything that starts using
			// cookies or server-rendered forms.
			.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				// The docs page itself is public; every endpoint it lets you
				// try still requires a real bearer token via "Authorize".
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				.anyRequest().authenticated()
			)
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

		return http.build();
	}

	// The web app lives on a different subdomain (staging.flowkeeper.se vs
	// api.staging.flowkeeper.se) — a different origin, so the browser
	// preflights every request that carries the Authorization header. With
	// no CorsConfigurationSource at all, Spring Security had nothing to
	// answer that preflight with, so the browser silently blocked every
	// authenticated call — confirmed live: first real end-to-end browser
	// test landed on "Couldn't load your account" right after a
	// successful login, since MockMvc/curl-based testing never exercises
	// browser CORS enforcement. Bearer tokens live in the Authorization
	// header, not cookies, so credentials/cookies don't need to be allowed.
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(List.of(allowedOrigin));
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

}
