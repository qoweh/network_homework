# 컴퓨터 네트워크 과제 모음

> 네트워크 프로그래밍 실습 프로젝트

## 📚 프로젝트 구조

```
network_homework/
├── second/          # 기본 채팅 프로그램 (레거시)
│   └── README 없음 (더 이상 사용하지 않음)
│
└── third/           # ✨ ARP 기능 채팅 프로그램 (최신)
    ├── ARP_README.md              → 상세 사용 설명서
    └── IMPLEMENTATION_SUMMARY.md  → 구현 내용 요약
```

## 🎯 최신 프로젝트: Third (ARP 채팅 프로그램)

### 개요
ARP(Address Resolution Protocol) 프로토콜을 완전히 구현한 패킷 기반 채팅 애플리케이션입니다.

### 주요 기능
- ✅ **ARP Request/Reply**: IP 주소를 MAC 주소로 변환
- ✅ **ARP 캐시 테이블**: IP-MAC 매핑 자동 관리
- ✅ **Gratuitous ARP**: 자신의 IP를 네트워크에 알림
- ✅ **Proxy ARP**: 다른 호스트 대신 ARP 응답
- ✅ **이더넷 역다중화**: EtherType 기반 프로토콜 선택
- ✅ **IP 통신**: IPv4 패킷 생성 및 파싱

### 계층 구조
```
ChatAppLayer (L7) - 메시지 인코딩/디코딩
    ↓
IPLayer (L3) - IP 패킷 생성/파싱
    ↓
EthernetLayer (L2) ←→ ARPLayer (L2)
    ↓
PhysicalLayer (L1) - 패킷 송수신 (jNetPcap)
```

### 📖 상세 문서

👉 **[third/ARP_README.md](./third/ARP_README.md)** - 전체 프로젝트 사용 가이드
- 설치 및 실행 방법
- 사용법 및 기능 설명
- Wireshark 캡처 가이드
- 트러블슈팅

👉 **[third/IMPLEMENTATION_SUMMARY.md](./third/IMPLEMENTATION_SUMMARY.md)** - 구현 내용 정리
- 코드 설명 및 구조
- ARP/IP 프로토콜 구현 세부사항
- 테스트 시나리오
- 핵심 개념 정리

## 🚀 빠른 시작

### 요구사항
- Java 21 이상
- jNetPcap 라이브러리
- macOS/Linux (관리자 권한 필요)

### 실행 방법

```bash
# 프로젝트 디렉토리로 이동
cd third

# 실행 스크립트 사용 (권장)
sudo ./run_arp_chat.sh

# 또는 직접 실행
sudo java --enable-preview \
  -cp "target/classes:lib/jnetpcap-wrapper-2.3.1-jdk21.jar" \
  com.demo.ARPChatApp
```

## 📁 파일 구조

### Third 프로젝트
```
third/
├── src/main/java/com/demo/
│   ├── ARPLayer.java       # ARP 프로토콜 구현
│   ├── IPLayer.java        # IP 계층 구현
│   ├── EthernetLayer.java  # 이더넷 계층 (역다중화)
│   ├── PhysicalLayer.java  # 물리 계층 (jNetPcap)
│   ├── ChatAppLayer.java   # 애플리케이션 계층
│   ├── BaseLayer.java      # 계층 인터페이스
│   ├── ARPChatApp.java     # ARP 기능 GUI
│   └── BasicChatApp.java   # 기본 채팅 GUI (레거시)
│
├── lib/                    # jNetPcap 라이브러리
├── run_arp_chat.sh        # ARP 채팅 실행 스크립트
├── run_basic_chat.sh      # 기본 채팅 실행 스크립트
├── ARP_README.md          # 상세 문서
└── IMPLEMENTATION_SUMMARY.md  # 구현 요약
```

## 🔍 주요 구현 내용

### 1. ARP 프로토콜
- ARP Request/Reply 메시지 생성 및 파싱
- ARP 캐시 테이블 (ConcurrentHashMap)
- Gratuitous ARP로 네트워크 진입 알림
- Proxy ARP로 다른 호스트 대신 응답

### 2. IP 계층
- IPv4 헤더 20바이트 생성
- IP 패킷 파싱 및 필터링
- ARP 연동하여 MAC 주소 자동 해석

### 3. 이더넷 역다중화
- EtherType 0x0800 → IPLayer
- EtherType 0x0806 → ARPLayer
- 여러 프로토콜 동시 지원

## 📊 실습 내용

### ARP 통신 흐름
```
[호스트 A]                    [호스트 B]
   │                             │
   │──── ARP Request ───────────>│ (브로드캐스트)
   │     "192.168.0.101의 MAC은?" │
   │                             │
   │<─── ARP Reply ──────────────│ (유니캐스트)
   │     "AA:BB:CC:DD:EE:FF입니다"│
   │                             │
   │──── IP Packet ─────────────>│ (메시지 전송)
```

## 🎓 학습 목표

- [x] 계층화된 네트워크 프로토콜 스택 이해
- [x] ARP 프로토콜 동작 원리 학습
- [x] 이더넷 프레임 구조 및 역다중화
- [x] IP 패킷 구조 및 라우팅
- [x] 패킷 캡처 및 분석 (jNetPcap)
- [x] 객체지향 설계 패턴 적용

## 🛠️ 기술 스택

- **언어**: Java 21
- **라이브러리**: jNetPcap 2.3.1
- **빌드**: Maven (선택)
- **GUI**: Swing
- **프로토콜**: ARP, IPv4, Ethernet

## ⚠️ 주의사항

- 관리자(sudo) 권한 필요 (패킷 캡처)
- 같은 네트워크 내에서만 통신 가능
- 방화벽 설정 확인 필요

## 📝 참고 자료

- RFC 826: ARP (Address Resolution Protocol)
- RFC 791: IP (Internet Protocol)
- IEEE 802.3: Ethernet
- jNetPcap Documentation

## 👨‍💻 개발 정보

- **과목**: 컴퓨터 네트워크
- **환경**: macOS/Linux
- **테스트**: Wireshark 패킷 분석

---

**더 자세한 내용은 [third/ARP_README.md](./third/ARP_README.md)를 참조하세요.**
