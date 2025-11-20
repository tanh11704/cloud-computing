FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app

COPY pom.xml .
# RUN mvn dependency:go-offline (Tạm bỏ qua để tránh lỗi mạng 502 ngẫu nhiên)

COPY src ./src
# Build và tải dependency cùng lúc (Maven sẽ tự retry tốt hơn)
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
