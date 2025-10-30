# 🧪 ARP 채팅 프로그램 테스트 및 보고서 작성 가이드

> ARP 기능 검증 및 Wireshark 캡처를 위한 단계별 실습 가이드

---

## 📋 보고서 요구사항

✅ **구현 항목:**
- IP/ARP 계층 추가 및 계층 연결
- ARP / Proxy ARP / Gratuitous ARP 기능 구현

✅ **제출 내용:**
- 이더넷 역다중화 및 ARP 기능 관련 코드 설명
- 실행 화면 및 Wireshark 캡처 화면

---

## 🚀 Step-by-Step 테스트 가이드

### 📍 사전 준비

#### 1. Wireshark 설치 및 실행
```bash
# macOS
brew install --cask wireshark

# 또는 https://www.wireshark.org/download.html 에서 다운로드
```

#### 2. Wireshark 필터 설정
```
arp or (ip.addr == 192.168.0.100)
```
- 상단 필터바에 입력하여 ARP 및 IP 패킷만 표시

#### 3. 프로그램 실행
```bash
cd third
sudo ./run_arp_chat.sh
```

---

## 🧪 테스트 시나리오

### ✅ Test 1: 기본 ARP Request/Reply

**목적:** ARP 프로토콜 동작 확인

**실행 순서:**
1. **네트워크 설정**
   - 네트워크 장치: `en0` 선택 (또는 사용 중인 인터페이스)
   - 내 IP 주소: `192.168.0.100` (예시)
   - 목적지 IP: `192.168.0.101` (테스트할 상대방 IP)
   - "설정" 버튼 클릭

2. **ARP Request 전송**
   - "ARP Request" 버튼 클릭
   - 메시지: `"ARP Request sent to 192.168.0.101"` 확인

3. **Wireshark 캡처 확인**
   ```
   [예상 패킷]
   ARP Request: Who has 192.168.0.101? Tell 192.168.0.100
   ARP Reply: 192.168.0.101 is at XX:XX:XX:XX:XX:XX
   ```

4. **ARP 캐시 테이블 확인**
   - GUI 우측 "ARP 캐시 테이블"에 항목 추가 확인
   - IP 주소와 MAC 주소 매핑 확인

**📸 캡처할 화면:**
- ✅ Wireshark ARP Request 패킷 (상세 정보 펼친 상태)
- ✅ Wireshark ARP Reply 패킷
- ✅ 프로그램 ARP 캐시 테이블 업데이트 화면

---

### ✅ Test 2: Gratuitous ARP

**목적:** 자신의 IP를 네트워크에 공지

**실행 순서:**
1. **네트워크 설정 완료 상태에서**
   - "Gratuitous ARP" 버튼 클릭

2. **Wireshark 캡처 확인**
   ```
   [예상 패킷]
   ARP Request: Who has 192.168.0.100? Tell 192.168.0.100
   - 송신자 IP = 대상 IP (동일함)
   - 브로드캐스트 (FF:FF:FF:FF:FF:FF)
   ```

3. **용도 이해**
   - IP 주소 충돌 감지
   - ARP 캐시 갱신 알림

**📸 캡처할 화면:**
- ✅ Wireshark Gratuitous ARP 패킷 (송신자 IP = 대상 IP 확인)
- ✅ 프로그램 실행 화면 (버튼 클릭 후)

---

### ✅ Test 3: Proxy ARP

**목적:** 다른 호스트를 대신하여 ARP 응답

**실행 순서:**
1. **Proxy ARP 설정**
   - "Proxy ARP 활성화" 체크박스 선택
   - Proxy IP: `192.168.0.200` (대리 응답할 IP)
   - Proxy MAC: `AA:BB:CC:DD:EE:FF` (대리 응답할 MAC)
   - "Proxy 추가" 버튼 클릭

2. **다른 장치에서 테스트**
   - 다른 PC/VM에서 `192.168.0.200`으로 ARP Request 전송
   ```bash
   # Linux/macOS
   arping -c 1 192.168.0.200
   ```

3. **Wireshark 캡처 확인**
   ```
   [예상 패킷]
   ARP Request: Who has 192.168.0.200?
   ARP Reply: 192.168.0.200 is at AA:BB:CC:DD:EE:FF (프로그램이 대신 응답)
   ```

**📸 캡처할 화면:**
- ✅ Proxy ARP 설정 화면
- ✅ Wireshark Proxy ARP Reply 패킷
- ✅ 프로그램 로그 (Proxy ARP 응답 메시지)

---

### ✅ Test 4: 이더넷 역다중화 (Ethernet Demultiplexing)

**목적:** EtherType에 따른 계층 분리 확인

**실행 순서:**
1. **IP 통신 테스트**
   - 목적지 IP: `192.168.0.101` 설정
   - 메시지 입력: `"Hello from IP layer"`
   - "전송" 버튼 클릭

