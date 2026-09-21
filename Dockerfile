# One image: the API and the pages it serves.
#
# The usual arrangement is two deployments and two domains, which is why CORS
# exists. This is one, because one thing to start beats two to keep in step -
# and the browser being same-origin means the allow-list stops mattering.
#
# Build context is the repository root, not backend/, because the frontend is
# part of the image.

# --- the frontend, built into static files -----------------------------------
FROM node:20-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# `npm run build` is `vue-tsc -b && vite build`, so a type error fails the
# image rather than shipping.
RUN npm run build

# --- the backend, with those files inside the jar ----------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS backend
WORKDIR /app
COPY backend/pom.xml .
# Warm the dependency cache before the sources, so editing code does not
# re-download the world.
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /frontend/dist ./src/main/resources/static
RUN mvn -q -B -DskipTests package

# --- what actually runs ------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# A free-tier box is small and the JVM's default heap sizing assumes it is not.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
