package com.demo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ChatAppLayer - 채팅 애플리케이션 계층 (OSI 응용 계층에 해당)
 * 
 * 역할:
 * - 사용자가 입력한 문자열 메시지를 바이트 배열로 변환 (송신)
 * - 수신한 바이트 배열을 문자열로 변환하여 UI에 표시 (수신)
 * 
 * 이 계층은 네트워크 프로토콜 스택의 최상위 계층으로,
 * 사용자와 직접 상호작용하는 애플리케이션 로직을 담당합니다.
 * 
 * 데이터 변환 예시:
 *   송신: "안녕" → UTF-8 인코딩 → [0xEC, 0x95, 0x88, 0xEB, 0x85, 0x95]
 *   수신: [0xEC, 0x95, 0x88, 0xEB, 0x85, 0x95] → UTF-8 디코딩 → "안녕"
 */
public class ChatAppLayer implements BaseLayer {
    private final String name = "ChatApp";
    private BaseLayer underLayer; // 하위 계층: EthernetLayer
    private final List<BaseLayer> uppers = new ArrayList<>(); // 상위 계층: 없음 (최상위 계층)
    private Consumer<String> onReceive; // 메시지 수신 시 호출될 콜백 함수 (UI 업데이트용)

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
     * 
     * 처리 과정:
     * 1. 문자열을 UTF-8 바이트 배열로 인코딩
     * 2. 하위 계층(Ethernet)의 Send 메서드 호출
     * 
     * @param text 전송할 메시지 문자열
     * @return 전송 성공 여부
     */
    public boolean sendMessage(String text) {
        if (underLayer == null) return false;
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return underLayer.Send(bytes, bytes.length);
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
     * 하위 계층(Ethernet)으로부터 데이터를 수신합니다.
     * 
     * 처리 과정:
     * 1. 바이트 배열 끝의 패딩(0x00) 제거
     *    - Ethernet 프레임은 최소 60바이트로 패딩되므로 불필요한 null 바이트 제거
     * 2. UTF-8 디코딩하여 문자열로 변환
     * 3. onReceive 콜백 함수 호출 (UI에 메시지 표시)
     * 
     * @param input 수신한 바이트 배열 (Ethernet 계층에서 헤더 제거 후 전달된 페이로드)
     * @return 처리 성공 여부
     */
    @Override
    public boolean Receive(byte[] input) {
        if (onReceive != null) {
            // 끝의 패딩(0x00) 제거: Ethernet 최소 프레임 크기 때문에 추가된 null 바이트 제거
            int end = input.length; 
            while (end > 0 && input[end-1] == 0x00) end--;
            
            // UTF-8 디코딩
            String msg = new String(input, 0, end, StandardCharsets.UTF_8);
            
            // UI 업데이트 콜백 호출
            onReceive.accept(msg);
        }
        return true;
    }
}
