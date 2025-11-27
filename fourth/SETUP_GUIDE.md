# Lab 4 실행 가이드

## 빠른 시작

### macOS
```bash
./run.sh
```

### Ubuntu (처음 실행 시)
```bash
# 1. 환경 설정 (한 번만)
./setup_toolchains.sh

# 2. SSH X11 forwarding 설정
# Mac에서 접속하는 경우:
brew install --cask xquartz  # XQuartz 설치
# 터미널 재시작 후:
ssh -X user@ubuntu-server

# 3. 프로그램 실행
./run.sh
```

## 스크립트 설명

### `setup_toolchains.sh` - Ubuntu 환경 설정 (한 번만)
- Java 21 JDK 설치 확인 및 자동 설치
- javac alternatives 등록
- Maven 3.9.9 업그레이드
- Maven toolchains.xml 생성
- 컴파일 테스트
- **Ubuntu 서버에서 처음 실행할 때만 필요**

### `run.sh` - 프로그램 실행 (macOS & Ubuntu)
- OS 자동 감지
- JAVA_HOME 자동 설정
- X11 DISPLAY 확인 (Ubuntu)
- Maven clean & compile
- GUI 프로그램 실행

## Ubuntu GUI 실행

### X11 Forwarding 설정

**필수 요구사항:**
1. SSH 접속 시 `-X` 옵션 사용
2. XQuartz 설치 (Mac에서 접속하는 경우)

```bash
# Mac에서
brew install --cask xquartz

# XQuartz 실행 후 터미널 재시작
# SSH 접속
ssh -X user@ubuntu-server

# DISPLAY 환경변수 확인
echo $DISPLAY  # 출력: localhost:10.0 같은 값이 나와야 함

# 프로그램 실행
cd /network_homework/fourth
./run.sh
```

### X11 문제 해결

**DISPLAY가 설정되지 않은 경우:**
```bash
export DISPLAY=:0
./run.sh
```

**권한 에러:**
```bash
xhost +local:
./run.sh
```

## 문제 해결

### 컴파일 에러
```bash
./setup_toolchains.sh  # 환경 재설정
./run.sh
```

### GUI 실행 안 됨
```bash
# DISPLAY 확인
echo $DISPLAY

# SSH 재접속 (X11 forwarding 포함)
ssh -X user@server
```

### Maven 버전 확인
```bash
mvn -version  # 3.9.9 이상
```

### Java 버전 확인
```bash
java -version   # OpenJDK 21
javac -version  # javac 21
```

