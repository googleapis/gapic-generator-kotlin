FROM openjdk:8

# copy generator
COPY . /usr/src/generator
WORKDIR /usr/src/generator

# build generator
RUN ./gradlew build

# copy distribution
WORKDIR /usr/generator
RUN tar xvf /usr/src/generator/build/distributions/kotlin-client-generator-boot-*.tar --strip-components=1 -C /usr/generator

# generator entrypoint
ENTRYPOINT [ "./bin/kotlin-client-generator", "--help" ]
