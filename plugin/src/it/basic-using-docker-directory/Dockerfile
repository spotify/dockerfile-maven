FROM scratch
MAINTAINER David Flemström <dflemstr@spotify.com>

ENV BASE /opt/base

COPY target/docker/lib $BASE/lib

ADD src/main/docker/foo.txt $BASE/