2. **Wireshark 캡처 확인**
   ```
   [예상 패킷]
   Ethernet Frame:
     - Destination: (ARP로 해석된 MAC 주소)
     - Source: (내 MAC 주소)
     - EtherType: 0x0800 (IPv4) ← IP 계층으로 전달
   
   IPv4 Packet:
     - Source IP: 192.168.0.100
     - Destination IP: 192.168.0.101
     - Protocol: 253 (Custom)
   ```

3. **코드 흐름 확인**
   ```
   ARPChatApp (메시지 전송)
       ↓
   IPLayer.Send() - IP 헤더 추가
       ↓ (ARP 캐시 조회 → MAC 주소 획득)
   EthernetLayer.Send() - EtherType 0x0800
       ↓
   PhysicalLayer.Send()
   
   [수신 측]
   PhysicalLayer.Receive()
       ↓
   EthernetLayer.Receive() - EtherType 확인
       ↓ 0x0800 → IPLayer
       ↓ 0x0806 → ARPLayer (역다중화!)
   IPLayer.Receive() - IP 파싱
       ↓
   ARPChatApp (메시지 표시)
   ```

**📸 캡처할 화면:**
- ✅ Wireshark IP 패킷 (Ethernet → IP 계층 구조 펼치기)
- ✅ Wireshark ARP 패킷 (Ethernet → ARP 계층 구조 펼치기)
- ✅ 프로그램 메시지 송수신 화면

---

## 📊 보고서 작성 가이드

### 1️⃣ 코드 설명 섹션

#### A. 이더넷 역다중화 (EthernetLayer.java)

**설명할 부분:**
```java
// Receive() 메서드 - 라인 약 180-200
public boolean Receive(byte[] input) {
    // EtherType 추출 (12-13 바이트)
    int etherType = ((input[12] & 0xFF) << 8) | (input[13] & 0xFF);
    
    // 역다중화: EtherType에 따라 상위 계층 선택
    if (etherType == 0x0800) {
        // IPv4 → IPLayer로 전달
        return ipLayer.Receive(payload);
    } else if (etherType == 0x0806) {
        // ARP → ARPLayer로 전달
        return arpLayer.Receive(payload);
    }
    // ...
}
```

**설명 포인트:**
- EtherType 값에 따른 분기 처리
- `0x0800` = IPv4, `0x0806` = ARP
- 각 상위 계층으로 페이로드 전달

---

#### B. ARP 기능 (ARPLayer.java)

**1) ARP Request (라인 약 145-175)**
```java
public void sendArpRequest(byte[] targetIp) {
    // ARP 패킷 구조 (28바이트)
    // - Hardware Type: 0x0001 (Ethernet)
    // - Protocol Type: 0x0800 (IPv4)
    // - Operation: 0x0001 (Request)
    // - Target MAC: 00:00:00:00:00:00 (unknown)
    
    // 브로드캐스트로 전송 (FF:FF:FF:FF:FF:FF)
    ethernetLayer.Send(arpPacket, BROADCAST_MAC);
}
```

**2) ARP Reply (라인 약 180-210)**
```java
public void sendArpReply(byte[] targetIp, byte[] targetMac) {
    // Operation: 0x0002 (Reply)
    // 유니캐스트로 응답 (요청자 MAC 주소)
}
```

**3) Gratuitous ARP (라인 약 215-245)**
```java
public void sendGratuitousArp() {
    // Sender IP = Target IP (자신의 IP)
    // 브로드캐스트로 공지
}
```

**4) Proxy ARP (라인 약 250-280)**
```java
public void addProxyArpEntry(byte[] proxyIp, byte[] proxyMac) {
    // Proxy 테이블에 추가
    // Receive()에서 해당 IP에 대한 ARP Request 받으면
    // Proxy MAC으로 대신 응답
}
```

---

#### C. IP 계층 (IPLayer.java)

**설명할 부분:**
```java
public boolean Send(byte[] data, byte[] destIp) {
    // 1. ARP 캐시에서 MAC 주소 조회
    byte[] destMac = arpLayer.getArpCacheEntry(destIp);
    
    if (destMac == null) {
        // 2. ARP Request 전송 (MAC 주소 학습)
        arpLayer.sendArpRequest(destIp);
        return false; // 재시도 필요
    }
    
    // 3. IP 헤더 생성 (20바이트)
    // 4. 이더넷 계층으로 전달 (EtherType 0x0800)
    return ethernetLayer.Send(ipPacket, destMac);
}
```

**설명 포인트:**
- IP → MAC 주소 변환 (ARP 연동)
- IP 헤더 필드 (버전, 길이, TTL, 프로토콜 등)

---

### 2️⃣ 실행 화면 섹션

**포함할 스크린샷:**
1. ✅ 프로그램 초기 화면 (네트워크 설정 전)
2. ✅ ARP Request 버튼 클릭 후
3. ✅ ARP 캐시 테이블 업데이트 화면
4. ✅ Gratuitous ARP 실행 화면
5. ✅ Proxy ARP 설정 화면
6. ✅ 메시지 송수신 화면

