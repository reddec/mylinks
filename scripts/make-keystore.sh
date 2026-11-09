#!/usr/bin/env bash
# Creates the release keystore once and prints the four GitHub Actions secrets.
# Run it from anywhere; it writes release.keystore + keystore.properties into the repo root.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

KEYSTORE="release.keystore"
PROPERTIES="keystore.properties"
ALIAS="${KEY_ALIAS:-mylinks}"

if [ -e "$KEYSTORE" ] || [ -e "$PROPERTIES" ]; then
  echo "$KEYSTORE or $PROPERTIES already exists." >&2
  echo "Refusing to overwrite: replacing the key breaks in-place upgrades for every install." >&2
  echo "Delete them yourself if you really mean to start a new signing identity." >&2
  exit 1
fi

# A keychain-friendly password: no shell or properties-file surprises.
PASSWORD="$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 32)"

KEYTOOL="keytool"
if [ -x "./.build-env/jdk/bin/keytool" ]; then
  KEYTOOL="./.build-env/jdk/bin/keytool"
fi

"$KEYTOOL" -genkeypair \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass "$PASSWORD" \
  -keypass "$PASSWORD" \
  -dname "CN=MyLinks, OU=self-hosted, O=reddec.net, C=DE"

cat > "$PROPERTIES" <<EOF
storeFile=$KEYSTORE
storePassword=$PASSWORD
keyAlias=$ALIAS
keyPassword=$PASSWORD
EOF

chmod 600 "$KEYSTORE" "$PROPERTIES"

cat <<EOF

Keystore written: $KEYSTORE ($(openssl x509 -in <(keytool -exportcert -keystore "$KEYSTORE" -alias "$ALIAS" -storepass "$PASSWORD" 2>/dev/null) -noout -fingerprint -sha256 2>/dev/null | sed 's/.*=//') )

Back this file up. Android only replaces an installed app when the signature matches, so losing
it means every user has to uninstall and reinstall.

Add these four secrets at
  https://github.com/reddec/mylinks/settings/secrets/actions

  KEYSTORE_BASE64
$(base64 -w0 "$KEYSTORE" | fold -w 76 | sed 's/^/    /')

  KEYSTORE_PASSWORD
    $PASSWORD

  KEY_ALIAS
    $ALIAS

  KEY_PASSWORD
    $PASSWORD

Then release with:  git tag -a v1.1.0 -m "MyLinks 1.1.0" && git push origin v1.1.0
EOF
