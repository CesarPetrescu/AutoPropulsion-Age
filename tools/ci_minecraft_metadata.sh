#!/usr/bin/env bash
# CI-only recovery for a failed NeoForge metadata endpoint. Uses NeoForge's pinned
# generator and Mojang's official version manifest, never a third-party mod jar.
set -euo pipefail
url='https://maven.neoforged.net/mojang-meta/net/neoforged/minecraft-dependencies/1.21.1/minecraft-dependencies-1.21.1.module'
if curl --fail --location --retry 2 --retry-delay 2 --max-time 30 "$url" -o "${RUNNER_TEMP:-/tmp}/minecraft-dependencies-probe.module"; then
    echo 'Official Minecraft dependency metadata endpoint is healthy.'
    exit 0
fi
pin=c0ad4ad30230d7023d397888081d51ad7dd84d3c
work=$(mktemp -d "${RUNNER_TEMP:-/tmp}/minecraft-metadata.XXXXXX")
git -C "$work" init -q
git -C "$work" remote add origin https://github.com/NeoForged/GradleMinecraftDependencies.git
git -C "$work" fetch -q --depth 1 origin "$pin"
git -C "$work" checkout -q --detach FETCH_HEAD
test "$(git -C "$work" rev-parse HEAD)" = "$pin"
(
    cd "$work"
    chmod +x gradlew
    ./gradlew --no-daemon -PminecraftVersion=1.21.1 publishToMavenLocal
)
metadata="$HOME/.m2/repository/net/neoforged/minecraft-dependencies/1.21.1/minecraft-dependencies-1.21.1.module"
python3 - "$metadata" <<'PY'
import json,sys
m=json.load(open(sys.argv[1]));c=m['component']
assert c['group']=='net.neoforged' and c['module']=='minecraft-dependencies' and c['version']=='1.21.1',c
assert len(m['variants'])>0
PY
sha256sum "$metadata"
# An exclusive repository applies to this one module only. Other dependencies
# still use their normal official repositories. The init script lives only on CI.
mkdir -p "${GRADLE_USER_HOME:-$HOME/.gradle}/init.d"
cat > "${GRADLE_USER_HOME:-$HOME/.gradle}/init.d/eln-ci-metadata.gradle" <<'GRADLE'
allprojects {
    repositories {
        exclusiveContent {
            forRepository { mavenLocal() }
            filter { includeModule('net.neoforged', 'minecraft-dependencies') }
        }
    }
}
GRADLE
echo "Regenerated Minecraft 1.21.1 dependency metadata using NeoForge generator $pin and official Mojang manifests."
