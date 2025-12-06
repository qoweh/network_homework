package com.demo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * IPLayer - IP(Internet Protocol) 계층 (OSI 3계층에 해당)
 * 
 * 역할:
 * - IP 패킷 생성: IP 헤더 추가 및 페이로드 캡슐화
 * - IP 패킷 파싱: 헤더 제거 및 페이로드 추출
 * - ARP 연동: IP 주소를 MAC 주소로 변환하여 전송
 * - 목적지 주소 기반 라우팅 (간단한 구현)
 * - 우선순위 지원: TOS 필드를 통한 메시지 우선순위 처리
 * 
 * IP 헤더 구조 (최소 20바이트, 옵션 제외):
 * ┌────────┬────────┬────────────┬─────────────┬───────────┬──────────┬────────────┬──────────┬─────────┬──────────┐
 * │Version │  IHL   │    TOS     │ Total Length│   ID      │  Flags   │   Offset   │   TTL    │Protocol │ Checksum │
 * │(4비트) │(4비트) │  (1바이트) │  (2바이트)  │(2바이트)  │ (3비트)  │ (13비트)   │(1바이트) │(1바이트)│(2바이트) │
 * └────────┴────────┴────────────┴─────────────┴───────────┴──────────┴────────────┴──────────┴─────────┴──────────┘
 * ┌──────────────────────┬──────────────────────┐
 * │   Source IP Address  │ Destination IP Addr  │
 * │      (4바이트)       │      (4바이트)       │
 * └──────────────────────┴──────────────────────┘
 * 
 * TOS 필드 구조:
 * ┌───────────────┬───┬───┬───┬───┐
 * │  Precedence   │ D │ T │ R │ 0 │
 * │   (3 bits)    │   │   │   │   │
 * └───────────────┴───┴───┴───┴───┘
 *   우선순위 값:
 *   - 111 (0xE0): 긴급 (Network Control)
 *   - 000 (0x00): 일반 (Routine)
 *   - 001 (0x20): 낮음 (Priority)
 * 
 * 이 구현에서는 간단한 IP 헤더만 생성 (Version 4, IHL 5, 옵션 없음)
 */
public class IPLayer implements BaseLayer {
    // ===== 계층 기본 정보 =====
    private static final String LAYER_NAME = "IP";
    private BaseLayer lowerLayer;              // 하위 계층: EthernetLayer
    private ARPLayer arpLayer;                  // ARP 계층: IP→MAC 주소 변환용
    private final List<BaseLayer> upperLayers = new ArrayList<>(); // 상위 계층: ChatAppLayer, FileAppLayer
    
    // ===== IP 주소 설정 =====
    private byte[] sourceIpAddress = new byte[4];      // 자신의 IP 주소
    private byte[] destinationIpAddress = new byte[4]; // 목적지 IP 주소
    
    // ===== IP 헤더 상수 =====
    private static final int IPV4_VERSION = 4;               // IPv4
    private static final int IP_HEADER_LENGTH_UNITS = 5;     // 5 * 4 = 20바이트 (옵션 없음)
    private static final int DEFAULT_TTL = 128;              // Time to Live
    
    // ===== 프로토콜 번호 상수 =====
    @SuppressWarnings("unused")
    private static final int PROTOCOL_TCP = 6;               // TCP (향후 확장용)
    @SuppressWarnings("unused")
    private static final int PROTOCOL_UDP = 17;              // UDP (향후 확장용)
    private static final int PROTOCOL_CHAT_APP = 253;        // ChatApp 프로토콜
    private static final int PROTOCOL_FILE_APP = 254;        // FileApp 프로토콜
    
    // ===== 패킷 ID 및 프로토콜 설정 =====
    private int packetIdentification = 0;                     // IP 패킷 ID (증가)
    private int currentProtocol = PROTOCOL_CHAT_APP;          // 현재 사용할 프로토콜
    
    // ===== TOS (Type of Service) 우선순위 상수 =====
    // TOS 상위 3비트: Precedence (우선순위)
    private static final int TOS_PRIORITY_HIGH = 0xE0;      // 111 00000 (긴급)
    private static final int TOS_PRIORITY_NORMAL = 0x00;    // 000 00000 (일반)
    private static final int TOS_PRIORITY_LOW = 0x20;       // 001 00000 (낮음)
    
