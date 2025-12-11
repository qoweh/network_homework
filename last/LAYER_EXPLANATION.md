**송신 측 (다중화)**:
```
ChatAppLayer ─┐
              ├─→ IPLayer ─→ EthernetLayer ─→ PhysicalLayer ─→ 네트워크
FileAppLayer ─┘
```
- ChatAppLayer와 FileAppLayer가 **하나의 IPLayer**를 통해 전송
- IPLayer가 프로토콜 번호(253=Chat, 254=File)를 붙여서 구분

**수신 측 (역다중화)**:
```
                                              ┌─→ ChatAppLayer (proto=253)
네트워크 ─→ PhysicalLayer ─→ EthernetLayer ─→ IPLayer ─┤
                                              └─→ FileAppLayer (proto=254)
```
- IPLayer가 프로토콜 번호를 보고 **어느 상위 계층**으로 전달할지 결정

> - "서버 다중화 - 클라이언트 역다중화"
> - "서버 역다중화 - 클라이언트 다중화"

---

## 🏗️ OSI 계층 구조 (이 프로그램 기준)

```
┌─────────────────────────────────────────────────────────────────┐
│  L7 (응용 계층)    │ ChatAppLayer.java, FileAppLayer.java      │
│                    │ 사용자 데이터를 처리 (문자열, 파일)        │
├────────────────────┼────────────────────────────────────────────┤
│  L3 (네트워크 계층) │ IPLayer.java                              │
│                    │ IP 주소 기반 라우팅, 프로토콜 구분          │
├────────────────────┼────────────────────────────────────────────┤
│  L2 (데이터링크)   │ EthernetLayer.java, ARPLayer.java         │
│                    │ MAC 주소 기반 전송, IP→MAC 변환            │
├────────────────────┼────────────────────────────────────────────┤
│  L1 (물리 계층)    │ PhysicalLayer.java                        │
│                    │ 실제 비트 전송 (jNetPcap 사용)             │
└────────────────────┴────────────────────────────────────────────┘
```

**참고**: L4~L6은 이 프로그램에서 생략됨 (간소화된 구현)

---

## 📦 각 계층별 상세 설명

### 1️⃣ L7: ChatAppLayer (채팅 응용 계층)

**역할**: 사용자의 문자열 메시지를 네트워크 전송 가능한 바이트로 변환

#### 📤 전송 시 (Send)
```java
String message = "안녕하세요";
       ↓
[1] UTF-8 인코딩 → byte[] 변환
[2] XOR 암호화 (옵션) → 암호화된 byte[]
[3] 큰 메시지면 Fragment화 (1KB 단위로 분할)
[4] 헤더 추가: Type(1B) + Priority(1B) + Timestamp(8B) + Seq(4B) + Total(4B)
[5] ipLayer.Send(packet, length) 호출
```

**추가된 헤더 (18바이트)**:
```
┌────────┬──────────┬───────────┬──────────┬──────────┬──────────┐
│ Type   │ Priority │ Timestamp │ Sequence │ TotalSeq │  Data    │
│ 1byte  │  1byte   │  8bytes   │  4bytes  │  4bytes  │ (가변)   │
└────────┴──────────┴───────────┴──────────┴──────────┴──────────┘
```

#### 📥 수신 시 (Receive)
```java
byte[] packet
       ↓
[1] 헤더 파싱 (Type, Priority, Timestamp 추출)
[2] Fragment면 재조립 대기
[3] XOR 복호화 (암호화 플래그 확인)
[4] UTF-8 디코딩 → String 변환
[5] 우선순위 큐에 추가 (PriorityBlockingQueue)
[6] 메시지 처리 스레드가 우선순위 순서로 꺼내서 콜백 호출
       ↓
콜백: UI에 메시지 표시
```

#### 🔑 핵심 기능
- **XOR 암호화**: 간단한 대칭 암호화 (key=0x42)
- **우선순위 큐**: 긴급 메시지 우선 처리
- **Fragmentation**: 큰 메시지 분할/재조립
- **타임스탬프**: 지연시간 측정

---

### 2️⃣ L7: FileAppLayer (파일 응용 계층)

**역할**: 파일을 작은 조각으로 나누어 전송, 수신 시 재조립

