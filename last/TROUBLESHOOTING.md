# 트러블슈팅 가이드

## 문제 1: 메시지가 2~3번 중복 수신되는 문제

### 원인
**타임스탬프 기반 중복 필터링 미구현**으로 인한 중복 처리

### 증상
- 맥북에서 칼리로 메시지 전송 시 칼리에서 2~3번 수신
- 칼리에서 맥북으로 메시지 전송 시 맥북에서 2~3번 수신
- 동일한 타임스탬프를 가진 메시지가 반복 표시

### 근본 원인 분석
1. **PhysicalLayer에서 패킷 중복 캡처**
   - Promiscuous mode 또는 스위치/허브가 패킷 반사
   - 같은 프레임이 여러 번 수신됨

2. **EthernetLayer의 불완전한 필터링**
   - 자기 수신 방지(출발지 MAC == 내 MAC)는 작동
   - 하지만 물리적 루프백은 차단 못함

3. **ChatAppLayer의 중복 처리**
   - 받은 메시지를 타임스탬프 확인 없이 모두 큐에 추가
   - 같은 내용이 여러 번 표시

### 해결 방법 ✅
**`ChatAppLayer.java`에 타임스탬프 기반 중복 필터 추가**

```java
// 중복 메시지 필터
private final Set<Long> recentTimestamps = Collections.newSetFromMap(new ConcurrentHashMap<>());
private static final long DEDUP_WINDOW_MS = 5000; // 5초

private boolean isDuplicate(long timestamp) {
    if (recentTimestamps.contains(timestamp)) {
        return true; // 중복
    }
    recentTimestamps.add(timestamp);
    
    // 오래된 타임스탬프 정리
    long cutoffTime = System.currentTimeMillis() - DEDUP_WINDOW_MS;
    recentTimestamps.removeIf(ts -> ts < cutoffTime);
    
    return false;
}
```

### 적용 결과
- 같은 타임스탬프의 메시지는 한 번만 처리
- 5초 이내 중복 메시지 자동 필터링
- 콘솔에 `[ChatApp] 중복 메시지 감지 - 드롭` 로그 출력

---

## 문제 2: IP 주소 설정 및 충돌 방지

### 상황
- 맥북의 `en7` IP가 매번 랜덤 생성 (169.254.x.x)
- 칼리에서 어떤 IP를 설정해야 안전한가?
- 이미 사용 중인 IP면 어떻게 하나?

### Link-local 주소(169.254.0.0/16) 이해

#### 자동 할당 범위
- **전체 범위**: 169.254.0.0 ~ 169.254.255.255
- **사용 가능 범위**: 169.254.1.0 ~ 169.254.254.255
- **예약 주소**:
  - 169.254.0.x: 네트워크 주소
  - 169.254.255.x: 브로드캐스트 주소

#### 맥북의 자동 IP 할당 (APIPA)
```bash
# 맥북 en7 인터페이스 확인
ifconfig en7
# 예시 출력:
# en7: inet 169.254.132.146 netmask 0xffff0000
```

- **특징**: 매번 랜덤 생성
- **충돌 방지**: ACD (Address Conflict Detection) 자동 실행
- **지속성**: 재부팅 시 변경 가능

### 칼리 IP 설정 전략

#### ✅ 권장 방법: 자동 생성 + 충돌 검사

**개선된 스크립트 사용** (`setup_eth0_ip_v2.sh`)

```bash
# 자동 IP 생성 모드 (충돌 검사 포함)
sudo ./setup_eth0_ip_v2.sh

# 결과:
# [AUTO] 자동 IP 생성 모드
# [INFO] 시도 1/5
# [INFO] 후보: 169.254.123.45
# [INFO] IP 충돌 검사: 169.254.123.45
# [OK] 사용 가능: 169.254.123.45
```

#### 작동 원리

1. **랜덤 IP 생성**
   ```bash
   169.254.$((RANDOM % 254 + 1)).$((RANDOM % 254 + 1))
   # 예: 169.254.87.201
   ```

