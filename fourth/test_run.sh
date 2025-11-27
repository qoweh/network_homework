#!/bin/bash

# Ubuntu 테스트 실행 스크립트
# Java 21 환경에서 프로젝트 테스트를 실행합니다.

echo "======================================"
echo "Lab 4 프로젝트 테스트"
echo "======================================"
echo ""

# JAVA_HOME 설정
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

echo "✓ JAVA_HOME: $JAVA_HOME"
echo "✓ Java 버전: $($JAVA_HOME/bin/java -version 2>&1 | head -n 1)"
echo ""

# 프로젝트 디렉토리로 이동
cd ~/network_homework/fourth

echo "======================================"
echo "테스트 실행 중..."
echo "======================================"
echo ""

JAVA_HOME=$JAVA_HOME mvn test

if [ $? -eq 0 ]; then
    echo ""
    echo "======================================"
    echo "✅ 모든 테스트 통과!"
    echo "======================================"
else
    echo ""
    echo "======================================"
    echo "❌ 테스트 실패!"
    echo "======================================"
    exit 1
fi
