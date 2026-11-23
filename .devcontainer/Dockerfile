# Jio Landline Android DevContainer
# Based on Ubuntu 22.04 with JDK 17 and Android SDK
FROM mcr.microsoft.com/devcontainers/base:ubuntu-22.04

# Avoid interactive prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jdk-headless \
    wget \
    unzip \
    git \
    curl \
    lib32stdc++6 \
    lib32z1 \
    libc6-i386 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Set JAVA_HOME
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

# Android SDK setup
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=$ANDROID_HOME
ENV PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH

# Install Android SDK Command Line Tools
RUN mkdir -p $ANDROID_HOME/cmdline-tools && \
    cd $ANDROID_HOME/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip && \
    unzip -q cmdline-tools.zip && \
    mv cmdline-tools latest && \
    rm cmdline-tools.zip

# Accept licenses and install required SDK components
# compileSdk 34, targetSdk 34 - need platform-34 and build-tools
# AGP 8.2.2 works with build-tools 33.0.0 or 34.0.0
RUN yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses && \
    $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
        "platforms;android-34" \
        "build-tools;34.0.0" \
        "build-tools;33.0.0" \
        "platform-tools" \
        "emulator" \
        "system-images;android-34;google_apis;x86_64" \
        "sources;android-34" \
        "ndk;26.3.11579264"

# Verify Android SDK installation
RUN $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --list

# Create gradle user home directory for caching
ENV GRADLE_USER_HOME=/home/vscode/.gradle
RUN mkdir -p $GRADLE_USER_HOME && chown -R vscode:vscode $GRADLE_USER_HOME

# Set workspace
WORKDIR /workspace

# Default command
CMD ["bash"]