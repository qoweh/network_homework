#!/bin/bash

# Ubuntu 환경 설정 스크립트
# 이 스크립트는 Ubuntu에서 프로젝트를 실행하기 위한 모든 의존성을 설치합니다.

echo "======================================"
echo "Lab 4 프로젝트 Ubuntu 환경 설정"
echo "======================================"
echo ""

# 현재 사용자 확인
CURRENT_USER=${SUDO_USER:-$USER}
USER_HOME=$(eval echo ~$CURRENT_USER)

# 루트 권한 확인
if [[ $EUID -ne 0 ]]; then
   echo "⚠️  이 스크립트는 sudo 권한이 필요합니다."
   echo "다음 명령으로 실행하세요: sudo ./setup_ubuntu.sh"
   exit 1
fi

echo "1. 시스템 업데이트 중..."
apt update

echo ""
echo "2. Java 21 설치 중..."
if java --version 2>/dev/null | grep -q "openjdk 21"; then
    echo "✅ Java 21이 이미 설치되어 있습니다."
else
    apt install -y openjdk-21-jdk
    if [[ $? -eq 0 ]]; then
        echo "✅ Java 21 설치 완료"
    else
        echo "❌ Java 21 설치 실패"
        exit 1
    fi
fi

echo ""
echo "3. Maven 설치 중..."
if command -v mvn &> /dev/null; then
    echo "✅ Maven이 이미 설치되어 있습니다."
else
    apt install -y maven
    if [[ $? -eq 0 ]]; then
        echo "✅ Maven 설치 완료"
    else
        echo "❌ Maven 설치 실패"
        exit 1
    fi
fi

echo ""
echo "4. libpcap 설치 중..."
apt install -y libpcap-dev libpcap0.8
if [[ $? -eq 0 ]]; then
    echo "✅ libpcap 설치 완료"
else
    echo "❌ libpcap 설치 실패"
    exit 1
fi

echo ""
echo "5. Git 확인 중..."
if command -v git &> /dev/null; then
    echo "✅ Git이 이미 설치되어 있습니다."
else
    apt install -y git
    echo "✅ Git 설치 완료"
fi

echo ""
echo "6. 환경 변수 설정 중..."
JAVA_HOME_PATH="/usr/lib/jvm/java-21-openjdk-amd64"

# .bashrc에 환경 변수 추가 (중복 방지)
if ! grep -q "JAVA_HOME.*java-21" "$USER_HOME/.bashrc"; then
    cat >> "$USER_HOME/.bashrc" << EOF

# Lab 4 프로젝트용 Java 21 설정
export JAVA_HOME=$JAVA_HOME_PATH
export PATH=\$JAVA_HOME/bin:\$PATH
EOF
    chown $CURRENT_USER:$CURRENT_USER "$USER_HOME/.bashrc"
    echo "✅ .bashrc에 환경 변수 추가 완료"
else
    echo "✅ 환경 변수가 이미 설정되어 있습니다."
fi

# 현재 세션에도 환경 변수 설정
export JAVA_HOME=$JAVA_HOME_PATH
export PATH=$JAVA_HOME/bin:$PATH

# Maven 테스트
echo ""
echo "8. Maven 동작 확인 중..."
if su - $CURRENT_USER -c "export JAVA_HOME=$JAVA_HOME_PATH && export PATH=\$JAVA_HOME/bin:\$PATH && mvn -version" &>/dev/null; then
    echo "✅ Maven이 정상적으로 작동합니다"
    su - $CURRENT_USER -c "export JAVA_HOME=$JAVA_HOME_PATH && mvn -version | head -n 1"
else
    echo "⚠️  Maven 동작 확인 실패. 수동으로 확인이 필요합니다."
fi

echo ""
echo "7. 네트워크 캡처 권한 설정 중..."
if [[ -f "$JAVA_HOME_PATH/bin/java" ]]; then
    setcap cap_net_raw,cap_net_admin=eip "$JAVA_HOME_PATH/bin/java"
    echo "✅ Java에 네트워크 캡처 권한 부여 완료"
else
    echo "⚠️  Java 실행 파일을 찾을 수 없습니다: $JAVA_HOME_PATH/bin/java"
fi

echo ""
echo "======================================"
echo "✅ 설치 완료!"
echo "======================================"
echo ""
echo "다음 단계:"
echo "1. 터미널을 재시작하거나 다음 명령 실행:"
echo "   source ~/.bashrc"
echo ""
echo "2. 환경 변수 확인:"
echo "   echo \$JAVA_HOME"
echo "   java -version"
echo "   mvn -version"
echo ""
echo "3. 프로젝트 디렉토리로 이동:"
echo "   cd ~/network_homework/fourth"
echo ""
echo "4. 프로그램 실행:"
echo "   ./run.sh"
echo ""
echo "💡 JAVA_HOME 에러가 나면:"
echo "   export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64"
echo "   export PATH=\$JAVA_HOME/bin:\$PATH"
echo ""
echo "문제가 발생하면 UBUNTU_FIX.md 파일을 참고하세요."
echo ""
