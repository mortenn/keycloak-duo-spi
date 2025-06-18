FROM busybox:1.36.1-uclibc

# Copy your JAR into the container image
COPY target/keycloak-duo-spi-jar-with-dependencies.jar /tmp/keycloak-duo-spi.jar

CMD ["sh", "-c", "cp /tmp/keycloak-duo-spi.jar /opt/providers/keycloak-duo-spi.jar"]
