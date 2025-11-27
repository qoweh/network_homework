# Ubuntu 24.04.3 LTS VM 설정 가이드

## 시스템 정보
- **OS**: Ubuntu 24.04.3 LTS (Noble Numbat)
- **Architecture**: ARM64 (aarch64)
- **현재 Maven**: 3.8.7
- **현재 Java**: OpenJDK 21.0.9

## 문제점
Maven 3.8.7에서 Java 21 컴파일 시 "release version 21 not supported" 에러 발생

## 해결 방법

### 방법 1: Maven 업그레이드 (권장)

```bash
# 1. Maven 3.9.9로 업그레이드
./upgrade_maven.sh

# 2. 터미널 재시작 또는 환경변수 적용
source /etc/profile.d/maven.sh

# 3. 프로그램 실행
./simple_run.sh
```

### 방법 2: 빠른 진단

```bash
# 현재 상태 진단 및 상세 로그 확인
./quick_fix_ubuntu.sh
```

## 상세 설명

### upgrade_maven.sh
- Maven 3.8.7 → 3.9.9 자동 업그레이드
- /opt/apache-maven-3.9.9에 설치
- 환경변수 자동 설정 (/etc/profile.d/maven.sh)
- 기존 시스템 Maven 유지 옵션

### simple_run.sh
- Ubuntu VM용 실행 스크립트
- JAVA_HOME 자동 설정 (ARM64)
- Maven 3.9.9 자동 감지
- Clean → Compile → Test → Run 순서 실행

### quick_fix_ubuntu.sh
- 빠른 문제 진단 스크립트
- Maven 캐시 삭제
- 상세 컴파일 로그 출력

## 실행 순서

```bash
# Git clone 후
cd fourth

# 스크립트 실행 권한 부여 (이미 완료됨)
chmod +x *.sh

# Maven 업그레이드
./upgrade_maven.sh

# 터미널 재시작 또는
source /etc/profile.d/maven.sh

# 프로그램 실행
./simple_run.sh
```

## GUI 실행 주의사항

이 프로그램은 GUI 기반이므로 X11 forwarding 필요:

```bash
# SSH 연결 시 -X 옵션 사용
ssh -X user@vm-server

# 또는 -Y 옵션 (trusted)
ssh -Y user@vm-server
```

로컬 Mac에서 XQuartz 실행 필요:
```bash
brew install --cask xquartz
```

## 문제 해결

### Maven 버전 확인
```bash
mvn -version
# Apache Maven 3.9.9 이상이어야 함
```

### Java 버전 확인
```bash
java -version
# openjdk version "21.0.9" 확인
```

### JAVA_HOME 확인
```bash
echo $JAVA_HOME
# /usr/lib/jvm/java-21-openjdk-arm64
```

### 수동 컴파일
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
export M2_HOME=/opt/apache-maven-3.9.9
export PATH=$M2_HOME/bin:$JAVA_HOME/bin:$PATH

mvn clean compile
```

## 기술 상세

### 에러 원인
- Maven 3.8.7의 Java 21 지원 불완전
- maven-compiler-plugin 3.13.0과 호환성 이슈
- 일부 빌드 도구체인에서 release flag 인식 실패

### 해결 원리
- Maven 3.9.9는 Java 21 완전 지원
- 업데이트된 컴파일러 플러그인 호환성
- 개선된 빌드 도구체인

## 참고

- Maven 3.9.x 이상 권장
- Java 21 최소 요구사항: Maven 3.8.5+
- 안정적인 빌드: Maven 3.9.9 + Java 21
