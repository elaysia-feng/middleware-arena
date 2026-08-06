FROM eclipse-temurin:21-jre
WORKDIR /app
COPY runner.web/target/*.jar app.jar
EXPOSE 9004
ENTRYPOINT ["java", "-jar", "app.jar"]
