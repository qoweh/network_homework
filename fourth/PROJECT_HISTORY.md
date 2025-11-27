# 프로젝트 작업 히스토리 및 요약

## 📚 프로젝트 개요

**프로젝트명**: 네트워크 프로그래밍 실습 - 파일 전송 채팅 프로그램
**개발 환경**: 
- macOS (개발 및 테스트)
- Ubuntu VM (테스트)
- Java 21
- Maven
- jNetPcap 2.3.1

---

## 🗂️ 프로젝트 진행 단계

### Lab 2 (second/) - 기본 채팅 프로그램
**목표**: 네트워크 기초 이해 및 계층 구조 학습

**구현 내용**:
- Physical Layer (jNetPcap 연동)
- Ethernet Layer (MAC 주소 기반 전송)
- ChatApp Layer (간단한 메시지 송수신)

**결과**: 레거시 코드로 더 이상 사용하지 않음

---

### Lab 3 (third/) - ARP 프로토콜 추가
**목표**: 주소 해석 프로토콜 구현

**구현 내용**:
1. **ARPLayer 구현**
   - ARP Request/Reply 처리
   - ARP 캐시 테이블 관리
   - Gratuitous ARP (네트워크 진입 알림)
   - Proxy ARP (대리 응답)

2. **IPLayer 추가**
   - IPv4 헤더 생성 및 파싱
   - IP 주소 기반 라우팅

3. **EthernetLayer 개선**
   - EtherType 기반 역다중화
   - 0x0800 (IP) → IPLayer
   - 0x0806 (ARP) → ARPLayer

**계층 구조**:
```
ChatAppLayer (L7)
    ↓
IPLayer (L3) ←→ ARPLayer (L2)
    ↓           ↓
EthernetLayer (L2)
    ↓
PhysicalLayer (L1)
```

**결과**: ARP 기능 완전 구현, IP 통신 가능

---

### Lab 4 (fourth/) - 파일 전송 프로그램 (현재)
**목표**: 파일 전송 및 프로토콜 역다중화

**구현 내용**:

#### 1. FileAppLayer 추가
- 파일을 1KB 단위로 Fragmentation
- Thread 기반 비동기 전송
- 진행률 실시간 표시
- Fragment 재조립

**Fragment 타입**:
- 0x01: FILE_START (파일 정보)
- 0x02: FILE_DATA (데이터)
- 0x03: FILE_END (완료)

#### 2. IP 프로토콜 역다중화
- Protocol 253 → ChatAppLayer
- Protocol 254 → FileAppLayer
- 프로토콜 필드 기반 상위 계층 선택

#### 3. ChatAppLayer Fragmentation
- 512바이트 초과 메시지 자동 분할
- UTF-8 한글 지원
- Out-of-order Fragment 재조립

#### 4. GUI 개선
- 파일 선택 버튼
- 파일 전송 버튼
- 진행 표시줄
- 실시간 상태 표시

**최종 계층 구조**:
```
┌─────────────────┬─────────────────┐
│  ChatAppLayer   │  FileAppLayer   │  L7
│  (Protocol 253) │  (Protocol 254) │
└────────┬────────┴────────┬────────┘
         │                 │
         └────────┬────────┘
                  │
         ┌────────▼────────┐
         │    IPLayer      │  L3 (역다중화)
         └────────┬────────┘
                  │
      ┌───────────┴───────────┐
      │                       │
┌─────▼─────┐         ┌───────▼──────┐
│EthernetLyr│         │   ARPLayer   │  L2
└─────┬─────┘         └───────┬──────┘
      │                       │
      └───────────┬───────────┘
                  │
         ┌────────▼────────┐
         │ PhysicalLayer   │  L1
         └─────────────────┘
```

---

## 🧪 테스트 환경 및 결과

### 테스트 구성

**1. macOS (호스트)**
- IP: 192.168.64.1
- 네트워크 인터페이스: bridge100
- MAC 주소: 자동 로드 성공
- 역할: 주요 개발 및 테스트 환경

**2. Ubuntu VM (게스트)**
- IP: 192.168.64.7
- 네트워크 인터페이스: enp0s1
- MAC 주소: 수동 입력 필요 (52:54:00:12:34:56)
- 역할: 크로스 플랫폼 테스트

