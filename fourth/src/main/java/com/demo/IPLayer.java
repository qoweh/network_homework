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
 * 이 구현에서는 간단한 IP 헤더만 생성 (Version 4, IHL 5, 옵션 없음)
 */
public class IPLayer implements BaseLayer {
    private final String name = "IP";
    private BaseLayer underLayer; // 하위 계층: EthernetLayer (데이터 전송용)
    private ARPLayer arpLayer;    // ARP 계층: IP→MAC 주소 변환용
    private final List<BaseLayer> uppers = new ArrayList<>(); // 상위 계층: ChatAppLayer
    
    // 자신의 IP 주소
    private byte[] myIp = new byte[4];
    
    // 목적지 IP 주소
    private byte[] dstIp = new byte[4];
    
    // IP 헤더 필드
    private static final int IP_VERSION = 4;           // IPv4
    private static final int IP_HEADER_LENGTH = 5;     // 5 * 4 = 20바이트 (옵션 없음)
    private static final int IP_TOS = 0;               // Type of Service
    private static final int IP_TTL = 128;             // Time to Live
    @SuppressWarnings("unused")
    private static final int IP_PROTOCOL_TCP = 6;      // TCP 프로토콜 (향후 확장용)
    @SuppressWarnings("unused")
    private static final int IP_PROTOCOL_UDP = 17;     // UDP 프로토콜 (향후 확장용)
    private static final int IP_PROTOCOL_CHAT = 253;   // ChatApp 프로토콜
    private static final int IP_PROTOCOL_FILE = 254;   // FileApp 프로토콜
    
    private int ipIdentification = 0; // IP 패킷 ID (증가)
    private int currentProtocol = IP_PROTOCOL_CHAT; // 현재 사용할 프로토콜 (기본값: ChatApp)
    
    /**
     * 자신의 IP 주소 설정
     * @param ip 4바이트 IP 주소
     */
    public void setMyIp(byte[] ip) {
        if (ip != null && ip.length >= 4) {
            System.arraycopy(ip, 0, myIp, 0, 4);
        }
    }
    
