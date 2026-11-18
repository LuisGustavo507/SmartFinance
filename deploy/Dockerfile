# syntax=docker/dockerfile:1

# 1. Angular: builds the static files that the Spring Boot jar serves.
FROM node:24-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2. Spring Boot: packages the API and the prebuilt frontend into one jar.
FROM eclipse-temurin:21-jdk-alpine AS backend
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw -B -Dmaven.test.skip=true dependency:go-offline
COPY src/ src/
COPY --from=frontend /build/frontend/dist/ frontend/dist/
RUN ./mvnw -B -Dmaven.test.skip=true package

# 3. Runtime: no build tools and no root user.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S smartfinance \
    && adduser -S smartfinance -G smartfinance
WORKDIR /app
COPY --from=backend /build/target/SmartFinance-*.jar app.jar
USER smartfinance
ENV TZ=UTC \
    PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
