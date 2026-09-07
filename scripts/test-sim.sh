#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .local-test/classes .local-test/reports
find sim/src/main/java sim/src/test/java tools/src/main/java -name '*.java' -print0 | xargs -0 javac --release 21 -d .local-test/classes
java -cp .local-test/classes com.photonspark.autopropulsion.sim.SimulationTests .local-test/reports/regression.json
java -cp .local-test/classes com.photonspark.autopropulsion.tools.Dyno --engine ref_i4_2.0 > .local-test/reports/reference-dyno.csv