    /**
     * 목적지 IP 주소 설정
     * @param ip 4바이트 IP 주소
     */
    public void setDstIp(byte[] ip) {
        if (ip != null && ip.length >= 4) {
            System.arraycopy(ip, 0, dstIp, 0, 4);
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
        return Arrays.copyOf(myIp, 4);
    }
    
    /**
     * 목적지 IP 주소 반환
     * @return 4바이트 IP 주소
     */
    public byte[] getDstIp() {
        return Arrays.copyOf(dstIp, 4);
    }
    
    /**
     * 현재 사용할 프로토콜 설정 (ChatApp 또는 FileApp)
     * @param protocol IP_PROTOCOL_CHAT(253) 또는 IP_PROTOCOL_FILE(254)
     */
    public void setProtocol(int protocol) {
        this.currentProtocol = protocol;
    }
    
    /**
     * ChatApp 프로토콜로 설정
     */
    public void useChatProtocol() {
        this.currentProtocol = IP_PROTOCOL_CHAT;
    }
    
    /**
     * FileApp 프로토콜로 설정
     */
    public void useFileProtocol() {
        this.currentProtocol = IP_PROTOCOL_FILE;
    }
    
    @Override
    public String GetLayerName() {
        return name;
    }
    
    @Override
    public BaseLayer GetUnderLayer() {
        return underLayer;
    }
    
    @Override
    public BaseLayer GetUpperLayer(int index) {
        return (index >= 0 && index < uppers.size()) ? uppers.get(index) : null;
    }
    
    @Override
    public void SetUnderLayer(BaseLayer pUnderLayer) {
        this.underLayer = pUnderLayer;
    }
    
    @Override
    public void SetUpperLayer(BaseLayer pUpperLayer) {
        if (!uppers.contains(pUpperLayer)) {
            uppers.add(pUpperLayer);
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
        if (underLayer == null || arpLayer == null) {
            System.out.println("[IP] 하위 계층 또는 ARP 계층이 설정되지 않음");
            return false;
        }
        
        // 목적지 IP에 대한 MAC 주소 조회
        String dstIpStr = formatIp(dstIp);
        byte[] dstMac = arpLayer.lookupArpCache(dstIpStr);
        
        // ARP 캐시에 없으면 ARP Request 전송
        if (dstMac == null) {
            System.out.println("[IP] ARP 캐시에 " + dstIpStr + " 없음 - ARP Request 전송");
            arpLayer.sendArpRequest(dstIp);
            
            // 실제로는 ARP Reply를 기다려야 하지만, 간단한 구현으로 실패 반환
            // 개선: ARP Reply 수신 후 재전송 큐에 추가
            return false;
        }
        
        System.out.println("[IP] 목적지 MAC 주소 발견: " + formatMac(dstMac));
        
        // IP 패킷 생성: IP 헤더(20바이트) + 페이로드
        int totalLength = 20 + length;
        byte[] ipPacket = new byte[totalLength];
        ByteBuffer buffer = ByteBuffer.wrap(ipPacket);
        
        // ===== IP 헤더 생성 (20바이트) =====
        
        // Version (4비트) + IHL (4비트) = 1바이트
        // Version=4, IHL=5 → 0x45
        buffer.put((byte) ((IP_VERSION << 4) | IP_HEADER_LENGTH));
        
        // Type of Service (1바이트)
        buffer.put((byte) IP_TOS);
        
        // Total Length (2바이트) - 전체 패킷 크기
        buffer.putShort((short) totalLength);
        
        // Identification (2바이트) - 패킷 ID
        buffer.putShort((short) (ipIdentification++ & 0xFFFF));
        
        // Flags (3비트) + Fragment Offset (13비트) = 2바이트
        // Flags=0 (Don't Fragment 비활성), Offset=0
        buffer.putShort((short) 0);
        
        // Time to Live (1바이트)
        buffer.put((byte) IP_TTL);
        
        // Protocol (1바이트) - ChatApp(253) 또는 FileApp(254)
        buffer.put((byte) currentProtocol);
        
        // Header Checksum (2바이트) - 간단한 구현으로 0 사용
        // 실제로는 IP 헤더의 체크섬 계산 필요
        buffer.putShort((short) 0);
        
        // Source IP Address (4바이트)
        buffer.put(myIp);
        
        // Destination IP Address (4바이트)
        buffer.put(dstIp);
        
        // ===== 페이로드 복사 =====
        buffer.put(input, 0, length);
        
        System.out.println("[IP] 패킷 전송: " + formatIp(myIp) + " -> " + formatIp(dstIp) + 
                         " (길이: " + totalLength + "바이트)");
        
        // EthernetLayer의 목적지 MAC을 설정해야 함
        // EthernetLayer를 직접 호출하여 MAC 설정
        if (underLayer instanceof EthernetLayer) {
            ((EthernetLayer) underLayer).setDstMac(dstMac);
        }
        
        // 하위 계층(Ethernet)으로 전송
        return underLayer.Send(ipPacket, ipPacket.length);
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
        if (version != IP_VERSION) {
            return false;
        }
        
        // 헤더 길이 계산 (IHL * 4 바이트)
        int headerLength = ihl * 4;
        
        if (input.length < headerLength) {
            return false;
        }
        
        // TOS
        buffer.get();
        
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
        byte[] srcIp = new byte[4];
        buffer.get(srcIp);
        
        // Destination IP
        byte[] dstIpReceived = new byte[4];
        buffer.get(dstIpReceived);
        
        System.out.println("[IP] 패킷 수신: " + formatIp(srcIp) + " -> " + formatIp(dstIpReceived) +
                         " (프로토콜: " + protocol + ")");
        
        // 목적지 IP 필터링 - 자신의 IP인 경우만 수락
        if (!Arrays.equals(dstIpReceived, myIp)) {
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
        for (BaseLayer upper : uppers) {
            // ChatApp 프로토콜 (253)
            if (protocol == IP_PROTOCOL_CHAT && upper instanceof ChatAppLayer) {
                System.out.println("[IP] ChatApp으로 전달 (" + payload.length + "바이트)");
                upper.Receive(payload);
                delivered = true;
            }
            // FileApp 프로토콜 (254)
            else if (protocol == IP_PROTOCOL_FILE && upper instanceof FileAppLayer) {
                System.out.println("[IP] FileApp으로 전달 (" + payload.length + "바이트)");
                upper.Receive(payload);
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
