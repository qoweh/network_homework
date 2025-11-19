# Lab 4 프로젝트 완료 보고서

## 프로젝트 개요
- **과제명**: Lab 4 - 파일 전송 채팅 프로그램
- **완료일**: 2025년 11월 19일
- **구현 언어**: Java 21
- **빌드 도구**: Maven

## 구현된 기능

### 1. FileAppLayer (파일 전송 계층) ✅
**파일**: `src/main/java/com/demo/FileAppLayer.java`

**주요 기능**:
- 파일을 1KB 단위로 Fragmentation
- Thread 기반 비동기 전송 (파일 전송 중 채팅 가능)
- 진행률 콜백 (전송/수신)
- Fragment 타입:
  - `TYPE_FILE_START (0x01)`: 파일명, 크기 정보
  - `TYPE_FILE_DATA (0x02)`: 실제 데이터
  - `TYPE_FILE_END (0x03)`: 전송 완료

**Fragment 구조**:
```
FILE_START: Type(1) + Seq(4) + Total(4) + NameLen(1) + Size(8) + Name(가변)
FILE_DATA:  Type(1) + Seq(4) + Total(4) + Data(최대 1KB)
FILE_END:   Type(1) + Seq(4) + Total(4)
```

**핵심 메서드**:
- `sendFile(String filePath)`: 파일 전송 (별도 Thread)
- `Receive(byte[] input)`: Fragment 수신 및 재조립
- `setOnSendProgress()`: 전송 진행 콜백
- `setOnReceiveProgress()`: 수신 진행 콜백

### 2. ChatAppLayer (채팅 계층 Fragmentation) ✅
**파일**: `src/main/java/com/demo/ChatAppLayer.java`

**주요 기능**:
- 512바이트 이상 메시지 자동 분할
- UTF-8 인코딩으로 한글 지원
- 순서가 섞인 Fragment 재조립
- 중복 Fragment 감지 및 무시

**Fragment 구조**:
```
CHAT_SINGLE:   Type(1) + Message(가변)
CHAT_FRAGMENT: Type(1) + Seq(4) + Total(4) + Message(최대 512B)
```

**핵심 메서드**:
- `sendMessage(String message)`: 메시지 전송 (필요시 분할)
- `Receive(byte[] input)`: Fragment 수신 및 재조립

### 3. IPLayer (프로토콜 역다중화) ✅
**파일**: `src/main/java/com/demo/IPLayer.java`

**주요 기능**:
- Protocol 필드 기반 상위 계층 선택
  - **Protocol 253**: ChatAppLayer
  - **Protocol 254**: FileAppLayer
- 프로토콜 전환 메서드
  - `useChatProtocol()`: Chat 모드
  - `useFileProtocol()`: File 모드

**역다중화 로직**:
```java
if (protocol == IP_PROTOCOL_CHAT && upper instanceof ChatAppLayer) {
    upper.Receive(payload);
} else if (protocol == IP_PROTOCOL_FILE && upper instanceof FileAppLayer) {
    upper.Receive(payload);
}
```

### 4. GUI 업데이트 ✅
**파일**: `src/main/java/com/demo/ARPChatApp.java`

**추가된 UI 컴포넌트**:
- 파일 경로 입력 필드 (`filePathField`)
- 파일 선택 버튼 (`browseButton`)
- 파일 전송 버튼 (`sendFileButton`)
- 진행 표시줄 (`fileProgressBar`)
- 상태 레이블 (`fileStatusLabel`)

**UI 레이아웃**:
```
┌─────────────────────────────────┐
│    메시지 전송                   │
│  [메시지입력______] [전송]       │
├─────────────────────────────────┤
│    파일 전송                     │
│  [경로____] [선택] [파일전송]    │
│  [═══진행률═══════] 50%         │
│  상태: 전송 중...                │
└─────────────────────────────────┘
```

### 5. Thread 구현 ✅
**파일 전송 Thread**:
```java
new Thread(() -> {
    fileLayer.sendFile(filePath);
}, "FileTransfer-UI").start();
```