**각 스크린샷에 추가할 설명:**
- 무엇을 테스트하는지
- 어떤 결과를 기대하는지
- 실제 결과 (성공/실패)

---

### 3️⃣ Wireshark 캡처 화면 섹션

**필수 캡처:**

**ARP Request 패킷:**
```
Frame X: 42 bytes on wire
Ethernet II
  Destination: Broadcast (ff:ff:ff:ff:ff:ff)
  Source: YourMAC (xx:xx:xx:xx:xx:xx)
  Type: ARP (0x0806)
Address Resolution Protocol (request)
  Hardware type: Ethernet (1)
  Protocol type: IPv4 (0x0800)
  Opcode: request (1)
  Sender MAC: xx:xx:xx:xx:xx:xx
  Sender IP: 192.168.0.100
  Target MAC: 00:00:00:00:00:00
  Target IP: 192.168.0.101
```

**ARP Reply 패킷:**
```
Address Resolution Protocol (reply)
  Opcode: reply (2)
  Sender MAC: yy:yy:yy:yy:yy:yy
  Sender IP: 192.168.0.101
  Target MAC: xx:xx:xx:xx:xx:xx
  Target IP: 192.168.0.100
```

**Gratuitous ARP 패킷:**
```
Address Resolution Protocol (request)
  Sender IP: 192.168.0.100
  Target IP: 192.168.0.100  ← 동일!
```

**IP over Ethernet:**
```
Ethernet II
  Type: IPv4 (0x0800)  ← 역다중화 확인!
Internet Protocol Version 4
  Source: 192.168.0.100
  Destination: 192.168.0.101
  Protocol: 253 (Custom)
```

---

## 📝 보고서 템플릿

```markdown
# ARP 채팅 프로그램 구현 보고서

## 1. 프로젝트 개요
- 프로젝트명: ARP 기능을 포함한 패킷 채팅 프로그램
- 구현 내용: IP/ARP 계층 추가, 이더넷 역다중화, ARP 프로토콜

## 2. 계층 구조 및 연결
[계층 다이어그램 첨부]
- ARPChatApp ↔ IPLayer ↔ EthernetLayer ↔ PhysicalLayer
- ARPChatApp ↔ ARPLayer ↔ EthernetLayer ↔ PhysicalLayer

## 3. 이더넷 역다중화 구현
### 3.1 코드 설명
[EthernetLayer.java Receive() 메서드 코드 첨부]

### 3.2 동작 원리
- EtherType 0x0800: IP 계층으로 전달
- EtherType 0x0806: ARP 계층으로 전달

## 4. ARP 기능 구현
### 4.1 ARP Request/Reply
[ARPLayer.java 코드 첨부]
[Wireshark 캡처 화면]

### 4.2 Gratuitous ARP
[코드 설명 + Wireshark 캡처]

### 4.3 Proxy ARP
[코드 설명 + Wireshark 캡처]

## 5. 실행 및 테스트 결과
### 5.1 ARP Request/Reply 테스트
[실행 화면 + Wireshark 캡처]

### 5.2 Gratuitous ARP 테스트
[실행 화면 + Wireshark 캡처]

### 5.3 Proxy ARP 테스트
[실행 화면 + Wireshark 캡처]

## 6. 결론
- 구현 완료 항목 체크
- 학습 내용 정리
```

---

## 🎯 체크리스트

### 테스트 완료 확인
- [ ] Wireshark 설치 및 필터 설정
- [ ] ARP Request/Reply 캡처
- [ ] Gratuitous ARP 캡처
- [ ] Proxy ARP 캡처
- [ ] IP 통신 캡처 (역다중화 확인)
- [ ] ARP 캐시 테이블 업데이트 확인

### 보고서 작성 완료 확인
- [ ] 계층 구조 다이어그램
- [ ] 이더넷 역다중화 코드 설명
- [ ] ARP 기능 코드 설명
- [ ] 실행 화면 5개 이상
- [ ] Wireshark 캡처 화면 5개 이상
- [ ] 각 화면에 설명 추가

---

## 💡 추가 팁

### Wireshark 필터 예제
```
# ARP만 보기
arp

# 특정 IP 주소 관련 패킷
ip.addr == 192.168.0.100

# ARP 또는 IP
arp or ip

# Gratuitous ARP 찾기
arp.dst.proto_ipv4 == arp.src.proto_ipv4
```

### 패킷 상세 정보 보는 법
1. 패킷 클릭
2. 하단 창에서 각 계층 펼치기
   - Ethernet II
   - ARP / IPv4
3. 16진수 값 확인 (우클릭 → Copy → Bytes)

### 스크린샷 찍는 법
- macOS: `Cmd + Shift + 4` (영역 선택)
- 전체 화면: `Cmd + Shift + 3`

---

**참고 문서:**
- [ARP_README.md](./ARP_README.md) - 상세 기능 설명
- [IMPLEMENTATION_SUMMARY.md](./IMPLEMENTATION_SUMMARY.md) - 코드 구조