    private int currentTosValue = TOS_PRIORITY_NORMAL;                                      // 현재 TOS 값
    private ChatAppLayer.Priority lastReceivedPriority = ChatAppLayer.Priority.NORMAL;  // 마지막 수신 우선순위
    
    /**
     * 자신의 IP 주소 설정
     * @param ip 4바이트 IP 주소
     */
    public void setMyIp(byte[] ip) {
        if (ip != null && ip.length >= 4) {
            System.arraycopy(ip, 0, sourceIpAddress, 0, 4);
        }
    }
    
    /**
     * 목적지 IP 주소 설정
     * @param ip 4바이트 IP 주소
     */
    public void setDstIp(byte[] ip) {
        if (ip != null && ip.length >= 4) {
            System.arraycopy(ip, 0, destinationIpAddress, 0, 4);
        }
    }
    
    /**
     * ARP 계층 설정 (IP→MAC 주소 변환용)
     * @param arpLayer ARP 계층 객체
     */
    public void setArpLayer(ARPLayer arpLayer) {
        this.arpLayer = arpLayer;
    }
    
    /**
     * 자신의 IP 주소 반환
     * @return 4바이트 IP 주소
     */
    public byte[] getMyIp() {
        return Arrays.copyOf(sourceIpAddress, 4);
    }
    
    /**
     * 목적지 IP 주소 반환
     * @return 4바이트 IP 주소
     */
    public byte[] getDstIp() {
        return Arrays.copyOf(destinationIpAddress, 4);
    }
    
    /**
     * 현재 사용할 프로토콜 설정 (ChatApp 또는 FileApp)
     * @param protocol PROTOCOL_CHAT_APP(253) 또는 PROTOCOL_FILE_APP(254)
     */
    public void setProtocol(int protocol) {
        this.currentProtocol = protocol;
    }
    
    /**
     * ChatApp 프로토콜로 설정
     */
    public void useChatProtocol() {
        this.currentProtocol = PROTOCOL_CHAT_APP;
    }
    
    /**
     * FileApp 프로토콜로 설정
     */
    public void useFileProtocol() {
        this.currentProtocol = PROTOCOL_FILE_APP;
    }
    
    // ===== 우선순위 관련 메서드 =====
    
    /**
     * 우선순위 설정 (ChatAppLayer.Priority 사용)
     */
    public void setPriority(ChatAppLayer.Priority priority) {
        switch (priority) {
            case HIGH -> this.currentTosValue = TOS_PRIORITY_HIGH;
            case LOW -> this.currentTosValue = TOS_PRIORITY_LOW;
            default -> this.currentTosValue = TOS_PRIORITY_NORMAL;
        }
        System.out.println("[IP] 우선순위 설정: " + priority.label + " (TOS=0x" + 
                          Integer.toHexString(currentTosValue).toUpperCase() + ")");
    }
    
    /**
     * 마지막 수신 패킷의 우선순위 반환
     */
    public ChatAppLayer.Priority getCurrentReceivedPriority() {
        return lastReceivedPriority;
    }
    
    /**
     * TOS 바이트에서 우선순위 추출
     */
    private ChatAppLayer.Priority priorityFromTos(int tos) {
        int precedence = (tos & 0xE0); // 상위 3비트
        return switch (precedence) {
            case TOS_PRIORITY_HIGH -> ChatAppLayer.Priority.HIGH;
            case TOS_PRIORITY_LOW -> ChatAppLayer.Priority.LOW;
            default -> ChatAppLayer.Priority.NORMAL;
        };
    }
    
    // ===== BaseLayer 인터페이스 구현 =====
    
    @Override
    public String GetLayerName() {
        return LAYER_NAME;
    }
    
