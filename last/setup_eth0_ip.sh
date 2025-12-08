#!/bin/bash

##############################################
# eth0 네트워크 인터페이스 IP 설정 스크립트
##############################################
#
# 사용법:
#   1. 기본값 사용 (169.254.142.80):
#      sudo ./setup_eth0_ip.sh
#
#   2. 커스텀 IP 사용:
#      sudo ./setup_eth0_ip.sh 169.254.132.200
#
#   3. 환경변수로 지정:
#      KALI_IP=169.254.132.200 sudo -E ./setup_eth0_ip.sh
#
##############################################

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# root 권한 체크
if [ "$EUID" -ne 0 ]; then 
    echo -e "${RED}[오류]${NC} root 권한이 필요합니다. 'sudo'를 사용하세요."
    exit 1
fi

# 네트워크 인터페이스 이름
INTERFACE="eth0"

# IP 주소 결정 (우선순위: 1.인자 2.환경변수 3.기본값)
if [ -n "$1" ]; then
    # 명령줄 인자가 있으면 사용
    IP_ADDRESS="$1"
    echo -e "${GREEN}[설정]${NC} 명령줄 인자 사용: ${IP_ADDRESS}"
elif [ -n "$KALI_IP" ]; then
    # 환경변수 KALI_IP가 있으면 사용
    IP_ADDRESS="$KALI_IP"
    echo -e "${GREEN}[설정]${NC} 환경변수 사용: ${IP_ADDRESS}"
else
    # 기본값 사용
    IP_ADDRESS="169.254.142.80"
    echo -e "${YELLOW}[설정]${NC} 기본값 사용: ${IP_ADDRESS}"
fi

# 서브넷 마스크 (링크-로컬 주소는 /16)
SUBNET_MASK="16"

echo ""
echo "=========================================="
echo "  eth0 네트워크 설정"
echo "=========================================="
echo "  인터페이스: ${INTERFACE}"
echo "  IP 주소:    ${IP_ADDRESS}/${SUBNET_MASK}"
echo "=========================================="
echo ""

# 1. 기존 IP 주소 제거
echo -e "${YELLOW}[1/3]${NC} 기존 IP 주소 제거 중..."
ip addr flush dev ${INTERFACE} 2>/dev/null
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} 기존 IP 주소 제거 완료"
else
    echo -e "${RED}✗${NC} 기존 IP 주소 제거 실패 (계속 진행)"
fi

# 2. 새 IP 주소 추가
echo -e "${YELLOW}[2/3]${NC} 새 IP 주소 추가 중..."
ip addr add ${IP_ADDRESS}/${SUBNET_MASK} dev ${INTERFACE}
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} IP 주소 추가 완료"
else
    echo -e "${RED}✗${NC} IP 주소 추가 실패"
    exit 2
fi

# 3. 인터페이스 활성화
echo -e "${YELLOW}[3/3]${NC} 인터페이스 활성화 중..."
ip link set ${INTERFACE} up
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} 인터페이스 활성화 완료"
else
    echo -e "${RED}✗${NC} 인터페이스 활성화 실패"
    exit 3
fi

echo ""
echo -e "${GREEN}=========================================="
echo "  설정 완료!"
echo "==========================================${NC}"
echo ""

# 4. 설정 확인
echo "현재 ${INTERFACE} 상태:"
ip addr show ${INTERFACE} | grep -E "inet |link/ether"

echo ""
echo -e "${GREEN}[안내]${NC} 자바 앱 설정:"
echo "  - 네트워크 장치: ${INTERFACE}"
echo "  - 내 IP 주소:    ${IP_ADDRESS}"
echo "  - MAC 주소:      $(ip link show ${INTERFACE} | grep link/ether | awk '{print $2}')"
echo ""
