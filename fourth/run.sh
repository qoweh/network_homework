#!/bin/bash

# Lab 4 실행 스크립트 (macOS & Ubuntu)

cd "$(dirname "$0")"

# OS 감지
if [[ "$OSTYPE" == "darwin"* ]]; then
    OS="macos"
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    OS="ubuntu"
else
    echo "❌ 지원하지 않는 OS입니다."
    exit 1
fi

# JAVA_HOME 설정
if [ "$OS" = "macos" ]; then
    export JAVA_HOME=$(/usr/libexec/java_home -v 21)
elif [ "$OS" = "ubuntu" ]; then
    ARCH=$(uname -m)
    if [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
        export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
    else
        export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
    fi
    
    if [ ! -d "$JAVA_HOME" ]; then
        echo "❌ Java 21이 설치되어 있지 않습니다."
        echo "   ./setup.sh를 먼저 실행하세요."
        exit 1
    fi
    
    # Maven 3.9+ 경로
    if [ -d "/opt/apache-maven-3.9.9" ]; then
        export M2_HOME=/opt/apache-maven-3.9.9
        export PATH=$M2_HOME/bin:$PATH
    fi
    
    # X11 확인
    if [ -z "$DISPLAY" ]; then
        echo "⚠️  X11 DISPLAY가 설정되어 있지 않습니다."
        echo "   SSH 접속 시: ssh -X user@server"
        echo "   또는 DISPLAY를 수동 설정하세요."
        echo ""
        read -p "계속 진행하시겠습니까? (y/n) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
fi

export PATH=$JAVA_HOME/bin:$PATH

echo "======================================"
echo "Lab 4 File Transfer Chat"
echo "======================================"
echo "OS: $OS"
echo "Java: $(java -version 2>&1 | head -n 1)"
echo ""

# 컴파일
echo "Maven Clean & Compile..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "❌ 컴파일 실패"
    [ "$OS" = "ubuntu" ] && echo "   ./setup_toolchains.sh를 실행하세요."
    exit 1
fi

echo "✓ 컴파일 성공"
echo ""

# 실행
echo "======================================"
echo "프로그램 실행"
echo "======================================"
if [ "$OS" = "ubuntu" ]; then
    echo "GUI 프로그램 실행 중..."
    echo "X11 forwarding: $DISPLAY"
    
    # Ubuntu: libpcap 네이티브 라이브러리 경로 추가
    export LD_LIBRARY_PATH=/usr/lib/aarch64-linux-gnu:/usr/lib/x86_64-linux-gnu:$LD_LIBRARY_PATH
    
    # DISPLAY 환경변수 명시적으로 전달
    export DISPLAY=$DISPLAY
fi
echo ""

DISPLAY=$DISPLAY mvn exec:exec@run-app
