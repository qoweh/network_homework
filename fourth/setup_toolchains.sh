#!/bin/bash

# Ubuntu Maven Toolchains 설정 스크립트
# Maven이 Java 21을 올바르게 사용하도록 강제

echo "======================================"
echo "Java Alternatives 설정"
echo "======================================"

# JAVA_HOME 설정
ARCH=$(uname -m)
if [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
else
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
fi

echo "JAVA_HOME: $JAVA_HOME"

# javac alternatives 등록 (없을 경우)
if ! sudo update-alternatives --list javac > /dev/null 2>&1; then
    echo "javac alternatives 등록 중..."
    sudo update-alternatives --install /usr/bin/javac javac $JAVA_HOME/bin/javac 2111
    sudo update-alternatives --set javac $JAVA_HOME/bin/javac
    echo "✓ javac alternatives 등록 완료"
else
    echo "✓ javac alternatives 이미 등록됨"
    sudo update-alternatives --set javac $JAVA_HOME/bin/javac
fi

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
echo "java: $(which java)"
echo "javac: $(which javac)"
echo "Java version: $(java -version 2>&1 | head -n 1)"
echo "javac version: $(javac -version 2>&1)"
echo "Maven version: $(mvn -version 2>&1 | head -n 1)"

# Maven 캐시 삭제 (이전 실패한 빌드 정리)
echo ""
echo "======================================"
echo "Maven 캐시 정리"
echo "======================================"
rm -rf ~/.m2/repository/com/demo

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
    echo "❌ 여전히 실패"
    echo "======================================"
    echo ""
    echo "상세 로그:"
    mvn compile -X 2>&1 | grep -A 10 "release version"
fi
