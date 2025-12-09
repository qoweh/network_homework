# Windows 환경 실행 가이드 🪟

> 이 문서는 Windows에서 네트워크 채팅 프로그램을 실행하는 방법을 단계별로 설명합니다.

---

## 📋 목차

1. [실행 방법 개요](#1-실행-방법-개요)
2. [방법 1: Docker Desktop 사용 (권장)](#2-방법-1-docker-desktop-사용-권장)
3. [방법 2: WSL2 사용](#3-방법-2-wsl2-사용)
4. [방법 3: 네이티브 Windows 실행](#4-방법-3-네이티브-windows-실행)
5. [문제 해결](#5-문제-해결)

---

## 1. 실행 방법 개요

Windows에서 이 프로그램을 실행하는 방법은 3가지가 있습니다:

| 방법 | 난이도 | 장점 | 단점 |
|------|--------|------|------|
| **Docker Desktop** | ⭐ 쉬움 | 설치 간단, 환경 격리 | GUI 사용 불가 (데모만 가능) |
| **WSL2** | ⭐⭐ 보통 | 전체 기능 사용 가능 | 설정 필요 |
| **네이티브** | ⭐⭐⭐ 어려움 | 최고 성능 | WinPcap/Npcap 설정 복잡 |

**권장 순서**: Docker Desktop → WSL2 → 네이티브

---

## 2. 방법 1: Docker Desktop 사용 (권장)

### 2.1 사전 요구사항

- **Windows 10/11** (64비트)
- **Docker Desktop for Windows**
- 최소 **4GB RAM**

### 2.2 Docker Desktop 설치

1. [Docker Desktop 다운로드](https://www.docker.com/products/docker-desktop/)에서 설치 파일 다운로드

2. 설치 프로그램 실행
   - "Use WSL 2 instead of Hyper-V" 옵션 체크 (권장)
   - 설치 완료 후 재부팅

3. Docker Desktop 실행 확인
   ```powershell
   docker --version
   # Docker version 24.x.x, build xxxxx
   ```

### 2.3 프로그램 실행

**PowerShell** 또는 **명령 프롬프트**에서:

```powershell
# 1. 프로젝트 폴더로 이동
cd C:\path\to\network_homework\last

# 2. Docker 이미지 빌드 및 실행
docker-compose up --build
```

### 2.4 실행 모드

```powershell
# 데모 모드 (기본) - 3가지 기능 시연
docker run --rm network-chat:latest

# 테스트 모드 - 25개 테스트 실행
docker run --rm -e APP_MODE=test network-chat:latest

# 인터랙티브 모드 - 셸 접속
docker run --rm -it -e APP_MODE=interactive network-chat:latest
```

### 2.5 출력 예시

```
╔════════════════════════════════════════════════════════════╗
║     Network Chat Application - Feature Demonstration       ║
╠════════════════════════════════════════════════════════════╣
║  1. XOR Encryption                                         ║
║  2. Priority Queue (HIGH/NORMAL/LOW)                       ║
║  3. Timestamp & Latency Logging                            ║
╚════════════════════════════════════════════════════════════╝

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🔐 [1] Encryption Demo
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   Original Message: Hello, Encrypted World!
   Encrypted (hex) : 0A272E2E2D66022C29312D3E32272066132D332E2423
   Decrypted       : Hello, Encrypted World!
   Verification    : ✓ PASS
```

> ⚠️ **참고**: Docker 모드에서는 GUI와 실제 네트워크 패킷 캡처가 불가능합니다. 데모 및 테스트 용도로만 사용하세요.

---

## 3. 방법 2: WSL2 사용

WSL2(Windows Subsystem for Linux 2)를 사용하면 Windows에서 Linux 환경을 실행하여 전체 기능을 사용할 수 있습니다.

### 3.1 WSL2 설치

**PowerShell (관리자 권한)**에서:

```powershell
# WSL 설치 (Windows 10 버전 2004 이상)
wsl --install

# Ubuntu 설치 (기본값)
wsl --install -d Ubuntu

# 재부팅 필요
```

### 3.2 Ubuntu 초기 설정

WSL Ubuntu 터미널에서:

```bash
# 패키지 업데이트
sudo apt update && sudo apt upgrade -y

# Java 21 설치
sudo apt install -y openjdk-21-jdk

# libpcap 설치 (패킷 캡처용)
sudo apt install -y libpcap-dev

# Maven 설치
sudo apt install -y maven
```

### 3.3 X11 서버 설정 (GUI 사용)

Windows에서 GUI를 표시하려면 X11 서버가 필요합니다.

#### 옵션 A: VcXsrv 사용 (권장)

1. [VcXsrv 다운로드](https://sourceforge.net/projects/vcxsrv/) 및 설치

2. XLaunch 실행
   - "Multiple windows" 선택
   - "Start no client" 선택
   - ✅ "Disable access control" 체크 (중요!)
   - 설정 저장

3. WSL에서 DISPLAY 설정
   ```bash
   # ~/.bashrc에 추가
   export DISPLAY=$(cat /etc/resolv.conf | grep nameserver | awk '{print $2}'):0.0
   ```

#### 옵션 B: WSLg 사용 (Windows 11)

Windows 11에서는 WSLg가 기본 포함되어 별도 설정 없이 GUI 사용 가능!

```bash
# WSLg 확인
echo $DISPLAY
# :0 또는 비슷한 값이 출력되면 OK
```

### 3.4 프로그램 실행

```bash
# 프로젝트 폴더로 이동 (Windows 경로를 /mnt/로 접근)
cd /mnt/c/Users/YourName/path/to/network_homework/last

# 또는 프로젝트를 WSL 홈으로 복사
cp -r /mnt/c/Users/YourName/path/to/network_homework ~/
cd ~/network_homework/last

# 실행 (관리자 권한 필요)
sudo ./run.sh
```

### 3.5 네트워크 인터페이스 확인

```bash
# 사용 가능한 네트워크 인터페이스 확인
ip link show

# 일반적인 인터페이스:
# - eth0: 이더넷 (WSL 가상 네트워크)
# - lo: 루프백
```

> ⚠️ **참고**: WSL의 네트워크는 가상화되어 있어 실제 물리 NIC에 직접 접근하기 어려울 수 있습니다. 테스트 목적으로는 루프백(lo) 인터페이스를 사용하세요.

---

## 4. 방법 3: 네이티브 Windows 실행

### 4.1 사전 요구사항

| 소프트웨어 | 버전 | 다운로드 |
|-----------|------|----------|
| JDK | 21+ | [Oracle](https://www.oracle.com/java/technologies/downloads/) 또는 [Adoptium](https://adoptium.net/) |
| Maven | 3.9+ | [Apache Maven](https://maven.apache.org/download.cgi) |
| Npcap | 1.70+ | [Npcap](https://npcap.com/#download) |

### 4.2 Java 21 설치

1. [Adoptium](https://adoptium.net/)에서 JDK 21 다운로드
2. 설치 시 "Set JAVA_HOME variable" 옵션 체크
3. 설치 확인:
   ```powershell
   java -version
   # openjdk version "21.0.x"
   ```

### 4.3 Maven 설치

1. [Apache Maven](https://maven.apache.org/download.cgi)에서 Binary zip 다운로드
2. `C:\Program Files\Apache\maven` 등에 압축 해제
3. 환경 변수 설정:
   - `M2_HOME` = `C:\Program Files\Apache\maven`
   - `Path`에 `%M2_HOME%\bin` 추가
4. 설치 확인:
   ```powershell
   mvn -version
   # Apache Maven 3.9.x
   ```

### 4.4 Npcap 설치 (⚠️ 중요)

Npcap은 Windows에서 패킷 캡처를 위한 필수 라이브러리입니다.

1. [Npcap 다운로드](https://npcap.com/#download)

2. 설치 옵션:
   - ✅ **"Install Npcap in WinPcap API-compatible Mode"** (필수!)
   - ✅ "Support loopback traffic"
   - ✅ "Install in Admin-Only mode" (보안용)

3. 재부팅 권장

### 4.5 jNetPcap Windows 라이브러리

jNetPcap은 플랫폼별 네이티브 라이브러리가 필요합니다.

```powershell
# lib/native 폴더에 Windows DLL이 있는지 확인
dir lib\native\

# 필요한 파일:
# - jnetpcap.dll (Windows 64비트)
```

> ⚠️ 현재 프로젝트는 macOS/Linux용으로 구성되어 있습니다. Windows용 jNetPcap DLL이 필요할 수 있습니다.

### 4.6 프로그램 빌드

```powershell
# 프로젝트 폴더로 이동
cd C:\path\to\network_homework\last

# 빌드 (테스트 제외)
mvn clean compile -DskipTests

# 테스트 실행
mvn test
```

### 4.7 프로그램 실행

```powershell
# 관리자 권한으로 PowerShell 실행 필요!

# 방법 1: Maven으로 실행
mvn exec:java -Dexec.mainClass="com.demo.NetworkChatApp"

# 방법 2: 직접 실행
java --enable-preview --enable-native-access=ALL-UNNAMED ^
     -Djava.library.path=lib\native ^
     -cp "target\classes;lib\jnetpcap-wrapper-2.3.1-jdk21.jar" ^
     com.demo.NetworkChatApp
```

### 4.8 Windows 방화벽 설정

패킷 캡처가 방화벽에 의해 차단될 수 있습니다.

1. Windows 보안 → 방화벽 및 네트워크 보호
2. "방화벽에서 앱 허용"
3. Java 또는 프로그램 허용 추가

---

## 5. 문제 해결

### 5.1 Docker 관련

**문제**: `docker: command not found`
```powershell
# Docker Desktop이 실행 중인지 확인
# 시스템 트레이에서 Docker 아이콘 확인
```

**문제**: `error during connect: ... dial tcp ...`
```powershell
# Docker Desktop 재시작
# 또는 WSL 재시작
wsl --shutdown
```

### 5.2 WSL 관련

**문제**: GUI가 표시되지 않음
```bash
# DISPLAY 환경 변수 확인
echo $DISPLAY

# VcXsrv가 실행 중인지 확인 (Windows)
# "Disable access control" 옵션이 체크되어 있는지 확인
```

**문제**: `Permission denied`
```bash
# sudo로 실행
sudo ./run.sh

# 또는 권한 수정
chmod +x run.sh
```

### 5.3 네이티브 Windows 관련

**문제**: `UnsatisfiedLinkError: no jnetpcap in java.library.path`
```powershell
# java.library.path 확인
java -XshowSettings:properties -version 2>&1 | findstr java.library.path

# DLL 파일 위치 확인
# jnetpcap.dll이 java.library.path에 있어야 함
```

**문제**: `PcapException: The operation requires root/Administrator privileges`
```powershell
# 관리자 권한으로 PowerShell 실행
# 시작 메뉴 → PowerShell → 우클릭 → 관리자 권한으로 실행
```

**문제**: Npcap이 인식되지 않음
```powershell
# Npcap 재설치
# "WinPcap API-compatible Mode" 옵션 필수!

# 서비스 확인
sc query npcap
```

### 5.4 공통 문제

**문제**: `java.lang.UnsupportedClassVersionError`
```powershell
# Java 버전 확인
java -version

# Java 21 이상 필요
# JAVA_HOME 환경 변수 확인
echo %JAVA_HOME%
```

**문제**: 네트워크 인터페이스를 찾을 수 없음
```powershell
# 관리자 권한으로 실행 필요
# Npcap/WinPcap 설치 확인

# 네트워크 인터페이스 목록 확인
ipconfig /all
```

---

## 📌 요약

| 환경 | 권장 방법 | GUI | 패킷 캡처 |
|------|----------|-----|----------|
| 빠른 테스트 | Docker | ❌ | ❌ (데모만) |
| 전체 기능 | WSL2 + X11 | ✅ | ⚠️ (가상화) |
| 최고 성능 | 네이티브 + Npcap | ✅ | ✅ |

**가장 간단한 방법**: Docker Desktop으로 데모 실행

**전체 기능 사용**: WSL2 + VcXsrv (또는 Windows 11의 WSLg)

---

## 🔗 참고 링크

- [Docker Desktop for Windows](https://docs.docker.com/desktop/install/windows-install/)
- [WSL 설치 가이드](https://learn.microsoft.com/ko-kr/windows/wsl/install)
- [VcXsrv 설정 가이드](https://sourceforge.net/projects/vcxsrv/)
- [Npcap 공식 사이트](https://npcap.com/)
- [Adoptium JDK 다운로드](https://adoptium.net/)

---

*이 문서는 Windows 10/11 기준으로 작성되었습니다.*
