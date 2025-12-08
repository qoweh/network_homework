#!/bin/bash

#############################################################################
# setup_eth0_ip.sh - Kali Linux eth0 IP 주소 자동 설정 스크립트 (개선 버전)
#
# 기능:
# 1. 네 가지 방법으로 IP 주소 입력 지원
#    - 완전한 IP: ./setup_eth0_ip.sh 169.254.142.80
#    - 부분 IP (마지막 옥텟 자동): ./setup_eth0_ip.sh 169.254.38
#    - 환경 변수: KALI_IP=169.254.142.80 sudo -E ./setup_eth0_ip.sh
#    - 자동 생성: 랜덤 Link-local IP 자동 생성
# 2. IP 충돌 감지: arping으로 중복 IP 확인
# 3. Link-local 주소 범위 권장: 169.254.0.0/16
#
# 사용법:
#   sudo ./setup_eth0_ip.sh 169.254.142.80    # 완전한 IP
#   sudo ./setup_eth0_ip.sh 169.254.38        # 마지막 옥텟 랜덤
#   KALI_IP=169.254.142.80 sudo -E ./setup_eth0_ip.sh
#   sudo ./setup_eth0_ip.sh                   # 완전 자동 생성
#############################################################################

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

INTERFACE="eth0"

# Root 권한 확인
if [[ $EUID -ne 0 ]]; then
   echo -e "${RED}[ERROR]${NC} root 권한이 필요합니다."
   echo "Usage: sudo $0 [IP_ADDRESS]"
   exit 1
fi

# Link-local 주소 확인
is_link_local() {
    local ip=$1
    if [[ $ip =~ ^169\.254\.[0-9]{1,3}\.[0-9]{1,3}$ ]]; then
        return 0
    else
        return 1
    fi
}

# IP 충돌 검사
check_ip_conflict() {
    local test_ip=$1
    
    if ! command -v arping &> /dev/null; then
        echo -e "${YELLOW}[WARN]${NC} arping 미설치, 충돌 검사 생략"
        return 0  # 성공으로 간주
    fi
    
    echo -e "${BLUE}[INFO]${NC} IP 충돌 검사: $test_ip"
    ip link set $INTERFACE up 2>/dev/null || true
    
    # -D: Duplicate Address Detection (응답 없으면 사용 가능)
    # -w 3: 3초 타임아웃
    # -c 2: 2번 시도
    if timeout 5 arping -D -I $INTERFACE -c 2 -w 3 "$test_ip" >/dev/null 2>&1; then
        echo -e "${GREEN}[OK]${NC} 사용 가능: $test_ip"
        return 0  # 사용 가능
    else
        local exit_code=$?
        echo -e "${RED}[ERROR]${NC} IP 충돌 또는 검사 실패 (종료 코드: $exit_code): $test_ip"
        return 1  # 충돌 또는 실패
    fi
}

# 랜덤 IP 생성
generate_random_ip() {
    local third=$((RANDOM % 254 + 1))
    local fourth=$((RANDOM % 254 + 1))
    echo "169.254.$third.$fourth"
}

# 부분 IP 완성 (169.254.38 → 169.254.38.랜덤)
complete_partial_ip() {
    local partial=$1
    
    # 169.254.38 형식인지 확인
    if [[ $partial =~ ^169\.254\.[0-9]{1,3}$ ]]; then
        # 1~254 범위의 랜덤 값 (0과 255 제외)
        local fourth=$((RANDOM % 254 + 1))
        local result="$partial.$fourth"
        echo "$result"
        return 0
    fi
    
    # 그대로 반환
    echo "$partial"
    return 1
}

# IP 주소 결정
IP_ADDRESS=""