    @Override
    public BaseLayer GetUnderLayer() {
        return lowerLayer;
    }
    
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
        if (!upperLayers.contains(layer)) {
            upperLayers.add(layer);
        }
    }
    
    /**
     * 상위 계층(ChatApp)으로부터 데이터를 받아 IP 패킷으로 캡슐화하여 전송
     * 
     * 전송 과정:
     * 1. 목적지 IP 주소에 대한 MAC 주소를 ARP 캐시에서 조회
     * 2. ARP 캐시에 없으면 ARP Request 전송 후 실패 반환
     * 3. IP 헤더 생성 (20바이트)
     * 4. 페이로드 추가
     * 5. 하위 계층(Ethernet)으로 전송
     * 
     * @param input 상위 계층의 페이로드
     * @param length 페이로드 길이
     * @return 전송 성공 여부
     */
    @Override
    public boolean Send(byte[] input, int length) {
        if (lowerLayer == null || arpLayer == null) {
            System.out.println("[IP] 하위 계층 또는 ARP 계층이 설정되지 않음");
            return false;
        }
        
        // 목적지 IP에 대한 MAC 주소 조회
        String destinationIpStr = formatIp(destinationIpAddress);
        byte[] destinationMac = arpLayer.lookupArpCache(destinationIpStr);
        
        // ARP 캐시에 없으면 ARP Request 전송
        if (destinationMac == null) {
            System.out.println("[IP] ARP 캐시에 " + destinationIpStr + " 없음 - ARP Request 전송");
            arpLayer.sendArpRequest(destinationIpAddress);
            return false;
        }
        
        System.out.println("[IP] 목적지 MAC 주소 발견: " + formatMac(destinationMac));
        
        // IP 패킷 생성: IP 헤더(20바이트) + 페이로드
        int totalLength = 20 + length;
        byte[] ipPacket = new byte[totalLength];
        ByteBuffer buffer = ByteBuffer.wrap(ipPacket);
        
        // ===== IP 헤더 생성 (20바이트) =====
        
        // Version (4비트) + IHL (4비트) = 1바이트
        buffer.put((byte) ((IPV4_VERSION << 4) | IP_HEADER_LENGTH_UNITS));
        
        // Type of Service (1바이트) - 우선순위 포함
        buffer.put((byte) currentTosValue);
        
        // Total Length (2바이트) - 전체 패킷 크기
        buffer.putShort((short) totalLength);
        
        // Identification (2바이트) - 패킷 ID
        buffer.putShort((short) (packetIdentification++ & 0xFFFF));
        
        // Flags (3비트) + Fragment Offset (13비트) = 2바이트
        buffer.putShort((short) 0);
        
        // Time to Live (1바이트)
        buffer.put((byte) DEFAULT_TTL);
        
        // Protocol (1바이트) - ChatApp(253) 또는 FileApp(254)
        buffer.put((byte) currentProtocol);
        
        // Header Checksum (2바이트) - 간단한 구현으로 0 사용
        buffer.putShort((short) 0);
        
        // Source IP Address (4바이트)
        buffer.put(sourceIpAddress);
        
        // Destination IP Address (4바이트)
        buffer.put(destinationIpAddress);
        
        // ===== 페이로드 복사 =====
        buffer.put(input, 0, length);
        
        System.out.println("[IP] 패킷 전송: " + formatIp(sourceIpAddress) + " -> " + formatIp(destinationIpAddress) + 
                         " (길이: " + totalLength + "바이트)");
        
        // EthernetLayer의 목적지 MAC을 설정
        if (lowerLayer instanceof EthernetLayer ethernetLayer) {
            ethernetLayer.setDstMac(destinationMac);
        }
        
        // 하위 계층(Ethernet)으로 전송
        return lowerLayer.Send(ipPacket, ipPacket.length);
    }
    
    /**
     * 하위 계층(Ethernet)으로부터 IP 패킷을 수신하여 처리
     * 
     * 처리 과정:
     * 1. IP 헤더 최소 크기 체크 (20바이트)
     * 2. IP 헤더 파싱 (버전, 프로토콜, 출발지/목적지 IP 등)
     * 3. 목적지 IP 필터링 (자신의 IP인 경우만 수락)
     * 4. IP 헤더 제거 후 페이로드 추출
     * 5. 상위 계층(ChatApp)으로 전달
     * 
     * @param input 수신한 IP 패킷
     * @return 처리 성공 여부
     */
    @Override
    public boolean Receive(byte[] input) {
        // 최소 IP 헤더 크기 체크
        if (input == null || input.length < 20) {
            return false;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(input);
        
        // Version + IHL 파싱
        int versionIhl = buffer.get() & 0xFF;
        int version = (versionIhl >> 4) & 0x0F;
        int ihl = versionIhl & 0x0F;
        
        // IPv4만 처리
        if (version != IPV4_VERSION) {
            return false;
        }
        
        // 헤더 길이 계산 (IHL * 4 바이트)
        int headerLength = ihl * 4;
        
        if (input.length < headerLength) {
            return false;
        }
        
        // TOS - 우선순위 추출
        int tos = buffer.get() & 0xFF;
        lastReceivedPriority = priorityFromTos(tos);
        
        // Total Length
        int totalLength = buffer.getShort() & 0xFFFF;
        
        // Identification
        buffer.getShort();
        
        // Flags + Fragment Offset
        buffer.getShort();
        
        // TTL
        buffer.get();
        
        // Protocol
        int protocol = buffer.get() & 0xFF;
        
        // Checksum
        buffer.getShort();
        
        // Source IP
        byte[] senderIp = new byte[4];
        buffer.get(senderIp);
        
        // Destination IP
        byte[] receivedDestIp = new byte[4];
        buffer.get(receivedDestIp);
        
        System.out.println("[IP] 패킷 수신: " + formatIp(senderIp) + " -> " + formatIp(receivedDestIp) +
                         " (프로토콜: " + protocol + ")");
        
        // 목적지 IP 필터링 - 자신의 IP인 경우만 수락
        if (!Arrays.equals(receivedDestIp, sourceIpAddress)) {
            System.out.println("[IP] 목적지 IP 불일치 - 패킷 드롭");
            return false;
        }
        
        // 페이로드 추출 (IP 헤더 제거)
        int payloadLength = totalLength - headerLength;
        if (payloadLength <= 0 || headerLength + payloadLength > input.length) {
            return false;
        }
        
        byte[] payload = Arrays.copyOfRange(input, headerLength, headerLength + payloadLength);
        
        // ===== IP 역다중화: Protocol 필드에 따라 상위 계층 선택 =====
        boolean delivered = false;
        for (BaseLayer upperLayer : upperLayers) {
            // ChatApp 프로토콜 (253)
            if (protocol == PROTOCOL_CHAT_APP && upperLayer instanceof ChatAppLayer) {
                System.out.println("[IP] ChatApp으로 전달 (" + payload.length + "바이트)");
                upperLayer.Receive(payload);
                delivered = true;
            }
            // FileApp 프로토콜 (254)
            else if (protocol == PROTOCOL_FILE_APP && upperLayer instanceof FileAppLayer) {
                System.out.println("[IP] FileApp으로 전달 (" + payload.length + "바이트)");
                upperLayer.Receive(payload);
                delivered = true;
            }
        }
        
        if (!delivered) {
            System.out.println("[IP] 경고: 처리할 상위 계층 없음 (프로토콜: " + protocol + ")");
        }
        
        return delivered;
    }
    
    /**
     * IP 주소를 문자열로 포맷팅 (예: "192.168.0.1")
     */
    private String formatIp(byte[] ip) {
        if (ip == null || ip.length < 4) {
            return "0.0.0.0";
        }
        return String.format("%d.%d.%d.%d",
            ip[0] & 0xFF, ip[1] & 0xFF, ip[2] & 0xFF, ip[3] & 0xFF);
    }
    
    /**
     * MAC 주소를 문자열로 포맷팅 (예: "AA:BB:CC:DD:EE:FF")
     */
    private String formatMac(byte[] mac) {
        if (mac == null || mac.length < 6) {
            return "00:00:00:00:00:00";
        }
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
            mac[0] & 0xFF, mac[1] & 0xFF, mac[2] & 0xFF,
            mac[3] & 0xFF, mac[4] & 0xFF, mac[5] & 0xFF);
    }
}