2. **충돌 검사 (arping)**
   ```bash
   arping -D -I eth0 -c 2 -w 3 169.254.87.201
   # -D: Duplicate Address Detection
   # -I: Interface
   # -c: Count (2번 시도)
   # -w: Timeout (3초)
   ```

3. **결과 처리**
   - **응답 없음** → 사용 가능 → 설정
   - **응답 있음** → 충돌 → 다른 IP로 재시도 (최대 5번)

#### 수동 IP 지정 방법

**맥북 IP 확인 후 근처 IP 사용**

```bash
# 1. 맥북에서 en7 IP 확인
ifconfig en7 | grep "inet " | awk '{print $2}'
# 예시 출력: 169.254.132.146

# 2. 칼리에서 비슷한 IP 설정 (마지막 옥텟만 변경)
sudo ./setup_eth0_ip_v2.sh 169.254.132.200

# 또는 환경 변수 사용
KALI_IP=169.254.132.200 sudo -E ./setup_eth0_ip_v2.sh
```

### IP 충돌이 발생하는 경우

#### 증상
```
[ERROR] IP 충돌: 169.254.132.80
```

#### 원인
- 해당 IP를 다른 장치가 이미 사용 중
- 이전 설정이 남아있음

#### 해결 방법

```bash
# 1. 자동 모드로 재시도 (추천)
sudo ./setup_eth0_ip_v2.sh

# 2. 다른 IP 수동 지정
sudo ./setup_eth0_ip_v2.sh 169.254.132.250

# 3. 기존 설정 완전 초기화
sudo ip addr flush dev eth0
sudo ip link set eth0 down
sleep 2
sudo ./setup_eth0_ip_v2.sh
```

### 스크립트 비교

#### 기존 스크립트 (`setup_eth0_ip.sh`)
- ❌ 고정 기본값 (169.254.142.80)
- ❌ 충돌 검사 없음
- ✅ 수동 IP 지정 가능

#### 개선 스크립트 (`setup_eth0_ip_v2.sh`)
- ✅ 자동 랜덤 IP 생성
- ✅ arping 충돌 검사
- ✅ 5번 재시도 메커니즘
- ✅ 수동 IP 지정도 가능
- ✅ Link-local 범위 검증

### 사용 예시

```bash
# === 시나리오 1: 자동 모드 (추천) ===
kali@kali:~$ sudo ./setup_eth0_ip_v2.sh
[AUTO] 자동 IP 생성 모드
[INFO] 시도 1/5
[INFO] 후보: 169.254.87.201
[INFO] IP 충돌 검사: 169.254.87.201
[OK] 사용 가능: 169.254.87.201
========================================
인터페이스: eth0
설정 IP:    169.254.87.201/16
========================================

# === 시나리오 2: 맥북 IP 기반 수동 설정 ===
# 맥북 IP: 169.254.132.146
kali@kali:~$ sudo ./setup_eth0_ip_v2.sh 169.254.132.200
[OK] 명령줄 인자: 169.254.132.200
[INFO] IP 충돌 검사: 169.254.132.200
[OK] 사용 가능: 169.254.132.200

# === 시나리오 3: 환경 변수 사용 ===
kali@kali:~$ KALI_IP=169.254.132.250 sudo -E ./setup_eth0_ip_v2.sh
[OK] 환경 변수: 169.254.132.250
```

---

## 문제 3: sudo 없이 실행하면 에러

### 증상
```bash
./run.sh
# 에러: 컴파일 실패 또는 권한 오류
```

### 원인

#### 1. Raw Socket 권한
- jNetPcap이 `/dev/bpf*` 접근 필요
- macOS/Linux는 root만 허용

#### 2. Maven 파일 소유권
```bash
# sudo로 실행 후
ls -la target/
# drwxr-xr-x  root  root  target/
# -rw-r--r--  root  root  createdFiles.lst

# 일반 사용자로 실행 시
./run.sh
# 에러: Permission denied (createdFiles.lst 삭제 불가)
```

