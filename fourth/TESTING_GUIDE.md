# Lab 4 테스트 가이드

## 🎯 테스트 준비

### 1단계: 프로그램 실행
```bash
cd fourth
sudo ./run.sh
```

**중요**: 두 대의 컴퓨터(또는 VM)에서 동시에 실행해야 합니다.
- 컴퓨터 A: 192.168.64.7 
- 컴퓨터 B: 192.168.64.1

---

## 📝 초기 설정 방법

### 컴퓨터 A (왼쪽 화면) 설정

1. **네트워크 어댑터 선택**
   - 드롭다운에서 실제 네트워크 인터페이스 선택
   - Ubuntu: `enp0s1` 또는 `eth0`
   - macOS: `en0` 또는 `vmenet0`

2. **MAC 주소 확인**
   - 어댑터 선택하면 **자동으로 채워짐**
   - 만약 `00:00:00:00:00:00`으로 나오면:
     ```bash
     # 터미널에서 확인
     ifconfig
     # 또는
     ip link show
     ```
   - 실제 MAC 주소를 복사해서 수동으로 입력 (예: `52:54:00:12:34:56`)

3. **IP 주소 입력**
   - 내 IP 주소: `192.168.64.7`
   - 목적지 IP: `192.168.64.1` (상대방 컴퓨터 IP)

4. **설정 버튼 클릭**
   - 콘솔에 "설정 완료 - 통신 준비됨" 메시지 확인
   - Gratuitous ARP가 자동 전송됨

### 컴퓨터 B (오른쪽 화면) 설정

1. **네트워크 어댑터 선택**
   - 실제 네트워크 인터페이스 선택

2. **MAC 주소 확인**
   - 자동으로 채워지는지 확인

3. **IP 주소 입력**
   - 내 IP 주소: `192.168.64.1`
   - 목적지 IP: `192.168.64.7` (상대방 컴퓨터 IP)

4. **설정 버튼 클릭**

---

## 🧪 테스트 시나리오

### 테스트 1: ARP 통신 확인

**목적**: ARP 프로토콜이 정상 작동하는지 확인

**컴퓨터 A에서:**
1. "ARP Request" 버튼 클릭
2. 메시지 영역에서 확인:
   ```
   [ARP] Request 전송: 192.168.64.1의 MAC 주소는?
   [ARP] Reply 수신: 192.168.64.1 -> AA:BB:CC:DD:EE:FF
   ```
3. ARP 캐시 테이블에 상대방 IP/MAC 추가됨 확인

**컴퓨터 B에서:**
1. 메시지 영역에서 확인:
   ```
   [ARP] Request 수신: 192.168.64.7이 내 MAC을 요청
   [ARP] Reply 전송: 192.168.64.1 -> AA:BB:CC:DD:EE:FF
   ```
2. ARP 캐시 테이블에 상대방 IP/MAC 자동 추가됨

**기대 결과**: ✅ 양쪽 컴퓨터의 ARP 캐시 테이블에 서로의 정보가 등록됨

---

### 테스트 2: 채팅 메시지 전송

**목적**: 짧은 메시지와 긴 메시지 Fragmentation 테스트

#### 2-1. 짧은 메시지 (Fragmentation 없음)

**컴퓨터 A에서:**
1. 메시지 입력: `hi`
2. "전송" 버튼 클릭 또는 Enter
3. 메시지 영역 확인:
   ```
   [시스템] 메시지 전송: hi (Fragmentation: No)
   ```

**컴퓨터 B에서:**
1. 수신 메시지 확인:
   ```
   [수신] 메시지: hi
   ```

**기대 결과**: ✅ 메시지가 즉시 전송되고 수신됨 (1개 패킷)

#### 2-2. 긴 메시지 (Fragmentation 있음)

**컴퓨터 A에서:**
1. 메시지 입력: 512바이트 이상 긴 텍스트
   ```
   Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas.
   ```
2. "전송" 버튼 클릭
3. 메시지 영역 확인:
   ```
   [시스템] 메시지 전송: Lorem... (Fragmentation: Yes, 3 fragments)
   ```

