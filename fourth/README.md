# Fourth - 파일 전송 채팅 프로그램

> IP 프로토콜 역다중화를 이용한 파일 전송 및 채팅 애플리케이션

## 🚀 실행
```bash
sudo ./run.sh
```

## ✨ 주요 기능

### 파일 전송 (FileAppLayer)
- 1KB 단위 Fragmentation
- Thread 기반 비동기 전송
- 진행률 표시 (GUI)
- Fragment 타입: FILE_START, FILE_DATA, FILE_END

### 채팅 (ChatAppLayer)
- 512B 이상 메시지 자동 분할
- UTF-8 한글 지원
- Out-of-order Fragment 재조립

### IP 역다중화
- **Protocol 253**: ChatAppLayer
- **Protocol 254**: FileAppLayer

## 🏗️ 계층 구조
```
┌─────────────────┬─────────────────┐
│  ChatAppLayer   │  FileAppLayer   │  L7
│  (Proto 253)    │  (Proto 254)    │
└────────┬────────┴────────┬────────┘
         └────────┬────────┘
                  │
         ┌────────▼────────┐
         │    IPLayer      │  L3 (역다중화)
         └────────┬────────┘
      ┌───────────┴───────────┐
┌─────▼─────┐         ┌───────▼──────┐
│EthernetLyr│         │   ARPLayer   │  L2
└─────┬─────┘         └───────┬──────┘
      └───────────┬───────────┘
         ┌────────▼────────┐
         │ PhysicalLayer   │  L1
         └─────────────────┘
```

## 📁 핵심 파일
| 파일 | 설명 |
|------|------|
| `FileAppLayer.java` | 파일 전송 (1KB 분할) |
| `ChatAppLayer.java` | 채팅 (512B 분할) |
| `IPLayer.java` | IP 역다중화 |
| `ARPChatApp.java` | GUI (파일 전송 UI) |

## 🧪 테스트
```bash
mvn test
# Tests: 11, Failures: 0 ✅
```

## ⚠️ 요구사항
- Java 21+
- 관리자 권한 (sudo)
- libpcap
