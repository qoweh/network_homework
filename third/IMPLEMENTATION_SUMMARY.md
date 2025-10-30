# ARP 채팅 프로그램 구현 완료 보고서

## 📋 작업 요약

ARP(Address Resolution Protocol) 기능을 포함한 계층화된 네트워크 채팅 프로그램을 성공적으로 구현했습니다.

## 🎯 구현 완료 항목

### ✅ 1. IP/ARP 계층 추가 및 계층 연결

#### 새로 추가된 클래스
- **ARPLayer.java** (새로 작성)
  - ARP Request/Reply 메시지 처리
  - ARP 캐시 테이블 관리 (ConcurrentHashMap)
  - Gratuitous ARP 기능
  - Proxy ARP 기능
  - IP-MAC 주소 매핑 자동화

- **IPLayer.java** (새로 작성)
  - IPv4 패킷 생성 (20바이트 헤더)
  - IP 패킷 파싱 및 목적지 필터링
  - ARP 계층 연동 (MAC 주소 자동 해석)
  - IP 라우팅 기능

#### 수정된 클래스
- **EthernetLayer.java** (역다중화 추가)
  - EtherType 기반 상위 계층 선택
  - 0x0800 (IPv4) → IPLayer
  - 0x0806 (ARP) → ARPLayer
  - MAC 주소 필터링 강화

#### 계층 연결 구조
```
ChatAppLayer (L7)
    ↓
IPLayer (L3)
    ↓
EthernetLayer (L2) ←→ ARPLayer (L2)
    ↓
PhysicalLayer (L1)
```

### ✅ 2. ARP 기능 구현

#### ARP Request/Reply
```java
// ARP Request 전송 (브로드캐스트)
public boolean sendArpRequest(byte[] targetIp)
- 목적지 MAC: FF:FF:FF:FF:FF:FF
- Operation: 1 (Request)
- 네트워크 전체에 질의

// ARP Reply 전송 (유니캐스트)
public boolean sendArpReply(byte[] targetMac, byte[] targetIp)
- Operation: 2 (Reply)
- 요청자에게 직접 응답
```

#### Proxy ARP
```java
// 다른 호스트 대신 ARP 응답
public void addProxyArpEntry(String ip, byte[] mac)
public void setProxyArpEnabled(boolean enabled)

// 동작 과정
1. ARP Request 수신
2. Proxy 테이블에 해당 IP 존재 확인
3. 자신의 MAC으로 ARP Reply 전송
4. 실제 패킷은 프록시가 전달
```

#### Gratuitous ARP
```java
// 자신의 IP를 네트워크에 알림
public boolean sendGratuitousArp()

// 용도
1. IP 주소 충돌 감지
2. 네트워크 진입 알림
3. 다른 호스트의 ARP 캐시 업데이트
```

### ✅ 3. 이더넷 역다중화

#### EtherType 기반 계층 선택
```java
// EthernetLayer.java - Receive() 메서드
int receivedEtherType = ((input[12] & 0xFF) << 8) | (input[13] & 0xFF);

// 역다중화 로직
for (BaseLayer upper : uppers) {
    if (receivedEtherType == 0x0800 && upper instanceof IPLayer) {
        upper.Receive(payload);  // IP 패킷 → IPLayer
    }
    else if (receivedEtherType == 0x0806 && upper instanceof ARPLayer) {
        upper.Receive(payload);  // ARP 패킷 → ARPLayer
    }
}
```

### ✅ 4. GUI 업데이트

#### ARPChatApp.java (새로 작성)
- **네트워크 설정 패널**
  - 장치 선택 드롭다운
  - 내 IP/목적지 IP 입력
  - 설정 버튼

- **ARP 캐시 테이블**
  - IP-MAC 매핑 실시간 표시
  - JTable 컴포넌트
  - 새로고침 버튼

- **ARP 기능 버튼**
  - ARP Request 전송
  - Gratuitous ARP 전송
  - ARP 캐시 초기화

- **Proxy ARP 설정**
  - 활성화 체크박스
  - Proxy IP/MAC 입력
  - Proxy 엔트리 추가

## 📊 코드 설명

### 1. ARP 패킷 구조 (28바이트)