**컴퓨터 B에서:**
1. 수신 메시지 확인:
   ```
   [수신] Fragment 1/3 수신 중...
   [수신] Fragment 2/3 수신 중...
   [수신] Fragment 3/3 수신 중...
   [수신] 메시지 재조립 완료: Lorem ipsum...
   ```

**기대 결과**: ✅ 메시지가 분할 전송되고 정확히 재조립됨

#### 2-3. 한글 메시지 (UTF-8 인코딩)

**컴퓨터 A에서:**
1. 메시지 입력: `안녕하세요`
2. "전송" 버튼 클릭

**컴퓨터 B에서:**
1. 수신 메시지 확인:
   ```
   [수신] 메시지: 안녕하세요
   ```

**기대 결과**: ✅ 한글이 깨지지 않고 정확히 전송됨

---

### 테스트 3: 파일 전송

**목적**: 파일 전송 및 진행률 표시 테스트

#### 3-1. 작은 파일 전송 (1KB 미만)

**컴퓨터 A에서:**
1. "파일 선택" 버튼 클릭
2. 작은 텍스트 파일 선택 (예: `test.txt`)
3. "파일 전송" 버튼 클릭
4. 진행률 표시줄 확인:
   ```
   전송 중... 0% → 50% → 100%
   ```
5. 상태: `전송 완료: test.txt (512 bytes)`

**컴퓨터 B에서:**
1. 메시지 영역 확인:
   ```
   [파일] 수신 시작: test.txt (512 bytes)
   [파일] 수신 중... 0% → 50% → 100%
   [파일] 수신 완료: received_files/test.txt
   ```
2. `received_files/` 폴더에 파일 저장 확인

**기대 결과**: ✅ 파일이 정확히 전송되고 저장됨

#### 3-2. 큰 파일 전송 (1KB 이상, Fragmentation)

**컴퓨터 A에서:**
1. "파일 선택" 버튼 클릭
2. 큰 파일 선택 (예: 이미지, PDF 등)
3. "파일 전송" 버튼 클릭
4. 진행률 실시간 확인:
   ```
   전송 중... 10% → 20% → ... → 100%
   ```

**컴퓨터 B에서:**
1. 진행률 실시간 확인:
   ```
   수신 중... 10% → 20% → ... → 100%
   ```
2. 파일 내용 비교:
   ```bash
   # 원본과 수신 파일 비교
   diff original.pdf received_files/original.pdf
   ```

**기대 결과**: ✅ 파일이 1KB 단위로 분할 전송되고 정확히 재조립됨

#### 3-3. 파일 전송 중 채팅 (동시 작업)

**컴퓨터 A에서:**
1. 큰 파일 전송 시작 (진행 중)
2. 동시에 메시지 입력: `파일 전송 중입니다`
3. "전송" 버튼 클릭

**컴퓨터 B에서:**
1. 파일 수신 진행 중
2. 동시에 메시지 수신 확인

**기대 결과**: ✅ 파일 전송과 채팅이 동시에 정상 작동 (Thread 기반)

---

### 테스트 4: Gratuitous ARP

**목적**: 네트워크 진입 알림 테스트

**컴퓨터 A에서:**
1. "Gratuitous ARP" 버튼 클릭
2. 메시지 확인:
   ```
   [ARP] Gratuitous ARP 전송: 내 IP는 192.168.64.7, MAC은 AA:BB:CC:DD:EE:FF
   ```

**컴퓨터 B에서:**
1. 메시지 확인:
   ```
   [ARP] Gratuitous ARP 수신: 192.168.64.7 -> AA:BB:CC:DD:EE:FF
   ```
2. ARP 캐시 테이블 자동 업데이트 확인

**기대 결과**: ✅ 상대방 ARP 캐시에 내 정보가 자동 등록됨

---

### 테스트 5: Proxy ARP

**목적**: 대리 ARP 응답 테스트