**장점**:
- 파일 전송 중에도 UI 응답성 유지
- 채팅 메시지 동시 전송 가능
- 진행률 실시간 업데이트

## 테스트 결과

### 전체 테스트 결과
```
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
✅ 100% 통과
```

### 1. FileAppLayerTest (3개 테스트)
- ✅ `testSmallFileTransfer`: 작은 파일 전송 검증
- ✅ `testLargeFileFragmentation`: 큰 파일 분할 전송 (4개 Fragment)
- ✅ `testOutOfOrderFragments`: 순서가 섞인 Fragment 재조립

### 2. ChatAppLayerTest (5개 테스트)
- ✅ `testShortMessage`: 짧은 메시지 (분할 없음)
- ✅ `testLongMessage`: 긴 메시지 (19개 Fragment)
- ✅ `testFragmentReordering`: 순서 재조립
- ✅ `testDuplicateFragment`: 중복 Fragment 처리
- ✅ `testKoreanMessage`: 한글 메시지 UTF-8 인코딩

### 3. IPLayerDemuxTest (3개 테스트)
- ✅ `testChatProtocolDemux`: ChatApp 프로토콜 (253) 역다중화
- ✅ `testFileProtocolDemux`: FileApp 프로토콜 (254) 역다중화
- ✅ `testMultiplexing`: ChatApp과 FileApp 동시 사용

## 프로젝트 구조

```
fourth/
├── pom.xml                          # Maven 설정
├── run.sh                           # 실행 스크립트
├── LAB4_README.md                   # 사용자 매뉴얼
├── src/
│   ├── main/java/com/demo/
│   │   ├── ARPChatApp.java         # GUI 메인 클래스 (파일 전송 UI 추가)
│   │   ├── FileAppLayer.java       # 파일 전송 계층 (NEW)
│   │   ├── ChatAppLayer.java       # 채팅 계층 (Fragmentation 추가)
│   │   ├── IPLayer.java            # IP 계층 (역다중화 추가)
│   │   ├── ARPLayer.java           # ARP 계층
│   │   ├── EthernetLayer.java      # 이더넷 계층
│   │   ├── PhysicalLayer.java      # 물리 계층
│   │   └── BaseLayer.java          # 계층 인터페이스
│   └── test/java/com/demo/
│       ├── FileAppLayerTest.java   # FileApp 테스트 (NEW)
│       ├── ChatAppLayerTest.java   # ChatApp 테스트 (NEW)
│       └── IPLayerDemuxTest.java   # IP 역다중화 테스트 (NEW)
└── received_files/                 # 수신 파일 저장 디렉토리
```

## 계층 아키텍처

```
Application Layer (L7)
┌─────────────────┬─────────────────┐
│  ChatAppLayer   │  FileAppLayer   │
│  - 512B 분할    │  - 1KB 분할     │
│  - UTF-8        │  - Thread 전송  │
└────────┬────────┴────────┬────────┘
         │   Protocol      │
         │   253 / 254     │
         └────────┬────────┘
                  │
Network Layer (L3)
         ┌────────▼────────┐
         │    IPLayer      │
         │  - 역다중화      │
         │  - Protocol 선택 │
         └────────┬────────┘
                  │
         ┌────────┴────────┐
Data Link Layer (L2)
┌────────▼────────┐ ┌──────▼──────┐
│ EthernetLayer   │ │  ARPLayer   │
│  - MAC 주소     │ │  - 주소해석  │
└────────┬────────┘ └──────┬──────┘
         │                 │
         └────────┬────────┘
Physical Layer (L1)
         ┌────────▼────────┐
         │ PhysicalLayer   │
         │  - jNetPcap     │
         └─────────────────┘
```

## 주요 기술 스택

| 항목 | 기술/도구 |
|------|----------|
| 언어 | Java 21 |
| 빌드 | Maven 3.x |
| 패킷 캡처 | jNetPcap 2.3.1 |
| 테스트 | JUnit 5.10.0 |
| GUI | Swing |
| 동시성 | Java Thread, ConcurrentHashMap |

