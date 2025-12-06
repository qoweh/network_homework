# Last - 최종 프로젝트 ⭐

> 암호화, 우선순위 큐, 타임스탬프 기능이 추가된 네트워크 채팅 프로그램

## 🚀 실행

### 로컬 (macOS/Linux)
```bash
sudo ./run.sh
```

### Docker (Windows/macOS/Linux)
```bash
docker-compose up --build
```

## ✨ 새로운 기능 (3가지)

### 1. 🔒 암호화 통신
- XOR 암호화 (키: 0x42)
- 헤더 플래그로 암호화 여부 표시
- GUI 체크박스로 On/Off

### 2. ⚡ 우선순위 큐
- 긴급 (HIGH): TOS 0xE0
- 일반 (NORMAL): TOS 0x00
- 낮음 (LOW): TOS 0x20
- GUI 콤보박스로 선택

### 3. 📊 타임스탬프/로깅
- 8바이트 타임스탬프 헤더
- 지연시간(Latency) 측정
- packet.log 파일 기록

## 📦 패킷 헤더 구조
```
[Type+Flag(1B)] [Priority(1B)] [Timestamp(8B)] [Seq(4B)] [Total(4B)] [Data]
```

## 🧪 테스트
```bash
mvn test
# Tests: 25, Failures: 0 ✅
```

| 테스트 클래스 | 수 | 내용 |
|--------------|---|------|
| NewFeaturesTest | 14 | 암호화, 우선순위, 타임스탬프 |
| ChatAppLayerTest | 5 | 채팅 기능 |
| FileAppLayerTest | 3 | 파일 전송 |
| IPLayerDemuxTest | 3 | IP 역다중화 |

## 🐳 Docker
```bash
# 데모 실행 (기본)
docker run --rm network-chat:latest

# 테스트 실행
docker run --rm -e APP_MODE=test network-chat:latest

# 인터랙티브 모드
docker run --rm -it -e APP_MODE=interactive network-chat:latest
```

## 📁 핵심 파일
| 파일 | 변경 내용 |
|------|----------|
| `ChatAppLayer.java` | 암호화, 타임스탬프, Priority 헤더 |
| `IPLayer.java` | TOS 기반 우선순위 |
| `ARPChatApp.java` | GUI (체크박스, 콤보박스, 지연시간) |
| `DemoApp.java` | Docker 데모용 |

## ⚠️ 요구사항
- Java 21+
- 관리자 권한 (sudo) 또는 Docker
- libpcap (로컬 실행 시)
