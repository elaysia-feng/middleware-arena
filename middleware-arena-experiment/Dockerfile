FROM eclipse-temurin:21-jre
WORKDIR /app
COPY experiment.web/target/*.jar app.jar
EXPOSE 9003
ENTRYPOINT ["java", "-jar", "app.jar"]
