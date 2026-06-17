FROM maven:3.9.6-eclipse-temurin-22 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean compile

FROM eclipse-temurin:22-jre
WORKDIR /app
COPY --from=build /app/target/classes ./target/classes
EXPOSE 8080
EXPOSE 5050
CMD ["java", "-cp", "target/classes", "com.polyvault.App", "server"]