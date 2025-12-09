package com.demo;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 우선순위 큐 기능 통합 테스트
 * 
 * 테스트 시나리오:
 * 1. 일반 메시지 3개 → 긴급 메시지 1개 → 낮음 메시지 1개 순서로 전송
 * 2. 수신 측에서 긴급 메시지가 먼저 처리되는지 확인
 * 3. 같은 우선순위 내에서는 FIFO 순서가 유지되는지 확인
 */
public class PriorityQueueTest {
    
    private ChatAppLayer senderChatApp;
    private ChatAppLayer receiverChatApp;
    private IPLayer senderIpLayer;
    private IPLayer receiverIpLayer;
    
    private List<String> receivedMessages;
    private List<Long> receivedTimestamps;
    private CountDownLatch latch;
    
    /**
     * Mock 하위 계층 (실제 네트워크 전송 없이 바로 수신 측으로 전달)
     * ChatAppLayer ↔ DirectTransport ↔ ChatAppLayer 직접 연결
     */
    static class DirectTransportLayer implements BaseLayer {
        private BaseLayer upperLayer;
        private DirectTransportLayer peer; // 상대방 전송 계층
        
        void setPeer(DirectTransportLayer peer) {
            this.peer = peer;
        }
        
        @Override
        public boolean Send(byte[] input, int length) {
            if (peer != null && peer.upperLayer != null) {
                // 상대방의 상위 계층으로 바로 전달 (네트워크 시뮬레이션)
                byte[] data = new byte[length];
                System.arraycopy(input, 0, data, 0, length);
                // 직접 Receive 호출
                new Thread(() -> {
                    try {
                        Thread.sleep(5); // 약간의 지연 시뮬레이션
                        peer.upperLayer.Receive(data);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
                return true;
            }
            return false;
        }
        
        @Override
        public boolean Receive(byte[] input) {
            if (upperLayer != null) {
                return upperLayer.Receive(input);
            }
            return false;
        }
        
        @Override
        public String GetLayerName() { return "DirectTransport"; }
        
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
        // 전송 측 설정 (ChatAppLayer만 사용, IPLayer 생략)
        senderChatApp = new ChatAppLayer((msg) -> {});
        DirectTransportLayer senderTransport = new DirectTransportLayer();
        
        // 수신 측 설정 (ChatAppLayer만 사용, IPLayer 생략)
        receiverChatApp = new ChatAppLayer((msg) -> {});
        DirectTransportLayer receiverTransport = new DirectTransportLayer();
        
        // 계층 연결 (ChatApp ↔ Transport 직접 연결)
        senderChatApp.SetUnderLayer(senderTransport);
        senderTransport.SetUpperLayer(senderChatApp);
        
        receiverChatApp.SetUnderLayer(receiverTransport);
        receiverTransport.SetUpperLayer(receiverChatApp);
        
        // 양방향 연결
        senderTransport.setPeer(receiverTransport);
        receiverTransport.setPeer(senderTransport);
        
        // IP 계층 변수는 사용하지 않음 (테스트 단순화)
        senderIpLayer = null;
        receiverIpLayer = null;
        
        // 수신 메시지 수집
        receivedMessages = new ArrayList<>();
        receivedTimestamps = new ArrayList<>();
        
        receiverChatApp.setOnReceiveWithLatency((formattedMessage, sentAt) -> {
            // 포맷된 메시지에서 실제 메시지 내용만 추출
            // 형식: "[우선순위] 메시지 (지연: Xms)"
            String actualMessage = extractMessageContent(formattedMessage);
            receivedMessages.add(actualMessage);
            receivedTimestamps.add(sentAt);
            System.out.println("[TEST] 수신: " + formattedMessage + " (sent=" + sentAt + ")");
            if (latch != null) {
                latch.countDown();
            }
        });
    }
    
    /**
     * 포맷된 메시지에서 실제 메시지 내용만 추출
     * 입력: "[긴급] test (지연: 123ms)"
     * 출력: "test"
     */
    private String extractMessageContent(String formattedMessage) {
        // "[우선순위] " 부분 제거
        int start = formattedMessage.indexOf("] ");
        if (start != -1) {
            formattedMessage = formattedMessage.substring(start + 2);
        }
        // " (지연: Xms)" 부분 제거
        int end = formattedMessage.indexOf(" (지연:");
        if (end != -1) {
            formattedMessage = formattedMessage.substring(0, end);
        }
        return formattedMessage;
    }
    
    @AfterEach
    void tearDown() throws InterruptedException {
        // 스레드 정리 대기
        Thread.sleep(200);
    }
    
    /**
     * 테스트 1: 우선순위에 따른 메시지 순서 변경 검증
     * 
     * 시나리오:
     * 1. NORMAL 우선순위로 "msg1", "msg2", "msg3" 전송
     * 2. HIGH 우선순위로 "urgent" 전송
     * 3. LOW 우선순위로 "low" 전송
     * 
     * 예상 결과:
     * - 수신 순서: "urgent" (HIGH) → "msg1", "msg2", "msg3" (NORMAL) → "low" (LOW)
     */
    @Test
    @DisplayName("우선순위에 따른 메시지 재정렬 검증")
    void testPriorityReordering() throws InterruptedException {
        // 5개 메시지를 기다림
        latch = new CountDownLatch(5);
        
        // 전송: 일반 3개 → 긴급 1개 → 낮음 1개
        // 빠르게 연속 전송하여 모두 큐에 쌓이도록 함
        senderChatApp.setPriority(ChatAppLayer.Priority.NORMAL);
        senderChatApp.sendMessage("msg1");
        senderChatApp.sendMessage("msg2");
        senderChatApp.sendMessage("msg3");
        
        senderChatApp.setPriority(ChatAppLayer.Priority.HIGH);
        senderChatApp.sendMessage("urgent");
        
        senderChatApp.setPriority(ChatAppLayer.Priority.LOW);
        senderChatApp.sendMessage("low");
        
        // 모든 메시지가 큐에 쌓이도록 짧은 대기
        Thread.sleep(100);
        
        // 모든 메시지 수신 대기 (최대 5초)
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "5개 메시지가 모두 수신되어야 함");
        
        // 검증: 5개 메시지 모두 수신
        assertEquals(5, receivedMessages.size(), "5개 메시지가 수신되어야 함");
        
        // 검증: 첫 번째 메시지는 "urgent" (긴급 우선순위)
        assertEquals("urgent", receivedMessages.get(0), 
            "긴급 메시지가 가장 먼저 처리되어야 함");
        
        // 검증: 마지막 메시지는 "low" (낮은 우선순위)
        assertEquals("low", receivedMessages.get(4), 
            "낮은 우선순위 메시지가 마지막에 처리되어야 함");
        
        // 검증: 중간 3개는 일반 우선순위 메시지 (FIFO 순서)
        assertEquals("msg1", receivedMessages.get(1), 
            "일반 우선순위 내에서 FIFO 순서 유지: msg1이 msg2보다 먼저");
        assertEquals("msg2", receivedMessages.get(2), 
            "일반 우선순위 내에서 FIFO 순서 유지: msg2가 msg3보다 먼저");
        assertEquals("msg3", receivedMessages.get(3), 
            "일반 우선순위 내에서 FIFO 순서 유지: msg3이 마지막");
        
        System.out.println("\n[TEST] 수신 순서 검증 완료:");
        for (int i = 0; i < receivedMessages.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + receivedMessages.get(i));
        }
    }
    
