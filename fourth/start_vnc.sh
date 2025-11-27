#!/bin/bash
# VNC 서버 시작 스크립트 (Ubuntu VM에서 실행)

echo "======================================"
echo "  VNC 서버 시작"
echo "======================================"
echo

# 기존 VNC 세션 확인
if [ -f ~/.vnc/*.pid ]; then
    echo "기존 VNC 세션 발견. 종료 중..."
    vncserver -kill :1 2>/dev/null || true
    sleep 2
fi

# VNC 서버 시작
echo "VNC 서버 시작 중 (디스플레이 :1, 해상도 1920x1080)..."
vncserver :1 -geometry 1920x1080 -depth 24 -localhost no

echo
echo "======================================"
echo "  VNC 서버 시작 완료!"
echo "======================================"
echo
echo "Mac에서 접속:"
echo "  1. Finder > Go > Connect to Server (Cmd+K)"
echo "  2. 주소 입력: vnc://192.168.64.7:5901"
echo "  3. VNC 비밀번호 입력"
echo
echo "또는 Mac 터미널에서:"
echo "  open vnc://192.168.64.7:5901"
echo
echo "VNC 서버 중지:"
echo "  vncserver -kill :1"
echo
echo "======================================"
