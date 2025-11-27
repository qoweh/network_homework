#!/bin/bash

# Lab 4 환경 설정 스크립트
# macOS & Ubuntu 자동 감지

echo "======================================"
echo "Lab 4 환경 설정"
echo "======================================"

# OS 감지
if [[ "$OSTYPE" == "darwin"* ]]; then
    OS="macos"
    echo "✓ OS: macOS"
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    OS="ubuntu"
    echo "✓ OS: Ubuntu"
else
    echo "❌ 지원하지 않는 OS입니다."
    exit 1
fi

# ====================================
# macOS 설정
# ====================================
if [ "$OS" = "macos" ]; then
    echo ""
    echo "macOS 환경은 이미 설정되어 있습니다."
    echo "바로 ./run.sh를 실행하세요."
    exit 0
fi

# ====================================
# Ubuntu 설정
# ====================================
echo ""
echo "======================================"
echo "Ubuntu 환경 설정 시작"
echo "======================================"

# 1. 시스템 정보 확인
echo ""
echo "[1/5] 시스템 정보 확인"
ARCH=$(uname -m)
echo "   Architecture: $ARCH"
lsb_release -a 2>/dev/null | grep Description || cat /etc/os-release | grep PRETTY_NAME

# 2. Java 설치 확인
echo ""
echo "[2/5] Java 21 확인"
if ! command -v java &> /dev/null; then
    echo "   Java가 설치되어 있지 않습니다. 설치 중..."
    sudo apt update
    sudo apt install -y openjdk-21-jdk
else
    JAVA_VER=$(java -version 2>&1 | head -n 1)
    echo "   ✓ $JAVA_VER"
fi

# JAVA_HOME 설정
if [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
else
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
fi
echo "   ✓ JAVA_HOME: $JAVA_HOME"

# 3. Maven 확인 및 업그레이드
echo ""
echo "[3/5] Maven 확인"
if ! command -v mvn &> /dev/null; then
    echo "   Maven이 설치되어 있지 않습니다. 설치 중..."
    sudo apt install -y maven
fi

MAVEN_VER=$(mvn -version | head -n 1 | grep -oP '\d+\.\d+\.\d+')
echo "   현재 Maven: $MAVEN_VER"

# Maven 버전이 3.9 미만이면 업그레이드
MAVEN_MAJOR=$(echo $MAVEN_VER | cut -d. -f1)
MAVEN_MINOR=$(echo $MAVEN_VER | cut -d. -f2)

if [ "$MAVEN_MAJOR" -lt 3 ] || ([ "$MAVEN_MAJOR" -eq 3 ] && [ "$MAVEN_MINOR" -lt 9 ]); then
    echo "   Maven 3.9.9로 업그레이드 중..."
    
    cd /tmp
    wget -q https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz
    
    if [ $? -ne 0 ]; then
        wget -q https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz
    fi
    
    tar -xzf apache-maven-3.9.9-bin.tar.gz
    sudo mv apache-maven-3.9.9 /opt/
    
    # 환경변수 설정
    echo 'export M2_HOME=/opt/apache-maven-3.9.9' | sudo tee /etc/profile.d/maven.sh > /dev/null
    echo 'export PATH=$M2_HOME/bin:$PATH' | sudo tee -a /etc/profile.d/maven.sh > /dev/null
    echo 'export MAVEN_OPTS="-Djava.home=/usr/lib/jvm/java-21-openjdk-arm64"' | sudo tee -a /etc/profile.d/maven.sh > /dev/null
    sudo chmod +x /etc/profile.d/maven.sh
    
    export M2_HOME=/opt/apache-maven-3.9.9
    export PATH=$M2_HOME/bin:$PATH
    
    rm -f /tmp/apache-maven-3.9.9-bin.tar.gz
    
    echo "   ✓ Maven 3.9.9 설치 완료"
else
    echo "   ✓ Maven 버전 충분 ($MAVEN_VER)"
fi

# 4. libpcap 설치
echo ""
echo "[4/5] libpcap 설치"
if ! dpkg -l | grep -q libpcap-dev; then
    sudo apt install -y libpcap-dev
fi
echo "   ✓ libpcap 설치됨"

# 5. 권한 설정
echo ""
echo "[5/5] 권한 설정"
sudo setcap cap_net_raw,cap_net_admin=eip $(which java)
echo "   ✓ Java raw socket 권한 설정 완료"

# 환경변수를 .bashrc에 추가
echo ""
echo "======================================"
echo "환경변수 설정"
echo "======================================"

if ! grep -q "JAVA_HOME=$JAVA_HOME" ~/.bashrc; then
    echo "export JAVA_HOME=$JAVA_HOME" >> ~/.bashrc
    echo "export PATH=\$JAVA_HOME/bin:\$PATH" >> ~/.bashrc
fi

if [ -d "/opt/apache-maven-3.9.9" ]; then
    if ! grep -q "M2_HOME=/opt/apache-maven-3.9.9" ~/.bashrc; then
        echo "export M2_HOME=/opt/apache-maven-3.9.9" >> ~/.bashrc
        echo "export PATH=\$M2_HOME/bin:\$PATH" >> ~/.bashrc
        echo "export MAVEN_OPTS=\"-Djava.home=\$JAVA_HOME\"" >> ~/.bashrc
    fi
fi

# 테스트: Maven이 올바른 javac를 사용하는지 확인
echo ""
echo "======================================"
echo "설치 검증"
echo "======================================"
echo "Java: $($JAVA_HOME/bin/java -version 2>&1 | head -n 1)"
echo "javac: $($JAVA_HOME/bin/javac -version 2>&1)"
if [ -d "/opt/apache-maven-3.9.9" ]; then
    echo "Maven: $(/opt/apache-maven-3.9.9/bin/mvn -version | head -n 1)"
fi

echo ""
echo "======================================"
echo "✅ 설정 완료!"
echo "======================================"
echo ""
echo "다음 명령으로 환경변수를 적용하세요:"
echo "  source ~/.bashrc"
echo ""
echo "또는 터미널을 재시작한 후:"
echo "  ./run.sh"
echo ""
