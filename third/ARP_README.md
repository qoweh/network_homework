# ARP 기능이 추가된 채팅 프로그램

## 📋 프로젝트 개요

ARP(Address Resolution Protocol) 프로토콜을 구현한 패킷 기반 채팅 애플리케이션입니다.
IP 주소를 MAC 주소로 변환하는 ARP 기능을 포함하며, 계층화된 네트워크 프로토콜 스택을 구현했습니다.

## 🏗️ 계층 구조

```
┌──────────────────────┐
│   ChatAppLayer       │  응용 계층 (L7) - 메시지 인코딩/디코딩
└──────────┬───────────┘
           │
┌──────────▼───────────┐
│      IPLayer         │  네트워크 계층 (L3) - IP 패킷 생성/파싱
└──────────┬───────────┘
           │
    ┌──────┴──────┐
    │             │
┌───▼────┐   ┌───▼────┐
│Ethernet│   │  ARP   │  데이터링크 계층 (L2)
│ Layer  │   │ Layer  │  - 프레임 생성 / ARP 주소 해석
└───┬────┘   └───┬────┘
    │             │
    └──────┬──────┘
           │
┌──────────▼───────────┐
│   PhysicalLayer      │  물리 계층 (L1) - 실제 패킷 송수신
└──────────────────────┘
```

## ✨ 주요 기능

### 1. ARP 프로토콜 구현
- **ARP Request**: IP 주소에 대한 MAC 주소 요청 (브로드캐스트)
- **ARP Reply**: ARP 요청에 대한 MAC 주소 응답 (유니캐스트)
- **ARP 캐시 테이블**: IP-MAC 매핑 저장 및 관리
- **Gratuitous ARP**: 자신의 IP를 네트워크에 알림
- **Proxy ARP**: 다른 호스트 대신 ARP 응답

### 2. 이더넷 역다중화
- EtherType 기반 상위 계층 선택
  - `0x0800`: IPv4 → IPLayer
  - `0x0806`: ARP → ARPLayer

### 3. IP 통신
- IPv4 패킷 생성 및 파싱
- IP 헤더 20바이트 구현
- ARP 연동하여 MAC 주소 자동 해석

## 📁 파일 구조

```
src/main/java/com/demo/
├── BaseLayer.java          # 계층 인터페이스
├── PhysicalLayer.java      # 물리 계층 (jNetPcap)
├── EthernetLayer.java      # 이더넷 계층 (역다중화 지원)
├── ARPLayer.java           # ARP 계층 (새로 추가)
├── IPLayer.java            # IP 계층 (새로 추가)
├── ChatAppLayer.java       # 채팅 애플리케이션 계층
├── ARPChatApp.java         # ARP 기능 GUI (새로 추가)
└── BasicChatApp.java       # 기존 채팅 GUI (레거시)
```

## 🔧 구현 세부사항

### ARPLayer.java
- **ARP 패킷 구조** (28바이트)
  - Hardware Type (2) + Protocol Type (2)
  - HW Len (1) + Protocol Len (1) + Operation (2)
  - Sender MAC (6) + Sender IP (4)
  - Target MAC (6) + Target IP (4)

- **주요 메서드**
  - `sendArpRequest()`: ARP Request 전송
  - `sendArpReply()`: ARP Reply 전송
  - `sendGratuitousArp()`: Gratuitous ARP 전송
  - `lookupArpCache()`: ARP 캐시 조회
  - `addArpCacheEntry()`: ARP 캐시 추가

### IPLayer.java
- **IP 헤더 구조** (20바이트, 옵션 제외)
  - Version (4비트) + IHL (4비트) + TOS (1)
  - Total Length (2) + ID (2) + Flags+Offset (2)
  - TTL (1) + Protocol (1) + Checksum (2)
  - Source IP (4) + Destination IP (4)

- **주요 메서드**
  - `Send()`: IP 패킷 전송 (ARP 캐시 조회 후 전송)
  - `Receive()`: IP 패킷 수신 및 목적지 필터링
  - `setArpLayer()`: ARP 계층 연동 설정

### EthernetLayer.java (수정)
- **이더넷 역다중화 구현**
  ```java
  // EtherType에 따라 상위 계층 선택
  if (etherType == 0x0800) → IPLayer
  if (etherType == 0x0806) → ARPLayer
  ```

- **MAC 주소 필터링**
  - 자기 수신 방지 (출발지 == 자신)
  - 목적지 필터 (자신 또는 브로드캐스트만 수락)

### ARPChatApp.java
- **GUI 구성**
  - 네트워크 설정 패널 (장치 선택, IP 설정)
  - 메시지 표시 영역
  - ARP 캐시 테이블 (실시간 업데이트)
  - ARP 기능 버튼 (Request, Gratuitous, 캐시 초기화)
  - Proxy ARP 설정 패널

