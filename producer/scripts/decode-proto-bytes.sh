#!/usr/bin/env bash

set -o errexit
set -o errtrace
set -o pipefail

envelope_proto_bytes_base64_string=$1

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
  fi
}

function decode_envelope_proto_bytes_base64_string() {
  proto_bytes=$(printf "%s" "$envelope_proto_bytes_base64_string" | base64 -d)
  printf "%s" "$proto_bytes" | target/protoc/bin/protoc --decode=com.vasquez.beer.SomeCustomEnvelope src/main/resources/proto/beer.proto
}

function parse_event_data_proto_bytes() {
  event_data_line=$(printf "%s" "$envelope_decoded" | grep "event_data")
  readarray -d \" -t split<<< "$event_data_line"
  event_data_proto_bytes=${split[1]}
  printf "%b" "$event_data_proto_bytes"
}

function decode_response_proto_bytes() {
  parse_event_data_proto_bytes | target/protoc/bin/protoc --decode=com.vasquez.beer.Response src/main/resources/proto/beer.proto
}

download_protoc

envelope_decoded=$(decode_envelope_proto_bytes_base64_string)
printf "===============[envelope decoded]===============\n\n%s\n\n" "$envelope_decoded"

response_decoded=$(decode_response_proto_bytes)
printf "===============[response decoded]===============\n\n%s\n" "$response_decoded"
