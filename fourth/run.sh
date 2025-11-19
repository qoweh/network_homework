#!/bin/bash

# Lab 4 파일 전송 채팅 프로그램 실행 스크립트

cd "$(dirname "$0")"

# Java 21 사용
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

echo "======================================"
echo "Lab 4: 파일 전송 채팅 프로그램"
echo "======================================"
echo ""
echo "Java Version: $(java --version | head -n 1)"
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
mvn compile

# exec:exec로 실행 (native library path 포함)
mvn exec:exec@run-app
