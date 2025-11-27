#!/bin/bash

# Ubuntu Maven + Java 21 컴파일 에러 긴급 수정 스크립트
# "release version 21 not supported" 에러 해결

cd "$(dirname "$0")"

echo "======================================"
echo "Ubuntu 컴파일 테스트"
echo "======================================"

# JAVA_HOME 설정
ARCH=$(uname -m)
if [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
else
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
fi

# Maven 3.9.9 경로 설정
if [ -d "/opt/apache-maven-3.9.9" ]; then
    export M2_HOME=/opt/apache-maven-3.9.9
    export PATH=$M2_HOME/bin:$PATH
fi

# Maven이 올바른 Java를 사용하도록 강제
export MAVEN_OPTS="-Djava.home=$JAVA_HOME"

echo "JAVA_HOME: $JAVA_HOME"
echo "Java: $(java -version 2>&1 | head -n 1)"
echo "Maven: $(mvn -version 2>&1 | head -n 1)"
echo ""

# Maven clean & compile (WARNING 숨김)
echo "======================================"
echo "Maven Clean & Compile"
echo "======================================"

JAVA_HOME=$JAVA_HOME MAVEN_OPTS="$MAVEN_OPTS" mvn clean compile -q 2>&1 | grep -v "systemPath" | grep -v "unresolvable"

COMPILE_STATUS=${PIPESTATUS[0]}

if [ $COMPILE_STATUS -eq 0 ]; then
    echo ""
    echo "======================================"
    echo "✅ 컴파일 성공!"
    echo "======================================"
    echo ""
    echo "테스트 실행:"
    JAVA_HOME=$JAVA_HOME MAVEN_OPTS="$MAVEN_OPTS" mvn test -q
    
    TEST_STATUS=$?
    if [ $TEST_STATUS -eq 0 ]; then
        echo ""
        echo "======================================"
        echo "✅ 모든 테스트 통과!"
        echo "======================================"
        echo ""
        echo "프로그램 실행:"
        echo "  ./run.sh"
    else
        echo ""
        echo "⚠️  일부 테스트 실패 (하지만 실행 가능)"
        echo "프로그램 실행:"
        echo "  ./run.sh"
    fi
else
    echo ""
    echo "======================================"
    echo "❌ 컴파일 실패"
    echo "======================================"
    echo ""
    echo "상세 로그:"
    JAVA_HOME=$JAVA_HOME MAVEN_OPTS="$MAVEN_OPTS" mvn compile -X 2>&1 | tail -50
fi
