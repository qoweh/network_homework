#!/bin/bash

# Ubuntu 환경 확인 스크립트
# Java와 Maven 설정이 올바른지 확인합니다.

echo "======================================"
echo "환경 설정 확인"
echo "======================================"
echo ""

# JAVA_HOME 설정 (자동 감지)
if [ -d "/usr/lib/jvm/java-21-openjdk-arm64" ]; then
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
elif [ -d "/usr/lib/jvm/java-21-openjdk-amd64" ]; then
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
else
    echo "❌ Java 21을 찾을 수 없습니다!"
    exit 1
fi
export PATH=$JAVA_HOME/bin:$PATH

echo "1. JAVA_HOME 확인"
echo "   $JAVA_HOME"
echo ""

echo "2. Java 설치 확인"
if command -v java &> /dev/null; then
    echo "   ✅ Java 설치됨"
    java -version 2>&1 | head -n 3 | sed 's/^/   /'
else
    echo "   ❌ Java 설치 안 됨"
    echo ""
    echo "다음 명령으로 설치하세요:"
    echo "sudo apt install -y openjdk-21-jdk"
    exit 1
fi

echo ""
echo "3. Maven 설치 확인"
if command -v mvn &> /dev/null; then
    echo "   ✅ Maven 설치됨"
    mvn -version 2>&1 | head -n 1 | sed 's/^/   /'
else
    echo "   ❌ Maven 설치 안 됨"
    echo ""
    echo "다음 명령으로 설치하세요:"
    echo "sudo apt install -y maven"
    exit 1
fi

echo ""
echo "4. 프로젝트 디렉토리 확인"
if [ -d ~/network_homework/fourth ]; then
    echo "   ✅ 프로젝트 디렉토리 존재"
    echo "   $(cd ~/network_homework/fourth && pwd)"
else
    echo "   ❌ 프로젝트 디렉토리 없음"
    echo ""
    echo "Git 클론이 필요합니다."
    exit 1
fi

echo ""
echo "5. pom.xml 확인"
if [ -f ~/network_homework/fourth/pom.xml ]; then
    echo "   ✅ pom.xml 존재"
else
    echo "   ❌ pom.xml 없음"
    exit 1
fi

echo ""
echo "======================================"
echo "✅ 모든 환경 설정 정상!"
echo "======================================"
echo ""
echo "다음 명령으로 실행하세요:"
echo ""
echo "  ./quick_run.sh     - 프로그램 실행"
echo "  ./test_run.sh      - 테스트 실행"
echo ""
