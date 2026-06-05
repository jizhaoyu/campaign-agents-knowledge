FROM node:22-bookworm-slim AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
COPY --from=frontend-build /workspace/frontend/dist ./frontend/dist
RUN mvn -B -ntp -Pfrontend -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN addgroup --system agent && adduser --system --ingroup agent agent
COPY --from=backend-build /workspace/target/knowledge-ticket-agent-0.0.1-SNAPSHOT.jar /app/app.jar
RUN mkdir -p /app/storage /app/data && chown -R agent:agent /app
USER agent
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=mysql
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
