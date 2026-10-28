# 1단계: 빌드 - JDK와 Gradle로 실행 가능한 jar 파일을 만든다
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# 빌드 설정 파일을 먼저 복사해서 라이브러리를 받아 둔다 (소스만 바뀌면 이 단계는 캐시를 재사용)
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

# 소스를 복사하고 jar를 만든다 (테스트는 로컬에서 돌리므로 여기선 생략)
COPY src src
RUN ./gradlew bootJar --no-daemon

# 2단계: 실행 - JRE와 jar만 담아서 이미지를 작게 만든다
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]