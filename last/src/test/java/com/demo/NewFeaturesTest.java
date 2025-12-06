package com.demo;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 새로운 기능 테스트 (암호화, 우선순위, 타임스탬프/로깅)
 */
public class NewFeaturesTest {
    
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
        private int currentTos = 0;
        
        @Override
        public boolean Send(byte[] input, int length) {
            byte[] packet = new byte[length];
            System.arraycopy(input, 0, packet, 0, length);
            sentPackets.add(packet);
            System.out.println("[IPMock] 패킷 전송: " + length + " bytes, TOS: " + currentTos);
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
        
        public void setTos(int tos) {
            this.currentTos = tos;
        }
        
        public int getTos() {
            return currentTos;
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
    
    // ==================== 암호화 테스트 ====================
    
    @Test
    @DisplayName("암호화 활성화 - 짧은 메시지")
    void testEncryptionShortMessage() throws Exception {
        CountDownLatch receiveLatch = new CountDownLatch(1);
        final String[] receivedMessage = {null};
        
        receiverChatApp.setOnReceive(msg -> {
            System.out.println("[Test] 수신 메시지: " + msg);
            receivedMessage[0] = msg;
            receiveLatch.countDown();
        });
        
        // 암호화 활성화
        senderChatApp.setEncryptionEnabled(true);
        
        String testMessage = "Hello, Encryption!";
        senderChatApp.sendMessage(testMessage);
        
        // 패킷 확인 (암호화되어 있어야 함)
        List<byte[]> packets = senderIpLayer.getSentPackets();
        assertEquals(1, packets.size(), "짧은 메시지는 1개 패킷");
        
        byte[] packet = packets.get(0);
        // Type 바이트의 최상위 비트가 1이어야 함 (암호화 플래그)
        assertTrue((packet[0] & 0x80) != 0, "암호화 플래그가 설정되어야 함");
        
        // 수신 시뮬레이션
        receiverIpLayer.simulateReceive(packet);
        
        boolean received = receiveLatch.await(2, TimeUnit.SECONDS);
        assertTrue(received, "메시지 수신 실패");
        assertTrue(receivedMessage[0].contains(testMessage), "복호화된 메시지가 원본을 포함해야 함");
        
        System.out.println("[Test] 암호화 테스트 성공!");
    }
    
    @Test
    @DisplayName("암호화 비활성화 - 플래그 확인")
    void testEncryptionDisabled() throws Exception {
        CountDownLatch receiveLatch = new CountDownLatch(1);
        final String[] receivedMessage = {null};
        
        receiverChatApp.setOnReceive(msg -> {
            receivedMessage[0] = msg;
            receiveLatch.countDown();
        });
        
        // 암호화 비활성화 (기본값)
        senderChatApp.setEncryptionEnabled(false);
        
        String testMessage = "Plain text message";
        senderChatApp.sendMessage(testMessage);
        
        List<byte[]> packets = senderIpLayer.getSentPackets();
        assertEquals(1, packets.size());
        
        byte[] packet = packets.get(0);
        // Type 바이트의 최상위 비트가 0이어야 함
        assertEquals(0, packet[0] & 0x80, "암호화 플래그가 설정되지 않아야 함");
        
        receiverIpLayer.simulateReceive(packet);
        
        boolean received = receiveLatch.await(2, TimeUnit.SECONDS);
        assertTrue(received);
        assertTrue(receivedMessage[0].contains(testMessage));
    }
    
    @Test
    @DisplayName("암호화된 긴 메시지 (Fragmentation)")
    void testEncryptionLongMessage() throws Exception {
        CountDownLatch receiveLatch = new CountDownLatch(1);
        final String[] receivedMessage = {null};
        
        receiverChatApp.setOnReceive(msg -> {
            receivedMessage[0] = msg;
            receiveLatch.countDown();
        });
        
        senderChatApp.setEncryptionEnabled(true);
        
        // 긴 메시지 생성 (500자 이상)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("EncryptedLine").append(i).append(" ");
        }
        String testMessage = sb.toString();
        
        senderChatApp.sendMessage(testMessage);
        
        List<byte[]> packets = senderIpLayer.getSentPackets();
        assertTrue(packets.size() > 1, "긴 메시지는 여러 패킷으로 분할되어야 함");
        
        // 모든 패킷이 암호화 플래그를 가지고 있어야 함
        for (byte[] packet : packets) {
            assertTrue((packet[0] & 0x80) != 0, "모든 Fragment가 암호화되어야 함");
        }
        
        // 수신 시뮬레이션
        for (byte[] packet : packets) {
            receiverIpLayer.simulateReceive(packet);
        }
        
        boolean received = receiveLatch.await(3, TimeUnit.SECONDS);
        assertTrue(received, "메시지 수신 실패");
        assertTrue(receivedMessage[0].contains("EncryptedLine0"), "복호화된 메시지 확인");
    }
    
    // ==================== 우선순위 테스트 ====================
    
    @Test
    @DisplayName("우선순위 설정 - HIGH")
    void testPriorityHigh() throws Exception {
        CountDownLatch receiveLatch = new CountDownLatch(1);
        final String[] receivedMessage = {null};
        
        receiverChatApp.setOnReceive(msg -> {
            System.out.println("[Test] 수신: " + msg);
            receivedMessage[0] = msg;
            receiveLatch.countDown();
        });
        
        senderChatApp.setPriority(ChatAppLayer.Priority.HIGH);
        
        String testMessage = "긴급 메시지!";
        senderChatApp.sendMessage(testMessage);
        
        List<byte[]> packets = senderIpLayer.getSentPackets();
        assertEquals(1, packets.size());
        
        receiverIpLayer.simulateReceive(packets.get(0));
        
        boolean received = receiveLatch.await(2, TimeUnit.SECONDS);
        assertTrue(received);
        assertTrue(receivedMessage[0].contains(testMessage));
        // 우선순위 라벨 확인
        assertTrue(receivedMessage[0].contains("[긴급]"), "긴급 우선순위 라벨이 있어야 함");
    }
    
    @Test
    @DisplayName("우선순위 설정 - NORMAL")
    void testPriorityNormal() throws Exception {
        CountDownLatch receiveLatch = new CountDownLatch(1);
        final String[] receivedMessage = {null};
        
        receiverChatApp.setOnReceive(msg -> {
            receivedMessage[0] = msg;
            receiveLatch.countDown();
        });
        
        senderChatApp.setPriority(ChatAppLayer.Priority.NORMAL);
        
        String testMessage = "일반 메시지";
        senderChatApp.sendMessage(testMessage);
        
        List<byte[]> packets = senderIpLayer.getSentPackets();
        receiverIpLayer.simulateReceive(packets.get(0));
        
        boolean received = receiveLatch.await(2, TimeUnit.SECONDS);
        assertTrue(received);
        assertTrue(receivedMessage[0].contains("[일반]"), "일반 우선순위 라벨이 있어야 함");
    }
    
    @Test
    @DisplayName("우선순위 설정 - LOW")
    void testPriorityLow() throws Exception {
        CountDownLatch receiveLatch = new CountDownLatch(1);
        final String[] receivedMessage = {null};
        
        receiverChatApp.setOnReceive(msg -> {
            receivedMessage[0] = msg;
            receiveLatch.countDown();
        });
        
        senderChatApp.setPriority(ChatAppLayer.Priority.LOW);
        
        String testMessage = "낮은 우선순위 메시지";
        senderChatApp.sendMessage(testMessage);
        
        List<byte[]> packets = senderIpLayer.getSentPackets();
        receiverIpLayer.simulateReceive(packets.get(0));
        
        boolean received = receiveLatch.await(2, TimeUnit.SECONDS);
        assertTrue(received);
        assertTrue(receivedMessage[0].contains("[낮음]"), "낮음 우선순위 라벨이 있어야 함");
    }
    
    // ==================== 타임스탬프/지연시간 테스트 ====================
    
    @Test
    @DisplayName("타임스탬프 포함 확인")
    void testTimestampIncluded() throws Exception {
        CountDownLatch receiveLatch = new CountDownLatch(1);
        final String[] receivedMessage = {null};
        
        receiverChatApp.setOnReceive(msg -> {
            receivedMessage[0] = msg;
            receiveLatch.countDown();
        });
        
        String testMessage = "타임스탬프 테스트";
        long beforeSend = System.currentTimeMillis();
        senderChatApp.sendMessage(testMessage);
        
        List<byte[]> packets = senderIpLayer.getSentPackets();
        assertEquals(1, packets.size());
        
        byte[] packet = packets.get(0);
        // 타임스탬프는 바이트 2-9에 위치 (Type 1B + Priority 1B + Timestamp 8B)
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(packet, 2, 8);
        long timestamp = buffer.getLong();
        
        assertTrue(timestamp >= beforeSend, "타임스탬프가 전송 시간 이후여야 함");
        assertTrue(timestamp <= System.currentTimeMillis(), "타임스탬프가 현재 시간 이전이어야 함");
        
        // 수신
        receiverIpLayer.simulateReceive(packet);
        
        boolean received = receiveLatch.await(2, TimeUnit.SECONDS);
        assertTrue(received);
        // 지연시간 포함 확인
        assertTrue(receivedMessage[0].contains("지연:") || receivedMessage[0].contains("ms"), 
            "지연시간 정보가 포함되어야 함");
    }
    
    @Test
    @DisplayName("지연시간 계산 확인")
    void testLatencyCalculation() throws Exception {
        CountDownLatch receiveLatch = new CountDownLatch(1);
        final String[] receivedMessage = {null};
        
        receiverChatApp.setOnReceive(msg -> {
            System.out.println("[Test] 수신된 메시지: " + msg);
            receivedMessage[0] = msg;
            receiveLatch.countDown();
        });
        
        String testMessage = "지연시간 테스트";
        senderChatApp.sendMessage(testMessage);
        
        List<byte[]> packets = senderIpLayer.getSentPackets();
        
        // 약간의 지연 후 수신 (50ms)
        Thread.sleep(50);
        receiverIpLayer.simulateReceive(packets.get(0));
        
        boolean received = receiveLatch.await(2, TimeUnit.SECONDS);
        assertTrue(received);
        
        // 지연시간이 50ms 이상이어야 함
        // 메시지 형식: "[우선순위] 메시지 (지연: Xms)"
        String msg = receivedMessage[0];
        assertTrue(msg.contains("지연:"), "지연 정보가 있어야 함");
        
        // 지연시간 파싱
        int latencyStart = msg.indexOf("지연:") + 4;
        int latencyEnd = msg.indexOf("ms", latencyStart);
        if (latencyStart > 3 && latencyEnd > latencyStart) {
            String latencyStr = msg.substring(latencyStart, latencyEnd).trim();
            int latency = Integer.parseInt(latencyStr);
            assertTrue(latency >= 45, "지연시간이 약 50ms 이상이어야 함 (실제: " + latency + "ms)");
        }
    }
    
    // ==================== 로깅 테스트 ====================
    
    @Test
    @DisplayName("패킷 로그 파일 생성 확인")
    void testPacketLogCreation() throws Exception {
        String testMessage = "로그 테스트 메시지";
        senderChatApp.sendMessage(testMessage);
        
        // 로그 파일 확인
        File logFile = new File("packet.log");
        assertTrue(logFile.exists(), "packet.log 파일이 생성되어야 함");
        
        // 로그 내용 확인
        String logContent = new String(Files.readAllBytes(logFile.toPath()));
        assertTrue(logContent.contains("[SEND]"), "SEND 로그가 있어야 함");
        assertTrue(logContent.contains(testMessage), "메시지 내용이 로그에 있어야 함");
    }
    
    @Test
    @DisplayName("수신 시 로그 기록 확인")
    void testReceiveLog() throws Exception {
        CountDownLatch receiveLatch = new CountDownLatch(1);
        
        receiverChatApp.setOnReceive(msg -> {
            receiveLatch.countDown();
        });
        
        String testMessage = "수신 로그 테스트";
        senderChatApp.sendMessage(testMessage);
        
        List<byte[]> packets = senderIpLayer.getSentPackets();
        receiverIpLayer.simulateReceive(packets.get(0));
        
        boolean received = receiveLatch.await(2, TimeUnit.SECONDS);
        assertTrue(received);
        
        // 로그 파일 확인
        File logFile = new File("packet.log");
        String logContent = new String(Files.readAllBytes(logFile.toPath()));
        assertTrue(logContent.contains("[RECV]"), "RECV 로그가 있어야 함");
    }
    
    // ==================== 복합 테스트 ====================
    
    @Test
    @DisplayName("암호화 + 우선순위 + 긴 메시지")
    void testCombinedFeatures() throws Exception {
        CountDownLatch receiveLatch = new CountDownLatch(1);
        final String[] receivedMessage = {null};
        
        receiverChatApp.setOnReceive(msg -> {
            receivedMessage[0] = msg;
            receiveLatch.countDown();
        });
        
        // 암호화 + 긴급 우선순위
        senderChatApp.setEncryptionEnabled(true);
        senderChatApp.setPriority(ChatAppLayer.Priority.HIGH);
        
        // 긴 메시지
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("Combined").append(i).append(" ");
        }
        String testMessage = sb.toString();
        
        senderChatApp.sendMessage(testMessage);
        
        List<byte[]> packets = senderIpLayer.getSentPackets();
        assertTrue(packets.size() >= 1);
        
        // 모든 패킷이 암호화되어 있어야 함
        for (byte[] packet : packets) {
            assertTrue((packet[0] & 0x80) != 0, "암호화 플래그 확인");
        }
        
        // 수신 시뮬레이션
        for (byte[] packet : packets) {
            receiverIpLayer.simulateReceive(packet);
        }
        
        boolean received = receiveLatch.await(3, TimeUnit.SECONDS);
        assertTrue(received, "복합 기능 메시지 수신 실패");
        assertTrue(receivedMessage[0].contains("[긴급]"), "긴급 우선순위 라벨");
        assertTrue(receivedMessage[0].contains("Combined0"), "메시지 내용 확인");
    }
    
