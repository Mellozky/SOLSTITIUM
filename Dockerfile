# Imagen de despliegue: API Spring Boot con la interfaz React empaquetada en el mismo JAR.

FROM eclipse-temurin:21-jdk AS construccion
WORKDIR /proyecto
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY frontend/ frontend/
COPY src/ src/
# "false" retira el aviso de datos de prueba cuando la base tenga valores reales (ver README).
ARG VITE_AVISO_DATOS_PRUEBA
# Las pruebas se ejecutan en el CI contra una base exclusiva; aquí no hay base disponible.
RUN ./mvnw -B -Pinterfaz package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /aplicacion
RUN useradd --system --uid 10001 solstitium
COPY --from=construccion /proyecto/target/SOLSTITIUM-*.jar aplicacion.jar
USER solstitium
# El plan gratuito de Render ofrece 512 MB: limitar el heap a una fracción de la memoria del contenedor.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "aplicacion.jar"]
