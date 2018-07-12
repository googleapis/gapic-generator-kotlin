#!/bin/bash

#
# Fake protoc plugin to generate the Code generator request used by the tests.
#
# Run create-test-data.sh if you update the protos and need to recreate generate.data
#

# save the code generator request
cat > ./generate.data
