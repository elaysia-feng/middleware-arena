FROM eclipse-temurin:21-jre
WORKDIR /app
COPY order.web/target/*.jar app.jar
EXPOSE 9006
ENTRYPOINT ["java", "-jar", "app.jar"]