    /**
     * 테스트 2: 같은 우선순위 내 FIFO 순서 유지 검증
     * 
     * 시나리오:
     * - 모두 HIGH 우선순위로 "h1", "h2", "h3" 전송
     * 
     * 예상 결과:
     * - 수신 순서: "h1" → "h2" → "h3" (전송 순서 그대로)
     */
    @Test
    @DisplayName("같은 우선순위 내 FIFO 순서 유지")
    void testFifoWithinSamePriority() throws InterruptedException {
        latch = new CountDownLatch(3);
        
        // 모두 긴급 우선순위로 빠르게 연속 전송
        senderChatApp.setPriority(ChatAppLayer.Priority.HIGH);
        senderChatApp.sendMessage("h1");
        senderChatApp.sendMessage("h2");
        senderChatApp.sendMessage("h3");
        
        boolean received = latch.await(3, TimeUnit.SECONDS);
        assertTrue(received, "3개 메시지가 모두 수신되어야 함");
        
        assertEquals(3, receivedMessages.size());
        assertEquals("h1", receivedMessages.get(0), "첫 번째 전송한 메시지가 먼저 수신되어야 함");
        assertEquals("h2", receivedMessages.get(1), "두 번째 전송한 메시지가 두 번째로 수신되어야 함");
        assertEquals("h3", receivedMessages.get(2), "세 번째 전송한 메시지가 마지막으로 수신되어야 함");
        
        System.out.println("\n[TEST] 같은 우선순위 FIFO 검증 완료");
    }
    
