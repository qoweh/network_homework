#!/bin/bash

# Ubuntu Maven Toolchains 설정 스크립트
# Maven이 Java 21을 올바르게 사용하도록 강제

echo "======================================"
echo "Maven Toolchains 설정"
echo "======================================"

# JAVA_HOME 설정
ARCH=$(uname -m)
if [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
else
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
fi

echo "JAVA_HOME: $JAVA_HOME"

# Maven 설정 디렉토리 생성
mkdir -p ~/.m2

# toolchains.xml 생성
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
cat ~/.m2/toolchains.xml

# Maven settings.xml도 업데이트
echo ""
echo "======================================"
echo "컴파일 테스트"
echo "======================================"

cd "$(dirname "$0")"

# 환경변수 설정
export JAVA_HOME=$JAVA_HOME
export PATH=$JAVA_HOME/bin:$PATH

if [ -d "/opt/apache-maven-3.9.9" ]; then
    export M2_HOME=/opt/apache-maven-3.9.9
    export PATH=$M2_HOME/bin:$PATH
fi

# Maven clean & compile
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
    echo "Java alternatives 설정 필요:"
    echo "sudo update-alternatives --config java"
    echo "sudo update-alternatives --config javac"
fi
