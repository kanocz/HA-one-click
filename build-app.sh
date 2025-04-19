#!/bin/bash
set -e

# Configuration
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
DOCKER_IMAGE_NAME="one-action-click-builder"
OUTPUT_DIR="${APP_DIR}/docker-build-output"
BUILD_TYPE=${1:-debug}  # Default to debug if no argument provided
# Get current user UID and GID for file permissions
CURRENT_UID=$(id -u)
CURRENT_GID=$(id -g)

echo "=== One Action Click App Docker Build Script ==="
echo "App directory: ${APP_DIR}"
echo "Build type: ${BUILD_TYPE}"

# Validate build type
if [[ "${BUILD_TYPE}" != "debug" && "${BUILD_TYPE}" != "release" ]]; then
  echo "Error: Build type must be either 'debug' or 'release'"
  echo "Usage: $0 [debug|release]"
  exit 1
fi

# Create output directory if it doesn't exist
mkdir -p "${OUTPUT_DIR}"

# Create a temporary Dockerfile
DOCKERFILE_PATH="${APP_DIR}/Dockerfile.build"
cat > "${DOCKERFILE_PATH}" << 'EOF'
FROM gradle:7.4.2-jdk11

# Install required Android SDK components
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools
WORKDIR ${ANDROID_HOME}/cmdline-tools

# Download and install Android SDK
RUN apt-get update && apt-get install -y wget unzip \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-8512546_latest.zip -O cmdline-tools.zip \
    && unzip cmdline-tools.zip \
    && mv cmdline-tools latest \
    && rm cmdline-tools.zip \
    && apt-get clean

# Accept licenses and install required components
RUN mkdir -p /root/.android \
    && touch /root/.android/repositories.cfg \
    && yes | sdkmanager --licenses \
    && sdkmanager "platforms;android-33" "build-tools;33.0.0" "platform-tools"

WORKDIR /app
EOF

echo "=== Building Docker image ==="
docker build -t "${DOCKER_IMAGE_NAME}" -f "${DOCKERFILE_PATH}" .

echo "=== Building Android app (${BUILD_TYPE}) ==="

# For release builds, we'll create simple placeholder icons directly
if [[ "${BUILD_TYPE}" == "release" ]]; then
  echo "Creating placeholder launcher icons for release build..."
  
  # Clean up old icons if they exist
  find "${APP_DIR}/app/src/main/res" -name "ic_launcher*.png" -delete

  # Create directories for different densities
  mkdir -p "${APP_DIR}/app/src/main/res/mipmap-mdpi" \
           "${APP_DIR}/app/src/main/res/mipmap-hdpi" \
           "${APP_DIR}/app/src/main/res/mipmap-xhdpi" \
           "${APP_DIR}/app/src/main/res/mipmap-xxhdpi" \
           "${APP_DIR}/app/src/main/res/mipmap-xxxhdpi"
  
  # Create simple placeholder 1x1 pixel PNG files for all icon sizes
  # This is a minimal valid PNG file (1x1 pixel, transparent)
  for type in "ic_launcher" "ic_launcher_round"; do
    echo -e "\x89\x50\x4E\x47\x0D\x0A\x1A\x0A\x00\x00\x00\x0D\x49\x48\x44\x52\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1F\x15\xC4\x89\x00\x00\x00\x0A\x49\x44\x41\x54\x78\x9C\x63\x00\x01\x00\x00\x05\x00\x01\x0D\x0A\x2D\xB4\x00\x00\x00\x00\x49\x45\x4E\x44\xAE\x42\x60\x82" > "${APP_DIR}/app/src/main/res/mipmap-mdpi/${type}.png"
    echo -e "\x89\x50\x4E\x47\x0D\x0A\x1A\x0A\x00\x00\x00\x0D\x49\x48\x44\x52\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1F\x15\xC4\x89\x00\x00\x00\x0A\x49\x44\x41\x54\x78\x9C\x63\x00\x01\x00\x00\x05\x00\x01\x0D\x0A\x2D\xB4\x00\x00\x00\x00\x49\x45\x4E\x44\xAE\x42\x60\x82" > "${APP_DIR}/app/src/main/res/mipmap-hdpi/${type}.png"
    echo -e "\x89\x50\x4E\x47\x0D\x0A\x1A\x0A\x00\x00\x00\x0D\x49\x48\x44\x52\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1F\x15\xC4\x89\x00\x00\x00\x0A\x49\x44\x41\x54\x78\x9C\x63\x00\x01\x00\x00\x05\x00\x01\x0D\x0A\x2D\xB4\x00\x00\x00\x00\x49\x45\x4E\x44\xAE\x42\x60\x82" > "${APP_DIR}/app/src/main/res/mipmap-xhdpi/${type}.png"
    echo -e "\x89\x50\x4E\x47\x0D\x0A\x1A\x0A\x00\x00\x00\x0D\x49\x48\x44\x52\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1F\x15\xC4\x89\x00\x00\x00\x0A\x49\x44\x41\x54\x78\x9C\x63\x00\x01\x00\x00\x05\x00\x01\x0D\x0A\x2D\xB4\x00\x00\x00\x00\x49\x45\x4E\x44\xAE\x42\x60\x82" > "${APP_DIR}/app/src/main/res/mipmap-xxhdpi/${type}.png"
    echo -e "\x89\x50\x4E\x47\x0D\x0A\x1A\x0A\x00\x00\x00\x0D\x49\x48\x44\x52\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1F\x15\xC4\x89\x00\x00\x00\x0A\x49\x44\x41\x54\x78\x9C\x63\x00\x01\x00\x00\x05\x00\x01\x0D\x0A\x2D\xB4\x00\x00\x00\x00\x49\x45\x4E\x44\xAE\x42\x60\x82" > "${APP_DIR}/app/src/main/res/mipmap-xxxhdpi/${type}.png"
  done
