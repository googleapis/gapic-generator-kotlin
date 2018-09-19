#!/bin/bash

set -e

# parse command line options
PARAMS=""
while (( "$#" )); do
  case "$1" in
    # -f|--flag-with-argument)
    #   FARG=$2
    #   shift 2
    #   ;;
    --no-format)
      SKIP_FORMAT=1
      shift
      ;;
    --no-lint)
      SKIP_LINT=1
      shift
      ;;
    --no-compile)
      SKIP_COMPILE=1
      shift
      ;;
    --format-custom)
      FORMAT_CUSTOM_ARGS=$2
      shift 2
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
  java -jar /usr/google-java-format/formatter.jar --dry-run $(find /generated -type f -name "*.java")
  java -jar /usr/google-java-format/formatter.jar --replace $(find /generated -type f -name "*.java")

  echo
  echo "Formatting Kotlin code..."
  /usr/ide/intellij/bin/format.sh -s /usr/ide/format.xml -r /generated -m *.kt
fi

# custom format
if [ -z ${FORMAT_CUSTOM_ARGS+x} ]; then
  :
else
  echo
  echo "Formatting with custom format.xml rule set..."
  /usr/ide/intellij/bin/format.sh -s /usr/ide/format.xml -r /generated -m $FORMAT_CUSTOM_ARGS
fi

# lint
if [ -z ${SKIP_LINT+x} ]; then
  echo
  echo "Linting Kotlin code..."
  echo "  Why? Imports are generated conservatively, and include default imports, to avoid potential name collisions."
  echo "  You may choose to remove them if it is safe to do so."
  /usr/local/bin/ktlint --color /generated/**/*.kt || true
fi

echo
echo "Generated artifacts:"
tree -C /generated
echo
echo "Successfully generated your client library."
echo
