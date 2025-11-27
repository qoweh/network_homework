#!/bin/bash

# Lab 4 파일 전송 채팅 프로그램 실행 스크립트

cd "$(dirname "$0")"

# Java 21 경로 설정 (OS별 자동 감지)
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null)
elif [[ -d "/usr/lib/jvm/java-21-openjdk-amd64" ]]; then
    # Ubuntu/Debian
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
elif [[ -d "/usr/lib/jvm/java-21" ]]; then
    # 다른 Linux
    export JAVA_HOME=/usr/lib/jvm/java-21
else
    # Java 21이 설치되지 않은 경우 시스템 기본 Java 사용
    echo "⚠️  경고: Java 21을 찾을 수 없습니다. 시스템 기본 Java를 사용합니다."
    export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java) 2>/dev/null || which java)))
fi

# JAVA_HOME이 설정되었는지 확인
if [[ -z "$JAVA_HOME" || ! -d "$JAVA_HOME" ]]; then
    echo "❌ 오류: JAVA_HOME을 설정할 수 없습니다."
    echo "Java 21을 설치하거나 JAVA_HOME 환경변수를 수동으로 설정하세요."
    exit 1
fi

export PATH="$JAVA_HOME/bin:$PATH"

echo "======================================"
echo "Lab 4: 파일 전송 채팅 프로그램"
echo "======================================"
echo ""
echo "Java Home: $JAVA_HOME"
echo "Java Version: $(java --version 2>/dev/null | head -n 1 || echo 'Unknown')"
echo ""
echo "기능:"
echo "  - 채팅 메시지 전송 (Fragmentation 지원)"
echo "  - 파일 전송 (Thread 기반)"
echo "  - IP 프로토콜 역다중화 (Chat: 253, File: 254)"
echo "  - ARP 캐시 관리"
echo ""
echo "프로그램을 시작합니다..."
echo ""

# 먼저 컴파일
mvn compile || {
    echo "❌ 컴파일 실패!"
    exit 1
}

# exec:exec로 실행 (native library path 포함)
mvn exec:exec@run-app
