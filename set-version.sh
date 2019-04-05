#!/bin/bash

VERSION="$1"

if [ -z "$VERSION" ]; then
  echo "Enter version number: "
  read VERSION
fi

if [ -z "$VERSION" ]; then
  echo "No version number. Aborting!"
  exit 1
fi

# Update api version
BUILD_FILES=(
  './gax-kotlin/build.gradle.kts'
  './generator/build.gradle.kts'
)
for FILE in "${BUILD_FILES[@]}"; do
  sed -i '' -E 's/version = ".+"/version = "'"$VERSION"'"/' $FILE
done

# Update gax version in all server build scripts
BUILD_FILES=(
  './generator/build.gradle.kts'
  './example-client/build.gradle.kts'
  './example-api-cloud-clients/build.gradle.kts'
  './example-api-cloud-clients-android/app/build.gradle'
  './showcase-test/build.gradle.kts'
)

for FILE in "${BUILD_FILES[@]}"; do
  sed -i '' -E 's/"(com.google.api:kgax-grpc:).+"/"\1'"$VERSION"'"/' $FILE
  sed -i '' -E 's/"(com.google.api:kgax-grpc-android:).+"/"\1'"$VERSION"'"/' $FILE
done
