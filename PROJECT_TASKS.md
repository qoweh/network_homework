### 🔧 핵심 기능 개발

| # | 작업 내용 | 상세 설명 | 난이도 | 관련 코드 |
| --- | --- | --- | --- | --- |
| 1 | **XOR 암호화 기능 구현** | ChatAppLayer에 XOR 암호화/복호화 로직 추가, 암호화 플래그(0x80) 설계 | ⭐⭐⭐ | [ChatAppLayer.java#L229](last/src/main/java/com/demo/ChatAppLayer.java#L229) |
| 2 | **우선순위 큐 시스템 구현** | PriorityBlockingQueue 적용, HIGH/NORMAL/LOW 3단계 우선순위 | ⭐⭐⭐ | [ChatAppLayer.java#L117](last/src/main/java/com/demo/ChatAppLayer.java#L117) |
| 3 | **타임스탬프 로깅 시스템** | 8바이트 타임스탬프 헤더, 지연시간 계산, packet.log 기록 | ⭐⭐⭐ | [ChatAppLayer.java#L333](last/src/main/java/com/demo/ChatAppLayer.java#L333) |
| 4 | **메시지 Fragmentation 구현** | 큰 메시지 분할 전송, 시퀀스 번호 관리 | ⭐⭐⭐ | [ChatAppLayer.java#L400](last/src/main/java/com/demo/ChatAppLayer.java#L400) |
| 5 | **메시지 Reassembly 구현** | Fragment 재조립 버퍼, 완료 체크 로직 | ⭐⭐⭐ | [ChatAppLayer.java#L605](last/src/main/java/com/demo/ChatAppLayer.java#L605) |

---

### 🌐 프로토콜 계층 구현

| # | 작업 내용 | 상세 설명 | 난이도 | 관련 코드 |
| --- | --- | --- | --- | --- |
| 6 | **IP 헤더 생성/파싱** | 20바이트 IP 헤더, TOS 필드 우선순위 연동 | ⭐⭐⭐ | [IPLayer.java#L240](last/src/main/java/com/demo/IPLayer.java#L240) |
| 7 | **IP 프로토콜 역다중화** | Protocol 필드로 ChatApp(253)/FileApp(254) 분기 | ⭐⭐ | [IPLayer.java#L390](last/src/main/java/com/demo/IPLayer.java#L390) |
| 8 | **Ethernet 프레임 생성/파싱** | 14바이트 헤더, 최소 60바이트 패딩 | ⭐⭐ | [EthernetLayer.java#L138](last/src/main/java/com/demo/EthernetLayer.java#L138) |
| 9 | **EtherType 역다중화** | IP(0x0800)/ARP(0x0806) 분기 처리 | ⭐⭐ | [EthernetLayer.java#L265](last/src/main/java/com/demo/EthernetLayer.java#L265) |
| 10 | **ARP 캐시 테이블 구현** | IP-MAC 매핑 저장, 조회, 관리 | ⭐⭐ | [ARPLayer.java#L35](last/src/main/java/com/demo/ARPLayer.java#L35) |
| 11 | **ARP Request/Reply 처리** | ARP 패킷 생성 및 응답 로직 | ⭐⭐⭐ | [ARPLayer.java#L309](last/src/main/java/com/demo/ARPLayer.java#L309) |
| 12 | **Proxy ARP 기능** | 다른 호스트 대신 ARP 응답 | ⭐⭐ | [ARPLayer.java#L355](last/src/main/java/com/demo/ARPLayer.java#L355) |
| 13 | **Gratuitous ARP 전송** | 자신의 IP를 네트워크에 알림 | ⭐⭐ | [ARPLayer.java#L270](last/src/main/java/com/demo/ARPLayer.java#L270) |

---

### 📁 파일 전송 기능

| # | 작업 내용 | 상세 설명 | 난이도 | 관련 코드 |
| --- | --- | --- | --- | --- |
| 14 | **파일 분할 전송** | 1KB 단위 Fragment, FILE_START/DATA/END 프로토콜 | ⭐⭐⭐ | [FileAppLayer.java#L132](last/src/main/java/com/demo/FileAppLayer.java#L132) |
| 15 | **파일 재조립** | Fragment 수집 및 원본 파일 복원 | ⭐⭐⭐ | [FileAppLayer.java#L252](last/src/main/java/com/demo/FileAppLayer.java#L252) |
| 16 | **전송 진행률 콜백** | 진행률 UI 업데이트 | ⭐⭐ | [FileAppLayer.java#L338](last/src/main/java/com/demo/FileAppLayer.java#L338) |
| 17 | **별도 Thread 전송** | 채팅과 파일 전송 동시 사용 가능 | ⭐⭐ | [FileAppLayer.java#L134](last/src/main/java/com/demo/FileAppLayer.java#L134) |

---

### 🖥️ GUI 개발

| # | 작업 내용 | 상세 설명 | 난이도 | 관련 코드 |
| --- | --- | --- | --- | --- |
| 18 | **네트워크 장치 선택 UI** | ComboBox로 NIC 선택, MAC 자동 로드 | ⭐⭐ | [NetworkChatApp.java#L270](last/src/main/java/com/demo/NetworkChatApp.java#L270) |
| 19 | **IP/MAC 주소 설정 패널** | 입력 필드, 유효성 검사 | ⭐⭐ | [NetworkChatApp.java#L280](last/src/main/java/com/demo/NetworkChatApp.java#L280) |
| 20 | **ARP 캐시 테이블 UI** | JTable로 캐시 표시, 실시간 업데이트 | ⭐⭐ | [NetworkChatApp.java#L340](last/src/main/java/com/demo/NetworkChatApp.java#L340) |
| 21 | **암호화 체크박스 UI** | 암호화 On/Off 토글 | ⭐ | [NetworkChatApp.java#L450](last/src/main/java/com/demo/NetworkChatApp.java#L450) |
| 22 | **우선순위 선택 콤보박스** | 긴급/일반/낮음 선택 | ⭐ | [NetworkChatApp.java#L460](last/src/main/java/com/demo/NetworkChatApp.java#L460) |
| 23 | **지연시간 표시 레이블** | 실시간 latency 표시 | ⭐ | [NetworkChatApp.java#L470](last/src/main/java/com/demo/NetworkChatApp.java#L470) |
| 24 | **파일 전송 진행바** | JProgressBar 연동 | ⭐⭐ | [NetworkChatApp.java#L520](last/src/main/java/com/demo/NetworkChatApp.java#L520) |
| 25 | **메시지 표시 영역** | JTextArea, 스크롤 | ⭐ | [NetworkChatApp.java#L330](last/src/main/java/com/demo/NetworkChatApp.java#L330) |

---

### 🧪 테스트 코드 작성

| # | 작업 내용 | 상세 설명 | 난이도 | 관련 코드 |
| --- | --- | --- | --- | --- |
| 26 | **암호화 테스트** | XOR 암호화/복호화 검증 (4개 테스트) | ⭐⭐ | [NewFeaturesTest.java](last/src/test/java/com/demo/NewFeaturesTest.java) |
| 27 | **우선순위 큐 테스트** | 우선순위 순서 검증 (4개 테스트) | ⭐⭐ | [PriorityQueueTest.java](last/src/test/java/com/demo/PriorityQueueTest.java) |
| 28 | **타임스탬프 테스트** | 지연시간 계산 검증 (3개 테스트) | ⭐⭐ | [NewFeaturesTest.java](last/src/test/java/com/demo/NewFeaturesTest.java) |
| 29 | **통합 테스트** | 암호화+우선순위+타임스탬프 조합 (3개 테스트) | ⭐⭐ | [NewFeaturesTest.java](last/src/test/java/com/demo/NewFeaturesTest.java) |
| 30 | **채팅 기능 테스트** | 메시지 송수신 검증 (5개 테스트) | ⭐⭐ | [ChatAppLayerTest.java](last/src/test/java/com/demo/ChatAppLayerTest.java) |
| 31 | **파일 전송 테스트** | 파일 분할/재조립 검증 (3개 테스트) | ⭐⭐ | [FileAppLayerTest.java](last/src/test/java/com/demo/FileAppLayerTest.java) |
| 32 | **IP 역다중화 테스트** | 프로토콜 분기 검증 (3개 테스트) | ⭐⭐ | [IPLayerDemuxTest.java](last/src/test/java/com/demo/IPLayerDemuxTest.java) |

---

### 📝 문서화 작업

| # | 작업 내용 | 상세 설명 | 난이도 | 관련 코드 |
| --- | --- | --- | --- | --- |
| 33 | [**README.md](http://readme.md/) 작성** | 프로젝트 개요, 실행 방법 | ⭐ | [README.md](last/README.md) |
| 34 | [**DOCUMENTATION.md](http://documentation.md/) 작성** | 상세 기술 문서 (1300줄+) | ⭐⭐⭐ | [DOCUMENTATION.md](last/done/DOCUMENTATION.md) |
| 35 | **WINDOWS_GUIDE.md 작성** | Windows 환경 실행 가이드 | ⭐⭐ | [WINDOWS_GUIDE.md](last/WINDOWS_GUIDE.md) |
| 36 | **코드 주석 작성** | 각 클래스/메서드 Javadoc | ⭐⭐ | [src/main/java](last/src/main/java) |

---

### 🐳 DevOps / 인프라

| # | 작업 내용 | 상세 설명 | 난이도 | 관련 코드 |
| --- | --- | --- | --- | --- |
| 37 | **Dockerfile 작성** | 멀티스테이지 빌드, Java 21 환경 | ⭐⭐ | [Dockerfile](last/Dockerfile) |
| 38 | **docker-compose.yml** | 서비스 정의, 환경변수 설정 | ⭐⭐ | [docker-compose.yml](last/docker-compose.yml) |
| 39 | **DemoApp 구현** | Docker 환경용 데모 프로그램 | ⭐⭐ | [src/main/java](last/src/main/java) |
| 40 | [**run.sh](http://run.sh/) 스크립트** | OS 감지, 환경 설정, 실행 자동화 | ⭐⭐ | [run.sh](last/run.sh) |

---

### 🔄 코드 리팩토링

| # | 작업 내용 | 상세 설명 | 난이도 | 관련 코드 |
| --- | --- | --- | --- | --- |
| 41 | **변수명 개선** | 영어 명명 규칙 적용 (lowerLayer, upperLayers 등) | ⭐⭐ | [BaseLayer.java](last/src/main/java/com/demo/BaseLayer.java) |
| 42 | **상수 정리** | 매직 넘버 상수화 (TOS_PRIORITY_HIGH 등) | ⭐⭐ | [IPLayer.java](last/src/main/java/com/demo/IPLayer.java) |
| 43 | **클래스명 개선** | MessageReassemblyBuffer, PrioritizedMessage 등 | ⭐⭐ | [ChatAppLayer.java](last/src/main/java/com/demo/ChatAppLayer.java) |
| 44 | **불필요 파일 정리** | BasicChatApp, DeprecatedBasicChatApp 삭제 | ⭐ | [src/main/java](last/src/main/java) |

---