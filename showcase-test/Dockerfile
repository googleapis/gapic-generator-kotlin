FROM golang:1.11

# install showcase
RUN mkdir -p /showcase
RUN curl -sSL https://github.com/googleapis/gapic-showcase/releases/download/v0.0.5/gapic-showcase-v1alpha2-0.0.5-linux-amd64 > /usr/local/bin/showcase && \
    chmod +x /usr/local/bin/showcase
RUN curl -sSL https://github.com/googleapis/gapic-showcase/releases/download/v0.0.5/gapic-showcase-v1alpha2-0.0.5-protos.tar.gz | tar xvz --strip-components=1 -C /showcase

EXPOSE 7469
CMD ["start"]
ENTRYPOINT ["/usr/local/bin/showcase"]
