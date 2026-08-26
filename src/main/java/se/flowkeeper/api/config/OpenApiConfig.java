package se.flowkeeper.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private static final String BEARER_SCHEME = "bearer-jwt";

	@Bean
	public OpenAPI flowkeeperOpenApi() {
		return new OpenAPI()
			.info(new Info()
				.title("FlowKeeper API")
				.description("Backend for the FlowKeeper web and mobile clients. "
					+ "Get a token from the flowkeeper Keycloak realm, then Authorize below.")
				.version("v1"))
			.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
			.components(new Components().addSecuritySchemes(BEARER_SCHEME,
				new SecurityScheme()
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")));
	}

}
