package com.demo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ARPLayer - ARP(Address Resolution Protocol) 계층
 * 
 * 역할:
 * - IP 주소를 MAC 주소로 변환하는 프로토콜 구현
 * - ARP 캐시 테이블 관리 (IP-MAC 매핑 저장)
 * - ARP Request/Reply 메시지 송수신
 * - Proxy ARP: 다른 호스트 대신 ARP 응답
 * - Gratuitous ARP: 자신의 IP를 네트워크에 알림
 * 
 * ARP 패킷 구조 (28바이트):
 * ┌──────────────┬──────────────┬───────────┬──────────────┬───────────────┬──────────────┬───────────────┬──────────────┐
 * │ Hardware Type│ Protocol Type│ HW Len    │ Protocol Len │ Operation     │ Sender HW    │ Sender Proto  │ Target HW    │
 * │  (2바이트)   │  (2바이트)   │ (1바이트) │  (1바이트)   │  (2바이트)    │  (6바이트)   │  (4바이트)    │  (6바이트)   │
 * └──────────────┴──────────────┴───────────┴──────────────┴───────────────┴──────────────┴───────────────┴──────────────┘
 *     0x0001          0x0800         6            4          1=REQ/2=REPLY   Sender MAC     Sender IP      Target MAC
 *    (Ethernet)       (IPv4)                                                                              (Target Proto 4바이트 추가)
 * 
 * ARP 동작 과정:
 * 1. ARP Request: "192.168.0.5의 MAC 주소를 아는 사람?" (브로드캐스트)
 * 2. ARP Reply: "192.168.0.5는 AA:BB:CC:DD:EE:FF입니다" (유니캐스트)
 * 3. ARP 캐시에 저장하여 재사용
 */
public class ARPLayer implements BaseLayer {
    private final String name = "ARP";
    private BaseLayer underLayer; // 하위 계층: EthernetLayer
    private final List<BaseLayer> uppers = new ArrayList<>(); // 상위 계층: IPLayer
    
    // ARP 캐시 테이블 (IP 주소 → MAC 주소 매핑)
    // ConcurrentHashMap 사용으로 멀티스레드 안전성 보장
    private final Map<String, byte[]> arpCache = new ConcurrentHashMap<>();
    
    // 자신의 네트워크 정보
    private byte[] myMac = new byte[6];    // 자신의 MAC 주소
    private byte[] myIp = new byte[4];     // 자신의 IP 주소
    
    // Proxy ARP 설정 (다른 호스트 대신 ARP 응답)
    private boolean proxyArpEnabled = false;
    private final Map<String, byte[]> proxyTable = new ConcurrentHashMap<>();
    
    // ARP 프로토콜 상수
    private static final int HARDWARE_TYPE_ETHERNET = 0x0001;
    private static final int PROTOCOL_TYPE_IP = 0x0800;
    private static final int HARDWARE_LEN = 6;
    private static final int PROTOCOL_LEN = 4;
    private static final int OPERATION_REQUEST = 1;
    private static final int OPERATION_REPLY = 2;
    
    /**
     * 자신의 MAC 주소 설정
     * @param mac 6바이트 MAC 주소
     */
    public void setMyMac(byte[] mac) {
        if (mac != null && mac.length >= 6) {
            System.arraycopy(mac, 0, myMac, 0, 6);
        }
    }
    
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
     * Proxy ARP 활성화/비활성화
     * @param enabled true면 Proxy ARP 동작
     */
    public void setProxyArpEnabled(boolean enabled) {
        this.proxyArpEnabled = enabled;
    }
    
    /**
     * Proxy ARP 테이블에 엔트리 추가
     * @param ip IP 주소 문자열 (예: "192.168.0.100")
     * @param mac 6바이트 MAC 주소
     */
    public void addProxyArpEntry(String ip, byte[] mac) {
        if (mac != null && mac.length >= 6) {
            byte[] macCopy = Arrays.copyOf(mac, 6);
            proxyTable.put(ip, macCopy);
        }
    }
    
    /**
     * ARP 캐시 테이블 조회
     * @param ip IP 주소 문자열 (예: "192.168.0.100")
     * @return MAC 주소 (6바이트) 또는 null (캐시에 없음)
     */
    public byte[] lookupArpCache(String ip) {
        byte[] mac = arpCache.get(ip);
        return (mac != null) ? Arrays.copyOf(mac, 6) : null;
    }
    
    /**
     * ARP 캐시 테이블에 엔트리 추가
     * @param ip IP 주소 문자열
     * @param mac 6바이트 MAC 주소
     */
    public void addArpCacheEntry(String ip, byte[] mac) {
        if (mac != null && mac.length >= 6) {
            byte[] macCopy = Arrays.copyOf(mac, 6);
            arpCache.put(ip, macCopy);
            System.out.println("[ARP 캐시] 추가: " + ip + " -> " + formatMac(macCopy));
        }
    }
    