```
┌──────────────┬──────────────┬───────────┬──────────────┐
│Hardware Type │Protocol Type │  HW Len   │ Protocol Len │
│   (2바이트)  │   (2바이트)  │ (1바이트) │  (1바이트)   │
│    0x0001    │    0x0800    │     6     │      4       │
└──────────────┴──────────────┴───────────┴──────────────┘
┌───────────────┬──────────────┬───────────────┬──────────────┐
│  Operation    │ Sender MAC   │  Sender IP    │  Target MAC  │
│  (2바이트)    │  (6바이트)   │  (4바이트)    │  (6바이트)   │
│ 1=REQ/2=REPLY │              │               │              │
└───────────────┴──────────────┴───────────────┴──────────────┘
┌──────────────┐
│  Target IP   │
│  (4바이트)   │
└──────────────┘
```

### 2. IP 패킷 구조 (20바이트 헤더)

```
┌────────┬────────┬─────────┬──────────────┬─────────┐
│Version │  IHL   │   TOS   │Total Length  │   ID    │
│(4비트) │(4비트) │(1바이트)│  (2바이트)   │(2바이트)│
│   4    │   5    │    0    │              │         │
└────────┴────────┴─────────┴──────────────┴─────────┘
┌─────────┬─────────┬─────────┬──────────┬──────────┐
│  Flags  │  TTL    │Protocol │ Checksum │ Src IP   │
│(2바이트)│(1바이트)│(1바이트)│(2바이트) │(4바이트) │
│    0    │   128   │   253   │    0     │          │
└─────────┴─────────┴─────────┴──────────┴──────────┘
┌──────────┐
│  Dst IP  │
│(4바이트) │
└──────────┘
```

### 3. 이더넷 역다중화 플로우

```
┌─────────────────────┐
│ Ethernet Frame 수신  │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ EtherType 파싱      │
│ (바이트 12-13)      │
└──────────┬──────────┘
           │
      ┌────┴────┐
      │         │
┌─────▼────┐ ┌─▼──────┐
│ 0x0800?  │ │ 0x0806?│
│   IPv4   │ │   ARP  │
└─────┬────┘ └─┬──────┘
      │         │
┌─────▼────┐ ┌─▼──────┐
│ IPLayer  │ │ARPLayer│
└──────────┘ └────────┘
```

## 🖥️ 실행 방법

### 방법 1: 실행 스크립트 사용 (권장)

```bash
# ARP 채팅 프로그램 실행
cd /Users/pilt/project-collection/network/network_homework/second
sudo ./run_arp_chat.sh

# 기존 채팅 프로그램 실행 (레거시)
sudo ./run_basic_chat.sh
```

### 방법 2: 직접 실행

```bash
# 1. 컴파일 (최초 1회)
cd /Users/pilt/project-collection/network/network_homework/second
mkdir -p target/classes
javac --enable-preview --release 21 -d target/classes \
  -cp "lib/jnetpcap-wrapper-2.3.1-jdk21.jar" \
  src/main/java/com/demo/*.java

# 2. ARP 채팅 프로그램 실행
sudo java --enable-preview \
  -cp "target/classes:lib/jnetpcap-wrapper-2.3.1-jdk21.jar" \
  com.demo.ARPChatApp

# 3. 기존 채팅 프로그램 실행
sudo java --enable-preview \
  -cp "target/classes:lib/jnetpcap-wrapper-2.3.1-jdk21.jar" \
  com.demo.BasicChatApp
```

## 📸 실행 화면 설명

### 1. 초기 화면
- 상단: 네트워크 장치 선택, IP 주소 설정
- 중앙 좌측: 메시지 표시 영역
- 중앙 우측: ARP 캐시 테이블
- 하단: 메시지 입력 및 ARP 기능 버튼

### 2. 사용 시나리오

#### 시나리오 1: 기본 ARP 통신
```
[호스트 A - 192.168.0.100]
1. 설정 버튼 클릭
2. Gratuitous ARP 자동 전송
3. 목적지 IP 입력: 192.168.0.101
4. "ARP Request" 버튼 클릭
5. ARP 캐시 테이블에 192.168.0.101 추가 확인
6. 메시지 입력 후 전송
```

