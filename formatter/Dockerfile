FROM ubuntu:xenial

# downloads
ENV IDE_PACKAGE ideaIC-2018.1.6.tar.gz
ENV IDE_DOWNLOAD_URL http://download-cf.jetbrains.com/idea/${IDE_PACKAGE}
ENV IDE_SHA ca7c746a26bc58c6c87c34e33fbba6f767f2df9dca34eb688e3c07a126cdc393 *ideaIC-2018.1.6.tar.gz

# install dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl openjdk-8-jdk && \
    apt-get clean all && \
    rm -rf /var/lib/apt/lists/*

# create use
RUN mkdir -p /home/ide && \
    useradd ide -d /home/ide && \
    chown -R ide:ide /home/ide

# install Intellij CD
RUN curl ${IDE_DOWNLOAD_URL} > /home/ide/${IDE_PACKAGE} && \
    echo ${IDE_SHA} > /home/ide/${IDE_PACKAGE}.sha256 && \
    cd /home/ide && \
    sha256sum -c ${IDE_PACKAGE}.sha256 && \
    mkdir intellij && \
    tar xvzf ${IDE_PACKAGE} --strip-components=1 -C intellij && \
    rm -f ${IDE_PACKAGE} && \
    rm ${IDE_PACKAGE}.sha256

# add formatting rules
ADD format.xml /home/ide/format.xml

# mount point
RUN mkdir /src
VOLUME [ "/src" ]

USER ide

# run formatter
ENTRYPOINT ["/home/ide/intellij/bin/format.sh", "-s", "/home/ide/format.xml", "-r", "/src"]
