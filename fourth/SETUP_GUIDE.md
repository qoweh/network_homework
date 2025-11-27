# Lab 4 실행 가이드

## 빠른 시작

### macOS
```bash
./run.sh
```

### Ubuntu (처음 실행 시)
```bash
./setup.sh              # 환경 설정 (Java 21, Maven 3.9.9)
source ~/.bashrc        # 또는 터미널 재시작
./run.sh                # 프로그램 실행
```

## 스크립트 설명

### `setup.sh` - 환경 설정 (Ubuntu 전용)
- Java 21 설치 확인
- Maven 3.9.9로 자동 업그레이드 (3.9 미만인 경우)
- libpcap 설치
- 권한 설정
- 환경변수 자동 설정

### `run.sh` - 프로그램 실행 (macOS & Ubuntu)
- OS 자동 감지
- JAVA_HOME 자동 설정
- Maven clean & compile
- GUI 프로그램 실행

## Ubuntu GUI 실행

X11 forwarding 필요:
```bash
# SSH 연결 시
ssh -X user@server

# XQuartz 설치 (Mac에서 접속하는 경우)
brew install --cask xquartz
```

## 문제 해결

### 컴파일 에러
```bash
./setup.sh              # 환경 재설정
source ~/.bashrc
./run.sh
```

### Maven 버전 확인
```bash
mvn -version            # 3.9.9 이상이어야 함
```

### Java 버전 확인
```bash
java -version           # OpenJDK 21
```
