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
USER flowkeeper
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
