#!/bin/bash

# Lab 4 실행 스크립트
# macOS & Ubuntu 자동 감지

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
    
    # Ubuntu: Maven 3.9+ 확인
    if [ -d "/opt/apache-maven-3.9.9" ]; then
        export M2_HOME=/opt/apache-maven-3.9.9
        export PATH=$M2_HOME/bin:$PATH
    fi
    
    # Maven이 올바른 javac를 사용하도록 강제 설정
    export MAVEN_OPTS="-Djava.home=$JAVA_HOME"
fi

export PATH=$JAVA_HOME/bin:$PATH

echo "======================================"
echo "Lab 4 File Transfer Chat"
echo "======================================"
echo ""
echo "OS: $OS"
echo "Java: $(java -version 2>&1 | head -n 1)"
echo "Maven: $(mvn -version 2>&1 | head -n 1)"
echo ""

# 컴파일
echo "======================================"
echo "Maven Clean & Compile"
echo "======================================"

# pom.xml에 명시적으로 javac 경로 전달
export JAVA_HOME=$JAVA_HOME
export PATH=$JAVA_HOME/bin:$PATH

# Ubuntu에서는 Maven에 명시적으로 Java 경로 전달
if [ "$OS" = "ubuntu" ]; then
    # Maven toolchains 확인
    if [ ! -f ~/.m2/toolchains.xml ]; then
        echo "⚠️  Maven toolchains 설정이 필요합니다."
        echo "   ./setup_toolchains.sh를 먼저 실행하세요."
        exit 1
    fi
    mvn clean compile -Dmaven.compiler.executable=$JAVA_HOME/bin/javac
else
    mvn clean compile -q
fi

if [ $? -ne 0 ]; then
    echo ""
    echo "❌ 컴파일 실패"
    if [ "$OS" = "ubuntu" ]; then
        echo ""
        echo "해결 방법:"
        echo "  ./setup_toolchains.sh"
    fi
    exit 1
else
    echo "✓ 컴파일 성공"
fi

# 실행
echo ""
echo "======================================"
echo "프로그램 실행"
echo "======================================"
if [ "$OS" = "ubuntu" ]; then
    echo "⚠️  GUI 프로그램: X11 forwarding 필요 (ssh -X)"
fi
echo ""

# Ubuntu에서는 JAVA_HOME을 명시적으로 전달
if [ "$OS" = "ubuntu" ]; then
    JAVA_HOME=$JAVA_HOME mvn exec:exec@run-app
else
    mvn exec:exec@run-app
fi
