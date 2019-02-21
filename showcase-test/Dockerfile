FROM golang:1.11

# install showcase
RUN mkdir -p /showcase
RUN curl -sSL https://github.com/googleapis/gapic-showcase/releases/download/v0.0.12/gapic-showcase-0.0.12-linux-amd64.tar.gz | tar -zx -C /usr/local/bin/
RUN chmod +x /usr/local/bin/gapic-showcase
RUN curl -sSL https://github.com/googleapis/gapic-showcase/releases/download/v0.0.12/gapic-showcase-0.0.12-protos.tar.gz | tar xvz --strip-components=1 -C /showcase

EXPOSE 7469
CMD ["run"]
ENTRYPOINT ["/usr/local/bin/gapic-showcase"]
