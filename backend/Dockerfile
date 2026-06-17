# ---- Stage 1: build jar dengan Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependency dulu (layer ini jarang berubah)
COPY pom.xml .
RUN mvn -q -e -B dependency:go-offline

# Build aplikasi
COPY src ./src
RUN mvn -q -e -B clean package -DskipTests

# ---- Stage 2: runtime ramping (JRE saja) ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/jazz-improv-backend-1.0.0.jar app.jar

# Render menyuntik env PORT; Spring membacanya via application.properties
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
