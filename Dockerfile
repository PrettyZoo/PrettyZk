FROM eclipse-temurin:17-jre AS builder
WORKDIR /app
COPY . .
RUN ./gradlew :app:jar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/app/build/libs/app-*.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar", "--web", "--port", "8080"]
