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
    private final String name = "ChatApp";
    private BaseLayer underLayer; // 하위 계층: IPLayer
    private final List<BaseLayer> uppers = new ArrayList<>();
    private Consumer<String> onReceive; // 메시지 수신 시 호출될 콜백 함수
    private BiConsumer<String, Long> onReceiveWithLatency; // 지연시간 포함 콜백
    
    // Fragment 타입
    private static final byte TYPE_CHAT_SINGLE = 0x01;
    private static final byte TYPE_CHAT_FRAGMENT = 0x02;
    
    // 암호화 관련 상수
    private static final byte FLAG_ENCRYPTED = (byte) 0x80;  // 10000000 (최상위 비트)
    private static final byte TYPE_MASK = 0x7F;               // 01111111 (타입 마스크)
    private static final byte ENCRYPTION_KEY = 0x42;          // 암호화 키 (고정)
    
    // Fragment 크기 (최대 페이로드)
    private static final int MAX_DATA_SIZE = 512; // 512 bytes per fragment
    
    // 수신 중인 메시지 재조립 버퍼
    private final Map<Integer, ChatReceiveContext> receivingMessages = new ConcurrentHashMap<>();
    
    // ===== 새로운 기능: 암호화 =====
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
    
    // 우선순위 메시지 클래스
    private static class PriorityMessage implements Comparable<PriorityMessage> {
        final String text;
        final Priority priority;
        final long timestamp;
        final long sendTime;
        
        PriorityMessage(String text, Priority priority, long sendTime) {
            this.text = text;
            this.priority = priority;
            this.sendTime = sendTime;
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public int compareTo(PriorityMessage other) {
            // 1. 우선순위가 높을수록 먼저 (order 낮을수록 우선)
            int priorityCompare = Integer.compare(this.priority.order, other.priority.order);
            if (priorityCompare != 0) return priorityCompare;
            // 2. 같은 우선순위면 먼저 온 것부터
            return Long.compare(this.timestamp, other.timestamp);
        }
    }
    
    // 우선순위 큐
    private final PriorityBlockingQueue<PriorityMessage> messageQueue = new PriorityBlockingQueue<>();
    private Thread processingThread;
    private volatile boolean running = true;
    
    // ===== 새로운 기능: 로깅 =====
    private static final String LOG_FILE = "packet.log";
    private static PrintWriter logWriter;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    
    static {
        try {
            logWriter = new PrintWriter(new FileWriter(LOG_FILE, true), true);
        } catch (IOException e) {
            System.err.println("[ChatApp] 로그 파일 생성 실패: " + e.getMessage());
        }
    }
    
    /**
     * 채팅 메시지 수신 컨텍스트
     */
    private static class ChatReceiveContext {
        int totalSequences;
        Map<Integer, byte[]> fragments = new HashMap<>();
        long sendTimestamp; // 송신 시 타임스탬프
        Priority priority = Priority.NORMAL;
        
        ChatReceiveContext(int totalSequences) {
            this.totalSequences = totalSequences;
        }
        
        boolean isComplete() {
            return fragments.size() >= totalSequences;
        }
        
        byte[] assembleMessage() {
            int totalSize = 0;
            for (byte[] frag : fragments.values()) {
                totalSize += frag.length;
            }
            
            byte[] result = new byte[totalSize];
            int offset = 0;
            
            for (int i = 0; i < totalSequences; i++) {
                byte[] frag = fragments.get(i);
                if (frag != null) {
                    System.arraycopy(frag, 0, result, offset, frag.length);
                    offset += frag.length;
                }
            }
            
            return result;
        }
    }

    /**
     * ChatAppLayer 생성자
     * @param onReceive 메시지 수신 시 호출될 콜백 함수 (예: UI에 메시지 표시)
     */
    public ChatAppLayer(Consumer<String> onReceive) {
        this.onReceive = onReceive;
        startMessageProcessing();
    }

    /**
     * 수신 콜백 함수를 변경합니다.
     * @param onReceive 새로운 콜백 함수
     */
    public void setOnReceive(Consumer<String> onReceive) {
        this.onReceive = onReceive;
    }
    
    /**
     * 지연시간 포함 수신 콜백 설정
     */
    public void setOnReceiveWithLatency(BiConsumer<String, Long> callback) {
        this.onReceiveWithLatency = callback;
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
     */
    private byte[] xorCrypt(byte[] data, byte key) {
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
     * 현재 우선순위 반환
     */
    public Priority getPriority() {
        return currentPriority;
    }
    
    /**
     * 메시지 처리 스레드 시작
     */
    private void startMessageProcessing() {
        processingThread = new Thread(() -> {
            while (running) {
                try {
                    PriorityMessage msg = messageQueue.take();
                    long receiveTime = System.currentTimeMillis();
                    long latency = receiveTime - msg.sendTime;
                    
                    String formattedMessage = String.format("%s %s (지연: %dms)", 
                        msg.priority.label, msg.text, latency);
                    
                    log("RECV", String.format("%s (send=%d, recv=%d, latency=%dms)", 
                        msg.text, msg.sendTime, receiveTime, latency));
                    
                    if (onReceiveWithLatency != null) {
                        onReceiveWithLatency.accept(formattedMessage, latency);
                    } else if (onReceive != null) {
                        onReceive.accept(formattedMessage);
                    }
                    
                    // 약간의 딜레이로 UI 업데이트 시간 확보
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "MessageProcessor");
        processingThread.setDaemon(true);
        processingThread.start();
    }
    
    /**
     * 메시지 처리 중지
     */
    public void stopMessageProcessing() {
        running = false;
        if (processingThread != null) {
            processingThread.interrupt();
        }
    }
    
    // ===== 로깅 기능 메서드 =====
    
    /**
     * 로그 기록
     */
    private void log(String action, String message) {
        if (logWriter == null) return;
        
        String timestamp = DATE_FORMAT.format(new Date());
        String logLine = String.format("%s [%s] %s", timestamp, action, message);
        
        logWriter.println(logLine);
        System.out.println("[ChatApp:LOG] " + logLine);
    }
    
    /**
     * 로그 파일 경로 반환
     */
    public static String getLogFilePath() {
        return LOG_FILE;
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
        if (underLayer == null) return false;
        
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        long sendTime = System.currentTimeMillis();
        
        // 암호화 처리
        byte[] dataToSend = bytes;
        if (encryptionEnabled) {
            dataToSend = xorCrypt(bytes, ENCRYPTION_KEY);
            log("SEND", text + " [암호화됨] (timestamp=" + sendTime + ")");
        } else {
            log("SEND", text + " (timestamp=" + sendTime + ")");
        }
        
        // Type 바이트 생성 (암호화 플래그 포함)
        byte typeFlag = encryptionEnabled ? FLAG_ENCRYPTED : 0;
        byte priorityByte = (byte) currentPriority.order;
        
        // 작은 메시지는 Fragment화하지 않음
        if (dataToSend.length <= MAX_DATA_SIZE) {
            // TYPE_CHAT_SINGLE + Priority + Timestamp + Data
            // 헤더: 1 + 1 + 8 = 10바이트
            ByteBuffer buffer = ByteBuffer.allocate(1 + 1 + 8 + dataToSend.length);
            buffer.put((byte) (TYPE_CHAT_SINGLE | typeFlag));
            buffer.put(priorityByte);
            buffer.putLong(sendTime);
            buffer.put(dataToSend);
            
            return underLayer.Send(buffer.array(), buffer.position());
        }
        
        // 큰 메시지는 Fragment화
        int totalSequences = (int) Math.ceil((double) dataToSend.length / MAX_DATA_SIZE);
        System.out.println("[ChatApp] 메시지 Fragment화: " + totalSequences + "개");
        log("SEND", text + " (fragments=" + totalSequences + ", timestamp=" + sendTime + ")");
        
        for (int seq = 0; seq < totalSequences; seq++) {
            int offset = seq * MAX_DATA_SIZE;
            int length = Math.min(MAX_DATA_SIZE, dataToSend.length - offset);
            byte[] fragment = Arrays.copyOfRange(dataToSend, offset, offset + length);
            
            // TYPE_CHAT_FRAGMENT + Priority + Timestamp + Sequence + TotalSeq + Data
            // 헤더: 1 + 1 + 8 + 4 + 4 = 18바이트
            ByteBuffer buffer = ByteBuffer.allocate(1 + 1 + 8 + 4 + 4 + fragment.length);
            buffer.put((byte) (TYPE_CHAT_FRAGMENT | typeFlag));
            buffer.put(priorityByte);
            buffer.putLong(sendTime);
            buffer.putInt(seq);
            buffer.putInt(totalSequences);
            buffer.put(fragment);
            
            if (!underLayer.Send(buffer.array(), buffer.position())) {
                System.err.println("[ChatApp] Fragment 전송 실패: " + seq);
                return false;
            }
        }
        
        return true;
    }

    @Override
    public String GetLayerName() { return name; }

    @Override
    public BaseLayer GetUnderLayer() { return underLayer; }

    @Override
    public BaseLayer GetUpperLayer(int index) { 
        return (index >=0 && index < uppers.size()) ? uppers.get(index) : null; 
    }

    @Override
    public void SetUnderLayer(BaseLayer pUnderLayer) { 
        this.underLayer = pUnderLayer; 
    }

    @Override
    public void SetUpperLayer(BaseLayer pUpperLayer) { 
        if (!uppers.contains(pUpperLayer)) uppers.add(pUpperLayer); 
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
        boolean isEncrypted = (typeFlagByte & FLAG_ENCRYPTED) != 0;
        byte type = (byte) (typeFlagByte & TYPE_MASK);
        
        // 우선순위 추출 (1바이트)
        byte priorityByte = buffer.get();
        Priority priority = priorityFromByte(priorityByte);
        
        // 타임스탬프 추출 (8바이트)
        if (buffer.remaining() < 8) {
            return false;
        }
        long sendTimestamp = buffer.getLong();
        
        switch (type) {
            case TYPE_CHAT_SINGLE:
                // 단일 메시지 (Fragment화되지 않음)
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                
                // 복호화 처리
                if (isEncrypted) {
                    data = xorCrypt(data, ENCRYPTION_KEY);
                    System.out.println("[ChatApp] 메시지 복호화됨");
                }
                
                String msg = new String(data, StandardCharsets.UTF_8);
                
                // 우선순위 큐에 추가 (헤더에서 추출한 우선순위 사용)
                messageQueue.offer(new PriorityMessage(msg, priority, sendTimestamp));
                
                break;
                
            case TYPE_CHAT_FRAGMENT:
                // Fragment화된 메시지
                if (buffer.remaining() < 8) {
                    return false;
                }
                
                int sequence = buffer.getInt();
                int totalSequences = buffer.getInt();
                
                byte[] fragmentData = new byte[buffer.remaining()];
                buffer.get(fragmentData);
                
                // 복호화 처리
                if (isEncrypted) {
                    fragmentData = xorCrypt(fragmentData, ENCRYPTION_KEY);
                }
                
                handleFragment(sequence, totalSequences, fragmentData, sendTimestamp, isEncrypted, priority);
                break;
                
            default:
                System.err.println("[ChatApp] 알 수 없는 메시지 타입: " + type);
                return false;
        }
        
        return true;
    }
    
    /**
     * IPLayer에서 우선순위 추출 (TOS 필드)
     */
    private Priority extractPriorityFromIPLayer() {
        if (underLayer instanceof IPLayer ipLayer) {
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
     * Fragment 처리 및 재조립
     */
    private void handleFragment(int sequence, int totalSequences, byte[] data, long sendTimestamp, boolean wasEncrypted, Priority priority) {
        // 고유 ID 생성 (totalSequences를 기준으로)
        int messageId = totalSequences;
        
        ChatReceiveContext context = receivingMessages.get(messageId);
        if (context == null) {
            context = new ChatReceiveContext(totalSequences);
            context.sendTimestamp = sendTimestamp;
            context.priority = priority;
            receivingMessages.put(messageId, context);
            System.out.println("[ChatApp] 새 메시지 수신 시작 (총 " + totalSequences + "개 Fragment)");
        }
        
        // Fragment 저장
        context.fragments.put(sequence, data);
        System.out.println("[ChatApp] Fragment 수신: " + (sequence + 1) + "/" + totalSequences);
        
        // 모든 Fragment 수신 완료 확인
        if (context.isComplete()) {
            byte[] completeMessage = context.assembleMessage();
            String msg = new String(completeMessage, StandardCharsets.UTF_8);
            
            System.out.println("[ChatApp] 메시지 재조립 완료: " + msg.length() + "바이트" + 
                              (wasEncrypted ? " [복호화됨]" : ""));
            
            // 우선순위 큐에 추가
            messageQueue.offer(new PriorityMessage(msg, context.priority, context.sendTimestamp));
            
            // 컨텍스트 제거
            receivingMessages.remove(messageId);
        }
    }
}
