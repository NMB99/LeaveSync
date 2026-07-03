package com.leavesync.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI(@Value("${leavesync.deployed-url}") String deployedUrl) {

        List<Server> serverList = new ArrayList<>();
        if (!deployedUrl.isEmpty()) {
            serverList.add(new Server().url(deployedUrl).description("Deployed (Render)"));
        }
        serverList.add(new Server().url("http://localhost:8080").description("Local development"));

        return new OpenAPI()
                .info(new Info()
                        .title("LeaveSync API")
                        .description("""
                                An employee leave management backend application API for Harlow Digital, a remote-first UK tech company (~ 200 employees).
                                Handles leave requests, approvals, balance tracking, user & team management, year-end processing and reporting.
                                
                                **Visitor credentials (HR role):**
                                - Email: `visitor@leavesync.com`
                                - Password: `Visitor@1234`
                                
                                Use `POST /api/auth/login` to get JWT token, then click **Authorise** and paste it.
                                
                                **GitHub**: https://github.com/NMB99/LeaveSync
                                
                                **LinkedIn**: https://www.linkedin.com/in/nilay-bhaisare
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Nilay Bhaisare")
                                .email("nilay.bhaisare@gmail.com")
                                .url("https://github.com/NMB99/LeaveSync")
                        )
                )
                .servers(serverList)
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                );
    }
}
