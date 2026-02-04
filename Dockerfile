FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY pom.xml ./
COPY truthcrawl-core/pom.xml truthcrawl-core/
COPY truthcrawl-cli/pom.xml truthcrawl-cli/
COPY truthcrawl-it/pom.xml truthcrawl-it/
COPY truthcrawl-core/src truthcrawl-core/src
COPY truthcrawl-cli/src truthcrawl-cli/src
COPY truthcrawl-it/src truthcrawl-it/src
RUN apk add --no-cache maven && mvn package -pl truthcrawl-core,truthcrawl-cli -q -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /build/truthcrawl-core/target/truthcrawl-core-0.1.0-SNAPSHOT.jar lib/
COPY --from=build /build/truthcrawl-cli/target/truthcrawl-cli-0.1.0-SNAPSHOT.jar lib/

RUN printf '#!/bin/sh\nexec java -cp "/app/lib/*" io.truthcrawl.cli.Main "$@"\n' > /app/truthcrawl \
    && chmod +x /app/truthcrawl

COPY docker-entrypoint.sh /app/
RUN chmod +x /app/docker-entrypoint.sh

ENV TRUTHCRAWL_DATA=/data
VOLUME /data
EXPOSE 8080

ENTRYPOINT ["/app/docker-entrypoint.sh"]
