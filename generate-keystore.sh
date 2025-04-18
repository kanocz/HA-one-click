#!/bin/bash
set -e

# Configuration
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
KEYSTORE_FILE="${APP_DIR}/keystore.jks"
KEY_ALIAS="release"
STORE_PASSWORD="androidSuperPass"
KEY_PASSWORD="changeit"

echo "=== Generating new keystore for signing APKs ==="
echo "Keystore file: ${KEYSTORE_FILE}"
echo "Key alias: ${KEY_ALIAS}"

# Remove old keystore if it exists
if [ -f "${KEYSTORE_FILE}" ]; then
  echo "Removing old keystore file..."
  rm "${KEYSTORE_FILE}"
fi

# Generate a new keystore
echo "Generating new keystore..."
keytool -genkeypair \
  -keystore "${KEYSTORE_FILE}" \
  -alias "${KEY_ALIAS}" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass "${STORE_PASSWORD}" \
  -keypass "${KEY_PASSWORD}" \
  -dname "CN=One Action Click App, OU=Development, O=Anton Skorochod, L=Jablonec nad Nisou, S=Liberecky kraj, C=CZ" \
  -noprompt

# Verify the keystore was created
if [ -f "${KEYSTORE_FILE}" ]; then
  echo "=== Keystore created successfully ==="
  echo "You can now build release APKs with: ./build-app.sh release"
  
  # Show keystore information
  echo "=== Keystore Information ==="
  keytool -list -v -keystore "${KEYSTORE_FILE}" -storepass "${STORE_PASSWORD}" | grep -A 2 "Alias name:"
else
  echo "=== Failed to create keystore ==="
  exit 1
fi