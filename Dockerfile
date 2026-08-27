# 빌드 스테이지 — Gradle Wrapper로 bootJar 생성. 테스트는 별도(/test, pre-commit)로 이미 검증하므로 스킵.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle gradle
RUN chmod +x gradlew
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# 런타임 스테이지 — JRE만 포함한 슬림 이미지
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