    @Test
    @DisplayName("빈 메시지 처리")
    void testEmptyMessage() throws Exception {
        // 빈 메시지는 전송하지 않아야 함 (또는 예외 없이 처리)
        try {
            senderChatApp.sendMessage("");
            // 예외가 발생하지 않으면 통과
        } catch (Exception e) {
            fail("빈 메시지 처리 중 예외 발생: " + e.getMessage());
        }
    }
    
    @Test
    @DisplayName("특수문자 포함 메시지")
    void testSpecialCharacters() throws Exception {
        CountDownLatch receiveLatch = new CountDownLatch(1);
        final String[] receivedMessage = {null};
        
        receiverChatApp.setOnReceive(msg -> {
            receivedMessage[0] = msg;
            receiveLatch.countDown();
        });
        
        String testMessage = "특수문자 테스트: !@#$%^&*()_+-=[]{}|;':\",./<>?";
        senderChatApp.sendMessage(testMessage);
        
        List<byte[]> packets = senderIpLayer.getSentPackets();
        receiverIpLayer.simulateReceive(packets.get(0));
        
        boolean received = receiveLatch.await(2, TimeUnit.SECONDS);
        assertTrue(received);
        assertTrue(receivedMessage[0].contains("!@#$%^&*()"), "특수문자가 정확히 전송되어야 함");
    }
    
    @Test
    @DisplayName("연속 메시지 전송")
    void testConsecutiveMessages() throws Exception {
        final int MESSAGE_COUNT = 5;
        CountDownLatch receiveLatch = new CountDownLatch(MESSAGE_COUNT);
        final List<String> receivedMessages = new ArrayList<>();
        
        receiverChatApp.setOnReceive(msg -> {
            synchronized (receivedMessages) {
                receivedMessages.add(msg);
            }
            receiveLatch.countDown();
        });
        
        // 5개 메시지 연속 전송
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            senderChatApp.sendMessage("연속메시지" + i);
        }
        
        List<byte[]> packets = senderIpLayer.getSentPackets();
        assertEquals(MESSAGE_COUNT, packets.size(), "5개 패킷이 전송되어야 함");
        
        // 수신 시뮬레이션
        for (byte[] packet : packets) {
            receiverIpLayer.simulateReceive(packet);
        }
        
        boolean received = receiveLatch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "모든 메시지 수신 실패");
        assertEquals(MESSAGE_COUNT, receivedMessages.size(), "5개 메시지가 수신되어야 함");
    }
}
