# Ubuntu 실행 가이드 (Quick Reference)

## 🔥 빠른 해결 방법

사진에서 보이는 에러들을 해결하는 방법:

### 에러 1: `/usr/libexec/java_home: No such file or directory`
❌ **원인**: macOS 전용 명령어를 Ubuntu에서 사용
✅ **해결**: `run.sh`가 자동으로 OS를 감지하도록 수정됨

### 에러 2: `release version 21 not supported`
❌ **원인**: Java 21이 설치되지 않았거나 JAVA_HOME 미설정
✅ **해결**:
```bash
# Java 21 설치
sudo apt install openjdk-21-jdk

# 환경 변수 설정
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# 다시 실행
./run.sh
```

### 에러 3: `Could not find or load main class com.demo.ARPChatApp`
❌ **원인**: 클래스 경로 문제
✅ **해결**:
```bash
mvn clean compile
mvn exec:exec@run-app
```

---

## 📦 완전 자동 설치 (권장)

```bash
# 1. 모든 의존성 자동 설치
sudo ./setup_ubuntu.sh

# 2. 환경 변수 적용
source ~/.bashrc

# 3. 실행
./run.sh
```

---

## 🔧 수동 설치 (자동 설치 실패 시)

```bash
# 1. Java 21 설치
sudo apt update
sudo apt install -y openjdk-21-jdk

# 2. Maven 설치
sudo apt install -y maven

# 3. libpcap 설치
sudo apt install -y libpcap-dev

# 4. 환경 변수 설정
echo 'export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64' >> ~/.bashrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc

# 5. 네트워크 권한 부여
sudo setcap cap_net_raw,cap_net_admin=eip $JAVA_HOME/bin/java

# 6. 실행
./run.sh
```

---

## ✅ 설치 확인

```bash
# Java 버전 확인
java --version
# 출력: openjdk 21.0.x ...

# Maven 버전 확인  
mvn --version
# 출력: Apache Maven 3.x.x

# JAVA_HOME 확인
echo $JAVA_HOME
# 출력: /usr/lib/jvm/java-21-openjdk-amd64
```

---

## 🚀 실행 방법

### 방법 1: run.sh 사용 (권장)
```bash
./run.sh
```

### 방법 2: Maven 직접 사용
```bash
mvn clean compile
mvn exec:exec@run-app
```

### 방법 3: sudo로 실행 (권한 문제 시)
```bash
sudo ./run.sh
```

---

## 📝 체크리스트

실행 전 확인사항:

- [ ] Java 21 설치됨
- [ ] Maven 설치됨
- [ ] JAVA_HOME 환경 변수 설정
- [ ] libpcap 설치됨 (Ubuntu)
- [ ] 네트워크 권한 부여 또는 sudo 사용
- [ ] GUI 환경 (X11/Wayland)

---

## 🆘 여전히 안 될 때

### 1. 완전 초기화
```bash
mvn clean
rm -rf target/
./run.sh
```

### 2. 디버그 모드
```bash
mvn -X clean compile
mvn -X exec:exec@run-app
```

### 3. Java 경로 직접 확인
```bash
ls -la /usr/lib/jvm/
# java-21-openjdk-amd64가 있는지 확인
```

### 4. 수동 Java 실행
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
mvn compile
$JAVA_HOME/bin/java \
  --enable-preview \
  --enable-native-access=ALL-UNNAMED \
  -cp target/classes:lib/jnetpcap-wrapper-2.3.1-jdk21.jar \
  -Djava.library.path=lib/native \
  com.demo.ARPChatApp
```

---

## 📞 추가 문서

- [UBUNTU_SETUP.md](UBUNTU_SETUP.md) - 상세 설치 가이드
- [README.md](README.md) - 프로젝트 개요
- `mvn test` - 테스트 실행으로 설치 검증

---

## 💡 핵심 포인트

1. **Ubuntu는 macOS와 다릅니다**
   - `java_home` 명령어 없음 → JAVA_HOME 수동 설정
   - 네트워크 권한 필요 → sudo 또는 setcap

2. **Java 21 필수**
   - Java 17 이하는 작동 안 함
   - `release version 21 not supported` 에러 = Java 21 미설치

3. **자동 설치 스크립트 사용**
   - `sudo ./setup_ubuntu.sh` 한 번이면 모든 준비 완료
