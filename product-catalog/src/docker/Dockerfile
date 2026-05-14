# --- Stage 1: Build Stage ---
FROM eclipse-temurin:21.0.9_10-jre-alpine-3.23
WORKDIR /app
RUN addgroup -S pyur && adduser -S pyur -G pyur
USER pyur:pyur

# The plugin provides the context; we just copy the JAR from target
COPY maven/product-catalog-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]