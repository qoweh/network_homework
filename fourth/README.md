# Lab 4: 파일 전송 채팅 프로그램

## 🚀 빠른 시작

### macOS
```bash
./run.sh
```

### Ubuntu/Linux
```bash
# 1. 환경 설정 (최초 1회만)
sudo ./setup_ubuntu.sh

# 2. 터미널 재시작 또는
source ~/.bashrc

# 3. 실행
./run.sh
```

## 📋 기능

- ✅ 채팅 메시지 전송 (Fragmentation 지원)
- ✅ 파일 전송 (Thread 기반, 진행률 표시)
- ✅ IP 프로토콜 역다중화 (Chat: 253, File: 254)
- ✅ ARP 캐시 관리
- ✅ Out-of-order Fragment 재조립
- ✅ UTF-8 한글 지원

## 🐛 Ubuntu 문제 해결

### 문제 1: "release version 21 not supported"
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
./run.sh
```

### 문제 2: "Permission denied"
```bash
sudo ./run.sh
```

자세한 내용: [UBUNTU_SETUP.md](UBUNTU_SETUP.md)

## 🧪 테스트
```bash
mvn test
```
**결과: 11/11 테스트 통과 ✅**
