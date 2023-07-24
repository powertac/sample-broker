FROM maven:3-openjdk-11 AS build
WORKDIR /opt/powertac/sample-broker/build
COPY . .
RUN mvn clean package

FROM openjdk:11-jre-slim
ENV BROKER_JAR=sample-broker-1.9.0.jar
WORKDIR /opt/powertac/sample-broker
COPY --from=build /opt/powertac/sample-broker/build/target/${BROKER_JAR} ./sample-broker.jar
ENTRYPOINT ["java", "-jar", "/opt/powertac/sample-broker/sample-broker.jar"]