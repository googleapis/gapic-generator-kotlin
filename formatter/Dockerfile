FROM openjdk:8-alpine

# downloads
ENV IDE_PACKAGE ideaIC-2018.1.6.tar.gz
ENV IDE_DOWNLOAD_URL http://download-cf.jetbrains.com/idea/${IDE_PACKAGE}
ENV IDE_SHA ca7c746a26bc58c6c87c34e33fbba6f767f2df9dca34eb688e3c07a126cdc393  ${IDE_PACKAGE}

# install dependecies
RUN apk --no-cache add curl

WORKDIR /home/ide

# install Intellij CD
RUN curl ${IDE_DOWNLOAD_URL} > ${IDE_PACKAGE} && \
    echo "${IDE_SHA}" | sha256sum -c - && \
    mkdir intellij && \
    tar xvzf ${IDE_PACKAGE} --strip-components=1 -C intellij && \
    rm -f ${IDE_PACKAGE}

# add formatting rules
COPY format.xml ./format.xml

# mount point
RUN mkdir /src
VOLUME [ "/src" ]

# run formatter
ENTRYPOINT ["/home/ide/intellij/bin/format.sh", "-s", "/home/ide/format.xml", "-r", "/src"]
