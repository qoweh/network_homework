# Third - ARP 채팅 프로그램

> ARP 프로토콜 기능이 포함된 패킷 기반 채팅 애플리케이션

## 🚀 빠른 시작

```bash
# ARP 채팅 프로그램 실행 (관리자 권한 필요)
sudo ./run_arp_chat.sh

# 또는 기본 채팅 프로그램 (레거시 - ARP 기능 없는 기존(second 디렉토리) 버전)
sudo ./run_basic_chat.sh
```

> `sudo (관리자 권한)가 필요한 이유 : 패킷 캡처 (Packet Capture) 때문`
>  
> ```java
> // PhysicalLayer.java에서
> m_Adapter = Pcap.openLive(deviceName, ...);  // ← 여기서 권한 필요!
> ```

## 📚 문서

- **[ARP_README.md](./ARP_README.md)** - 전체 프로젝트 상세 문서
- **[IMPLEMENTATION_SUMMARY.md](./IMPLEMENTATION_SUMMARY.md)** - 구현 내용 요약

## ✨ 주요 기능

- ARP Request/Reply 처리
- ARP 캐시 테이블 관리
- Gratuitous ARP
- Proxy ARP
- IP 통신 (IPv4)
- 이더넷 역다중화

## 📊 계층 구조

```
ChatApp → IP → Ethernet/ARP → Physical
```

더 자세한 내용은 [ARP_README.md](./ARP_README.md)를 참조하세요.
