#!/bin/bash

set -e

# usage
show_help() {
    echo "Usage: $0 [options]" >&2
    echo
    echo "  Options:"
    echo "    --android             Generate code for the Android platform"
    echo "    --lite                Generate code for the Android platform (same as --android)"
    echo "    --auth-google-cloud   Generate additional code for Google Cloud Platform APIs"
    echo "    --no-compile          Skip compiling the generated code"
    echo "    --no-format           Skip formatting the generated code"
    echo "    --overwrite           Overwrite everything in the output directory"
    echo
    echo "  Example:"
    echo "    $ mkdir my-output "
    echo "    $ docker run --rm -it \\"
    echo "         --mount type=bind,source=\"$(pwd)\"/example-server/src/main/proto,target=/proto \\"
    echo "         --mount type=bind,source=\"$(pwd)\"/my-output,target=/generated \\"
    echo "       gcr.io/kotlin-gapic/kgen --android"
    echo
}

# parse command line options
PARAMS=""
while (( "$#" )); do
  case "$1" in
    # -f|--flag-with-argument)
    #   FARG=$2
    #   shift 2
    #   ;;
    -h|--help)
      show_help
      exit 0
      ;;
    --android|--lite)
      IS_ANDROID=1
      shift
      ;;
    --auth-google-cloud)
      IS_AUTH_GCLOUD=1
      shift
      ;;
    --no-compile)
      SKIP_COMPILE=1
      shift
      ;;
    --no-format)
      SKIP_FORMAT=1
      shift
      ;;
    --overwrite)
      DO_OVERWRITE=1
      shift
      ;;
    --) # end argument parsing
      shift
      break
      ;;
    -*|--*=) # unsupported flags
      echo "Error: Unsupported flag $1" >&2
      exit 1
      ;;
    *) # preserve positional arguments
      PARAMS="$PARAMS $1"
      shift
      ;;
  esac
done
eval set -- "$PARAMS"

# check input directory
if [ -z "$(ls -A /proto)" ]; then
   echo "No input .proto files were given. Aborting!"
   echo
   show_help
   exit 1
fi

# check output directory
if [ ! -z "$(ls -A /generated)" ]; then
  if [ -z ${DO_OVERWRITE+x} ]; then
    echo "Output directory is not empty and the --overwrite flag is missing. Aborting."
    exit 1
  else 
    echo "Cleaning output directory..."
    rm -rf /generated/*
  fi
fi

# create build config
if [ -z ${IS_ANDROID+x} ]; then
  echo "Using standard configuration..."
  cp build.server.gradle build.gradle
else
  echo "Using Android configuration..."
  cp build.android.gradle build.gradle
fi

# configure options
if [ ! -z ${IS_AUTH_GCLOUD+x} ]; then
  sed -i '/\/\/ EXTRA-PLUGIN-OPTIONS/a\
                    option "auth-google-cloud"
  ' build.gradle
fi

# generate
echo
echo "Generating client code..."
if [ -z ${SKIP_COMPILE+x} ]; then
  ./gradlew build
else
  echo "[Skipping compilation step - generate code only]"
  ./gradlew generateProto
fi
echo
cat /tmp/kotlin_generator.log

# copy generated code
cp -R build/generated/source/proto/main/* /generated
cp -R build/generated/source/protoTest/* /generated

# format
if [ -z ${SKIP_FORMAT+x} ]; then
  echo
  echo "Formatting Java code..."
  java -jar /usr/google-java-format/formatter.jar --replace $(find /generated -type f -name "*.java")

  echo
  echo "Formatting Kotlin code..."
  if [ -z ${IS_ANDROID+x} ]; then
    /usr/local/bin/ktlint --format /generated/**/*.kt || true
  else
    /usr/local/bin/ktlint --format --android /generated/**/*.kt || true
  fi
fi

echo
echo "Generated artifacts:"
tree -C /generated
echo
echo "Successfully generated your client library."
echo
