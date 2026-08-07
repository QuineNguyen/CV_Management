# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests \
 && mv target/*.jar target/app.jar

# ---------- Stage 2: run ----------
FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache tzdata curl \
 && addgroup -S app && adduser -S -G app app

ENV TZ=Asia/Ho_Chi_Minh
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Ho_Chi_Minh -Dfile.encoding=UTF-8"

WORKDIR /app
COPY --from=build /build/target/app.jar /app/app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