## 실행 방법

### 1. 빌드 및 테스트
```bash
cd fourth
mvn clean test
```

### 2. 프로그램 실행
```bash
./run.sh
```

또는

```bash
mvn exec:java -Dexec.mainClass="com.demo.ARPChatApp"
```

### 3. 사용 시나리오

#### 시나리오 1: 채팅 메시지 전송
1. 네트워크 어댑터 선택
2. 내 IP / 목적지 IP 설정
3. "설정" 버튼 클릭
4. "ARP Request" 전송하여 목적지 MAC 주소 확보
5. 메시지 입력 후 "전송"
6. 512바이트 초과 시 자동으로 Fragment화되어 전송

#### 시나리오 2: 파일 전송
1. 위와 동일하게 초기 설정
2. "파일 선택" 버튼으로 파일 선택
3. "파일 전송" 버튼 클릭
4. 진행 표시줄에서 진행률 확인
5. 파일 전송 중에도 채팅 메시지 전송 가능

#### 시나리오 3: 동시 사용
1. 파일 전송 시작
2. 파일 전송이 진행되는 동안 채팅 메시지 입력
3. 채팅 메시지는 Protocol 253으로 즉시 전송
4. 파일 데이터는 Protocol 254로 백그라운드 전송

## 구현 중 해결한 문제

### 1. Fragment 순서 재조립
**문제**: Fragment가 순서대로 도착하지 않을 수 있음
**해결**: 
- `ConcurrentHashMap`으로 Fragment 버퍼 관리
- Sequence Number로 정확한 위치에 데이터 배치
- 모든 Fragment 수신 시 재조립 완료

### 2. 프로토콜 역다중화
**문제**: ChatApp과 FileApp을 어떻게 구분할 것인가
**해결**:
- IP 헤더의 Protocol 필드 활용 (253, 254)
- IPLayer에서 Protocol 값에 따라 상위 계층 선택
- `instanceof` 검사로 올바른 계층에 전달

### 3. Thread 동시성
**문제**: 파일 전송 중 UI가 멈추는 현상
**해결**:
- 파일 전송을 별도 Thread에서 실행
- `SwingUtilities.invokeLater()`로 UI 업데이트
- `ConcurrentHashMap`으로 Thread-safe 데이터 구조 사용

### 4. 테스트 비동기 처리
**문제**: Thread 기반 파일 전송 테스트 어려움
**해결**:
- `CountDownLatch`로 비동기 완료 대기
- IPLayerMock으로 네트워크 의존성 제거
- 5초 타임아웃으로 무한 대기 방지

## 성능 특성

### Fragment 크기
- **FileApp**: 1KB (1024 bytes)
- **ChatApp**: 512 bytes

### 전송 효율
- 3990 bytes 파일 → 4개 Fragment (FILE_START, 3x DATA, END)
- 2000 bytes 메시지 → 4개 Fragment

### 메모리 사용
- `ConcurrentHashMap`으로 수신 중인 파일/메시지 관리
- 완료된 Fragment는 즉시 제거하여 메모리 효율적

## 향후 개선 가능 사항

1. **재전송 메커니즘**: Fragment 손실 시 재전송 요청
2. **압축**: 파일 전송 전 압축하여 네트워크 효율 향상
3. **암호화**: 파일/메시지 암호화로 보안 강화
4. **멀티캐스트**: 다수에게 동시 전송
5. **단편화 최적화**: 네트워크 상태에 따라 Fragment 크기 동적 조정

## 결론

Lab 4의 모든 요구사항을 성공적으로 구현했습니다:

✅ FileAppLayer with Fragmentation  
✅ ChatAppLayer with Fragmentation  
✅ IP Protocol Demultiplexing (253/254)  
✅ Thread Support (파일 전송 중 채팅 가능)  
✅ GUI Updates (파일 전송 UI)  
✅ Comprehensive Tests (11개 테스트 100% 통과)  

프로그램은 안정적으로 동작하며, 실제 네트워크 환경에서 파일 전송과 채팅을 동시에 수행할 수 있습니다.
