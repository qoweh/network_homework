# VM과 Mac 간 GUI 테스트 가이드

## 문제: X11 GUI가 검은 화면으로 표시됨

현재 상황: XQuartz로 X11 forwarding은 작동하지만, Swing GUI가 검은 화면으로 표시됩니다.

---

## 해결 방법

### 🎯 **추천: 방법 2 - VNC를 통한 GUI 접근** (가장 안정적)

#### 1단계: Ubuntu VM에 GUI 환경 설치
```bash
# Ubuntu VM에서 실행
sudo apt update
sudo apt install -y ubuntu-desktop-minimal xfce4 xfce4-goodies
sudo apt install -y tigervnc-standalone-server tigervnc-common
```

#### 2단계: VNC 서버 설정
```bash
# VNC 비밀번호 설정
vncpasswd
# 비밀번호 입력 (예: 1234)

# VNC 서버 시작 (해상도: 1920x1080, 디스플레이 :1)
vncserver :1 -geometry 1920x1080 -depth 24
```

#### 3단계: Mac에서 VNC 클라이언트로 접속
```bash
# Mac에서 Finder 열기 > Go > Connect to Server (Cmd+K)
# 주소 입력: vnc://<VM_IP_ADDRESS>:5901
# 예: vnc://192.168.64.7:5901

# 또는 Mac 터미널에서:
open vnc://192.168.64.7:5901
```

#### 4단계: VNC 데스크톱에서 프로그램 실행
```bash
# VNC 데스크톱의 터미널에서
cd ~/fourth  # (또는 프로젝트 경로)
./run.sh
```

**장점:**
- ✅ 완전한 GUI 환경
- ✅ 색상/테마 문제 없음
- ✅ 안정적인 연결
- ✅ 복사/붙여넣기 가능

---

### 방법 1: X11 Forwarding 개선 (현재 방법)

#### A. SSH 설정 강화
```bash
# Mac에서 VM 접속 시
ssh -X -C -o ForwardX11Trusted=yes pilt@192.168.64.7

# 또는 ~/.ssh/config에 추가:
Host vm-ubuntu
    HostName 192.168.64.7
    User pilt
    ForwardX11 yes
    ForwardX11Trusted yes
    Compression yes
```

#### B. XQuartz 설정 확인
1. XQuartz → Preferences (Cmd+,)
2. **Output** 탭:
   - ✅ "Colors" → "Full Color" 선택
   - ✅ "True Color" 활성화
3. **Input** 탭:
   - ✅ "Emulate three button mouse" 체크
4. XQuartz 재시작 필요

#### C. Java Swing 렌더링 강제
```bash
# Ubuntu VM의 run.sh에서 실행 시:
export _JAVA_OPTIONS="-Dsun.java2d.xrender=false -Dawt.useSystemAAFontSettings=on"
./run.sh
```

---

### 방법 3: Mac에서 직접 실행 + VM과 통신

#### 1단계: Mac에서 프로그램 실행
```bash
# Mac 터미널
cd /Users/pilt/project-collection/network/network_homework/fourth
./run.sh
```

#### 2단계: 네트워크 설정
- **Mac**: 
  - 장치: `bridge100` 선택
  - MAC: 자동 입력됨
  - 내 IP: `192.168.64.1` (check_network.sh로 확인)
  - 목적지 IP: `192.168.64.7` (VM IP)

- **VM**:
  - 장치: `enp0s1` 선택
  - MAC: 수동 입력 필요 (아래 명령으로 확인)
  - 내 IP: `192.168.64.7`
  - 목적지 IP: `192.168.64.1` (Mac IP)

```bash
# VM에서 MAC 주소 확인
ip link show enp0s1 | grep "link/ether" | awk '{print $2}'
```

**장점:**
- ✅ 양쪽 모두 네이티브 GUI 사용
- ✅ X11 문제 없음
- ✅ 빠른 반응 속도

---

## 네트워크 설정 확인

### Mac에서 실행:
```bash
./check_network.sh
# 출력 예시:
# bridge100: inet 192.168.64.1
```

### Ubuntu VM에서 실행:
```bash
ip addr show enp0s1
# 출력 예시:
# inet 192.168.64.7/24
```

---

## 테스트 절차

### 1. 연결 테스트
```bash
# Mac에서 VM으로 ping
ping -c 3 192.168.64.7

# VM에서 Mac으로 ping
ping -c 3 192.168.64.1
```

### 2. 프로그램 실행
- **Mac**: `bridge100` 선택, IP: 192.168.64.1
- **VM**: `enp0s1` 선택, IP: 192.168.64.7
- 양쪽 설정 후 메시지 전송 테스트

### 3. ARP 테이블 확인
- "ARP Request" 버튼 클릭
- ARP 캐시 테이블에 상대방 MAC 주소 표시 확인

---

## 문제 해결

### 문제: MAC 주소가 00:00:00:00:00:00으로 표시
**해결:** GUI에서 수동으로 입력
```bash
# MAC 주소 확인
# Mac:
ifconfig bridge100 | grep ether

# Ubuntu:
ip link show enp0s1 | grep link/ether
```

### 문제: 패킷이 드롭됨
**원인:** MAC 주소 미설정 또는 잘못된 네트워크 장치 선택

**해결:**
1. 올바른 네트워크 장치 선택 (Mac: bridge100, VM: enp0s1)
2. MAC 주소 수동 입력
3. "설정" 버튼 클릭하여 적용
4. 터미널에 "[시스템] 내 MAC: XX:XX:XX:XX:XX:XX" 출력 확인

### 문제: 브로드캐스트 주소 관련 오류
**해결:** IP 주소를 같은 서브넷으로 설정
- Mac: 192.168.64.1
- VM: 192.168.64.7

---

## 권장 방법 요약

**개발/테스트 목적:** 
→ **방법 3 (양쪽 네이티브 실행)** - 가장 간단하고 안정적

**하나의 GUI만 필요:** 
→ **방법 2 (VNC)** - VM GUI를 Mac에서 원격 제어

**X11 문제 해결:** 
→ **방법 1 (X11 개선)** - XQuartz 설정 + Java 옵션

---

## 다음 단계

1. ✅ 코드 수정 완료 (MAC 주소 수동 입력 기능 추가)
2. ⏭️ 방법 선택 및 실행
3. ⏭️ 양쪽에서 프로그램 실행 후 통신 테스트
