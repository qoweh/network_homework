# Third - ARP 채팅 프로그램

> ARP 프로토콜 기능이 포함된 패킷 기반 채팅 애플리케이션

## 🚀 실행
```bash
sudo ./run_arp_chat.sh
```

## ✨ 주요 기능

### ARP 프로토콜
- **ARP Request/Reply**: IP→MAC 주소 해석
- **ARP 캐시 테이블**: IP-MAC 매핑 저장
- **Gratuitous ARP**: 네트워크 진입 알림
- **Proxy ARP**: 대리 응답

### 이더넷 역다중화
- `0x0800` (IPv4) → IPLayer
- `0x0806` (ARP) → ARPLayer

## 🏗️ 계층 구조
```
ChatAppLayer (L7)
    ↓
IPLayer (L3)
    ↓
EthernetLayer ←→ ARPLayer (L2)
    ↓
PhysicalLayer (L1)
```

## 📁 핵심 파일
| 파일 | 설명 |
|------|------|
| `ARPLayer.java` | ARP 프로토콜 구현 |
| `IPLayer.java` | IPv4 패킷 처리 |
| `EthernetLayer.java` | 이더넷 역다중화 |
| `ARPChatApp.java` | GUI 메인 클래스 |

## ⚠️ 요구사항
- Java 21+
- 관리자 권한 (sudo)
- libpcap
