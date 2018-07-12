#!/bin/bash
protoc --plugin=protoc-gen-testdata=plugin.sh --testdata_out=. --proto_path=proto proto/google/example/test.proto
