#!/bin/bash

# Ubuntu 24.04.3 LTS용 간단한 실행 스크립트
# Maven 3.8.7+ 호환

echo "======================================"
echo "Lab 4 실행 스크립트 (Ubuntu VM)"
echo "======================================"

# JAVA_HOME 설정 (ARM64)
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
export PATH=$JAVA_HOME/bin:$PATH

# Maven 3.9.9 사용 (업그레이드한 경우)
if [ -d "/opt/apache-maven-3.9.9" ]; then
    export M2_HOME=/opt/apache-maven-3.9.9
    export PATH=$M2_HOME/bin:$PATH
    echo "✓ Maven 3.9.9 사용"
fi

echo "Java 버전: $(java -version 2>&1 | head -n 1)"
echo "Maven 버전: $(mvn -version | head -n 1)"
echo ""

# 프로젝트 디렉토리로 이동
cd "$(dirname "$0")"

# 1. Clean
echo "======================================"
echo "1. Maven Clean..."
echo "======================================"
mvn clean

if [ $? -ne 0 ]; then
    echo "❌ Clean 실패"
    exit 1
fi

# 2. Compile
echo ""
echo "======================================"
echo "2. Maven Compile..."
echo "======================================"
mvn compile

if [ $? -ne 0 ]; then
    echo "❌ Compile 실패"
    echo ""
    echo "⚠️  해결 방법:"
    echo "1. Maven 업그레이드: ./upgrade_maven.sh"
    echo "2. 터미널 재시작 후 다시 실행"
    exit 1
fi

# 3. Test (선택사항)
echo ""
echo "테스트를 실행할까요? (y/n)"
read -r response
if [[ "$response" == "y" ]]; then
    echo ""
    echo "======================================"
    echo "3. Maven Test..."
    echo "======================================"
    mvn test
    
    if [ $? -ne 0 ]; then
        echo "❌ Test 실패"
        exit 1
    fi
fi

# 4. 실행
echo ""
echo "======================================"
echo "4. 프로그램 실행..."
echo "======================================"
echo ""
echo "⚠️  주의: GUI 프로그램이므로 X11 forwarding이 필요합니다"
echo "SSH 연결 시: ssh -X user@host"
echo ""

mvn exec:exec@run-app

echo ""
echo "======================================"
echo "실행 완료"
echo "======================================"
