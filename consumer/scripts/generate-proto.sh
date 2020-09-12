#!/usr/bin/env bash

set -o errexit
set -o errtrace
set -o pipefail

function download_protoc() {
  export PROTOC_VERSION="3.9.1"
  export PROTOC_TAG="v${PROTOC_VERSION}"

  if [ ! -f target/protoc/bin/protoc ]; then
    rm -rf target/protoc
    mkdir -p target/protoc
    echo "fetching protoc..."
    wget https://github.com/protocolbuffers/protobuf/releases/download/"${PROTOC_TAG}"/protoc-"${PROTOC_VERSION}"-osx-x86_64.zip -O target/protoc.zip
    unzip target/protoc.zip -d target/protoc
    echo "protoc fetched!"
  else
    echo "protoc already downloaded"
  fi
}

function generate_proto() {
  rm -rf target/generated-sources/java
  mkdir -p target/generated-sources/java
  target/protoc/bin/protoc --java_out=target/generated-sources/java src/main/resources/proto/beer.proto
  echo "proto files generated"
}

function main() {
  download_protoc
  generate_proto
}

main
