#!/bin/sh
# usage: update-site.sh <site-root> <version> <repo-zip>
# Adds one p2 repository version to the update site and regenerates
# the composite repository and the site pages. Run from the repo root
# (needs README.md) or set README_FILE to another path.
set -eu

SITE=$1
VER=$2
ZIP=$3
BASE_URL=https://pravets.github.io/edt-editing
README_FILE=${README_FILE:-README.md}

mkdir -p "$SITE/update/$VER"
if command -v unzip >/dev/null 2>&1; then
  unzip -oq "$ZIP" -d "$SITE/update/$VER"
else
  # bsdtar on Windows can read zip too
  tar -xf "$ZIP" -C "$SITE/update/$VER"
fi

mkdir -p "$SITE/$VER"
cp "$ZIP" "$SITE/$VER/repo.zip"

VERSIONS=$(ls "$SITE/update" | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | sort -rV)
COUNT=$(printf '%s\n' $VERSIONS | wc -l)
timestamp=$(date +%s)

cat > "$SITE/update/compositeContent.xml" <<EOF
<?xml version='1.0' encoding='UTF-8'?>
<?compositeMetadataRepository version='1.0.0'?>
<repository name='1C:EDT Disable Editing Plugins'
    type='org.eclipse.equinox.internal.p2.metadata.repository.CompositeMetadataRepository' version='1.0.0'>
  <properties size='1'>
    <property name='p2.timestamp' value='${timestamp}'/>
  </properties>
  <children size='${COUNT}'>
$(for v in $VERSIONS; do echo "    <child location='${v}'/>"; done)
  </children>
</repository>
EOF
cat > "$SITE/update/compositeArtifacts.xml" <<EOF
<?xml version='1.0' encoding='UTF-8'?>
<?compositeArtifactRepository version='1.0.0'?>
<repository name='1C:EDT Disable Editing Plugins'
    type='org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository' version='1.0.0'>
  <properties size='1'>
    <property name='p2.timestamp' value='${timestamp}'/>
  </properties>
  <children size='${COUNT}'>
$(for v in $VERSIONS; do echo "    <child location='${v}'/>"; done)
  </children>
</repository>
EOF

{
  echo 'version=1'
  echo 'metadata.repository.factory.order=compositeContent.xml,\!'
  echo 'artifact.repository.factory.order=compositeArtifacts.xml,\!'
} > "$SITE/update/p2.index"

{
  printf -- '---\n---\n\n'
  echo "For manual install Select menu: \`Help -> Install New Software...\` past in p2-repository URL [$BASE_URL/update]($BASE_URL/update)"
  echo ''
  echo 'Select installable feature `Disable Editing plugin for 1C:EDT`'
  echo ''
  echo '## Download P2 archives'
  echo ''
  echo 'Download zip archive of P2-repository, then select menu: `Help -> Install New Software...` click on `Archive` button and select ziped file of repository'
  echo ''
  echo 'Available versions:'
  echo ''
  for v in $VERSIONS; do
    echo "- [$v P2-ZIP](../$v/repo.zip)"
  done
} > "$SITE/update/index.md"

{
  printf -- '---\n---\n\n'
  cat "$README_FILE"
} > "$SITE/index.md"
