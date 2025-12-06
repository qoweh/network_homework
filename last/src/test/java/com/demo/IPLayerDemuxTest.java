package com.demo;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * IP 계층 역다중화 테스트
 */
public class IPLayerDemuxTest {
    
    private IPLayer senderIpLayer;
    private IPLayer receiverIpLayer;
    private ChatAppLayer receiverChatApp;
    private FileAppLayer receiverFileApp;
    private EthernetLayerMock senderEthernet;
    private EthernetLayerMock receiverEthernet;
    private ARPLayerMock senderArp;
    private ARPLayerMock receiverArp;
    
    /**
     * Ethernet 계층 Mock
     */
    static class EthernetLayerMock implements BaseLayer {
        private BaseLayer upperLayer;
        private byte[] lastSentData;
        
        @Override
        public boolean Send(byte[] input, int length) {
            lastSentData = new byte[length];
            System.arraycopy(input, 0, lastSentData, 0, length);
            System.out.println("[EthernetMock] 패킷 전송: " + length + " bytes");
            return true;
        }
        
        @Override
        public boolean Receive(byte[] input) {
            if (upperLayer != null) {
                return upperLayer.Receive(input);
            }
            return false;
        }
        
        public void simulateReceive(byte[] data) {
            if (upperLayer != null) {
                upperLayer.Receive(data);
            }
        }
        
        public byte[] getLastSentData() {
            return lastSentData;
        }
        
        public void setDstMac(byte[] mac) {
            // Mock: 아무것도 하지 않음
        }
        
        public void setEtherType(int type) {
            // Mock: 아무것도 하지 않음
        }
        
        @Override
        public String GetLayerName() { return "EthernetMock"; }
        
        @Override
        public BaseLayer GetUnderLayer() { return null; }
        
        @Override
        public BaseLayer GetUpperLayer(int index) { return upperLayer; }
        
        @Override
        public void SetUnderLayer(BaseLayer layer) {}
        
        @Override
        public void SetUpperLayer(BaseLayer layer) {
            this.upperLayer = layer;
        }
    }
    
    /**
     * ARP 계층 Mock
     */
    static class ARPLayerMock extends ARPLayer {
        private byte[] mockMac = new byte[]{0x11, 0x22, 0x33, 0x44, 0x55, 0x66};
        
        @Override
        public byte[] lookupArpCache(String ip) {
            // Mock: 항상 같은 MAC 주소 반환
            return mockMac;
        }
    }
    
    @BeforeEach
    void setUp() {
        // Sender 설정
        senderIpLayer = new IPLayer();
        senderEthernet = new EthernetLayerMock();
        senderArp = new ARPLayerMock();
        
        senderIpLayer.SetUnderLayer(senderEthernet);
        senderEthernet.SetUpperLayer(senderIpLayer);
        senderIpLayer.setArpLayer(senderArp);
        
        senderIpLayer.setMyIp(new byte[]{(byte)192, (byte)168, 1, 10});
        senderIpLayer.setDstIp(new byte[]{(byte)192, (byte)168, 1, 20});
        
        // Receiver 설정
        receiverIpLayer = new IPLayer();
        receiverEthernet = new EthernetLayerMock();
        receiverArp = new ARPLayerMock();
        
        receiverChatApp = new ChatAppLayer(null);
        receiverFileApp = new FileAppLayer();
        
        receiverIpLayer.SetUnderLayer(receiverEthernet);
        receiverEthernet.SetUpperLayer(receiverIpLayer);
        receiverIpLayer.setArpLayer(receiverArp);
        
        // 상위 계층 연결
        receiverChatApp.SetUnderLayer(receiverIpLayer);
        receiverIpLayer.SetUpperLayer(receiverChatApp);
        
        receiverFileApp.SetUnderLayer(receiverIpLayer);
        receiverIpLayer.SetUpperLayer(receiverFileApp);
        
        receiverIpLayer.setMyIp(new byte[]{(byte)192, (byte)168, 1, 20});
    }
    
    @Test
    @DisplayName("ChatApp 프로토콜 역다중화 테스트")
    void testChatProtocolDemux() throws Exception {
        CountDownLatch receiveLatch = new CountDownLatch(1);
        final String[] receivedMessage = {null};
        
        receiverChatApp.setOnReceive(msg -> {
            System.out.println("[Test] ChatApp 메시지 수신: " + msg);
            receivedMessage[0] = msg;
            receiveLatch.countDown();
        });
        
        // ChatApp 프로토콜로 설정
        senderIpLayer.useChatProtocol();
        
        // 메시지 전송 (새 헤더 형식: TYPE(1) + PRIORITY(1) + TIMESTAMP(8) + DATA)
        String testMessage = "Hello";
        byte[] messageBytes = testMessage.getBytes("UTF-8");
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(1 + 1 + 8 + messageBytes.length);
        buffer.put((byte) 0x01); // TYPE_CHAT_SINGLE
        buffer.put((byte) 1);    // PRIORITY_NORMAL
        buffer.putLong(System.currentTimeMillis()); // timestamp
        buffer.put(messageBytes);
        byte[] combined = buffer.array();
        
        senderIpLayer.Send(combined, combined.length);
        
        // IP 패킷 가져오기
        byte[] ipPacket = senderEthernet.getLastSentData();
        assertNotNull(ipPacket, "IP 패킷이 전송되지 않음");
        
        // Protocol 필드 확인 (9번째 바이트)
        assertEquals(253, ipPacket[9] & 0xFF, "ChatApp 프로토콜(253)이어야 함");
        
        // 수신 시뮬레이션
        receiverEthernet.simulateReceive(ipPacket);
        
        // 검증
        boolean received = receiveLatch.await(2, TimeUnit.SECONDS);
        assertTrue(received, "ChatApp 메시지 수신 실패");
        assertTrue(receivedMessage[0].contains(testMessage));
    }
    
