#!/bin/bash

#
# Builds the Dockerfile with the generator and gax dependencies
# from the local maven repo.
#

# use custom tag, if one is provided
if [[ -z "${TAG}" ]]; then
  TAG="kgen"
fi

# clear local maven repo
rm -rf ~/.m2/repository/com/google/api/gapic-generator-kotlin
rm -rf ~/.m2/repository/com/google/api/kgax-*

# build everything and install locally
for DIR in "gax-kotlin" "generator"
do
  pushd $DIR
  ./gradlew clean build publishToMavenLocal -x check
  popd
done

# copy artifacts to staging area
rm -rf ./build
mkdir ./build
cp -R ~/.m2/repository/com/google/api/kgax* ./build/
cp -R ~/.m2/repository/com/google/api/gapic-generator-kotlin ./build/

# build docker wrapper
docker build -t ${TAG} .
