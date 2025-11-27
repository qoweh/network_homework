#!/bin/bash

# Maven 업그레이드 스크립트 for Ubuntu 24.04.3 LTS
# Maven 3.8.7 -> Maven 3.9.9로 업그레이드

echo "======================================"
echo "Maven 업그레이드 시작"
echo "======================================"

# 현재 Maven 버전 확인
echo "현재 Maven 버전:"
mvn -version

# Maven 3.9.9 다운로드
echo ""
echo "Maven 3.9.9 다운로드 중..."
cd /tmp
wget https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz

if [ $? -ne 0 ]; then
    echo "❌ Maven 다운로드 실패. 미러 사이트 시도 중..."
    wget https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz
fi

# 압축 해제
echo "압축 해제 중..."
tar -xzf apache-maven-3.9.9-bin.tar.gz

# /opt/로 이동 (sudo 필요)
echo "Maven 설치 중... (sudo 권한 필요)"
sudo mv apache-maven-3.9.9 /opt/

# 기존 시스템 Maven 제거 (선택사항)
echo ""
echo "기존 시스템 Maven 유지할까요? (y/n)"
read -r response
if [[ "$response" != "y" ]]; then
    sudo apt remove maven -y
fi

# 환경변수 설정
echo ""
echo "환경변수 설정 중..."
echo 'export M2_HOME=/opt/apache-maven-3.9.9' | sudo tee -a /etc/profile.d/maven.sh
echo 'export PATH=$M2_HOME/bin:$PATH' | sudo tee -a /etc/profile.d/maven.sh
sudo chmod +x /etc/profile.d/maven.sh

# 현재 세션에 적용
export M2_HOME=/opt/apache-maven-3.9.9
export PATH=$M2_HOME/bin:$PATH

# 임시 파일 삭제
rm -f /tmp/apache-maven-3.9.9-bin.tar.gz

echo ""
echo "======================================"
echo "✅ Maven 업그레이드 완료!"
echo "======================================"
echo ""
echo "새로운 Maven 버전:"
/opt/apache-maven-3.9.9/bin/mvn -version

echo ""
echo "⚠️  중요: 터미널을 재시작하거나 다음 명령을 실행하세요:"
echo "source /etc/profile.d/maven.sh"
echo ""
echo "또는 현재 세션에서 바로 사용하려면:"
echo "export M2_HOME=/opt/apache-maven-3.9.9"
echo "export PATH=\$M2_HOME/bin:\$PATH"
