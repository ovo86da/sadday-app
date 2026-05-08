# =============================================================================
# Stage 1 — Build
# =============================================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Descargar dependencias primero (cache de capas de Docker)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copiar fuentes y compilar
COPY src ./src
RUN mvn clean package -DskipTests --no-transfer-progress

# =============================================================================
# Stage 2 — Runtime
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Usuario no-root por seguridad (nunca correr como root)
RUN addgroup -S sadday && adduser -S sadday -G sadday

WORKDIR /app

# Copiar JAR desde el stage de build
COPY --from=builder /build/target/*.jar app.jar

# Directorio de logs con permisos correctos
RUN mkdir -p /app/logs && chown sadday:sadday /app/logs

USER sadday

EXPOSE 8080

ENTRYPOINT ["java", \
  "-jar", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseVirtualThreads", \
  "-XX:MaxRAMPercentage=75.0", \
  "app.jar"]
