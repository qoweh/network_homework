#!/bin/bash

echo "===================================="
echo "  네트워크 설정 확인 도구"
echo "===================================="
echo

# OS 감지
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo "[Mac OS]"
    echo
    echo "1. VM 관련 네트워크 인터페이스:"
    ifconfig | grep -A 5 "vmenet\|bridge" | grep -E "^[a-z]|inet "
    echo
    echo "2. 주 네트워크 인터페이스 (en0 - Wi-Fi):"
    ifconfig en0 | grep -E "^en0|inet |ether "
    
elif [[ -f /etc/os-release ]]; then
    echo "[Linux/Ubuntu]"
    echo
    echo "1. 네트워크 인터페이스:"
    ip addr show | grep -E "^[0-9]+:|inet "
    echo
    echo "2. MAC 주소:"
    ip link show | grep -E "^[0-9]+:|link/ether"
fi

echo
echo "===================================="
