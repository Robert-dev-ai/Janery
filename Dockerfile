# Janery publicado: el frontend compilado va dentro del backend y ambos se sirven en el mismo dominio.
# Las pruebas no se repiten aquí: ya las corre la CI (.github/workflows/ci.yml) antes del merge a main.

# 1. Frontend
FROM node:24-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npx ng build

# 2. Backend, con el frontend en static/
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /app/backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=frontend /app/frontend/dist/frontend/browser src/main/resources/static
RUN ./mvnw -B -q package -DskipTests && cp target/janery-backend-*.jar /app/janery.jar

# 3. Imagen final: solo Java y el .jar
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 janery
USER janery
WORKDIR /app
COPY --from=backend /app/janery.jar janery.jar
# El plan gratuito tiene 512 MB: limita la memoria de Java para no pasarse.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "janery.jar"]