#### 📤 전송 시
```java
File file = new File("photo.jpg");
       ↓
[1] FILE_START 패킷: 파일명, 크기, 총 Fragment 수
[2] FILE_DATA 패킷: 1KB씩 데이터 전송 (순서번호 포함)
[3] FILE_END 패킷: 전송 완료 알림
       ↓
ipLayer.Send() 호출
```

#### 📥 수신 시
```java
byte[] packet
       ↓
[1] FILE_START: 수신 준비 (버퍼 할당)
[2] FILE_DATA: 버퍼에 데이터 누적
[3] FILE_END: 파일 저장
```

---

### 3️⃣ L3: IPLayer (네트워크 계층)

**역할**: IP 주소 기반 통신, 프로토콜 역다중화

#### 📤 전송 시 (Send)
```java
byte[] data (from ChatAppLayer/FileAppLayer)
       ↓
[1] ARP 캐시에서 목적지 MAC 조회
    - 없으면 ARP Request 전송 후 대기
[2] IP 헤더 생성 (20바이트):
    - Version(4) + IHL(5) + TOS(우선순위) + Total Length
    - TTL(128) + Protocol(253 또는 254)
    - Source IP + Destination IP
[3] ethernetLayer.setDstMac(목적지MAC)
[4] ethernetLayer.Send(ipPacket)
```

**IP 헤더 구조 (20바이트)**:
```
┌────────┬────────┬────────┬──────────────┐
│Version │  IHL   │  TOS   │ Total Length │  ← 4바이트
├────────┴────────┴────────┴──────────────┤
│      Identification     │Flags│ Offset  │  ← 4바이트
├─────────────────────────┴─────┴─────────┤
│   TTL   │ Protocol │   Header Checksum  │  ← 4바이트
├─────────┴──────────┴────────────────────┤
│           Source IP Address             │  ← 4바이트
├─────────────────────────────────────────┤
│        Destination IP Address           │  ← 4바이트
└─────────────────────────────────────────┘
```

#### 📥 수신 시 (Receive) - ⭐역다중화⭐
```java
byte[] ipPacket
       ↓
[1] IP 헤더 파싱
[2] Protocol 필드 확인:
    - 253 → ChatAppLayer.Receive() 호출
    - 254 → FileAppLayer.Receive() 호출
[3] TOS 필드에서 우선순위 추출
[4] 페이로드만 상위 계층에 전달
```

#### 🔑 핵심: 프로토콜 역다중화
```java
// IPLayer.Receive() 내부
int protocol = ipPacket[9] & 0xFF;
if (protocol == 253) {
    chatAppLayer.Receive(payload);  // 채팅 메시지
} else if (protocol == 254) {
    fileAppLayer.Receive(payload);  // 파일 데이터
}
```

---

### 4️⃣ L2: EthernetLayer (데이터링크 계층)

**역할**: MAC 주소 기반 프레임 전송, EtherType 역다중화

#### 📤 전송 시 (Send)
```java
byte[] ipPacket
       ↓
[1] 이더넷 헤더 생성 (14바이트):
    - 목적지 MAC (6B) + 출발지 MAC (6B) + EtherType (2B)
[2] 페이로드 추가
[3] 최소 60바이트 패딩 (필요시)
[4] physicalLayer.Send(frame)
```

**이더넷 프레임 구조**:
```
┌──────────────┬──────────────┬──────────┬─────────────┐
│ 목적지 MAC   │ 출발지 MAC   │ EtherType│   페이로드  │
│   (6바이트)  │   (6바이트)  │ (2바이트)│ (46~1500)   │
└──────────────┴──────────────┴──────────┴─────────────┘
      6              6            2         46~1500
```

#### 📥 수신 시 (Receive) - ⭐역다중화⭐
```java
byte[] frame
       ↓
[1] 목적지 MAC 확인 (자신 또는 브로드캐스트)
[2] EtherType 확인:
    - 0x0800 → IPLayer.Receive() (IP 패킷)
    - 0x0806 → ARPLayer.Receive() (ARP 패킷)
[3] 헤더 제거하고 페이로드만 전달
```

#### 🔑 핵심: EtherType 역다중화
```java
// EthernetLayer.Receive() 내부
int etherType = ((frame[12] & 0xFF) << 8) | (frame[13] & 0xFF);
if (etherType == 0x0800) {
    ipLayer.Receive(payload);   // IP 패킷
} else if (etherType == 0x0806) {
    arpLayer.Receive(payload);  // ARP 패킷
}
```

