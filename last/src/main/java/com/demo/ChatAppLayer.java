package com.demo;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * ChatAppLayer - 채팅 애플리케이션 계층 (OSI 응용 계층에 해당)
 * 
 * 역할:
 * - 사용자가 입력한 문자열 메시지를 바이트 배열로 변환 (송신)
 * - 수신한 바이트 배열을 문자열로 변환하여 UI에 표시 (수신)
 * - Fragmentation 지원: 긴 메시지를 작은 조각으로 나누어 전송
 * 
 * 추가 기능:
 * - 암호화 통신: XOR 암호화를 통한 도청 방지
 * - 우선순위 큐: 긴급 메시지 우선 처리
 * - 타임스탬프 및 로깅: 전송/수신 시간 측정 및 로깅
 * 
 * Fragment 헤더 구조 (확장됨):
 * ┌────────────┬───────────┬──────────┬──────────┬──────────┐
 * │ Type+Flag  │ Timestamp │ Sequence │Total Seq │  Data    │
 * │ (1 byte)   │ (8 bytes) │ (4 bytes)│ (4 bytes)│ (가변)   │
 * └────────────┴───────────┴──────────┴──────────┴──────────┘
 * 
 * Type (하위 7비트):
 * - 0x01: CHAT_SINGLE (단일 메시지, Fragmentation 불필요)
 * - 0x02: CHAT_FRAGMENT (Fragment화된 메시지)
 * 
 * Flag (상위 1비트):
 * - 0x80: 암호화 플래그 (데이터가 암호화됨)
 */
public class ChatAppLayer implements BaseLayer {
    // ===== 계층 기본 정보 =====
    private static final String LAYER_NAME = "ChatApp";
    private BaseLayer lowerLayer; // 하위 계층: IPLayer
    private final List<BaseLayer> upperLayers = new ArrayList<>();
    
    // ===== 콜백 함수 =====
    private Consumer<String> messageReceivedCallback;
    private BiConsumer<String, Long> messageReceivedWithLatencyCallback;
    
    // ===== 메시지 타입 상수 =====
    private static final byte MSG_TYPE_SINGLE = 0x01;      // 단일 메시지 (Fragment 불필요)
    private static final byte MSG_TYPE_FRAGMENT = 0x02;    // Fragment화된 메시지
    
    // ===== 암호화 관련 상수 =====
    private static final byte ENCRYPTION_FLAG = (byte) 0x80;  // 10000000 (암호화 플래그)
    private static final byte MSG_TYPE_MASK = 0x7F;            // 01111111 (타입 마스크)
    private static final byte XOR_ENCRYPTION_KEY = 0x42;       // XOR 암호화 키
    
    // ===== Fragment 설정 =====
    private static final int MAX_FRAGMENT_SIZE = 512; // Fragment당 최대 데이터 크기 (바이트)
    
    // ===== 메시지 재조립 버퍼 =====
    private final Map<Integer, MessageReassemblyBuffer> reassemblyBuffers = new ConcurrentHashMap<>();
    
    // ===== 중복 메시지 필터 =====
    // 타임스탬프와 메시지 해시를 결합하여 중복 체크 (같은 시간에 다른 메시지는 허용)
    private final Set<String> recentMessageIds = ConcurrentHashMap.newKeySet();
    private static final long DEDUP_WINDOW_MS = 5000; // 5초 내 같은 메시지 ID는 중복으로 간주
    private volatile long lastCleanupTime = System.currentTimeMillis();
    
    // ===== 암호화 설정 =====
    private boolean encryptionEnabled = false;
    
    // ===== 새로운 기능: 우선순위 =====
    public enum Priority {
        HIGH(0, "[긴급]"),
        NORMAL(1, "[일반]"),
        LOW(2, "[낮음]");
        
        public final int order;
        public final String label;
        
        Priority(int order, String label) {
            this.order = order;
            this.label = label;
        }
    }
    
    private Priority currentPriority = Priority.NORMAL;
    
