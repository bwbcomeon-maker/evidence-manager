FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /build

COPY backend/app/pom.xml ./pom.xml
RUN mvn -q -DskipTests dependency:go-offline

COPY backend/app/src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

RUN mkdir -p /app/data/uploads

COPY --from=builder /build/target/*.jar /app/app.jar

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
