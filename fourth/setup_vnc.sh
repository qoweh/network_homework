#!/bin/bash
# VNC 서버 설치 및 설정 스크립트 (Ubuntu VM에서 실행)

echo "======================================"
echo "  VNC 서버 설치 및 설정"
echo "======================================"
echo

# 1. VNC 서버 설치
echo "[1/4] VNC 서버 설치 중..."
sudo apt update
sudo apt install -y tigervnc-standalone-server tigervnc-common

# 2. 경량 데스크톱 환경 설치 (이미 설치되어 있으면 스킵)
echo "[2/4] 데스크톱 환경 확인 중..."
if ! command -v startxfce4 &> /dev/null; then
    echo "XFCE4 데스크톱 설치 중..."
    sudo apt install -y xfce4 xfce4-goodies
else
    echo "XFCE4 이미 설치됨"
fi

# 3. VNC 비밀번호 설정
echo "[3/4] VNC 비밀번호 설정"
echo "비밀번호를 입력하세요 (예: 1234):"
vncpasswd

# 4. VNC 설정 파일 생성
echo "[4/4] VNC 시작 스크립트 생성 중..."
mkdir -p ~/.vnc

cat > ~/.vnc/xstartup << 'EOF'
#!/bin/bash
unset SESSION_MANAGER
unset DBUS_SESSION_BUS_ADDRESS
export XKL_XMODMAP_DISABLE=1
export XDG_CURRENT_DESKTOP="XFCE"
export XDG_SESSION_TYPE="x11"

# XFCE 시작
startxfce4 &
EOF

chmod +x ~/.vnc/xstartup

echo
echo "======================================"
echo "  설치 완료!"
echo "======================================"
echo
echo "VNC 서버 시작 방법:"
echo "  vncserver :1 -geometry 1920x1080 -depth 24"
echo
echo "Mac에서 접속 방법:"
echo "  1. Finder > Go > Connect to Server (Cmd+K)"
echo "  2. vnc://192.168.64.7:5901 입력"
echo "  3. 설정한 비밀번호 입력"
echo
echo "VNC 서버 중지 방법:"
echo "  vncserver -kill :1"
echo
echo "======================================"
