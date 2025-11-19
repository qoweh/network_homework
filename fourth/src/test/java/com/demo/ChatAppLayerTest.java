package com.demo;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * ChatAppLayer Fragmentation 테스트
 */
public class ChatAppLayerTest {
    
    private ChatAppLayer senderChatApp;
    private ChatAppLayer receiverChatApp;
    private IPLayerMock senderIpLayer;
    private IPLayerMock receiverIpLayer;
    
    /**
     * IP 계층 Mock (테스트용)
     */
    static class IPLayerMock implements BaseLayer {
        private List<byte[]> sentPackets = new ArrayList<>();
        private BaseLayer upperLayer;
        
        @Override
        public boolean Send(byte[] input, int length) {
            byte[] packet = new byte[length];
            System.arraycopy(input, 0, packet, 0, length);
            sentPackets.add(packet);
            System.out.println("[IPMock] 패킷 전송: " + length + " bytes");
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
        
        public List<byte[]> getSentPackets() {
            return new ArrayList<>(sentPackets);
        }
        
        public void clearSentPackets() {
            sentPackets.clear();
        }
        
        @Override
        public String GetLayerName() { return "IPMock"; }
        
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
    
    @BeforeEach
    void setUp() {
        senderIpLayer = new IPLayerMock();
        receiverIpLayer = new IPLayerMock();
        
        senderChatApp = new ChatAppLayer(null);
        receiverChatApp = new ChatAppLayer(null);
        
        senderChatApp.SetUnderLayer(senderIpLayer);
        senderIpLayer.SetUpperLayer(senderChatApp);
        
        receiverChatApp.SetUnderLayer(receiverIpLayer);
        receiverIpLayer.SetUpperLayer(receiverChatApp);
    }
    
    @Test
    @DisplayName("짧은 메시지 전송 (Fragmentation 불필요)")
    void testShortMessage() throws Exception {
        CountDownLatch receiveLatch = new CountDownLatch(1);
        final String[] receivedMessage = {null};
        
        receiverChatApp.setOnReceive(msg -> {
            receivedMessage[0] = msg;
            receiveLatch.countDown();
        });
        
        String testMessage = "안녕하세요!";
        senderChatApp.sendMessage(testMessage);
        
        // 전송된 패킷 수 확인 (1개여야 함)
        List<byte[]> packets = senderIpLayer.getSentPackets();
        assertEquals(1, packets.size(), "짧은 메시지는 Fragment화되지 않아야 함");
        
        // 수신 시뮬레이션
        receiverIpLayer.simulateReceive(packets.get(0));
        
        // 검증
        boolean received = receiveLatch.await(1, TimeUnit.SECONDS);
        assertTrue(received, "메시지 수신 대기 시간 초과");
        assertEquals(testMessage, receivedMessage[0], "수신 메시지가 다름");
    }
    
    @Test
    @DisplayName("긴 메시지 Fragmentation 테스트")
    void testLongMessage() throws Exception {
        CountDownLatch receiveLatch = new CountDownLatch(1);
        final String[] receivedMessage = {null};
        
        receiverChatApp.setOnReceive(msg -> {
            System.out.println("[Receiver] 메시지 수신: " + msg.length() + " bytes");
            receivedMessage[0] = msg;
            receiveLatch.countDown();
        });
        
        // 긴 메시지 생성 (2KB 이상)
        StringBuilder longMessage = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longMessage.append("This is a very long message for testing fragmentation. Line ").append(i).append(". ");
        }
        
        String testMessage = longMessage.toString();
        System.out.println("[Sender] 전송 메시지 크기: " + testMessage.length() + " bytes");
        
        senderChatApp.sendMessage(testMessage);
        
        // 전송된 Fragment 수 확인
        List<byte[]> packets = senderIpLayer.getSentPackets();
        System.out.println("[Sender] Fragment 수: " + packets.size());
        assertTrue(packets.size() > 1, "긴 메시지는 Fragment화되어야 함");
        
        // 모든 Fragment를 수신 측으로 전달
        for (byte[] packet : packets) {
            receiverIpLayer.simulateReceive(packet);
        }
        
        // 검증
        boolean received = receiveLatch.await(3, TimeUnit.SECONDS);
        assertTrue(received, "메시지 수신 대기 시간 초과");
        assertNotNull(receivedMessage[0], "메시지를 수신하지 못함");
        assertEquals(testMessage, receivedMessage[0], "재조립된 메시지가 원본과 다름");
    }
    