    @Test
    @DisplayName("FileApp 프로토콜 역다중화 테스트")
    void testFileProtocolDemux() throws Exception {
        CountDownLatch receiveLatch = new CountDownLatch(1);
        final boolean[] fileStartReceived = {false};
        
        receiverFileApp.setOnReceiveProgress((fileName, progress) -> {
            System.out.println("[Test] FileApp 진행: " + fileName + " " + progress + "%");
            if (!fileStartReceived[0]) {
                fileStartReceived[0] = true;
                receiveLatch.countDown();
            }
        });
        
        // FileApp 프로토콜로 설정
        senderIpLayer.useFileProtocol();
        
        // FILE_START Fragment 생성
        byte[] fileName = "test.txt".getBytes("UTF-8");
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(50);
        buffer.put((byte) 0x01); // TYPE_FILE_START
        buffer.putInt(0); // sequence
        buffer.putInt(1); // totalSequences
        buffer.put((byte) fileName.length); // fileNameLen
        buffer.putLong(100); // fileSize
        buffer.put(fileName);
        
        senderIpLayer.Send(buffer.array(), buffer.position());
        
        // IP 패킷 가져오기
        byte[] ipPacket = senderEthernet.getLastSentData();
        assertNotNull(ipPacket);
        
        // Protocol 필드 확인
        assertEquals(254, ipPacket[9] & 0xFF, "FileApp 프로토콜(254)이어야 함");
        
        // 수신 시뮬레이션
        receiverEthernet.simulateReceive(ipPacket);
        
        // 검증
        boolean received = receiveLatch.await(2, TimeUnit.SECONDS);
        assertTrue(received, "FileApp Fragment 수신 실패");
        assertTrue(fileStartReceived[0], "FILE_START 처리 실패");
    }
    
    @Test
    @DisplayName("ChatApp과 FileApp 동시 사용 테스트")
    void testMultiplexing() throws Exception {
        CountDownLatch chatLatch = new CountDownLatch(1);
        CountDownLatch fileLatch = new CountDownLatch(1);
        
        final String[] receivedChatMsg = {null};
        final boolean[] fileReceived = {false};
        
        receiverChatApp.setOnReceive(msg -> {
            receivedChatMsg[0] = msg;
            chatLatch.countDown();
        });
        
        receiverFileApp.setOnReceiveProgress((fileName, progress) -> {
            fileReceived[0] = true;
            fileLatch.countDown();
        });
        
        // 1. ChatApp 메시지 전송 (새 헤더 형식: TYPE(1) + PRIORITY(1) + TIMESTAMP(8) + DATA)
        senderIpLayer.useChatProtocol();
        String chatMessage = "Test";
        byte[] chatMsgBytes = chatMessage.getBytes("UTF-8");
        java.nio.ByteBuffer chatBuffer = java.nio.ByteBuffer.allocate(1 + 1 + 8 + chatMsgBytes.length);
        chatBuffer.put((byte) 0x01); // TYPE_CHAT_SINGLE
        chatBuffer.put((byte) 1);    // PRIORITY_NORMAL
        chatBuffer.putLong(System.currentTimeMillis()); // timestamp
        chatBuffer.put(chatMsgBytes);
        byte[] chatCombined = chatBuffer.array();
        
        senderIpLayer.Send(chatCombined, chatCombined.length);
        byte[] chatPacket = senderEthernet.getLastSentData();
        
        // 2. FileApp Fragment 전송
        senderIpLayer.useFileProtocol();
        byte[] fileNameBytes = "test.dat".getBytes("UTF-8");
        java.nio.ByteBuffer fileBuffer = java.nio.ByteBuffer.allocate(50);
        fileBuffer.put((byte) 0x01);
        fileBuffer.putInt(0);
        fileBuffer.putInt(1);
        fileBuffer.put((byte) fileNameBytes.length);
        fileBuffer.putLong(50);
        fileBuffer.put(fileNameBytes);
        
        senderIpLayer.Send(fileBuffer.array(), fileBuffer.position());
        byte[] filePacket = senderEthernet.getLastSentData();
        
        // 3. 수신 (순서 무관)
        receiverEthernet.simulateReceive(chatPacket);
        receiverEthernet.simulateReceive(filePacket);
        
        // 4. 검증
        boolean chatReceived = chatLatch.await(2, TimeUnit.SECONDS);
        boolean fileReceivedOk = fileLatch.await(2, TimeUnit.SECONDS);
        
        assertTrue(chatReceived, "ChatApp 메시지 수신 실패");
        assertTrue(fileReceivedOk, "FileApp Fragment 수신 실패");
        assertTrue(receivedChatMsg[0].contains(chatMessage));
        assertTrue(fileReceived[0]);
        
        System.out.println("[Test] ChatApp과 FileApp 동시 사용 성공!");
    }
}
