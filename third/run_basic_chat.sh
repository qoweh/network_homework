#!/bin/bash

# 기존 채팅 프로그램 실행 스크립트 (레거시)
# 관리자 권한 필요 (패킷 캡처를 위해)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "╔═══════════════════════════════════════════════════════════════════════════════╗"
echo "║                  패킷 기반 채팅 프로그램 (Basic Chat v1.0)                    ║"
echo "╚═══════════════════════════════════════════════════════════════════════════════╝"
echo ""

# 컴파일 확인
if [ ! -d "target/classes/com/demo" ]; then
    echo "[컴파일] 클래스 파일이 없습니다. 컴파일을 시작합니다..."
    mkdir -p target/classes
    javac --enable-preview --release 21 -d target/classes -cp "lib/jnetpcap-wrapper-2.3.1-jdk21.jar" \
        src/main/java/com/demo/BaseLayer.java \
        src/main/java/com/demo/PhysicalLayer.java \
        src/main/java/com/demo/EthernetLayer.java \
        src/main/java/com/demo/ChatAppLayer.java \
        src/main/java/com/demo/BasicChatApp.java
    
    if [ $? -ne 0 ]; then
        echo "[오류] 컴파일 실패"
        exit 1
    fi
    echo "[완료] 컴파일 성공"
    echo ""
fi

# 관리자 권한 확인
if [ "$EUID" -ne 0 ]; then 
    echo "[경고] 이 프로그램은 관리자 권한이 필요합니다."
    echo "       다음 명령어로 실행하세요:"
    echo ""
    echo "       sudo ./run_basic_chat.sh"
    echo ""
    exit 1
fi

# 프로그램 실행
echo "[시작] 기본 채팅 프로그램을 시작합니다..."
echo ""

java --enable-preview -cp "target/classes:lib/jnetpcap-wrapper-2.3.1-jdk21.jar" com.demo.BasicChatApp

echo ""
echo "[종료] 프로그램이 종료되었습니다."
