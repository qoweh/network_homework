## 패킷 전송하기

java --enable-preview --enable-native-access=ALL-UNNAMED \
     -cp target/classes:lib/jnetpcap-wrapper-2.3.1-jdk21.jar \
     -Djava.library.path=/path/to/jnetpcap/native/libs \
     com.demo.NetworkDeviceScan 0

## GUI 실행하기

java --enable-preview \
     --enable-native-access=ALL-UNNAMED \
     -cp target/classes:lib/jnetpcap-wrapper-2.3.1-jdk21.jar \
     -Djava.library.path=/path/to/jnetpcap/native/libs \
     com.demo.PacketSenderGui


## 한 번에 실행
export JAVA_HOME=$(/usr/libexec/java_home -v 21)          
export PATH="$JAVA_HOME/bin:$PATH"
mvn -DskipTests compile
java --enable-preview \
     --enable-native-access=ALL-UNNAMED \
     -cp target/classes:lib/jnetpcap-wrapper-2.3.1-jdk21.jar \
     -Djava.library.path=/path/to/jnetpcap/native/libs \
     com.demo.BasicChatApp
     <!-- com.demo.PacketSenderGui -->



## 패킷 filter
frame.protocols == "eth"
eth.type == 0xffff
