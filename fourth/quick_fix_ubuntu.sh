#!/bin/bash

# Ubuntu 24.04.3 LTS VM 빠른 수정 스크립트
# Maven 3.8.7에서 Java 21 컴파일 에러 해결

echo "======================================"
echo "Ubuntu VM 빠른 수정"
echo "======================================"

# 1. JAVA_HOME 설정
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
export PATH=$JAVA_HOME/bin:$PATH

echo "✓ JAVA_HOME: $JAVA_HOME"
echo "✓ Java 버전: $(java -version 2>&1 | head -n 1)"
echo ""

# 2. Maven 캐시 삭제
echo "Maven 캐시 삭제 중..."
rm -rf ~/.m2/repository

# 3. 프로젝트 clean
echo "프로젝트 클린 중..."
cd "$(dirname "$0")"
mvn clean -q

# 4. 컴파일 테스트 (상세 로그)
echo ""
echo "======================================"
echo "컴파일 테스트 (상세 로그)"
echo "======================================"
mvn compile -X 2>&1 | grep -A 5 -B 5 "release\|javac\|ERROR"

echo ""
echo "======================================"
echo "진단 완료"
echo "======================================"
echo ""
echo "해결 방법:"
echo "1. 위 로그에서 에러 확인"
echo "2. Maven 업그레이드: ./upgrade_maven.sh"
echo "3. 업그레이드 후 터미널 재시작"
echo "4. 실행: ./simple_run.sh"