---

### 5️⃣ L2: ARPLayer (주소 해석 프로토콜)

**역할**: IP 주소 → MAC 주소 변환

#### ARP 동작 과정
```
[Host A]                                     [Host B]
192.168.0.10                                 192.168.0.20
AA:AA:AA:AA:AA:AA                            BB:BB:BB:BB:BB:BB

   │                                              │
   │  ARP Request (브로드캐스트)                   │
   │  "192.168.0.20의 MAC 주소는?"                 │
   │ ─────────────────────────────────────────→   │
   │                                              │
   │  ARP Reply (유니캐스트)                       │
   │  "192.168.0.20은 BB:BB:BB:BB:BB:BB"         │
   │ ←─────────────────────────────────────────   │
   │                                              │
```

#### 📥 수신 시 (Receive)
```java
byte[] arpPacket
       ↓
[1] Operation 확인 (1=Request, 2=Reply)
[2] Request인 경우:
    - Target IP가 자신이면 Reply 전송
    - Proxy ARP 활성화 시 대리 응답
[3] Reply인 경우:
    - ARP 캐시에 IP-MAC 매핑 저장
```

---

### 6️⃣ L1: PhysicalLayer (물리 계층)

**역할**: 실제 네트워크 카드(NIC)와 통신

#### 📤 전송 시 (Send)
```java
byte[] frame
       ↓
pcap.sendPacket(ByteBuffer.wrap(frame))
       ↓
NIC → 네트워크 케이블/무선
```

#### 📥 수신 시 (run 메서드 - 별도 스레드)
```java
while (!Thread.interrupted()) {
    pcap.dispatch(1, (header, packet) -> {
        byte[] data = packet.getByteArray();
        ethernetLayer.Receive(data);
    });
}
```

---

## 🔄 전체 데이터 흐름 요약

### 송신 (캡슐화 - Encapsulation)
```
"Hello" (문자열)
    ↓ ChatAppLayer: UTF-8 인코딩 + 헤더 추가
[ChatApp 헤더 | 데이터]
    ↓ IPLayer: IP 헤더 추가
[IP 헤더 | ChatApp 헤더 | 데이터]
    ↓ EthernetLayer: Ethernet 헤더 추가
[Ethernet 헤더 | IP 헤더 | ChatApp 헤더 | 데이터]
    ↓ PhysicalLayer: NIC로 전송
```

### 수신 (역캡슐화 - Decapsulation)
```
[Ethernet 헤더 | IP 헤더 | ChatApp 헤더 | 데이터]
    ↓ PhysicalLayer: NIC에서 수신
    ↓ EthernetLayer: Ethernet 헤더 제거, EtherType 확인
[IP 헤더 | ChatApp 헤더 | 데이터]
    ↓ IPLayer: IP 헤더 제거, Protocol 확인 (253=Chat)
[ChatApp 헤더 | 데이터]
    ↓ ChatAppLayer: 헤더 파싱, 복호화, UTF-8 디코딩
"Hello" (문자열)
```

---

## 💡 발표 팁

1. **시작**: NetworkChatApp.java (GUI)에서 사용자가 버튼 클릭
2. **다중화 설명**: 채팅과 파일이 같은 IP Layer를 공유
3. **캡슐화 설명**: 각 계층이 헤더를 추가하며 내려감
4. **역다중화 설명**: 헤더를 보고 어느 상위 계층으로 보낼지 결정
5. **마무리**: 수신 측에서 원래 메시지 복원

---

## ❓ 자주 묻는 질문

### Q: ChatAppLayer는 어디서 호출되나요?
**A**: `NetworkChatApp.java`의 `handleSendMessage()` 메서드에서 호출됩니다.
```java
chatLayer.sendMessage(message);  // ← 여기서 호출!
```

### Q: 왜 L4~L6이 없나요?
**A**: 교육용 간소화 구현입니다. TCP/UDP 없이 직접 IP 위에서 동작합니다.

### Q: 우선순위 큐는 왜 필요한가요?
**A**: 긴급 메시지를 일반 메시지보다 먼저 처리하기 위함입니다.
