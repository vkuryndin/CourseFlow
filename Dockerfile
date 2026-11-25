FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 courseflow \
    && useradd --uid 10001 --gid 10001 --create-home courseflow \
    && install -d -m 0700 -o courseflow -g courseflow /home/courseflow/.courseflow
WORKDIR /app
COPY --chown=10001:10001 build/libs/*.jar /app/courseflow.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.home=/home/courseflow", "-jar", "/app/courseflow.jar"]
