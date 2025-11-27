#!/bin/bash

# Ubuntu Maven + Java 21 컴파일 에러 긴급 수정 스크립트
# "release version 21 not supported" 에러 해결

echo "======================================"
echo "Ubuntu Maven 컴파일 에러 수정"
echo "======================================"

# JAVA_HOME 설정
ARCH=$(uname -m)
if [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
else
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
fi

echo "JAVA_HOME: $JAVA_HOME"

# Maven 3.9.9 경로 설정
if [ -d "/opt/apache-maven-3.9.9" ]; then
    export M2_HOME=/opt/apache-maven-3.9.9
    export PATH=$M2_HOME/bin:$PATH
    echo "Maven: /opt/apache-maven-3.9.9"
fi

# Maven이 올바른 Java를 사용하도록 강제
export MAVEN_OPTS="-Djava.home=$JAVA_HOME"

echo ""
echo "======================================"
echo "환경 확인"
echo "======================================"
echo "Java 경로: $(which java)"
echo "Java 버전: $(java -version 2>&1 | head -n 1)"
echo "javac 경로: $(which javac)"
echo "javac 버전: $(javac -version 2>&1)"
echo "Maven 경로: $(which mvn)"
echo "Maven 버전: $(mvn -version 2>&1 | head -n 1)"
echo "MAVEN_OPTS: $MAVEN_OPTS"

echo ""
echo "======================================"
echo "컴파일 테스트"
echo "======================================"

cd "$(dirname "$0")"

# Maven clean
echo "Maven Clean..."
JAVA_HOME=$JAVA_HOME MAVEN_OPTS="$MAVEN_OPTS" mvn clean

# Maven compile with verbose
echo ""
echo "Maven Compile..."
JAVA_HOME=$JAVA_HOME MAVEN_OPTS="$MAVEN_OPTS" mvn compile

if [ $? -eq 0 ]; then
    echo ""
    echo "======================================"
    echo "✅ 컴파일 성공!"
    echo "======================================"
    echo ""
    echo "이제 ./run.sh를 실행하세요."
else
    echo ""
    echo "======================================"
    echo "❌ 컴파일 실패"
    echo "======================================"
    echo ""
    echo "추가 디버깅:"
    echo "1. alternatives 확인:"
    sudo update-alternatives --config java
    echo ""
    echo "2. Maven의 Java 설정:"
    mvn -version
fi
