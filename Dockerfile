FROM eclipse-temurin:17-jdk
WORKDIR /chatApp
COPY build/libs/*.jar chatApp.jar
ENTRYPOINT ["java", "-jar", "chatApp.jar"]
