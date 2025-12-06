# Second - 기본 채팅 프로그램 (레거시)

> ⚠️ 이 프로젝트는 더 이상 사용하지 않습니다. [last/](../last/)를 사용하세요.

## 개요
계층화된 이더넷 채팅 프로그램의 초기 버전입니다.

## 계층 구조
```
ChatAppLayer → EthernetLayer → PhysicalLayer
```

## 구현 내용
- Physical Layer (jNetPcap)
- Ethernet Layer (MAC 주소 기반)
- ChatApp Layer (메시지 송수신)

## 실행
```bash
mvn clean compile exec:exec
```