    /**
     * 테스트 3: 복잡한 우선순위 혼합 시나리오
     * 
     * 시나리오:
     * - LOW → NORMAL → HIGH → LOW → HIGH → NORMAL 순서로 전송
     * 
     * 예상 결과:
     * - 수신 순서: HIGH 2개 → NORMAL 2개 → LOW 2개
     */
    @Test
    @DisplayName("복잡한 우선순위 혼합 시나리오")
    void testComplexPriorityMixing() throws InterruptedException {
        latch = new CountDownLatch(6);
        
        // 빠르게 연속 전송하여 모두 큐에 쌓이도록 함
        senderChatApp.setPriority(ChatAppLayer.Priority.LOW);
        senderChatApp.sendMessage("L1");
        
        senderChatApp.setPriority(ChatAppLayer.Priority.NORMAL);
        senderChatApp.sendMessage("N1");
        
        senderChatApp.setPriority(ChatAppLayer.Priority.HIGH);
        senderChatApp.sendMessage("H1");
        
        senderChatApp.setPriority(ChatAppLayer.Priority.LOW);
        senderChatApp.sendMessage("L2");
        
        senderChatApp.setPriority(ChatAppLayer.Priority.HIGH);
        senderChatApp.sendMessage("H2");
        
        senderChatApp.setPriority(ChatAppLayer.Priority.NORMAL);
        senderChatApp.sendMessage("N2");
        
        // 모든 메시지가 큐에 쌓이도록 짧은 대기
        Thread.sleep(100);
        
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "6개 메시지가 모두 수신되어야 함");
        
        assertEquals(6, receivedMessages.size());
        
        // HIGH 우선순위 메시지들이 먼저 (H1, H2 순서)
        assertEquals("H1", receivedMessages.get(0));
        assertEquals("H2", receivedMessages.get(1));
        
        // NORMAL 우선순위 메시지들이 다음 (N1, N2 순서)
        assertEquals("N1", receivedMessages.get(2));
        assertEquals("N2", receivedMessages.get(3));
        
        // LOW 우선순위 메시지들이 마지막 (L1, L2 순서)
        assertEquals("L1", receivedMessages.get(4));
        assertEquals("L2", receivedMessages.get(5));
        
        System.out.println("\n[TEST] 복잡한 우선순위 혼합 검증 완료:");
        for (int i = 0; i < receivedMessages.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + receivedMessages.get(i));
        }
    }
    
    /**
     * 테스트 4: ChatAppLayer 우선순위 설정 검증
     * 
     * ChatAppLayer에서 setPriority를 호출하면
     * 올바르게 저장되고 전송되는지 검증
     */
    @Test
    @DisplayName("ChatAppLayer 우선순위 설정 검증")
    void testChatAppLayerPrioritySetting() {
        // HIGH 우선순위 설정 후 전송
        senderChatApp.setPriority(ChatAppLayer.Priority.HIGH);
        assertTrue(senderChatApp.sendMessage("test_high"), 
            "HIGH 우선순위 메시지 전송 성공해야 함");
        
        // NORMAL 우선순위 설정 후 전송
        senderChatApp.setPriority(ChatAppLayer.Priority.NORMAL);
        assertTrue(senderChatApp.sendMessage("test_normal"), 
            "NORMAL 우선순위 메시지 전송 성공해야 함");
        
        // LOW 우선순위 설정 후 전송
        senderChatApp.setPriority(ChatAppLayer.Priority.LOW);
        assertTrue(senderChatApp.sendMessage("test_low"), 
            "LOW 우선순위 메시지 전송 성공해야 함");
        
        System.out.println("[TEST] ChatAppLayer 우선순위 설정 검증 완료");
    }
    
    /**
     * 테스트 5: 암호화된 메시지의 우선순위 처리
     * 
     * 암호화 활성화 상태에서도 우선순위가 올바르게 동작하는지 검증
     */
    @Test
    @DisplayName("암호화된 메시지의 우선순위 처리")
    void testPriorityWithEncryption() throws InterruptedException {
        latch = new CountDownLatch(3);
        
        // 암호화 활성화
        senderChatApp.setEncryptionEnabled(true);
        receiverChatApp.setEncryptionEnabled(true);
        
        // 빠르게 연속 전송하여 모두 큐에 쌓이도록 함
        senderChatApp.setPriority(ChatAppLayer.Priority.NORMAL);
        senderChatApp.sendMessage("encrypted_normal");
        
        senderChatApp.setPriority(ChatAppLayer.Priority.HIGH);
        senderChatApp.sendMessage("encrypted_urgent");
        
        senderChatApp.setPriority(ChatAppLayer.Priority.LOW);
        senderChatApp.sendMessage("encrypted_low");
        
        // 모든 메시지가 큐에 쌓이도록 짧은 대기
        Thread.sleep(100);
        
        boolean received = latch.await(3, TimeUnit.SECONDS);
        assertTrue(received, "3개 암호화된 메시지가 모두 수신되어야 함");
        
        assertEquals(3, receivedMessages.size());
        
        // 우선순위 순서 검증
        assertEquals("encrypted_urgent", receivedMessages.get(0), 
            "암호화된 긴급 메시지가 먼저 처리되어야 함");
        assertEquals("encrypted_normal", receivedMessages.get(1), 
            "암호화된 일반 메시지가 두 번째로 처리되어야 함");
        assertEquals("encrypted_low", receivedMessages.get(2), 
            "암호화된 낮은 우선순위 메시지가 마지막에 처리되어야 함");
        
        System.out.println("\n[TEST] 암호화 + 우선순위 검증 완료");
    }
}
