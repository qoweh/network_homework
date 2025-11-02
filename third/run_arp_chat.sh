#!/bin/bash

# ARP 채팅 프로그램 실행 스크립트
# 관리자 권한 필요 (패킷 캡처를 위해)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "╔═══════════════════════════════════════════════════════════════════════════════╗"
echo "║              ARP 기능이 추가된 패킷 채팅 프로그램 (ARP Chat v2.0)             ║"
echo "╚═══════════════════════════════════════════════════════════════════════════════╝"
echo ""

# 컴파일 확인
if [ ! -d "target/classes/com/demo" ]; then
    echo "[컴파일] 클래스 파일이 없습니다. 컴파일을 시작합니다..."
    mkdir -p target/classes
    
    # Java 21로 컴파일
    export JAVA_HOME=$(/usr/libexec/java_home -v 21)
    $JAVA_HOME/bin/javac --enable-preview --release 21 -d target/classes -cp "lib/jnetpcap-wrapper-2.3.1-jdk21.jar" \
        src/main/java/com/demo/BaseLayer.java \
        src/main/java/com/demo/PhysicalLayer.java \
        src/main/java/com/demo/EthernetLayer.java \
        src/main/java/com/demo/ChatAppLayer.java \
        src/main/java/com/demo/ARPLayer.java \
        src/main/java/com/demo/IPLayer.java \
        src/main/java/com/demo/ARPChatApp.java \
        src/main/java/com/demo/BasicChatApp.java
    
    if [ $? -ne 0 ]; then
        echo "[오류] 컴파일 실패"
        exit 1
    fi
    echo "[완료] 컴파일 성공"
    echo ""
fi

# 관리자 권한 확인 (macOS/Linux)
if [ "$EUID" -ne 0 ]; then 
    echo "[경고] 이 프로그램은 관리자 권한이 필요합니다."
    echo "       다음 명령어로 실행하세요:"
    echo ""
    echo "       sudo ./run_arp_chat.sh"
    echo ""
    exit 1
fi

# 프로그램 실행
echo "[시작] ARP 채팅 프로그램을 시작합니다..."
echo ""

# Java 21 찾기
if command -v /usr/libexec/java_home &> /dev/null; then
    # macOS
    export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null)
    if [ -z "$JAVA_HOME" ]; then
        echo "[경고] Java 21을 찾을 수 없습니다. 기본 Java를 사용합니다."
        JAVA_CMD="java"
    else
        JAVA_CMD="$JAVA_HOME/bin/java"
    fi
else
    # Linux or other
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [ "$JAVA_VERSION" -ge 21 ]; then
            JAVA_CMD="java"
        else
            echo "[오류] Java 21 이상이 필요합니다. 현재 버전: $JAVA_VERSION"
            exit 1
        fi
    else
        echo "[오류] Java를 찾을 수 없습니다."
        exit 1
    fi
fi

echo "[실행] Java: $JAVA_CMD"
$JAVA_CMD --enable-preview -cp "target/classes:lib/jnetpcap-wrapper-2.3.1-jdk21.jar" com.demo.ARPChatApp

echo ""
echo "[종료] 프로그램이 종료되었습니다."