### VM 테스트를 위한 작업

#### 문제 1: MAC 주소 자동 로드 실패
**원인**: VM 환경에서 NetworkInterface가 MAC 주소를 제대로 못 가져옴

**해결**:
```java
// loadMacAddress() 메서드 개선
private static void loadMacAddress() {
    // 기존 MAC 주소 초기화
    Arrays.fill(myMacAddress, (byte) 0);
    
    // NetworkInterface 시도
    NetworkInterface ni = NetworkInterface.getByName(selectedDevice.name());
    if (ni != null && ni.isUp()) {
        byte[] mac = ni.getHardwareAddress();
        if (mac != null) {
            // 성공
        }
    }
    
    // 실패 시 명확한 가이드 제공
    System.err.println("해결 방법:");
    System.err.println("  1) 터미널: ifconfig " + deviceName);
    System.err.println("  2) 'ether' 다음의 MAC 주소 복사");
    System.err.println("  3) GUI에 수동 입력");
}
```

#### 문제 2: 설정 버튼 클릭 시 오류
**원인**: MAC 주소가 00:00:00:00:00:00으로 설정됨

**해결**:
```java
// handleSetup() 메서드에 검증 추가
private static void handleSetup() {
    // MAC 주소 검증
    if (macStr.equals("00:00:00:00:00:00")) {
        JOptionPane.showMessageDialog(null, 
            "MAC 주소가 설정되지 않았습니다!\n\n" +
            "해결 방법:\n" +
            "1. 터미널에서 'ifconfig' 실행\n" +
            "2. MAC 주소 확인 후 입력",
            "MAC 주소 필요", 
            JOptionPane.WARNING_MESSAGE);
        return;
    }
    // ...
}
```

#### 문제 3: IP 주소 기본값
**원인**: 기본값이 192.168.0.x였음

**해결**: 
- 기본값을 192.168.64.7 / 192.168.64.1로 변경
- VM과 macOS 환경에 맞게 수정

### 테스트 시나리오

#### 1. ARP 통신 테스트
```
[macOS] ARP Request 버튼 클릭
  → [VM] ARP Reply 자동 응답
  → [macOS] ARP 캐시에 VM IP/MAC 등록
```

#### 2. 채팅 메시지 테스트
```
[macOS] "안녕하세요" 입력 → 전송
  → [VM] 한글 메시지 수신 성공 (UTF-8)
  
[VM] 긴 메시지 (600바이트) 전송
  → Fragment 2개로 분할
  → [macOS] 재조립 성공
```

#### 3. 파일 전송 테스트
```
[VM] test.pdf (2MB) 선택 → 전송
  → 1KB씩 2048개 Fragment로 분할
  → 진행률 실시간 표시
  → [macOS] received_files/test.pdf 저장
  → 파일 무결성 검증 (diff 명령)
```

#### 4. 동시 작업 테스트
```
[VM] 파일 전송 시작 (진행 중)
  + [VM] 동시에 채팅 메시지 전송
  → 두 작업 모두 정상 작동 (Thread 덕분)
```

---

## 🔧 개발 과정에서 해결한 주요 이슈

### 1. 패킷 캡처 권한
**문제**: Permission denied
**해결**: `sudo ./run.sh`로 관리자 권한 실행

### 2. VM 네트워크 설정
**문제**: VM과 macOS 간 통신 불가
**해결**: 
- VM을 Bridge 모드로 설정
- 같은 서브넷(192.168.64.x) 사용

### 3. GUI 블로킹
**문제**: 파일 전송 중 GUI 멈춤
**해결**: Thread 기반 비동기 전송 구현

### 4. Fragment 순서 문제
**문제**: Out-of-order Fragment 처리
**해결**: Sequence Number 기반 재조립 버퍼

### 5. UTF-8 인코딩
**문제**: 한글 깨짐
**해결**: `StandardCharsets.UTF_8` 명시적 사용

---

## 📊 테스트 결과

### JUnit 테스트
```bash
mvn test

[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
✅ 100% 통과
```

**테스트 항목**:
1. FileAppLayerTest (3개)
   - 작은 파일 전송
   - 큰 파일 Fragmentation
   - Out-of-order Fragment 재조립