**컴퓨터 A에서:**
1. "Proxy ARP 설정" 체크박스 선택
2. Proxy IP 입력: `192.168.0.200`
3. Proxy MAC 입력: `AA:BB:CC:DD:EE:FF`
4. "Proxy 추가" 버튼 클릭
5. 콘솔 확인:
   ```
   [시스템] Proxy ARP 설정: 192.168.0.200 -> AA:BB:CC:DD:EE:FF
   ```

**컴퓨터 B에서:**
1. 목적지 IP를 Proxy IP로 변경: `192.168.0.200`
2. "ARP Request" 버튼 클릭
3. 메시지 확인:
   ```
   [ARP] Request 전송: 192.168.0.200의 MAC 주소는?
   ```

**컴퓨터 A에서:**
1. Proxy ARP 응답 확인:
   ```
   [ARP] Proxy ARP 응답: 192.168.0.200 -> AA:BB:CC:DD:EE:FF
   ```

**기대 결과**: ✅ 실제 존재하지 않는 IP에 대해 Proxy ARP가 응답

---

## ❌ 문제 해결

### 문제 1: MAC 주소가 00:00:00:00:00:00으로 표시됨

**원인**: 자동 로드 실패

**해결 방법**:
```bash
# 터미널에서 실제 MAC 주소 확인
ifconfig

# 예시 출력:
# enp0s1: flags=4163<UP,BROADCAST,RUNNING,MULTICAST>  mtu 1500
#         ether 52:54:00:12:34:56  txqueuelen 1000  (Ethernet)
```
- `ether` 다음에 나오는 주소를 GUI에 수동 입력
- 형식: `52:54:00:12:34:56`

### 문제 2: "메시지 전송 실패 (ARP 캐시 확인 필요)"

**원인**: 목적지 MAC 주소가 ARP 캐시에 없음

**해결 방법**:
1. "ARP Request" 버튼 클릭
2. ARP 캐시 테이블에서 목적지 IP 확인
3. 다시 메시지 전송

### 문제 3: 파일이 수신되지 않음

**원인**: IP 프로토콜 역다중화 문제 또는 ARP 캐시 문제

**해결 방법**:
1. ARP Request로 목적지 MAC 주소 확보
2. 작은 파일로 먼저 테스트
3. 콘솔 로그 확인:
   ```
   [IP] Protocol 254 수신 (FileApp)
   ```

### 문제 4: 프로그램이 실행되지 않음

**원인**: 관리자 권한 없음

**해결 방법**:
```bash
sudo ./run.sh
```

### 문제 5: 네트워크 어댑터가 목록에 없음

**원인**: jNetPcap이 장치를 찾지 못함

**해결 방법**:
```bash
# 네트워크 인터페이스 확인
ifconfig -a

# 권한 확인
sudo chmod +x run.sh
sudo ./run.sh
```

---

## 📊 테스트 체크리스트

- [ ] 프로그램 실행 (양쪽 컴퓨터)
- [ ] 네트워크 어댑터 선택
- [ ] MAC 주소 자동 로드 확인
- [ ] IP 주소 설정
- [ ] 설정 버튼 클릭
- [ ] ARP Request/Reply 테스트
- [ ] 짧은 메시지 전송
- [ ] 긴 메시지 전송 (Fragmentation)
- [ ] 한글 메시지 전송
- [ ] 작은 파일 전송
- [ ] 큰 파일 전송 (Fragmentation)
- [ ] 파일 전송 중 채팅
- [ ] Gratuitous ARP 테스트
- [ ] Proxy ARP 테스트
- [ ] 수신 파일 내용 확인

---

## 🎓 예상 결과

모든 테스트가 성공하면:
- ✅ ARP 프로토콜 정상 작동
- ✅ 채팅 메시지 Fragmentation 및 재조립
- ✅ 파일 전송 Fragmentation 및 재조립
- ✅ IP 프로토콜 역다중화 (253, 254)
- ✅ Thread 기반 동시 작업
- ✅ UTF-8 한글 지원
