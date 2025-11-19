# Lab 4: 파일 전송 채팅 프로그램

## 개요
IP 프로토콜 역다중화를 이용한 파일 전송 및 채팅 애플리케이션입니다.

## 주요 기능

### 1. 파일 전송 (FileAppLayer)
- **Fragmentation**: 큰 파일을 1KB 단위로 분할 전송
- **Thread 기반**: 파일 전송 중에도 채팅 가능
- **진행률 표시**: GUI에서 실시간 전송/수신 진행률 확인
- **Fragment 타입**:
  - `FILE_START (0x01)`: 파일명, 크기 정보
  - `FILE_DATA (0x02)`: 파일 데이터
  - `FILE_END (0x03)`: 전송 완료

### 2. 채팅 메시지 (ChatAppLayer)
- **Fragmentation 지원**: 512바이트 이상 메시지 자동 분할
- **UTF-8 인코딩**: 한글 메시지 지원
- **순서 재조립**: 순서가 바뀐 Fragment도 정확히 재조립

### 3. IP 프로토콜 역다중화
- **Protocol 253**: ChatAppLayer로 전달
- **Protocol 254**: FileAppLayer로 전달
- IP 계층에서 프로토콜 필드 기반으로 상위 계층 선택

### 4. ARP 기능
- ARP Request/Reply 처리
- ARP 캐시 테이블 관리
- Gratuitous ARP
- Proxy ARP

## 계층 구조

```
┌─────────────────┬─────────────────┐
│  ChatAppLayer   │  FileAppLayer   │  Application (L7)
│  (Protocol 253) │  (Protocol 254) │
└────────┬────────┴────────┬────────┘
         │                 │
         └────────┬────────┘
                  │
         ┌────────▼────────┐
         │    IPLayer      │  Network (L3)
         │  - 역다중화      │
         └────────┬────────┘
                  │
      ┌───────────┴───────────┐
      │                       │
┌─────▼─────┐         ┌───────▼──────┐
│EthernetLyr│         │   ARPLayer   │  Data Link (L2)
└─────┬─────┘         └───────┬──────┘
      │                       │
      └───────────┬───────────┘
                  │
         ┌────────▼────────┐
         │ PhysicalLayer   │  Physical (L1)
         └─────────────────┘
```

## 테스트 결과

```bash
$ mvn test

[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
```

### 테스트 항목
- ✅ **FileAppLayerTest** (3개):
  - 작은 파일 전송
  - 큰 파일 Fragmentation
  - 순서가 섞인 Fragment 재조립

- ✅ **ChatAppLayerTest** (5개):
  - 짧은 메시지 (Fragmentation 없음)
  - 긴 메시지 (Fragmentation)
  - Fragment 순서 재조립
  - 중복 Fragment 처리
  - 한글 메시지 UTF-8 인코딩

- ✅ **IPLayerDemuxTest** (3개):
  - ChatApp 프로토콜 역다중화 (253)
  - FileApp 프로토콜 역다중화 (254)
  - ChatApp과 FileApp 동시 사용

## 실행 방법

### 1. 프로그램 실행
```bash
./run.sh
```

또는

```bash
mvn exec:java -Dexec.mainClass="com.demo.ARPChatApp"
```

### 2. 사용 방법

#### 초기 설정
1. **네트워크 어댑터 선택**: 드롭다운에서 사용할 네트워크 인터페이스 선택
2. **IP 주소 설정**:
   - 내 IP: 본인 컴퓨터의 IP 주소
   - 목적지 IP: 통신할 상대방의 IP 주소
3. **설정 버튼 클릭**: 네트워크 계층 초기화

#### 채팅 메시지 전송
1. 하단 "메시지 전송" 패널에 메시지 입력
2. "전송" 버튼 클릭 또는 Enter 키
3. 512바이트 이상 메시지는 자동으로 Fragment화됨

#### 파일 전송
1. "파일 선택" 버튼 클릭하여 전송할 파일 선택
2. "파일 전송" 버튼 클릭
3. 진행 표시줄에서 전송 진행률 확인
4. 파일 전송 중에도 채팅 메시지 전송 가능

#### ARP 기능
- **ARP Request**: 목적지 MAC 주소 요청
- **Gratuitous ARP**: 네트워크 진입 알림
- **캐시 초기화**: ARP 캐시 테이블 비우기
- **Proxy ARP**: Proxy IP/MAC 설정 후 추가

## Fragment 프로토콜

### FileApp Fragment 구조
```
┌──────┬──────┬──────┬──────┬──────┬──────┐
│ Type │ Seq  │Total │FName │FSize │ Data │
│ (1B) │ (4B) │ (4B) │ Len  │ (8B) │(var) │
│      │      │      │ (1B) │      │      │
└──────┴──────┴──────┴──────┴──────┴──────┘
```

### ChatApp Fragment 구조
```
┌──────┬──────┬──────┬──────┐
│ Type │ Seq  │Total │ Msg  │
│ (1B) │ (4B) │ (4B) │(var) │
└──────┴──────┴──────┴──────┘
```

## 파일 수신
- 수신된 파일은 `received_files/` 디렉토리에 자동 저장됨
- 파일명 충돌 시 자동으로 덮어쓰기

## 요구사항 충족

### 1. FileAppLayer with Fragmentation ✅
- 1KB 단위로 파일 분할
- FILE_START, FILE_DATA, FILE_END 프로토콜

### 2. ChatAppLayer with Fragmentation ✅
- 512바이트 단위로 메시지 분할
- CHAT_SINGLE, CHAT_FRAGMENT 타입

### 3. IP Layer Demultiplexing ✅
- Protocol 253 → ChatAppLayer
- Protocol 254 → FileAppLayer

### 4. Thread Support ✅
- FileAppLayer.sendFile()은 별도 Thread에서 실행
- 파일 전송 중 채팅 가능

### 5. GUI Updates ✅
- 파일 선택 버튼
- 파일 전송 버튼
- 진행 표시줄
- 상태 레이블

## 개발 환경
- Java 21
- Maven 3.x
- jNetPcap 2.3.1
- JUnit 5.10.0

## 주의사항
- 관리자 권한 필요 (패킷 캡처)
- macOS: `sudo ./run.sh`
- Windows: 관리자 권한으로 실행
- 방화벽 설정 확인 필요

## 문제 해결

### "네트워크 연결 실패"
- 관리자 권한으로 실행
- 올바른 네트워크 어댑터 선택 확인

### "메시지 전송 실패 (ARP 캐시 확인 필요)"
- "ARP Request" 버튼 클릭
- ARP 캐시 테이블에 목적지 IP 등록 확인

### 파일 전송이 진행되지 않음
- 목적지 IP 주소 확인
- ARP 캐시에 목적지 MAC 주소 있는지 확인
- 상대방도 같은 프로그램 실행 중인지 확인