if [[ -n "$1" ]]; then
    # 부분 IP인지 확인 (169.254.38 형식)
    if [[ "$1" =~ ^169\.254\.[0-9]{1,3}$ ]]; then
        echo -e "${YELLOW}[AUTO]${NC} 부분 IP 감지: $1"
        
        # arping 없으면 바로 랜덤 생성
        if ! command -v arping &> /dev/null; then
            IP_ADDRESS=$(complete_partial_ip "$1")
            echo -e "${GREEN}[OK]${NC} IP 생성 (충돌 검사 생략): $IP_ADDRESS"
        else
            # 충돌 없는 IP 찾을 때까지 시도
            for attempt in {1..5}; do
                IP_ADDRESS=$(complete_partial_ip "$1")
                echo -e "${BLUE}[INFO]${NC} 시도 $attempt/5 - 후보: $IP_ADDRESS"
                
                if check_ip_conflict "$IP_ADDRESS"; then
                    echo -e "${GREEN}[OK]${NC} 사용 가능한 IP 생성: $IP_ADDRESS"
                    break
                fi
                
                if [[ $attempt -eq 5 ]]; then
                    echo -e "${RED}[ERROR]${NC} $1.X 범위에서 사용 가능한 IP를 찾지 못했습니다"
                    exit 1
                fi
            done
        fi
    else
        # 완전한 IP 주소
        IP_ADDRESS="$1"
        echo -e "${GREEN}[OK]${NC} 명령줄 인자: $IP_ADDRESS"
    fi
elif [[ -n "$KALI_IP" ]]; then
    IP_ADDRESS="$KALI_IP"
    echo -e "${GREEN}[OK]${NC} 환경 변수: $IP_ADDRESS"
else
    echo -e "${YELLOW}[AUTO]${NC} 자동 IP 생성 모드"
    
    # arping 없으면 바로 랜덤 생성
    if ! command -v arping &> /dev/null; then
        IP_ADDRESS=$(generate_random_ip)
        echo -e "${GREEN}[OK]${NC} IP 생성 (충돌 검사 생략): $IP_ADDRESS"
    else
        for attempt in {1..5}; do
            echo -e "${BLUE}[INFO]${NC} 시도 $attempt/5"
            IP_ADDRESS=$(generate_random_ip)
            echo -e "${BLUE}[INFO]${NC} 후보: $IP_ADDRESS"
            
            if check_ip_conflict "$IP_ADDRESS"; then
                break
            fi
            
            if [[ $attempt -eq 5 ]]; then
                echo -e "${RED}[ERROR]${NC} IP 찾기 실패"
                exit 1
            fi
        done
    fi
fi

# Link-local 주소 권장
if ! is_link_local "$IP_ADDRESS"; then
    echo -e "${YELLOW}[WARN]${NC} Link-local(169.254.x.x)이 아님: $IP_ADDRESS"
    read -p "계속? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "인터페이스: ${BLUE}$INTERFACE${NC}"
echo -e "설정 IP:    ${BLUE}$IP_ADDRESS/16${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# IP 주소 유효성 재확인
if [[ ! $IP_ADDRESS =~ ^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$ ]]; then
    echo -e "${RED}[ERROR]${NC} 유효하지 않은 IP 주소: $IP_ADDRESS"
    exit 1
fi

echo -e "${BLUE}[1/3]${NC} 기존 IP 제거..."
ip addr flush dev $INTERFACE 2>/dev/null || true

echo -e "${BLUE}[2/3]${NC} IP 설정: $IP_ADDRESS/16"
if ! ip addr add $IP_ADDRESS/16 dev $INTERFACE 2>/dev/null; then
    echo -e "${RED}[ERROR]${NC} IP 설정 실패. 이미 설정된 IP인지 확인하세요."
    # 기존 IP 확인
    echo -e "${YELLOW}[INFO]${NC} 현재 설정:"
    ip addr show $INTERFACE | grep -E "inet " || echo "  (IP 없음)"
    exit 1
fi

echo -e "${BLUE}[3/3]${NC} 인터페이스 활성화..."
ip link set $INTERFACE up

echo ""
echo -e "${GREEN}설정 완료!${NC}"
echo -e "\n${BLUE}현재 설정:${NC}"
ip addr show $INTERFACE | grep -E "inet "

echo ""
echo -e "${YELLOW}Java 프로그램 설정:${NC}"
echo -e "  Kali IP: ${BLUE}$IP_ADDRESS${NC}"
echo ""

exit 0