    // ===== 우선순위 메시지 래퍼 클래스 =====
    private static class PrioritizedMessage implements Comparable<PrioritizedMessage> {
        final String content;
        final Priority priority;
        final long queuedAt;      // 큐에 추가된 시간
        final long sentAt;        // 원본 전송 시간
        
        PrioritizedMessage(String content, Priority priority, long sentAt) {
            this.content = content;
            this.priority = priority;
            this.sentAt = sentAt;
            this.queuedAt = System.currentTimeMillis();
        }
        
        @Override
        public int compareTo(PrioritizedMessage other) {
            // 1. 우선순위가 높을수록 먼저 (order 값이 작을수록 우선순위 높음)
            int priorityCompare = Integer.compare(this.priority.order, other.priority.order);
            if (priorityCompare != 0) return priorityCompare;
            // 2. 같은 우선순위면 먼저 큐에 들어온 것부터 (FIFO)
            return Long.compare(this.queuedAt, other.queuedAt);
        }
    }
    
    // ===== 우선순위 큐 =====       // https://developer87.tistory.com/13
    private final PriorityBlockingQueue<PrioritizedMessage> priorityMessageQueue = new PriorityBlockingQueue<>();
    private Thread messageProcessorThread;
    private volatile boolean isProcessorRunning = true;
    
    // ===== 데모 모드 (우선순위 시연용) =====
    private volatile boolean demoMode = false;
    private static final long DEMO_MESSAGE_DELAY_MS = 800; // 각 메시지 처리 간격 (0.8초)
    
    // ===== 로깅 설정 =====
    private static final String LOG_FILE_PATH = "packet.log";
    private static PrintWriter logFileWriter;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    
    static {
        try {
            logFileWriter = new PrintWriter(new FileWriter(LOG_FILE_PATH, true), true);
        } catch (IOException e) {
            System.err.println("[ChatApp] 로그 파일 생성 실패: " + e.getMessage());
        }
    }
    
    /**
     * 메시지 재조립 버퍼 (Fragment화된 메시지 수집)
     */
    private static class MessageReassemblyBuffer {
        int expectedFragmentCount;
        Map<Integer, byte[]> receivedFragments = new HashMap<>();
        long originalSentTimestamp;
        Priority messagePriority = Priority.NORMAL;
        
        MessageReassemblyBuffer(int expectedFragmentCount) {
            this.expectedFragmentCount = expectedFragmentCount;
        }
        
        boolean isComplete() {
            return receivedFragments.size() >= expectedFragmentCount;
        }
        
        byte[] reassembleMessage() {
            int totalSize = 0;
            for (byte[] fragment : receivedFragments.values()) {
                totalSize += fragment.length;
            }
            
            byte[] result = new byte[totalSize];
            int offset = 0;
            
            for (int i = 0; i < expectedFragmentCount; i++) {
                byte[] fragment = receivedFragments.get(i);
                if (fragment != null) {
                    System.arraycopy(fragment, 0, result, offset, fragment.length);
                    offset += fragment.length;
                }
            }
            
            return result;
        }
    }

    /**
     * ChatAppLayer 생성자
     * @param messageCallback 메시지 수신 시 호출될 콜백 함수 (예: UI에 메시지 표시)
     */
    public ChatAppLayer(Consumer<String> messageCallback) {
        this.messageReceivedCallback = messageCallback;
        startMessageProcessor();
    }

    /**
     * 수신 콜백 함수를 변경합니다.
     * @param callback 새로운 콜백 함수
     */
    public void setOnReceive(Consumer<String> callback) {
        this.messageReceivedCallback = callback;
    }
    
    /**
     * 지연시간 포함 수신 콜백 설정
     */
    public void setOnReceiveWithLatency(BiConsumer<String, Long> callback) {
        this.messageReceivedWithLatencyCallback = callback;
    }
    
    // ===== 암호화 기능 메서드 =====
    
    /**
     * 암호화 활성화/비활성화
     */
    public void setEncryptionEnabled(boolean enabled) {
        this.encryptionEnabled = enabled;
        log("SYSTEM", "암호화 " + (enabled ? "활성화" : "비활성화"));
    }
    
