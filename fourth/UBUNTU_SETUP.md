# Ubuntu에서 실행하기

## 1. 필수 패키지 설치

```bash
# Java 21 설치
sudo apt update
sudo apt install -y openjdk-21-jdk

# Maven 설치
sudo apt install -y maven

# libpcap 개발 라이브러리 설치 (jNetPcap 필요)
sudo apt install -y libpcap-dev

# Git 설치 (없는 경우)
sudo apt install -y git
```

## 2. Java 21 확인

```bash
java --version
# 출력: openjdk 21.0.x ...

# JAVA_HOME 확인
echo $JAVA_HOME
# 출력이 없으면 수동 설정
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

## 3. 프로젝트 클론

```bash
git clone <repository-url>
cd network_homework/fourth
```

## 4. 실행

```bash
chmod +x run.sh
./run.sh
```

## 5. 문제 해결

### 문제 1: `release version 21 not supported`
**원인**: Maven이 Java 21을 인식하지 못함

**해결**:
```bash
# Java 21이 설치되어 있는지 확인
ls -l /usr/lib/jvm/

# JAVA_HOME 수동 설정
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# 다시 실행
./run.sh
```

### 문제 2: `Could not find or load main class`
**원인**: 클래스 경로 문제

**해결**:
```bash
# 클린 빌드
mvn clean compile
mvn exec:exec@run-app
```

### 문제 3: jNetPcap 관련 오류
**원인**: Native 라이브러리 경로 문제

**해결**:
```bash
# libpcap 설치 확인
dpkg -l | grep libpcap

# 없으면 설치
sudo apt install -y libpcap-dev libpcap0.8
```

### 문제 4: Permission denied (네트워크 캡처)
**원인**: 일반 사용자는 네트워크 인터페이스 접근 권한 없음

**해결 1 - sudo로 실행**:
```bash
sudo ./run.sh
```

**해결 2 - CAP_NET_RAW 권한 부여** (권장):
```bash
# Java 실행 파일에 권한 부여
sudo setcap cap_net_raw,cap_net_admin=eip $JAVA_HOME/bin/java

# 다시 실행 (sudo 없이)
./run.sh
```

## 6. 환경 변수 영구 설정

`~/.bashrc` 또는 `~/.profile`에 추가:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

적용:
```bash
source ~/.bashrc
```

## 7. 테스트 실행

```bash
# 모든 테스트 실행
mvn test

# 특정 테스트만 실행
mvn test -Dtest=FileAppLayerTest
mvn test -Dtest=ChatAppLayerTest
```

## 8. 수동 컴파일 및 실행

run.sh가 작동하지 않는 경우:

```bash
# 1. JAVA_HOME 설정
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# 2. 컴파일
mvn clean compile

# 3. 실행
mvn exec:exec@run-app
```

## 9. 디버그 모드 실행

```bash
# Maven 디버그 모드
mvn -X exec:exec@run-app

# Java 디버그 출력
mvn exec:exec@run-app -Dexec.args="-verbose:class"
```

## 10. 주의사항

- **Ubuntu 20.04 이상** 권장
- **Java 21** 필수 (Java 17 이하는 지원 안 됨)
- **Maven 3.6+** 필요
- **루트 권한** 또는 **CAP_NET_RAW** 필요 (네트워크 캡처용)
- **GUI 환경** 필요 (X11, Wayland)
  - GUI 없는 서버: `ssh -X` 또는 `xvfb` 사용

## 11. 원격 서버(GUI 없음)에서 실행

```bash
# Xvfb (가상 X 서버) 설치
sudo apt install -y xvfb

# Xvfb로 실행
xvfb-run -a ./run.sh
```