### 해결 방법

#### ✅ 방법 1: 항상 sudo 사용 (권장)
```bash
sudo ./run.sh
```

#### ✅ 방법 2: 파일 소유권 복구
```bash
# target 폴더 소유권을 현재 사용자로 변경
sudo chown -R $(whoami):$(id -gn) target/

# 이후 일반 실행 가능
./run.sh
```

#### ❌ 방법 3: CAP_NET_RAW 권한 부여 (비권장)
```bash
# 보안 위험 - 운영 환경에서는 절대 금지
sudo setcap cap_net_raw+ep $(which java)
```

### 근본 원인: BPF 디바이스 권한

```bash
# BPF 장치 확인
ls -l /dev/bpf*
# crw-------  1 root  wheel  /dev/bpf0

# 권한 구조:
# c   : Character device
# rw- : Owner (root) - Read/Write
# --- : Group - No permission
# --- : Others - No permission
```

**jNetPcap → BPF → 네트워크 카드 → 패킷 캡처**

---

## 자주 묻는 질문 (FAQ)

### Q1: 왜 같은 서브넷이어야 하나요?
**A:** 직접 연결(스위치/허브)에서는 **라우터가 없기 때문**입니다.

```
[같은 서브넷 - 직접 통신]
Mac (169.254.132.146/16) ↔ Switch ↔ Kali (169.254.132.200/16)
- ARP로 MAC 주소 획득 → 바로 전송

[다른 서브넷 - 라우터 필요]
Mac (192.168.1.10/24) → Router → ... → Router → Kali (10.0.0.20/8)
- 라우터가 없으면 통신 불가능
```

### Q2: 메시지가 한 번만 가는데 왜 2~3번 수신되나요?
**A:** 물리 계층에서 **패킷 루프백** 때문입니다.

```
스위치/허브 동작:
1. Mac에서 패킷 전송
2. 스위치가 모든 포트로 브로드캐스트
3. Kali 수신 (정상)
4. Mac 자신도 수신 (루프백) → EthernetLayer에서 차단
5. 하지만 일부 패킷은 중복 수신됨

해결: ChatAppLayer에서 타임스탬프 기반 중복 필터링
```

### Q3: arping이 없으면 어떻게 하나요?
**A:** 스크립트가 자동으로 처리합니다.

```bash
# arping 없으면:
[WARN] arping 미설치, 충돌 검사 생략

# 설치 방법 (Kali Linux):
sudo apt-get update
sudo apt-get install -y arping

# 설치 후 재실행:
sudo ./setup_eth0_ip_v2.sh
```

### Q4: 매번 IP가 바뀌면 불편한데요?
**A:** 맥북과 칼리 모두 **고정 IP 설정**을 권장합니다.

```bash
# 맥북 (en7 고정 IP 설정)
sudo ifconfig en7 169.254.100.1 netmask 255.255.0.0

# 칼리 (자동 생성 대신 수동 지정)
sudo ./setup_eth0_ip_v2.sh 169.254.100.2

# 이후 Java 프로그램에서도 고정 IP 사용
```

---

## 추가 디버깅 팁

### 네트워크 상태 확인
```bash
# 맥북
ifconfig en7
arp -an | grep 169.254

# 칼리
ip addr show eth0
ip neighbor show
```

### 패킷 캡처로 중복 확인
```bash
# 칼리에서 tcpdump 실행
sudo tcpdump -i eth0 -n 'ip proto 253' -v

# 같은 타임스탬프의 패킷이 여러 번 보이면 중복
```

### 로그 분석
```bash
# packet.log 확인
tail -f packet.log | grep RECV

# 같은 타임스탬프 검색
grep "sent=1765190577896" packet.log
```

---

## 변경 이력

- **2025-12-08**: 초기 버전 작성
  - 중복 메시지 필터링 추가
  - IP 충돌 검사 스크립트 개선
  - sudo 권한 요구사항 문서화
