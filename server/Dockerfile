FROM eclipse-temurin:17-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B package -DskipTests
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 workbench && mkdir uploads && chown workbench uploads
COPY --from=build /build/target/procurement-server-1.0.0.jar app.jar
USER workbench
EXPOSE 8080
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-jar", "app.jar", "--server.port=8080", "--server.address=0.0.0.0"]
