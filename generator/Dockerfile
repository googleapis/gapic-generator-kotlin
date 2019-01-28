#########################################################################
# ktlint
#########################################################################

FROM openjdk:8-alpine as ktlint

# install ktlint
RUN apk add --no-cache curl gnupg
RUN curl -sSLO https://github.com/shyiko/ktlint/releases/download/0.29.0/ktlint && \
    curl -sSLO https://github.com/shyiko/ktlint/releases/download/0.29.0/ktlint.asc && \
    curl -sS https://keybase.io/shyiko/pgp_keys.asc | gpg --import && gpg --verify ktlint.asc && \
    chmod a+x ktlint && \
    rm ktlint.asc && \
    mv ktlint /usr/local/bin/

# mount point
RUN mkdir /src
VOLUME ["/src"]

CMD ["--color", "--android", "-F", "/src/**/*.kt"]
ENTRYPOINT ["/usr/local/bin/ktlint"]

#########################################################################
# google-java-format
#########################################################################

FROM openjdk:8-alpine as javaformatter

# install google-java-format
RUN apk add --no-cache curl
RUN mkdir -p /usr/google-java-format && \
    curl -sSL https://github.com/google/google-java-format/releases/download/google-java-format-1.7/google-java-format-1.7-all-deps.jar > /usr/google-java-format/formatter.jar

# mount point
RUN mkdir /src
VOLUME ["/src"]

ENTRYPOINT exec java -jar /usr/google-java-format/formatter.jar --replace $(find /src -type f -name "*.java")

#########################################################################
# generator
#########################################################################

FROM openjdk:8 as generator

# copy formatters
COPY --from=ktlint /usr/local/bin/ktlint /usr/local/bin/ktlint
COPY --from=javaformatter /usr/google-java-format/formatter.jar /usr/google-java-format/formatter.jar

RUN apt-get update && apt-get install -y \
    tree \
    && rm -rf /var/lib/apt/lists/*

# copy distribution
COPY build/distributions/generator-boot-*.tar /tmp/generator/
RUN mkdir -p /usr/generator && \
    tar xvf /tmp/generator/generator-boot-*.tar --strip-components=1 -C /usr/generator && \
    rm -rf /tmp/generator

# move into the gradle project used to run generator
COPY runner /usr/src/generator/runner
WORKDIR /usr/src/generator/runner

# disable gradle daemon
RUN mkdir -p /root/.gradle && \
    echo "org.gradle.daemon=false" > /root/.gradle/gradle.properties && \
    echo "org.gradle.jvmargs=-Xmx4096m -XX:MaxPermSize=4096m" >> /root/.gradle/gradle.properties 

# run a build to cache the build artifacts
RUN cp build.android.gradle build.gradle && \
    ./gradlew build clean && \
    cp build.server.gradle build.gradle && \
    ./gradlew build clean && \
    rm build.gradle

# create input directories
RUN  rm -rf src/main/proto && \
     mkdir /proto && \
     ln -s /proto src/main/proto

# create output directories
RUN mkdir -p /generated

# generator script
ENTRYPOINT ["./generate.sh"]
