package com.demo;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * ChatAppLayer - 채팅 애플리케이션 계층 (OSI 응용 계층에 해당)
 * 
 * 역할:
 * - 사용자가 입력한 문자열 메시지를 바이트 배열로 변환 (송신)
 * - 수신한 바이트 배열을 문자열로 변환하여 UI에 표시 (수신)
 * - Fragmentation 지원: 긴 메시지를 작은 조각으로 나누어 전송
 * 
 * Fragment 헤더 구조 (9바이트):
 * ┌──────────┬──────────┬──────────┬──────────┐
 * │   Type   │ Sequence │Total Seq │  Data    │
 * │ (1 byte) │ (4 bytes)│ (4 bytes)│ (가변)   │
 * └──────────┴──────────┴──────────┴──────────┘
 * 
 * Type:
 * - 0x01: CHAT_SINGLE (단일 메시지, Fragmentation 불필요)
 * - 0x02: CHAT_FRAGMENT (Fragment화된 메시지)
 */
public class ChatAppLayer implements BaseLayer {
    private final String name = "ChatApp";
    private BaseLayer underLayer; // 하위 계층: IPLayer
    private final List<BaseLayer> uppers = new ArrayList<>();
    private Consumer<String> onReceive; // 메시지 수신 시 호출될 콜백 함수
    
    // Fragment 타입
    private static final byte TYPE_CHAT_SINGLE = 0x01;
    private static final byte TYPE_CHAT_FRAGMENT = 0x02;
    
    // Fragment 크기 (최대 페이로드)
    private static final int MAX_DATA_SIZE = 512; // 512 bytes per fragment
    
    // 수신 중인 메시지 재조립 버퍼
    private final Map<Integer, ChatReceiveContext> receivingMessages = new ConcurrentHashMap<>();
    
    /**
     * 채팅 메시지 수신 컨텍스트
     */
    private static class ChatReceiveContext {
        int totalSequences;
        Map<Integer, byte[]> fragments = new HashMap<>();
        
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
    }

    /**
     * 수신 콜백 함수를 변경합니다.
     * @param onReceive 새로운 콜백 함수
     */
    public void setOnReceive(Consumer<String> onReceive) {
        this.onReceive = onReceive;
    }

    /**
     * 문자열 메시지를 네트워크로 전송합니다.
     * 긴 메시지는 자동으로 Fragment화됩니다.
     * 
     * @param text 전송할 메시지 문자열
     * @return 전송 성공 여부
     */
    public boolean sendMessage(String text) {
        if (underLayer == null) return false;
        
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        
        // 작은 메시지는 Fragment화하지 않음
        if (bytes.length <= MAX_DATA_SIZE) {
            // TYPE_CHAT_SINGLE
            ByteBuffer buffer = ByteBuffer.allocate(1 + bytes.length);
            buffer.put(TYPE_CHAT_SINGLE);
            buffer.put(bytes);
            
            return underLayer.Send(buffer.array(), buffer.position());
        }
        
        // 큰 메시지는 Fragment화
        int totalSequences = (int) Math.ceil((double) bytes.length / MAX_DATA_SIZE);
        System.out.println("[ChatApp] 메시지 Fragment화: " + totalSequences + "개");
        
        for (int seq = 0; seq < totalSequences; seq++) {
            int offset = seq * MAX_DATA_SIZE;
            int length = Math.min(MAX_DATA_SIZE, bytes.length - offset);
            byte[] fragment = Arrays.copyOfRange(bytes, offset, offset + length);
            
            // TYPE_CHAT_FRAGMENT + Sequence + TotalSeq + Data
            ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 4 + fragment.length);
            buffer.put(TYPE_CHAT_FRAGMENT);
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
     * @param input 수신한 바이트 배열
     * @return 처리 성공 여부
     */
    @Override
    public boolean Receive(byte[] input) {
        if (input == null || input.length < 1) {
            return false;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(input);
        byte type = buffer.get();
        
        switch (type) {
            case TYPE_CHAT_SINGLE:
                // 단일 메시지 (Fragment화되지 않음)
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                
                if (onReceive != null) {
                    String msg = new String(data, StandardCharsets.UTF_8);
                    onReceive.accept(msg);
                }
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
                
                handleFragment(sequence, totalSequences, fragmentData);
                break;
                
            default:
                System.err.println("[ChatApp] 알 수 없는 메시지 타입: " + type);
                return false;
        }
        
        return true;
    }
    
    /**
     * Fragment 처리 및 재조립
     */
    private void handleFragment(int sequence, int totalSequences, byte[] data) {
        // 고유 ID 생성 (totalSequences를 기준으로)
        int messageId = totalSequences;
        
        ChatReceiveContext context = receivingMessages.get(messageId);
        if (context == null) {
            context = new ChatReceiveContext(totalSequences);
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
            
            System.out.println("[ChatApp] 메시지 재조립 완료: " + msg.length() + "바이트");
            
            if (onReceive != null) {
                onReceive.accept(msg);
            }
            
            // 컨텍스트 제거
            receivingMessages.remove(messageId);
        }
    }
}
