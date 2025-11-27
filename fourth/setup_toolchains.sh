#!/bin/bash

# Ubuntu Maven Toolchains 설정 스크립트
# Maven이 Java 21을 올바르게 사용하도록 강제

echo "======================================"
echo "Java JDK 설치 확인"
echo "======================================"

# JAVA_HOME 설정
ARCH=$(uname -m)
if [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
else
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
fi

echo "JAVA_HOME: $JAVA_HOME"

# javac 존재 확인
if [ ! -f "$JAVA_HOME/bin/javac" ]; then
    echo "❌ javac가 설치되어 있지 않습니다. JDK를 설치합니다..."
    sudo apt update
    sudo apt install -y openjdk-21-jdk-headless
    
    # 설치 후 재확인
    if [ ! -f "$JAVA_HOME/bin/javac" ]; then
        echo "❌ JDK 설치 실패"
        exit 1
    fi
    echo "✓ JDK 설치 완료"
else
    echo "✓ javac 존재: $JAVA_HOME/bin/javac"
fi

# javac alternatives 등록
echo ""
echo "======================================"
echo "Java Alternatives 설정"
echo "======================================"

sudo update-alternatives --install /usr/bin/javac javac $JAVA_HOME/bin/javac 2111
sudo update-alternatives --set javac $JAVA_HOME/bin/javac

echo "✓ javac alternatives 설정 완료"

# Maven 설정 디렉토리 생성
mkdir -p ~/.m2

# toolchains.xml 생성
echo ""
echo "======================================"
echo "Maven Toolchains 설정"
echo "======================================"

cat > ~/.m2/toolchains.xml << EOF
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>21</version>
      <vendor>openjdk</vendor>
    </provides>
    <configuration>
      <jdkHome>$JAVA_HOME</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
EOF

echo "✓ toolchains.xml 생성 완료"

# 환경변수 설정
export JAVA_HOME=$JAVA_HOME
export PATH=$JAVA_HOME/bin:$PATH

if [ -d "/opt/apache-maven-3.9.9" ]; then
    export M2_HOME=/opt/apache-maven-3.9.9
    export PATH=$M2_HOME/bin:$PATH
fi

# 확인
echo ""
echo "======================================"
echo "설정 확인"
echo "======================================"
echo "java: $(which java) -> $(java -version 2>&1 | head -n 1)"
echo "javac: $(which javac) -> $(javac -version 2>&1)"
echo "Maven: $(which mvn) -> $(mvn -version 2>&1 | head -n 1)"

# Maven 캐시 삭제
echo ""
echo "======================================"
echo "Maven 캐시 정리"
echo "======================================"
rm -rf ~/.m2/repository/com/demo
rm -rf target/

# 컴파일 테스트
echo ""
echo "======================================"
echo "컴파일 테스트"
echo "======================================"

cd "$(dirname "$0")"

mvn clean compile

if [ $? -eq 0 ]; then
    echo ""
    echo "======================================"
    echo "✅ 컴파일 성공!"
    echo "======================================"
    echo ""
    echo "이제 ./run.sh를 실행하세요."
else
    echo ""
    echo "======================================"
    echo "❌ 컴파일 실패"
    echo "======================================"
fi