#### 시나리오 2: Proxy ARP
```
[프록시 호스트 - 192.168.0.100]
1. Proxy IP: 192.168.0.200
2. Proxy MAC: AA:BB:CC:DD:EE:FF
3. "Proxy ARP 활성화" 체크
4. "Proxy 추가" 버튼 클릭
5. 다른 호스트가 192.168.0.200에 ARP Request 전송
6. 자동으로 ARP Reply 응답
```

## 🔍 Wireshark 캡처 가이드

### 필터 설정
```
# ARP 패킷만 보기
arp

# 특정 IP의 ARP
arp.src.proto_ipv4 == 192.168.0.100 or arp.dst.proto_ipv4 == 192.168.0.100

# IP 패킷
ip.addr == 192.168.0.100
```

### 캡처 확인 항목

#### ARP Request
- Destination: Broadcast (ff:ff:ff:ff:ff:ff)
- Ethernet Type: ARP (0x0806)
- Opcode: request (1)
- Sender MAC: 자신의 MAC
- Target MAC: 00:00:00:00:00:00

#### ARP Reply
- Destination: 요청자 MAC
- Ethernet Type: ARP (0x0806)
- Opcode: reply (2)
- Sender MAC: 자신의 MAC
- Target MAC: 요청자 MAC

#### IP 패킷
- Ethernet Type: IPv4 (0x0800)
- IP Protocol: 253 (사용자 정의)
- Source/Destination IP

## 🎓 핵심 개념 정리

### 1. ARP (Address Resolution Protocol)
- **목적**: IP 주소 → MAC 주소 변환
- **동작**: Request (브로드캐스트) → Reply (유니캐스트)
- **캐싱**: 학습한 매핑 저장으로 반복 요청 방지

### 2. 이더넷 역다중화
- **목적**: 하나의 이더넷 계층에서 여러 프로토콜 지원
- **방법**: EtherType 필드로 상위 계층 구분
- **효과**: IP와 ARP를 동시에 처리 가능

### 3. 계층화 설계
- **장점**: 각 계층의 독립성, 재사용성, 유지보수성
- **패턴**: 각 계층은 BaseLayer 인터페이스 구현
- **연결**: SetUpperLayer/SetUnderLayer로 계층 간 연결

## 📝 코드 품질

### 객체지향 설계
- ✅ 인터페이스 기반 설계 (BaseLayer)
- ✅ 캡슐화 (private 필드, public 메서드)
- ✅ 단일 책임 원칙 (각 계층이 하나의 역할)
- ✅ 의존성 주입 (setArpLayer 등)

### 주석 및 문서화
- ✅ 모든 클래스에 상세한 JavaDoc
- ✅ 한글 주석으로 이해도 향상
- ✅ 패킷 구조 ASCII 아트
- ✅ 동작 과정 flowchart

### 스레드 안전성
- ✅ ConcurrentHashMap 사용 (ARP 캐시)
- ✅ volatile 키워드 (PhysicalLayer)
- ✅ SwingUtilities.invokeLater (GUI 업데이트)

## 🚀 확장 가능성

### 추가 가능한 기능
1. **ARP Spoofing 방지**
   - Static ARP 엔트리
   - ARP 변경 감지 알림

2. **ICMP 계층 추가**
   - Ping 기능
   - Echo Request/Reply

3. **TCP/UDP 계층**
   - 신뢰성 있는 전송
   - 포트 기반 다중화

4. **라우팅 테이블**
   - 다중 네트워크 지원
   - 게이트웨이 설정

## 📚 참고 문서

- `ARP_README.md`: 전체 프로젝트 문서
- `src/main/java/com/demo/`: 소스 코드 (상세 주석)
- `run_arp_chat.sh`: 실행 스크립트

## ✅ 체크리스트

- [x] ARPLayer 구현 (Request, Reply, Gratuitous, Proxy)
- [x] IPLayer 구현 (패킷 생성, 파싱, ARP 연동)
- [x] EthernetLayer 수정 (역다중화)
- [x] ARPChatApp GUI 구현
- [x] 계층 연결 구조 완성
- [x] 코드 주석 (한글)
- [x] 객체지향 설계
- [x] 컴파일 성공
- [x] 실행 스크립트 작성
- [x] README 작성

## 🎉 구현 완료!

모든 요구사항이 성공적으로 구현되었습니다.
