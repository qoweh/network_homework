#!/bin/bash

# Kali Linux Setup Script for Network Homework Project
# Installs Java 21, Maven 3.9.9, and libpcap dependencies

set -e  # Exit immediately if a command exits with a non-zero status

echo "======================================"
echo "Kali Linux Environment Setup"
echo "======================================"

# Check for sudo
if [ "$EUID" -ne 0 ]; then 
  echo "Please run as root or use sudo"
  echo "Usage: sudo ./setup_toolchains.sh"
  exit 1
fi

# Update package list
echo "[*] Updating package list..."
apt-get update

# Install basic utilities
echo "[*] Installing basic utilities (wget, curl, tar)..."
apt-get install -y wget curl tar

# Install Java 21
echo "[*] Installing OpenJDK 21..."
apt-get install -y openjdk-21-jdk

# Install libpcap (Required for jNetPcap)
echo "[*] Installing libpcap-dev..."
apt-get install -y libpcap-dev

# Install X11 utilities
echo "[*] Installing X11 utilities..."
apt-get install -y xauth x11-apps

# Install Maven 3.9.9 Manually
MAVEN_VERSION="3.9.9"
MAVEN_DIR="/opt/apache-maven-${MAVEN_VERSION}"

if [ -d "$MAVEN_DIR" ]; then
    echo "[*] Maven ${MAVEN_VERSION} is already installed at ${MAVEN_DIR}"
else
    echo "[*] Installing Maven ${MAVEN_VERSION}..."
    cd /tmp
    wget https://archive.apache.org/dist/maven/maven-3/$\{MAVEN_VERSION\}/binaries/apache-maven-$\{MAVEN_VERSION\}-bin.tar.gz
    tar -xzf apache-maven-${MAVEN_VERSION}-bin.tar.gz
    mv apache-maven-${MAVEN_VERSION} /opt/
    rm apache-maven-${MAVEN_VERSION}-bin.tar.gz
    
    # Create symlink if it doesn't exist or force update
    ln -sf ${MAVEN_DIR}/bin/mvn /usr/bin/mvn
    echo "[*] Maven installed to ${MAVEN_DIR}"
fi

# Verify installations
echo ""
echo "======================================"
echo "Verification"
echo "======================================"
echo "Java Version:"
java -version
echo ""
echo "Maven Version:"
/opt/apache-maven-3.9.9/bin/mvn -version
echo ""
echo "Libpcap Status:"
dpkg -l | grep libpcap

echo ""
echo "======================================"
echo "Setup Complete!"
echo "You can now run: ./run.sh"
echo "======================================"
