package com.demo;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * FileAppLayer 테스트
 */
public class FileAppLayerTest {
    
    private FileAppLayer senderFileApp;
    private FileAppLayer receiverFileApp;
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
        senderFileApp = new FileAppLayer();
        receiverFileApp = new FileAppLayer();
        
        senderIpLayer = new IPLayerMock();
        receiverIpLayer = new IPLayerMock();
        
        senderFileApp.SetUnderLayer(senderIpLayer);
        senderIpLayer.SetUpperLayer(senderFileApp);
        
        receiverFileApp.SetUnderLayer(receiverIpLayer);
        receiverIpLayer.SetUpperLayer(receiverFileApp);
    }
    
    @Test
    @DisplayName("작은 파일 전송 테스트")
    void testSmallFileTransfer() throws Exception {
        // 1. 테스트 파일 생성
        File testFile = createTestFile("test_small.txt", "Hello, File Transfer!");
        
        // 2. 수신 완료 대기용 CountDownLatch
        CountDownLatch receiveLatch = new CountDownLatch(1);
        final boolean[] receiveSuccess = {false};
        
        receiverFileApp.setOnReceiveComplete((fileName, success) -> {
            System.out.println("파일 수신 완료: " + fileName + ", 성공: " + success);
            receiveSuccess[0] = success;
            receiveLatch.countDown();
        });
        
        // 3. 파일 전송 (Thread에서 실행됨)
        senderFileApp.sendFile(testFile.getAbsolutePath());
        
        // 4. 전송된 패킷을 수신 측으로 전달 (네트워크 시뮬레이션)
        Thread.sleep(500); // 전송 완료 대기
        
        List<byte[]> packets = senderIpLayer.getSentPackets();
        System.out.println("전송된 패킷 수: " + packets.size());
        
        for (byte[] packet : packets) {
            receiverIpLayer.simulateReceive(packet);
        }
        
        // 5. 수신 완료 대기
        boolean completed = receiveLatch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "파일 수신 완료 대기 시간 초과");
        assertTrue(receiveSuccess[0], "파일 수신 실패");
        
        // 6. 수신된 파일 검증
        File receivedFile = new File("received_files/test_small.txt");
        assertTrue(receivedFile.exists(), "수신 파일이 존재하지 않음");
        
        // 파일 내용 비교
        byte[] originalContent = java.nio.file.Files.readAllBytes(testFile.toPath());
        byte[] receivedContent = java.nio.file.Files.readAllBytes(receivedFile.toPath());
        assertArrayEquals(originalContent, receivedContent, "파일 내용이 다름");
        
        // 7. 정리
        testFile.delete();
        receivedFile.delete();
    }
    
    @Test
    @DisplayName("큰 파일 Fragmentation 테스트")
    void testLargeFileFragmentation() throws Exception {
        // 1. 큰 테스트 파일 생성 (3KB)
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            largeContent.append("This is line ").append(i).append(" of the large test file.\n");
        }
        
        File testFile = createTestFile("test_large.txt", largeContent.toString());
        long fileSize = testFile.length();
        System.out.println("테스트 파일 크기: " + fileSize + " bytes");
        
        // 2. 수신 완료 대기
        CountDownLatch receiveLatch = new CountDownLatch(1);
        final boolean[] receiveSuccess = {false};
        
        receiverFileApp.setOnReceiveComplete((fileName, success) -> {
            receiveSuccess[0] = success;
            receiveLatch.countDown();
        });
        
        // 3. 파일 전송
        senderFileApp.sendFile(testFile.getAbsolutePath());
        
        // 4. 전송 완료 대기
        Thread.sleep(1000);
        
        List<byte[]> packets = senderIpLayer.getSentPackets();
        System.out.println("전송된 Fragment 수: " + packets.size());
        
        // Fragment 수 검증 (최소 3개 이상이어야 함)
        assertTrue(packets.size() >= 3, "Fragmentation이 제대로 동작하지 않음");
        
        // 5. 수신 측으로 전달
        for (byte[] packet : packets) {
            receiverIpLayer.simulateReceive(packet);
            Thread.sleep(10); // 순차 처리 시뮬레이션
        }
        
        // 6. 수신 완료 대기
        boolean completed = receiveLatch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "파일 수신 완료 대기 시간 초과");
        assertTrue(receiveSuccess[0], "파일 수신 실패");
        
        // 7. 수신된 파일 검증
        File receivedFile = new File("received_files/test_large.txt");
        assertTrue(receivedFile.exists(), "수신 파일이 존재하지 않음");
        
        byte[] originalContent = java.nio.file.Files.readAllBytes(testFile.toPath());
        byte[] receivedContent = java.nio.file.Files.readAllBytes(receivedFile.toPath());
        
        assertEquals(originalContent.length, receivedContent.length, "파일 크기가 다름");
        assertArrayEquals(originalContent, receivedContent, "파일 내용이 다름");
        
        // 8. 정리
        testFile.delete();
        receivedFile.delete();
    }
    
    @Test
    @DisplayName("Fragment 순서 무작위 수신 테스트")
    void testOutOfOrderFragments() throws Exception {
        // 1. 테스트 파일 생성
        File testFile = createTestFile("test_random.txt", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
        
        // 2. 수신 대기
        CountDownLatch receiveLatch = new CountDownLatch(1);
        receiverFileApp.setOnReceiveComplete((fileName, success) -> {
            receiveLatch.countDown();
        });
        
        // 3. 파일 전송
        senderFileApp.sendFile(testFile.getAbsolutePath());
        Thread.sleep(500);
        
        List<byte[]> sentPackets = new ArrayList<>(senderIpLayer.getSentPackets());
        
        // 4. Fragment 순서 섞기 (첫 번째 FILE_START는 유지)
        List<byte[]> packets = new ArrayList<>();
        if (sentPackets.size() > 2) {
            byte[] first = sentPackets.get(0); // FILE_START
            byte[] last = sentPackets.get(sentPackets.size() - 1); // FILE_END
            
            // 중간 Fragment들을 복사해서 섞기
            List<byte[]> middle = new ArrayList<>();
            for (int i = 1; i < sentPackets.size() - 1; i++) {
                middle.add(sentPackets.get(i));
            }
            java.util.Collections.shuffle(middle);
            
            // 재조립
            packets.add(first);
            packets.addAll(middle);
            packets.add(last);
        } else {
            packets = sentPackets;
        }
        
        // 5. 섞인 순서로 수신
        for (byte[] packet : packets) {
            receiverIpLayer.simulateReceive(packet);
        }
        
        // 6. 검증
        boolean completed = receiveLatch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "순서가 섞인 Fragment 재조립 실패");
        
        File receivedFile = new File("received_files/test_random.txt");
        assertTrue(receivedFile.exists());
        
        byte[] originalContent = java.nio.file.Files.readAllBytes(testFile.toPath());
        byte[] receivedContent = java.nio.file.Files.readAllBytes(receivedFile.toPath());
        assertArrayEquals(originalContent, receivedContent);
        
        // 7. 정리
        testFile.delete();
        receivedFile.delete();
    }
    
    /**
     * 테스트 파일 생성 헬퍼 메서드
     */
    private File createTestFile(String fileName, String content) throws IOException {
        File file = new File(fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
    
    @AfterAll
    static void cleanup() {
        // received_files 디렉토리 정리
        File receivedDir = new File("received_files");
        if (receivedDir.exists() && receivedDir.isDirectory()) {
            File[] files = receivedDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            receivedDir.delete();
        }
    }
}
