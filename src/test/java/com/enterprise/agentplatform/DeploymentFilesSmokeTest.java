package com.enterprise.agentplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DeploymentFilesSmokeTest {

    @Test
    void shouldDeclareDockerBuildAndComposeRuntimeContracts() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(dockerfile)
                .contains("FROM node:22-bookworm-slim AS frontend-build")
                .contains("RUN npm ci")
                .contains("RUN npm run build")
                .contains("FROM maven:3.9-eclipse-temurin-17 AS backend-build")
                .contains("COPY --from=frontend-build /workspace/frontend/dist ./frontend/dist")
                .contains("RUN mvn -B -ntp -Pfrontend -DskipTests package")
                .contains("FROM eclipse-temurin:17-jre")
                .contains("ENTRYPOINT [\"java\", \"-jar\", \"/app/app.jar\"]");

        assertThat(compose)
                .contains("mysql:")
                .contains("image: mysql:8.4")
                .contains("MYSQL_DATABASE: agentdb")
                .contains("MYSQL_USER: ${MYSQL_APP_USER:-agent_app}")
                .contains("MYSQL_PASSWORD: ${MYSQL_APP_PASSWORD:-agent_app_password}")
                .contains("app:")
                .contains("SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-mysql}")
                .contains("MYSQL_JDBC_URL: jdbc:mysql://mysql:3306/agentdb")
                .contains("MYSQL_USERNAME: ${MYSQL_APP_USER:-agent_app}")
                .contains("MYSQL_PASSWORD: ${MYSQL_APP_PASSWORD:-agent_app_password}")
                .contains("condition: service_healthy")
                .contains("mysql-data:")
                .contains("app-storage:")
                .doesNotContain("MYSQL_USERNAME: root")
                .doesNotContain("MYSQL_PASSWORD: ${MYSQL_ROOT_PASSWORD:-123456}");
    }
}