    @Test
    @DisplayName("Fragment 순서 무작위 수신 테스트")
    void testFragmentReordering() throws Exception {
        CountDownLatch receiveLatch = new CountDownLatch(1);
        final String[] receivedMessage = {null};
        
        receiverChatApp.setOnReceive(msg -> {
            receivedMessage[0] = msg;
            receiveLatch.countDown();
        });
        
        // 긴 메시지 생성
        String testMessage = "A".repeat(2000); // 2000자
        
        senderChatApp.sendMessage(testMessage);
        
        // Fragment 가져오기
        List<byte[]> packets = new ArrayList<>(senderIpLayer.getSentPackets());
        assertTrue(packets.size() > 1, "Fragmentation되어야 함");
        
        // Fragment 순서 섞기
        java.util.Collections.shuffle(packets);
        System.out.println("[Test] Fragment 순서를 섞음");
        
        // 섞인 순서로 수신
        for (byte[] packet : packets) {
            receiverIpLayer.simulateReceive(packet);
        }
        
        // 검증
        boolean received = receiveLatch.await(3, TimeUnit.SECONDS);
        assertTrue(received, "메시지 수신 대기 시간 초과");
        assertEquals(testMessage, receivedMessage[0], "순서가 섞여도 재조립되어야 함");
    }
    
    @Test
    @DisplayName("중복 Fragment 무시 테스트")
    void testDuplicateFragment() throws Exception {
        CountDownLatch receiveLatch = new CountDownLatch(1);
        final String[] receivedMessage = {null};
        
        receiverChatApp.setOnReceive(msg -> {
            receivedMessage[0] = msg;
            receiveLatch.countDown();
        });
        
        String testMessage = "X".repeat(1500);
        senderChatApp.sendMessage(testMessage);
        
        List<byte[]> packets = senderIpLayer.getSentPackets();
        
        // 정상 수신
        for (byte[] packet : packets) {
            receiverIpLayer.simulateReceive(packet);
        }
        
        // 중복 Fragment 전송 (마지막 Fragment 재전송)
        if (packets.size() > 0) {
            System.out.println("[Test] 마지막 Fragment 중복 전송");
            receiverIpLayer.simulateReceive(packets.get(packets.size() - 1));
        }
        
        // 검증
        boolean received = receiveLatch.await(3, TimeUnit.SECONDS);
        assertTrue(received);
        assertEquals(testMessage, receivedMessage[0], "중복 Fragment가 있어도 정상 재조립되어야 함");
    }
    
    @Test
    @DisplayName("한글 메시지 Fragmentation 테스트")
    void testKoreanMessage() throws Exception {
        CountDownLatch receiveLatch = new CountDownLatch(1);
        final String[] receivedMessage = {null};
        
        receiverChatApp.setOnReceive(msg -> {
            receivedMessage[0] = msg;
            receiveLatch.countDown();
        });
        
        // 긴 한글 메시지
        StringBuilder koreanMessage = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            koreanMessage.append("안녕하세요. 이것은 한글 메시지 Fragmentation 테스트입니다. 줄 번호: ").append(i).append(". ");
        }
        
        String testMessage = koreanMessage.toString();
        System.out.println("[Test] 한글 메시지 크기: " + testMessage.getBytes("UTF-8").length + " bytes");
        
        senderChatApp.sendMessage(testMessage);
        
        List<byte[]> packets = senderIpLayer.getSentPackets();
        System.out.println("[Test] Fragment 수: " + packets.size());
        
        for (byte[] packet : packets) {
            receiverIpLayer.simulateReceive(packet);
        }
        
        boolean received = receiveLatch.await(3, TimeUnit.SECONDS);
        assertTrue(received);
        assertEquals(testMessage, receivedMessage[0], "한글 메시지가 제대로 재조립되어야 함");
    }
}