fi

# If this is a release build, use the keystore from the project root
if [[ "${BUILD_TYPE}" == "release" ]]; then
  # Define keystore variables
  KEYSTORE_PASSWORD=${KEYSTORE_PASSWORD:-"androidSuperPass"}
  KEY_PASSWORD=${KEY_PASSWORD:-"changeit"}
  KEY_ALIAS=${KEY_ALIAS:-"release"}
  
  # Check if keystore exists, if not, generate one for CI
  if [ ! -f "${APP_DIR}/keystore.jks" ]; then
    echo "Keystore not found, generating a new one for CI build..."
    if [ -f "${APP_DIR}/generate-keystore.sh" ]; then
      # Execute the keystore generation script if it exists
      chmod +x "${APP_DIR}/generate-keystore.sh"
      "${APP_DIR}/generate-keystore.sh"
    else
      # Generate a basic keystore directly
      echo "Generating basic keystore..."
      keytool -genkeypair -v \
        -keystore "${APP_DIR}/keystore.jks" \
        -alias "${KEY_ALIAS}" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass "${KEYSTORE_PASSWORD}" \
        -keypass "${KEY_PASSWORD}" \
        -dname "CN=HA One Action Click,OU=Development,O=Organization,L=City,S=State,C=CZ"
    fi
  fi
  
  echo "Using keystore from project root: ${APP_DIR}/keystore.jks"
  
  # Run Docker with existing keystore
  docker run --rm \
    -v "${APP_DIR}:/app" \
    -v "${OUTPUT_DIR}:/output" \
    -e USER_UID=${CURRENT_UID} \
    -e USER_GID=${CURRENT_GID} \
    -e KEYSTORE_FILE="/app/keystore.jks" \
    -e KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD}" \
    -e KEY_PASSWORD="${KEY_PASSWORD}" \
    -e KEY_ALIAS="${KEY_ALIAS}" \
    "${DOCKER_IMAGE_NAME}" \
    bash -c '
      # Build the release APK
      cd /app && ./gradlew assembleRelease
      
      # Copy output to the mounted volume
      cp -r app/build/outputs/apk /output/
      
      # Fix permissions for output files
      chown -R ${USER_UID}:${USER_GID} /output
    '
else
  # For debug builds, use the original command
  docker run --rm \
    -v "${APP_DIR}:/app" \
    -v "${OUTPUT_DIR}:/output" \
    -e USER_UID=${CURRENT_UID} \
    -e USER_GID=${CURRENT_GID} \
    "${DOCKER_IMAGE_NAME}" \
    bash -c "cd /app && ./gradlew assemble${BUILD_TYPE^} && cp -r app/build/outputs/apk /output/ && chown -R \${USER_UID}:\${USER_GID} /output"
fi

# Clean up
rm "${DOCKERFILE_PATH}"

# Check if build was successful
if [ -d "${OUTPUT_DIR}/apk/${BUILD_TYPE,,}" ]; then
  echo "=== Build Successful ==="
  echo "APK files are available in: ${OUTPUT_DIR}/apk/${BUILD_TYPE,,}"
  find "${OUTPUT_DIR}/apk/${BUILD_TYPE,,}" -name "*.apk" | while read -r apk; do
    echo "- $(basename "$apk")"
  done
else
  echo "=== Build Failed ==="
  echo "No APK files were generated"
  exit 1
fi