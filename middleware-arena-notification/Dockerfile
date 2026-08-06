FROM eclipse-temurin:21-jre
WORKDIR /app
COPY notification.web/target/*.jar app.jar
EXPOSE 9005
ENTRYPOINT ["java", "-jar", "app.jar"]