    /**
     * 암호화 상태 확인
     */
    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }
    
    /**
     * XOR 암호화/복호화 (동일 연산)
     * a ^ key ^ key = a
     *
     * ex) XOR 스왑 알고리즘
     * a = X, b= Y
     * a = a ^ b
     * b = a ^ b
     * a = a ^ b
     * -> a = Y, b = X
     */
    private byte[] applyXorEncryption(byte[] data, byte key) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key);
        }
        return result;
    }
    
    // ===== 우선순위 기능 메서드 =====
    
    /**
     * 현재 우선순위 설정
     */
    public void setPriority(Priority priority) {
        this.currentPriority = priority;
        log("SYSTEM", "우선순위 변경: " + priority.label);
    }
    
    /**
     * 데모 모드 설정 (우선순위 시연용)
     * 데모 모드 활성화 시 각 메시지 처리에 1.5초 지연 추가
     */
    public void setDemoMode(boolean enabled) {
        this.demoMode = enabled;
        String status = enabled ? "활성화 (메시지 처리 지연: " + DEMO_MESSAGE_DELAY_MS + "ms)" : "비활성화";
        log("SYSTEM", "데모 모드 " + status);
        System.out.println("[ChatApp] 데모 모드 " + status);
    }
    
    /**
     * 데모 모드 상태 확인
     */
    public boolean isDemoMode() {
        return demoMode;
    }
    
    /**
     * 현재 우선순위 반환
     */
    public Priority getPriority() {
        return currentPriority;
    }
    
    /**
     * 메시지 처리 스레드 시작
     */
    private void startMessageProcessor() {
        messageProcessorThread = new Thread(() -> {
            while (isProcessorRunning) {
                try {
                    PrioritizedMessage msg = priorityMessageQueue.take();
                    long receivedAt = System.currentTimeMillis();
                    long networkLatency = receivedAt - msg.sentAt;
                    
                    String formattedMessage = String.format("%s %s (지연: %dms)", 
                        msg.priority.label, msg.content, networkLatency);
                    
                    log("RECV", String.format("%s (sent=%d, received=%d, latency=%dms)", 
                        msg.content, msg.sentAt, receivedAt, networkLatency));
                    
                    // 데모 모드: 우선순위 시연을 위해 각 메시지 처리에 지연 추가
                    if (demoMode) {
                        System.out.println("\n" + "=".repeat(70));
                        System.out.println(String.format("🎬 [데모] 우선순위 큐에서 꺼냄: %s \"%s\"", 
                            msg.priority.label, msg.content));
                        System.out.println(String.format("    큐에 남은 메시지: %d개", priorityMessageQueue.size()));
                        System.out.println(String.format("    다음 메시지까지 %dms 대기...", DEMO_MESSAGE_DELAY_MS));
                        System.out.println("=".repeat(70) + "\n");
                        Thread.sleep(DEMO_MESSAGE_DELAY_MS);
                    }
                    
                    if (messageReceivedWithLatencyCallback != null) {
                        messageReceivedWithLatencyCallback.accept(formattedMessage, networkLatency);
                    } else if (messageReceivedCallback != null) {
                        messageReceivedCallback.accept(formattedMessage);
                    }
                    
                    // UI 업데이트 시간 확보
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "ChatMessageProcessor");
        messageProcessorThread.setDaemon(true);
        messageProcessorThread.start();
    }
    
    /**
     * 메시지 처리 중지
     */
    public void stopMessageProcessing() {
        isProcessorRunning = false;
        if (messageProcessorThread != null) {
            messageProcessorThread.interrupt();
        }
    }
    
    // ===== 로깅 기능 메서드 =====
    
    /**
     * 로그 기록
     */
    private void log(String action, String message) {
        if (logFileWriter == null) return;
        
        String timestamp = DATE_FORMAT.format(new Date());
        String logLine = String.format("%s [%s] %s", timestamp, action, message);
        
        logFileWriter.println(logLine);
        System.out.println("[ChatApp:LOG] " + logLine);
    }
    
    /**
     * 로그 파일 경로 반환
     */
    public static String getLogFilePath() {
        return LOG_FILE_PATH;
    }

    /**
     * 문자열 메시지를 네트워크로 전송합니다.
     * 긴 메시지는 자동으로 Fragment화됩니다.
     * 
     * 확장된 헤더 구조:
     * [Type+Flag(1B)] [Priority(1B)] [Timestamp(8B)] [Seq(4B)] [Total(4B)] [Data]
     * 
     * @param text 전송할 메시지 문자열
     * @return 전송 성공 여부
     */
    public boolean sendMessage(String text) {
        if (lowerLayer == null) return false;
        
        byte[] messageBytes = text.getBytes(StandardCharsets.UTF_8);
        long sentTimestamp = System.currentTimeMillis();
        
        // IPLayer에 현재 우선순위 설정 (TOS 필드에 반영)
        if (lowerLayer instanceof IPLayer ipLayer) {
            ipLayer.setPriority(currentPriority);
        }
        
        // 암호화 처리
        byte[] dataToSend = messageBytes;
        if (encryptionEnabled) {
            dataToSend = applyXorEncryption(messageBytes, XOR_ENCRYPTION_KEY);
            log("SEND", text + " [암호화됨] (timestamp=" + sentTimestamp + ")");
        } else {
            log("SEND", text + " (timestamp=" + sentTimestamp + ")");
        }
        
        // Type 바이트 생성 (암호화 플래그 포함)
        byte typeFlag = encryptionEnabled ? ENCRYPTION_FLAG : 0;
        byte priorityByte = (byte) currentPriority.order;
        
        // 작은 메시지는 Fragment화하지 않음
        if (dataToSend.length <= MAX_FRAGMENT_SIZE) {
            // MSG_TYPE_SINGLE + Priority + Timestamp + Data
            // 헤더: 1 + 1 + 8 = 10바이트
            ByteBuffer buffer = ByteBuffer.allocate(1 + 1 + 8 + dataToSend.length);
            buffer.put((byte) (MSG_TYPE_SINGLE | typeFlag));
            buffer.put(priorityByte);
            buffer.putLong(sentTimestamp);
            buffer.put(dataToSend);
            
            return lowerLayer.Send(buffer.array(), buffer.position());
        }
        
        // 큰 메시지는 Fragment화
        int fragmentCount = (int) Math.ceil((double) dataToSend.length / MAX_FRAGMENT_SIZE);
        System.out.println("[ChatApp] 메시지 Fragment화: " + fragmentCount + "개");
        log("SEND", text + " (fragments=" + fragmentCount + ", timestamp=" + sentTimestamp + ")");
        
        for (int seq = 0; seq < fragmentCount; seq++) {
            int offset = seq * MAX_FRAGMENT_SIZE;
            // 마지막 Fragment는 MAX_FRAGMENT_SIZE보다 작을 수 있으므로 실제 남은 크기와 비교하여 작은 값 선택
            int length = Math.min(MAX_FRAGMENT_SIZE, dataToSend.length - offset);
            byte[] fragment = Arrays.copyOfRange(dataToSend, offset, offset + length);
            
            // MSG_TYPE_FRAGMENT + Priority + Timestamp + Sequence + TotalSeq + Data
            // 헤더: 1 + 1 + 8 + 4 + 4 = 18바이트
            ByteBuffer buffer = ByteBuffer.allocate(1 + 1 + 8 + 4 + 4 + fragment.length);
            buffer.put((byte) (MSG_TYPE_FRAGMENT | typeFlag));
            buffer.put(priorityByte);
            buffer.putLong(sentTimestamp);
            buffer.putInt(seq);
            buffer.putInt(fragmentCount);
            buffer.put(fragment);
            
            if (!lowerLayer.Send(buffer.array(), buffer.position())) {
                System.err.println("[ChatApp] Fragment 전송 실패: " + seq);
                return false;
            }
        }
        
        return true;
    }

    @Override
    public String GetLayerName() { return LAYER_NAME; }

    @Override
    public BaseLayer GetUnderLayer() { return lowerLayer; }

    @Override
    public BaseLayer GetUpperLayer(int index) { 
        return (index >= 0 && index < upperLayers.size()) ? upperLayers.get(index) : null; 
    }

    @Override
    public void SetUnderLayer(BaseLayer layer) { 
        this.lowerLayer = layer; 
    }

    @Override
    public void SetUpperLayer(BaseLayer layer) { 
        if (!upperLayers.contains(layer)) upperLayers.add(layer); 
    }

    /**
     * BaseLayer 인터페이스의 Send 메서드 구현
     * 바이트 배열을 문자열로 변환한 후 sendMessage 호출
     * (일반적으로는 sendMessage를 직접 사용)
     */
    @Override
    public boolean Send(byte[] input, int length) { 
        return sendMessage(new String(input, 0, length, StandardCharsets.UTF_8)); 
    }

    /**
     * 하위 계층(IP)으로부터 데이터를 수신합니다.
     * Fragment화된 메시지는 재조립됩니다.
     * 
     * 확장된 헤더 구조:
     * [Type+Flag(1B)] [Priority(1B)] [Timestamp(8B)] [Seq(4B)] [Total(4B)] [Data]
     * 
     * @param input 수신한 바이트 배열
     * @return 처리 성공 여부
     */
    @Override
    public boolean Receive(byte[] input) {
        if (input == null || input.length < 2) {
            return false;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(input);
        byte typeFlagByte = buffer.get();
        
        // 암호화 플래그 추출
        boolean isEncrypted = (typeFlagByte & ENCRYPTION_FLAG) != 0;
        byte messageType = (byte) (typeFlagByte & MSG_TYPE_MASK);
        
        // 우선순위 추출 (1바이트)
        byte priorityByte = buffer.get();
        Priority priority = priorityFromByte(priorityByte);
        
        // 타임스탬프 추출 (8바이트)
        if (buffer.remaining() < 8) {
            return false;
        }
        long originalSentTimestamp = buffer.getLong();
        
        switch (messageType) {
            case MSG_TYPE_SINGLE:
                // 단일 메시지 (Fragment화되지 않음)
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                
                // 복호화 처리
                if (isEncrypted) {
                    data = applyXorEncryption(data, XOR_ENCRYPTION_KEY);
                    System.out.println("[ChatApp] 메시지 복호화됨");
                }
                
                String message = new String(data, StandardCharsets.UTF_8);
                
                // 중복 메시지 필터링 (타임스탬프 + 내용 해시 기반)
                if (isDuplicate(originalSentTimestamp, message.hashCode())) {
                    System.out.println("[ChatApp] 중복 메시지 감지 - 드롭 (timestamp=" + originalSentTimestamp + ")");
                    return true; // 중복이지만 처리는 성공으로 간주
                }
                
                // 우선순위 큐에 추가
                priorityMessageQueue.offer(new PrioritizedMessage(message, priority, originalSentTimestamp));
                
                // 데모 모드: 큐 상태 출력
                if (demoMode) {
                    System.out.println(String.format("📥 [데모] 메시지 도착 → 우선순위 큐에 추가: %s \"%s\" (큐 크기: %d)", 
                        priority.label, message, priorityMessageQueue.size()));
                }
                
                break;
                
            case MSG_TYPE_FRAGMENT:
                // Fragment화된 메시지
                if (buffer.remaining() < 8) {
                    return false;
                }
                
                int sequenceNumber = buffer.getInt();
                int totalFragments = buffer.getInt();
                
                byte[] fragmentData = new byte[buffer.remaining()];
                buffer.get(fragmentData);
                
                // 복호화 처리
                if (isEncrypted) {
                    fragmentData = applyXorEncryption(fragmentData, XOR_ENCRYPTION_KEY);
                }
                
                processFragment(sequenceNumber, totalFragments, fragmentData, originalSentTimestamp, isEncrypted, priority);
                break;
                
            default:
                System.err.println("[ChatApp] 알 수 없는 메시지 타입: " + messageType);
                return false;
        }
        
        return true;
    }
    
    /**
     * IPLayer에서 우선순위 추출 (TOS 필드)
     */
    @SuppressWarnings("unused")
    private Priority extractPriorityFromIPLayer() {
        if (lowerLayer instanceof IPLayer ipLayer) {
            return ipLayer.getCurrentReceivedPriority();
        }
        return Priority.NORMAL;
    }
    
    /**
     * 우선순위 바이트를 Priority enum으로 변환
     */
    private Priority priorityFromByte(byte b) {
        return switch (b) {
            case 0 -> Priority.HIGH;
            case 2 -> Priority.LOW;
            default -> Priority.NORMAL;
        };
    }
    
    /**
     * 중복 메시지 체크 (타임스탬프 + 메시지 해시 기반)
     * 같은 타임스탬프라도 다른 내용의 메시지는 허용
     * @param timestamp 메시지 타임스탬프
     * @param messageHash 메시지 내용의 해시 코드
     * @return 중복이면 true, 새 메시지면 false
     */
    private boolean isDuplicate(long timestamp, int messageHash) {
        String messageId = timestamp + ":" + messageHash;
        
        // 이미 처리한 메시지인지 확인
        if (recentMessageIds.contains(messageId)) {
            return true;
        }
        
        // 새 메시지 ID 등록
        recentMessageIds.add(messageId);
        
        // 주기적으로 오래된 항목 정리 (매 정리 주기마다)
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > DEDUP_WINDOW_MS) {
            lastCleanupTime = now;
            // 현재 크기가 너무 크면 전체 초기화 (단순한 구현)
            if (recentMessageIds.size() > 1000) {
                recentMessageIds.clear();
            }
        }
        
        return false;
    }
    
    /**
     * Fragment 처리 및 재조립
     */
    private void processFragment(int sequenceNumber, int totalFragments, byte[] data, 
                                  long sentTimestamp, boolean wasEncrypted, Priority priority) {
        // 고유 ID 생성 (totalFragments를 기준으로)
        int messageId = totalFragments;
        
        MessageReassemblyBuffer buffer = reassemblyBuffers.get(messageId);
        if (buffer == null) {
            buffer = new MessageReassemblyBuffer(totalFragments);
            buffer.originalSentTimestamp = sentTimestamp;
            buffer.messagePriority = priority;
            reassemblyBuffers.put(messageId, buffer);
            System.out.println("[ChatApp] 새 메시지 수신 시작 (총 " + totalFragments + "개 Fragment)");
        }
        
        // Fragment 저장
        buffer.receivedFragments.put(sequenceNumber, data);
        System.out.println("[ChatApp] Fragment 수신: " + (sequenceNumber + 1) + "/" + totalFragments);
        
        // 모든 Fragment 수신 완료 확인
        if (buffer.isComplete()) {
            byte[] completeMessage = buffer.reassembleMessage();
            String message = new String(completeMessage, StandardCharsets.UTF_8);
            
            System.out.println("[ChatApp] 메시지 재조립 완료: " + message.length() + "바이트" + 
                              (wasEncrypted ? " [복호화됨]" : ""));
            
            // 중복 메시지 필터링 (타임스탬프 + 내용 해시 기반)
            if (!isDuplicate(buffer.originalSentTimestamp, message.hashCode())) {
                // 우선순위 큐에 추가
                priorityMessageQueue.offer(new PrioritizedMessage(message, buffer.messagePriority, buffer.originalSentTimestamp));
            } else {
                System.out.println("[ChatApp] 중복 Fragment 메시지 감지 - 드롭 (timestamp=" + buffer.originalSentTimestamp + ")");
            }
            
            // 버퍼 제거
            reassemblyBuffers.remove(messageId);
        }
    }
}