## 🚀 실행 방법

### 1. 빌드
```bash
cd third
mvn clean compile
```

### 2. ARP 채팅 프로그램 실행 (관리자 권한 필요)
```bash
# macOS
sudo mvn exec:java -Dexec.mainClass="com.demo.ARPChatApp"

# 또는 직접 실행
sudo java -cp "target/classes:lib/jnetpcap-wrapper-2.3.1-jdk21.jar" com.demo.ARPChatApp
```

### 3. 기존 채팅 프로그램 실행 (레거시)
```bash
sudo mvn exec:java -Dexec.mainClass="com.demo.BasicChatApp"
```

## 📖 사용 방법

### 기본 설정
1. **네트워크 장치 선택**: 드롭다운에서 사용할 NIC 선택
2. **내 IP 주소 설정**: 자신의 IP 주소 입력 (예: 192.168.0.100)
3. **목적지 IP 설정**: 통신할 상대방 IP 입력 (예: 192.168.0.101)
4. **설정 버튼 클릭**: 계층 구조 초기화 및 네트워크 연결

### ARP 기능 사용
1. **ARP Request**: 목적지 IP의 MAC 주소 요청
2. **Gratuitous ARP**: 자신의 IP를 네트워크에 알림
3. **ARP 캐시 테이블**: IP-MAC 매핑 확인
4. **Proxy ARP**: 다른 호스트 대신 ARP 응답 설정

### 메시지 전송
1. 하단 메시지 입력창에 텍스트 입력
2. 전송 버튼 클릭 또는 Enter
3. ARP 캐시에 목적지 MAC이 있어야 전송 가능

## 🔍 Wireshark 캡처 확인

### ARP Request 패킷
- Destination MAC: `FF:FF:FF:FF:FF:FF` (브로드캐스트)
- EtherType: `0x0806` (ARP)
- Operation: `1` (Request)

### ARP Reply 패킷
- Destination MAC: 요청자의 MAC
- EtherType: `0x0806` (ARP)
- Operation: `2` (Reply)

### IP 패킷
- EtherType: `0x0800` (IPv4)
- IP Protocol: `253` (사용자 정의)

## 🧪 테스트 시나리오

### 1. 기본 ARP 통신
```
[호스트 A]                    [호스트 B]
   │                             │
   │──── ARP Request ───────────>│ (Who has 192.168.0.101?)
   │                             │
   │<─── ARP Reply ──────────────│ (192.168.0.101 is at BB:BB:...)
   │                             │
   │──── IP Packet ─────────────>│ (메시지 전송)
```

### 2. Gratuitous ARP
```
[호스트 A]
   │
   │──── Gratuitous ARP ────────> 네트워크 전체
   │     (192.168.0.100 is at AA:AA:...)
```

### 3. Proxy ARP
```
[호스트 A]          [프록시]           [호스트 C]
   │                  │                   │
   │── ARP Request ──>│                   │ (Who has 192.168.0.200?)
   │                  │                   │
   │<─ ARP Reply ─────│                   │ (대신 응답)
   │                  │                   │
   │── IP Packet ────>│─── 전달 ─────────>│
```

## ⚠️ 주의사항

1. **관리자 권한 필요**: 패킷 캡처를 위해 sudo/관리자 권한 필요
2. **방화벽 설정**: 방화벽에서 패킷이 차단되지 않도록 설정
3. **동일 네트워크**: 같은 LAN 내에서만 통신 가능
4. **MAC 주소 로드**: 일부 가상 NIC는 MAC 주소 로드 실패 가능

## 🔧 트러블슈팅

### "ARP 캐시에 없음" 오류
- **원인**: 목적지 IP의 MAC 주소를 모름
- **해결**: "ARP Request" 버튼 클릭 → 수신 대기 → 재전송

### 메시지 전송 실패
- **원인**: 계층이 초기화되지 않음
- **해결**: "설정" 버튼을 먼저 클릭

### 패킷이 수신되지 않음
- **원인**: 다른 네트워크 또는 방화벽 차단
- **해결**: ping으로 연결 확인, 방화벽 설정 확인

## 📊 성능 최적화

- **ARP 캐시**: ConcurrentHashMap으로 멀티스레드 안전성 보장
- **패킷 수신**: 200ms timeout으로 낮은 지연 시간
- **Non-promiscuous 모드**: CPU 부하 최소화

## 📝 참고 자료

- RFC 826: ARP (Address Resolution Protocol)
- RFC 791: IP (Internet Protocol)
- IEEE 802.3: Ethernet
- jNetPcap Documentation

## 👨‍💻 개발 정보

- **언어**: Java 21
- **라이브러리**: jNetPcap 2.3.1
- **빌드 도구**: Maven
- **GUI**: Swing

## 📄 라이선스

교육용 프로젝트
