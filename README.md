# 컴퓨터 네트워크 과제 모음

> 네트워크 프로그래밍 실습 프로젝트

## 📚 프로젝트 구조

```
network_homework/
├── second/          # 기본 채팅 프로그램 (레거시)
│   └── README 없음 (더 이상 사용하지 않음)
│
├── third/           # ARP 기능 채팅 프로그램
│   ├── ARP_README.md              → 상세 사용 설명서
│   └── IMPLEMENTATION_SUMMARY.md  → 구현 내용 요약
│
└── fourth/          # ✨ 파일 전송 채팅 프로그램 (최신)
    ├── README.md                  → 빠른 시작 가이드
    ├── LAB4_README.md            → 상세 사용 설명서
    └── PROJECT_SUMMARY.md        → 프로젝트 완료 보고서
```

## 🎯 최신 프로젝트: Fourth (파일 전송 채팅 프로그램)

### 개요
IP 프로토콜 역다중화를 이용한 파일 전송 및 채팅 애플리케이션입니다.

### 주요 기능
- ✅ **파일 전송**: 1KB Fragmentation, Thread 기반, 진행률 표시
- ✅ **채팅 메시지**: 512B Fragmentation, UTF-8 한글 지원
- ✅ **IP 프로토콜 역다중화**: Protocol 253 (Chat), 254 (File)
- ✅ **ARP 기능**: Request/Reply, 캐시 관리, GARP, Proxy ARP
- ✅ **순서 재조립**: Out-of-order Fragment 재조립

### 계층 구조
```
ChatAppLayer (253) / FileAppLayer (254) - L7
    ↓
IPLayer - L3 (프로토콜 역다중화)
    ↓
EthernetLayer / ARPLayer - L2
    ↓
PhysicalLayer (jNetPcap) - L1
```

### 📖 상세 문서

👉 **[fourth/README.md](./fourth/README.md)** - 빠른 시작 및 사용 가이드
- 실행 방법
- 기능 설명
- 사용법
- 문제 해결

👉 **[fourth/LAB4_README.md](./fourth/LAB4_README.md)** - 상세 기술 문서
- Fragment 프로토콜 구조
- 계층별 구현 설명
- 테스트 결과
- 요구사항 충족 내역

👉 **[fourth/PROJECT_SUMMARY.md](./fourth/PROJECT_SUMMARY.md)** - 프로젝트 완료 보고서
- 구현된 기능 상세
- 테스트 결과 분석
- 프로젝트 구조
- 기술 스택

## 🚀 빠른 시작

### 요구사항
- Java 21 이상
- jNetPcap 라이브러리
- macOS/Linux (관리자 권한 필요)

### 실행 방법

```bash
# 최신 프로젝트 (Fourth) 실행
cd fourth
sudo ./run.sh

# 또는 Maven으로 실행
mvn clean compile exec:java -Dexec.mainClass="com.demo.ARPChatApp"
```

## 📁 Fourth 프로젝트 구조

```
fourth/
├── src/main/java/com/demo/
│   ├── ARPChatApp.java     # GUI 메인 클래스 (파일 전송 UI)
│   ├── FileAppLayer.java   # 파일 전송 계층 (1KB 분할)
│   ├── ChatAppLayer.java   # 채팅 계층 (512B 분할)
│   ├── IPLayer.java        # IP 계층 (역다중화)
│   ├── ARPLayer.java       # ARP 프로토콜
│   ├── EthernetLayer.java  # 이더넷 계층
│   ├── PhysicalLayer.java  # 물리 계층 (jNetPcap)
│   └── BaseLayer.java      # 계층 인터페이스
│
├── src/test/java/com/demo/
│   ├── FileAppLayerTest.java     # 파일 전송 테스트
│   ├── ChatAppLayerTest.java     # 채팅 테스트
│   └── IPLayerDemuxTest.java     # 역다중화 테스트
│
├── lib/                    # jNetPcap 라이브러리
├── run.sh                  # 실행 스크립트
├── README.md               # 빠른 시작
├── LAB4_README.md         # 상세 문서
└── PROJECT_SUMMARY.md     # 완료 보고서
```

## 🔍 주요 구현 내용

### 1. FileAppLayer (파일 전송)
- 1KB 단위 Fragmentation
- Thread 기반 비동기 전송
- 진행률 콜백 및 GUI 업데이트
- Fragment 타입: FILE_START, FILE_DATA, FILE_END

### 2. ChatAppLayer (채팅)
- 512바이트 단위 Fragmentation
- UTF-8 한글 지원
- Out-of-order Fragment 재조립
- 중복 Fragment 필터링

### 3. IPLayer (역다중화)
- Protocol 253 → ChatAppLayer
- Protocol 254 → FileAppLayer
- 프로토콜 전환 메서드 제공

### 4. GUI 업데이트
- 파일 선택 버튼
- 파일 전송 버튼
- 진행 표시줄
- 실시간 상태 업데이트

## 🧪 테스트 결과

```bash
mvn test

Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
✅ 100% 통과
```

### 테스트 항목
- ✅ **FileAppLayerTest** (3개): 파일 전송, Fragmentation, 순서 재조립
- ✅ **ChatAppLayerTest** (5개): 메시지 분할, 재조립, 중복 처리, 한글
- ✅ **IPLayerDemuxTest** (3개): 프로토콜 역다중화, 동시 사용

## 🎓 프로젝트 진행 이력

### Second (기본 채팅)
- 기본적인 채팅 프로그램
- Physical, Ethernet 계층 구현
- 레거시 코드 (더 이상 사용하지 않음)

### Third (ARP 채팅)
- ARP 프로토콜 완전 구현
- ARP Request/Reply, 캐시 관리
- Gratuitous ARP, Proxy ARP
- IP 계층 추가

### Fourth (파일 전송) ⭐ 최신
- 파일 전송 기능 추가
- IP 프로토콜 역다중화
- ChatAppLayer Fragmentation
- FileAppLayer 구현
- Thread 기반 비동기 처리
- 11개 단위 테스트 작성

## 🛠️ 기술 스택

- **언어**: Java 21
- **라이브러리**: jNetPcap 2.3.1
- **빌드**: Maven 3.x
- **테스트**: JUnit 5.10.0
- **GUI**: Swing
- **프로토콜**: ARP, IPv4, Ethernet

## ⚠️ 주의사항

- 관리자(sudo) 권한 필요 (패킷 캡처)
- 같은 네트워크 내에서만 통신 가능
- 올바른 네트워크 어댑터 선택 필수
- 방화벽 설정 확인 필요

## 📝 참고 자료

- RFC 826: ARP (Address Resolution Protocol)
- RFC 791: IP (Internet Protocol)
- IEEE 802.3: Ethernet
- jNetPcap Documentation

## 👨‍💻 개발 정보

- **과목**: 컴퓨터 네트워크
- **환경**: macOS/Linux
- **테스트**: JUnit, Wireshark

---

**더 자세한 내용은 [fourth/README.md](./fourth/README.md)를 참조하세요.**
