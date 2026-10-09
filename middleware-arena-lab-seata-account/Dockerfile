FROM eclipse-temurin:21-jre
WORKDIR /app
COPY account.web/target/*.jar app.jar
EXPOSE 9008
ENTRYPOINT ["java", "-jar", "app.jar"]
