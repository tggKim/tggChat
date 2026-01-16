# 1) Build stage: jar 생성
FROM gradle:8.7-jdk17 AS build
WORKDIR /app

# Gradle 캐시 효율을 위해 먼저 빌드 메타 파일만 복사
COPY gradlew /app/gradlew
COPY gradle /app/gradle
COPY build.gradle* /app/
COPY settings.gradle* /app/

RUN chmod +x /app/gradlew

# 소스 복사 후 jar 빌드
COPY src /app/src
RUN /app/gradlew clean bootJar

# 2) Run stage: 기존 구조 유지 (/chatApp + chatApp.jar)
FROM eclipse-temurin:17-jdk
WORKDIR /chatApp
COPY --from=build /app/build/libs/*.jar chatApp.jar
ENTRYPOINT ["java", "-jar", "chatApp.jar"]
