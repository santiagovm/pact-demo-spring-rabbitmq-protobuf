#!/usr/bin/env bash

set -o errexit
set -o errtrace
set -o pipefail

mkdir -p src/test/resources/pacts
cp -r ../consumer/target/pacts src/test/resources