2. ChatAppLayerTest (5개)
   - 짧은 메시지
   - 긴 메시지 Fragmentation
   - Fragment 순서 재조립
   - 중복 Fragment 처리
   - 한글 메시지 UTF-8

3. IPLayerDemuxTest (3개)
   - ChatApp 프로토콜 역다중화 (253)
   - FileApp 프로토콜 역다중화 (254)
   - 동시 사용

### 실제 통신 테스트
- ✅ macOS ↔ VM 양방향 통신
- ✅ ARP Request/Reply
- ✅ Gratuitous ARP
- ✅ 채팅 메시지 (짧음/긴/한글)
- ✅ 파일 전송 (작은/큰 파일)
- ✅ 동시 작업 (파일+채팅)

---

## 📁 프로젝트 구조

```
fourth/
├── src/main/java/com/demo/
│   ├── ARPChatApp.java         # GUI 메인
│   ├── FileAppLayer.java       # 파일 전송 계층 ⭐ NEW
│   ├── ChatAppLayer.java       # 채팅 계층 (Fragmentation 추가)
│   ├── IPLayer.java            # IP 계층 (역다중화 추가) ⭐
│   ├── ARPLayer.java           # ARP 프로토콜
│   ├── EthernetLayer.java      # 이더넷 계층
│   ├── PhysicalLayer.java      # 물리 계층
│   └── BaseLayer.java          # 계층 인터페이스
│
├── src/test/java/com/demo/
│   ├── FileAppLayerTest.java   # 파일 전송 테스트 ⭐
│   ├── ChatAppLayerTest.java   # 채팅 테스트 ⭐
│   └── IPLayerDemuxTest.java   # 역다중화 테스트 ⭐
│
├── lib/
│   └── jnetpcap-wrapper-2.3.1-jdk21.jar
│
├── README.md                    # 빠른 시작 가이드
├── LAB4_README.md              # 상세 기술 문서
├── PROJECT_SUMMARY.md          # 프로젝트 완료 보고서
├── IMPLEMENTATION_DETAILS.md   # 구현 상세 ⭐
├── ARP_OPERATION.md            # ARP 동작 과정 ⭐
├── TESTING_GUIDE.md            # 테스트 가이드 ⭐
├── VM_SETUP_GUIDE.md           # VM 설정 가이드 ⭐
├── run.sh                      # 실행 스크립트
└── pom.xml                     # Maven 설정
```

---

## 🎯 핵심 성과

1. **계층화 아키텍처 완성**
   - OSI 7 Layer 모델 기반 구현
   - 각 계층의 독립성 보장
   - 확장 가능한 구조

2. **프로토콜 역다중화**
   - IP Protocol 필드 활용
   - 여러 상위 계층 동시 지원

3. **Fragmentation/재조립**
   - 큰 데이터를 작은 조각으로 분할
   - Out-of-order 처리
   - 효율적인 네트워크 사용

4. **Thread 기반 비동기 처리**
   - UI 블로킹 방지
   - 동시 작업 지원
   - 사용자 경험 향상

5. **크로스 플랫폼 지원**
   - macOS와 Linux(VM) 모두 지원
   - 플랫폼별 이슈 해결
   - 상세한 가이드 문서 작성

---

## 📝 문서화

작성된 문서:
1. README.md - 프로젝트 소개 및 빠른 시작
2. LAB4_README.md - 기술적 상세 설명
3. PROJECT_SUMMARY.md - 완료 보고서
4. IMPLEMENTATION_DETAILS.md - 역다중화, Fragmentation, Thread 구현
5. ARP_OPERATION.md - ARP Request, GARP, Proxy ARP
6. TESTING_GUIDE.md - 단계별 테스트 방법
7. VM_SETUP_GUIDE.md - VM 환경 설정 및 문제 해결

---

## 🚀 다음 단계 (특성화)

특성화 발표를 위해 추가할 기능:
1. 암호화 통신 (Encryption)
2. 우선순위 큐 (Priority)
3. 확인 응답 (ACK/재전송)
4. 타임스탬프 및 로깅

**목표 날짜**: 12/4 (발표일)
