# VM GUI 문제 해결 가이드

## 문제: VM에서 X11 forwarding GUI가 검은 화면

**원인:** XQuartz + SSH X11 forwarding은 Swing GUI 렌더링에 제한이 있음

---

## ✅ 해결 방법: VNC 사용 (권장)

### 1단계: VM에 VNC 서버 설치 (최초 1회만)

Ubuntu VM에 SSH 접속 후:

```bash
# 스크립트를 VM으로 복사
scp setup_vnc.sh pilt@192.168.64.7:~/

# VM에서 실행
ssh pilt@192.168.64.7
cd ~
chmod +x setup_vnc.sh
./setup_vnc.sh
```

설치 중 VNC 비밀번호를 물으면 입력하세요 (예: `1234`)

---

### 2단계: VNC 서버 시작

VM에서:

```bash
# 스크립트로 시작 (권장)
./start_vnc.sh

# 또는 직접 명령어:
vncserver :1 -geometry 1920x1080 -depth 24 -localhost no
```

출력 예시:
```
New Xvnc server 'ubuntu:1 (pilt)' on port 5901 for display :1.
Use xtigervncviewer -SecurityTypes VncAuth -passwd /home/pilt/.vnc/passwd :1 to connect to the VNC server.
```

---

### 3단계: Mac에서 VNC 접속

#### 방법 A: Finder 사용 (추천)
1. **Finder** 열기
2. **Go → Connect to Server** (또는 `Cmd+K`)
3. 서버 주소 입력: `vnc://192.168.64.7:5901`
4. **Connect** 클릭
5. VNC 비밀번호 입력

#### 방법 B: 터미널 사용
```bash
open vnc://192.168.64.7:5901
```

---

### 4단계: VNC 데스크톱에서 프로그램 실행

VNC 창이 열리면 Ubuntu 데스크톱이 보입니다:

1. **Applications → Terminal Emulator** 클릭
2. 터미널에서 실행:
```bash
cd ~/fourth  # 또는 프로젝트가 있는 경로
./run.sh
```

3. GUI가 정상적으로 표시됩니다! ✅

---

## 📝 테스트 시나리오

### Mac (로컬 실행)
```bash
cd /Users/pilt/project-collection/network/network_homework/fourth
./run.sh
```

**설정:**
- 네트워크 장치: `bridge100`
- 내 MAC: (자동 입력됨)
- 내 IP: `192.168.64.1`
- 목적지 IP: `192.168.64.7`

### VM (VNC 데스크톱에서 실행)
VNC 접속 → 터미널 → `./run.sh`

**설정:**
- 네트워크 장치: `enp0s1`
- 내 MAC: (수동 입력 필요)
  ```bash
  ip link show enp0s1 | grep "link/ether" | awk '{print $2}'
  ```
- 내 IP: `192.168.64.7`
- 목적지 IP: `192.168.64.1`

### 통신 테스트
1. 양쪽 설정 완료 후 **"설정"** 버튼 클릭
2. VM에서 **"ARP Request"** 클릭
3. ARP 캐시 테이블에 Mac의 MAC 주소 표시 확인
4. 메시지 입력 후 **"전송"** 클릭
5. Mac GUI에 메시지 표시 확인 ✅

---

## 🛠️ VNC 관리 명령어

### VNC 서버 중지
```bash
vncserver -kill :1
```

### VNC 서버 재시작
```bash
vncserver -kill :1
vncserver :1 -geometry 1920x1080 -depth 24 -localhost no
```

### 실행 중인 VNC 세션 확인
```bash
vncserver -list
```

### VNC 비밀번호 변경
```bash
vncpasswd
```

---

## ⚡ X11 Forwarding 대신 VNC를 사용하는 이유

| 방식 | 장점 | 단점 |
|------|------|------|
| **X11 Forwarding** | 설정 간단 | Swing GUI 렌더링 문제, 느림, 색상 문제 |
| **VNC** ✅ | 완전한 데스크톱, 안정적, 빠름, 색상 정상 | 초기 설정 필요 |

---

## 🔍 문제 해결

### 문제: VNC 접속 시 "Connection refused"
**원인:** VNC 서버가 시작되지 않음

**해결:**
```bash
# VM에서
vncserver -list  # 실행 중인 세션 확인
vncserver :1 -geometry 1920x1080 -depth 24 -localhost no
```

### 문제: VNC 화면이 회색 배경만 표시
**원인:** xstartup 설정 문제

**해결:**
```bash
# VM에서
vncserver -kill :1
cat > ~/.vnc/xstartup << 'EOF'
#!/bin/bash
unset SESSION_MANAGER
unset DBUS_SESSION_BUS_ADDRESS
startxfce4 &
EOF
chmod +x ~/.vnc/xstartup
vncserver :1 -geometry 1920x1080 -depth 24 -localhost no
```

### 문제: Mac에서 VM IP로 접속 안 됨
**확인:**
```bash
# Mac에서
ping 192.168.64.7
telnet 192.168.64.7 5901
```

**해결:** `-localhost no` 옵션으로 VNC 서버 시작

---

## 📌 다음 단계

1. ✅ `setup_vnc.sh`를 VM으로 복사
2. ✅ VM에서 VNC 서버 설치 및 시작
3. ✅ Mac에서 VNC 접속
4. ✅ VNC 데스크톱에서 `./run.sh` 실행
5. ✅ Mac과 VM 간 통신 테스트
