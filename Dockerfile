FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline
COPY src ./src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S flowkeeper && adduser -S flowkeeper -G flowkeeper
COPY --from=build /build/target/flowkeeper-api-*.jar app.jar
# /data/avatars is a volume mount point (flowkeeper-infra's
# docker-compose.prod.yml mounts flowkeeper_avatars_data there). Docker
# only copies an image path's ownership into a *fresh* named volume — if
# this directory doesn't exist in the image first, the volume comes up
# root-owned, and the non-root user below gets "Permission denied" on
# every upload despite the app starting up fine (createDirectories() is a
# no-op on a dir that already exists as the mount point).
RUN mkdir -p /data/avatars && chown -R flowkeeper:flowkeeper /data/avatars
USER flowkeeper
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