    /**
     * ARP 캐시 테이블 전체 반환
     * @return IP-MAC 매핑 맵
     */
    public Map<String, byte[]> getArpCache() {
        return new ConcurrentHashMap<>(arpCache);
    }
    
    /**
     * ARP 캐시 초기화
     */
    public void clearArpCache() {
        arpCache.clear();
        System.out.println("[ARP 캐시] 초기화됨");
    }
    
    /**
     * ARP Request 전송 - IP 주소에 대한 MAC 주소 요청
     * @param targetIp 찾고자 하는 IP 주소 (4바이트)
     * @return 전송 성공 여부
     */
    public boolean sendArpRequest(byte[] targetIp) {
        if (underLayer == null || targetIp == null || targetIp.length < 4) {
            return false;
        }
        
        // ARP Request 패킷 생성 (28바이트)
        byte[] arpPacket = new byte[28];
        ByteBuffer buffer = ByteBuffer.wrap(arpPacket);
        
        // Hardware Type (2바이트) = 0x0001 (Ethernet)
        buffer.putShort((short) HARDWARE_TYPE_ETHERNET);
        
        // Protocol Type (2바이트) = 0x0800 (IPv4)
        buffer.putShort((short) PROTOCOL_TYPE_IP);
        
        // Hardware Address Length (1바이트) = 6
        buffer.put((byte) HARDWARE_LEN);
        
        // Protocol Address Length (1바이트) = 4
        buffer.put((byte) PROTOCOL_LEN);
        
        // Operation (2바이트) = 1 (Request)
        buffer.putShort((short) OPERATION_REQUEST);
        
        // Sender Hardware Address (6바이트) = 자신의 MAC
        buffer.put(myMac);
        
        // Sender Protocol Address (4바이트) = 자신의 IP
        buffer.put(myIp);
        
        // Target Hardware Address (6바이트) = 00:00:00:00:00:00 (모름)
        buffer.put(new byte[6]);
        
        // Target Protocol Address (4바이트) = 찾고자 하는 IP
        buffer.put(targetIp, 0, 4);
        
        System.out.println("[ARP] Request 전송: Who has " + formatIp(targetIp) + "? Tell " + formatIp(myIp));
        
        // 하위 계층(Ethernet)으로 전송
        return underLayer.Send(arpPacket, arpPacket.length);
    }
    
    /**
     * ARP Reply 전송 - ARP Request에 대한 응답
     * @param targetMac 응답을 받을 MAC 주소 (6바이트)
     * @param targetIp 응답을 받을 IP 주소 (4바이트)
     * @return 전송 성공 여부
     */
    public boolean sendArpReply(byte[] targetMac, byte[] targetIp) {
        if (underLayer == null || targetMac == null || targetIp == null) {
            return false;
        }
        
        // ARP Reply 패킷 생성 (28바이트)
        byte[] arpPacket = new byte[28];
        ByteBuffer buffer = ByteBuffer.wrap(arpPacket);
        
        // Hardware Type (2바이트) = 0x0001 (Ethernet)
        buffer.putShort((short) HARDWARE_TYPE_ETHERNET);
        
        // Protocol Type (2바이트) = 0x0800 (IPv4)
        buffer.putShort((short) PROTOCOL_TYPE_IP);
        
        // Hardware Address Length (1바이트) = 6
        buffer.put((byte) HARDWARE_LEN);
        
        // Protocol Address Length (1바이트) = 4
        buffer.put((byte) PROTOCOL_LEN);
        
        // Operation (2바이트) = 2 (Reply)
        buffer.putShort((short) OPERATION_REPLY);
        
        // Sender Hardware Address (6바이트) = 자신의 MAC
        buffer.put(myMac);
        
        // Sender Protocol Address (4바이트) = 자신의 IP
        buffer.put(myIp);
        
        // Target Hardware Address (6바이트) = 요청자의 MAC
        buffer.put(targetMac, 0, 6);
        
        // Target Protocol Address (4바이트) = 요청자의 IP
        buffer.put(targetIp, 0, 4);
        
        System.out.println("[ARP] Reply 전송: " + formatIp(myIp) + " is at " + formatMac(myMac));
        
        // 하위 계층(Ethernet)으로 전송
        return underLayer.Send(arpPacket, arpPacket.length);
    }
    
