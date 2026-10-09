FROM eclipse-temurin:21-jre
WORKDIR /app
COPY storage.web/target/*.jar app.jar
EXPOSE 9007
ENTRYPOINT ["java", "-jar", "app.jar"]
