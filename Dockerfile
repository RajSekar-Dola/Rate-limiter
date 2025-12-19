FROM eclipse-temurin:17
WORKDIR /app
COPY target/rate-limiter.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