    /**
     * Gratuitous ARP 전송 - 자신의 IP를 네트워크에 알림
     * 용도:
     * 1. IP 주소 충돌 감지
     * 2. 다른 호스트의 ARP 캐시 업데이트
     * 3. 네트워크 진입 알림
     * 
     * @return 전송 성공 여부
     */
    public boolean sendGratuitousArp() {
        if (underLayer == null) {
            return false;
        }
        
        // Gratuitous ARP는 자신의 IP를 대상으로 ARP Request 전송
        byte[] arpPacket = new byte[28];
        ByteBuffer buffer = ByteBuffer.wrap(arpPacket);
        
        buffer.putShort((short) HARDWARE_TYPE_ETHERNET);
        buffer.putShort((short) PROTOCOL_TYPE_IP);
        buffer.put((byte) HARDWARE_LEN);
        buffer.put((byte) PROTOCOL_LEN);
        buffer.putShort((short) OPERATION_REQUEST);
        
        // Sender = 자신
        buffer.put(myMac);
        buffer.put(myIp);
        
        // Target = 자신 (Gratuitous ARP의 특징)
        buffer.put(new byte[6]); // Target MAC = 00:00:00:00:00:00
        buffer.put(myIp); // Target IP = 자신의 IP
        
        System.out.println("[ARP] Gratuitous ARP 전송: " + formatIp(myIp) + " is at " + formatMac(myMac));
        
        return underLayer.Send(arpPacket, arpPacket.length);
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
     * ARP 패킷 수신 처리
     * 
     * 처리 과정:
     * 1. ARP 패킷 파싱 (Operation, Sender/Target 정보 추출)
     * 2. Sender 정보를 ARP 캐시에 저장
     * 3. ARP Request인 경우:
     *    - 자신의 IP가 Target이면 ARP Reply 전송
     *    - Proxy ARP 활성화 시 대신 응답
     * 4. ARP Reply인 경우:
     *    - 상위 계층(IP)으로 전달
     * 
     * @param input ARP 패킷 (최소 28바이트)
     * @return 처리 성공 여부
     */
    @Override
    public boolean Receive(byte[] input) {
        // 최소 ARP 패킷 크기 체크
        if (input == null || input.length < 28) {
            return false;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(input);
        
        // ARP 헤더 파싱
        int hardwareType = buffer.getShort() & 0xFFFF;
        int protocolType = buffer.getShort() & 0xFFFF;
        int hwLen = buffer.get() & 0xFF;
        int protoLen = buffer.get() & 0xFF;
        int operation = buffer.getShort() & 0xFFFF;
        
        // Ethernet/IPv4 ARP만 처리
        if (hardwareType != HARDWARE_TYPE_ETHERNET || protocolType != PROTOCOL_TYPE_IP) {
            return false;
        }
        
        // Sender 정보 추출
        byte[] senderMac = new byte[6];
        buffer.get(senderMac);
        
        byte[] senderIp = new byte[4];
        buffer.get(senderIp);
        
        // Target 정보 추출
        byte[] targetMac = new byte[6];
        buffer.get(targetMac);
        
        byte[] targetIp = new byte[4];
        buffer.get(targetIp);
        
        // Sender 정보를 ARP 캐시에 저장 (학습)
        String senderIpStr = formatIp(senderIp);
        addArpCacheEntry(senderIpStr, senderMac);
        
        // ARP Request 처리
        if (operation == OPERATION_REQUEST) {
            System.out.println("[ARP] Request 수신: Who has " + formatIp(targetIp) + "? Tell " + formatIp(senderIp));
            
            // 자신의 IP가 Target인 경우 응답
            if (Arrays.equals(targetIp, myIp)) {
                System.out.println("[ARP] 자신의 IP에 대한 요청 - Reply 전송");
                return sendArpReply(senderMac, senderIp);
            }
            
            // Proxy ARP 처리 - 다른 호스트 대신 응답
            if (proxyArpEnabled) {
                String targetIpStr = formatIp(targetIp);
                byte[] proxyMac = proxyTable.get(targetIpStr);
                
                if (proxyMac != null) {
                    System.out.println("[ARP] Proxy ARP - " + targetIpStr + " 대신 응답");
                    // Proxy ARP Reply 전송 (자신의 MAC으로 응답)
                    byte[] tempMyMac = myMac.clone();
                    myMac = proxyMac; // 임시로 Proxy MAC 사용
                    boolean result = sendArpReply(senderMac, senderIp);
                    myMac = tempMyMac; // 원래 MAC 복원
                    return result;
                }
            }
        }
        // ARP Reply 처리
        else if (operation == OPERATION_REPLY) {
            System.out.println("[ARP] Reply 수신: " + formatIp(senderIp) + " is at " + formatMac(senderMac));
            
            // 상위 계층(IP)으로 전달하여 대기 중인 패킷 전송 가능하게 함
            for (BaseLayer upper : uppers) {
                upper.Receive(input);
            }
        }
        
        return true;
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
