#!/bin/bash

# Kali Linux Setup Script for Network Homework Project
# Installs Java 21, Maven, and libpcap dependencies

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

# Install Java 21
echo "[*] Installing OpenJDK 21..."
apt-get install -y openjdk-21-jdk

# Install Maven
echo "[*] Installing Maven..."
apt-get install -y maven

# Install libpcap (Required for jNetPcap)
echo "[*] Installing libpcap-dev..."
apt-get install -y libpcap-dev

# Install X11 utilities (xauth is often needed for X11 forwarding if not present)
echo "[*] Installing X11 utilities..."
apt-get install -y xauth x11-apps

# Verify installations
echo ""
echo "======================================"
echo "Verification"
echo "======================================"
echo "Java Version:"
java -version
echo ""
echo "Maven Version:"
mvn -version
echo ""
echo "Libpcap Status:"
dpkg -l | grep libpcap

echo ""
echo "======================================"
echo "Setup Complete!"
echo "You can now run: ./run.sh"
echo "======================================"
