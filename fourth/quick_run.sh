#!/bin/bash

# Ubuntu 빠른 실행 스크립트
# Java 21 환경에서 프로젝트를 빌드하고 실행합니다.

echo "======================================"
echo "Lab 4 프로젝트 실행"
echo "======================================"
echo ""

# JAVA_HOME 설정 (자동 감지)
if [ -d "/usr/lib/jvm/java-21-openjdk-arm64" ]; then
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
elif [ -d "/usr/lib/jvm/java-21-openjdk-amd64" ]; then
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
else
    echo "❌ Java 21을 찾을 수 없습니다!"
    echo "다음 명령으로 설치하세요: sudo apt install openjdk-21-jdk"
    exit 1
fi
export PATH=$JAVA_HOME/bin:$PATH

echo "✓ Java 버전:"
$JAVA_HOME/bin/java -version 2>&1 | head -n 1
echo ""

echo "✓ JAVA_HOME: $JAVA_HOME"
echo ""

# 프로젝트 디렉토리로 이동
cd ~/network_homework/fourth

echo "======================================"
echo "1. Clean 빌드 중..."
echo "======================================"
JAVA_HOME=$JAVA_HOME mvn clean

echo ""
echo "======================================"
echo "2. 컴파일 중..."
echo "======================================"
JAVA_HOME=$JAVA_HOME mvn compile

if [ $? -eq 0 ]; then
    echo ""
    echo "======================================"
    echo "✅ 컴파일 성공!"
    echo "======================================"
    echo ""
    echo "프로그램을 실행합니다..."
    echo ""
    JAVA_HOME=$JAVA_HOME mvn exec:exec@run-app
else
    echo ""
    echo "======================================"
    echo "❌ 컴파일 실패!"
    echo "======================================"
    echo ""
    echo "에러를 확인하세요."
    exit 1
fi
