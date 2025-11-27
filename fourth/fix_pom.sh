#!/bin/bash

# pom.xml 자동 수정 스크립트
# Java 21 설정을 pom.xml에 추가합니다.

echo "======================================"
echo "pom.xml Java 21 설정 수정"
echo "======================================"
echo ""

cd ~/network_homework/fourth

# 백업 생성
cp pom.xml pom.xml.backup
echo "✓ pom.xml 백업 완료: pom.xml.backup"

# pom.xml에서 maven-compiler-plugin 섹션 찾아서 수정
# release, source, target 추가

cat > /tmp/compiler_config.xml << 'EOF'
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.13.0</version>
        <configuration>
          <release>21</release>
          <source>21</source>
          <target>21</target>
          <excludes>
            <exclude>**/DeprecatedBasicChatApp.java</exclude>
          </excludes>
          <compilerArgs>
            <arg>--enable-preview</arg>
          </compilerArgs>
          <forceJavacCompilerUse>true</forceJavacCompilerUse>
        </configuration>
      </plugin>
EOF

echo "✓ 새로운 설정 파일 생성 완료"
echo ""
echo "수동으로 pom.xml을 편집해야 합니다:"
echo ""
echo "1. 다음 명령으로 pom.xml 열기:"
echo "   nano ~/network_homework/fourth/pom.xml"
echo ""
echo "2. maven-compiler-plugin 섹션을 찾아서"
echo "   <configuration> 태그 안에 다음 3줄 추가:"
echo ""
echo "   <release>21</release>"
echo "   <source>21</source>"
echo "   <target>21</target>"
echo ""
echo "3. Ctrl+O로 저장, Ctrl+X로 종료"
echo ""
echo "또는 빠른 자동 수정:"
echo ""

# sed를 사용한 자동 수정
if grep -q "<release>21</release>" pom.xml; then
    echo "✓ pom.xml이 이미 수정되어 있습니다!"
else
    echo "자동 수정을 시도합니다..."
    
    # <configuration> 다음에 release, source, target 추가
    sed -i.bak '/<artifactId>maven-compiler-plugin<\/artifactId>/,/<\/configuration>/ {
        /<configuration>/a\
          <release>21</release>\
          <source>21</source>\
          <target>21</target>
    }' pom.xml
    
    if grep -q "<release>21</release>" pom.xml; then
        echo "✅ pom.xml 자동 수정 성공!"
        echo ""
        echo "이제 다음 명령으로 빌드하세요:"
        echo "  ./test_run.sh"
    else
        echo "⚠️  자동 수정 실패. 수동으로 편집이 필요합니다."
        echo ""
        echo "다음 명령으로 직접 수정하세요:"
        echo "  nano ~/network_homework/fourth/pom.xml"
    fi
fi

echo ""
echo "======================================"
