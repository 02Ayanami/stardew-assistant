#!/bin/sh
set -eu
cd "$(dirname "$0")/../backend"
./mvnw -q test-compile dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt -Dmdep.includeScope=test
exec java -cp "target/test-classes:target/classes:$(cat target/test-classpath.txt)" dev.stardew.assistant.integration.E2eServer
