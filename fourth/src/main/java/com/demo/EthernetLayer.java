package com.demo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * EthernetLayer - 이더넷 데이터링크 계층 (OSI 2계층에 해당)
 * 
 * 역할:
 * - Ethernet 프레임 생성: 헤더(목적지/출발지 MAC, EtherType) 추가 및 최소 크기 패딩
 * - Ethernet 프레임 파싱: 헤더 제거 및 페이로드 추출
 * - 이더넷 역다중화: EtherType에 따라 상위 계층 선택 (IP 또는 ARP)
 * - MAC 주소 기반 필터링:
 *   1. EtherType 필터 (IP: 0x0800, ARP: 0x0806 지원)
 *   2. 자기 수신 방지 (출발지 MAC이 자신과 같으면 드롭)
 *   3. 목적지 필터 (자신 또는 브로드캐스트 주소만 수락)
 * 
 * Ethernet 프레임 구조 (IEEE 802.3):
 * ┌──────────────┬──────────────┬──────────┬─────────────┬──────┬─────┐
 * │ 목적지 MAC   │ 출발지 MAC   │ EtherType│   페이로드  │ 패딩 │ FCS │
 * │   (6바이트)  │   (6바이트)  │(2바이트) │ (가변 길이) │(가변)│(4)  │
 * └──────────────┴──────────────┴──────────┴─────────────┴──────┴─────┘
 *                                   ↑
 *                      0x0800 = IPv4, 0x0806 = ARP
 * 
 * 프레임 최소 크기: 64바이트 (헤더 14 + 페이로드 46 + FCS 4)
 * 이 코드에서는 FCS를 제외한 60바이트를 소프트웨어에서 생성하고,
 * FCS는 네트워크 카드(NIC)가 자동으로 추가합니다.
 */
public class EthernetLayer implements BaseLayer {
    private final String name = "Ethernet";
    private BaseLayer underLayer; // 하위 계층: PhysicalLayer
    private final List<BaseLayer> uppers = new ArrayList<>(); // 상위 계층: IPLayer, ARPLayer

    private byte[] srcMac = new byte[6];  // 출발지 MAC 주소 (이 컴퓨터의 NIC MAC)
    private byte[] dstMac = new byte[6];  // 목적지 MAC 주소 (상대방 NIC MAC 또는 브로드캐스트)
    private int etherType = 0x0800;       // EtherType 필드 (기본값: 0x0800 = IPv4)
    
    // EtherType 상수
    private static final int ETHERTYPE_IP = 0x0800;   // IPv4
    private static final int ETHERTYPE_ARP = 0x0806;  // ARP

    /**
     * 출발지 MAC 주소를 설정합니다.
     * @param mac 6바이트 MAC 주소
     */
    public void setSrcMac(byte[] mac) { 
        if (mac != null && mac.length >= 6) System.arraycopy(mac,0,srcMac,0,6); 
    }

    /**
     * 목적지 MAC 주소를 설정합니다.
     * @param mac 6바이트 MAC 주소 (브로드캐스트는 FF:FF:FF:FF:FF:FF)
     */
    public void setDstMac(byte[] mac) { 
        if (mac != null && mac.length >= 6) System.arraycopy(mac,0,dstMac,0,6); 
    }

    /**
     * EtherType을 설정합니다.
     * 표준 값: 0x0800=IPv4, 0x0806=ARP, 0x86DD=IPv6
     * @param et EtherType 값 (2바이트)
     */
    public void setEtherType(int et) { 
        this.etherType = et & 0xFFFF; 
    }
    
    /**
     * 출발지 MAC 주소를 반환합니다.
     * @return 6바이트 MAC 주소
     */
    public byte[] getSrcMac() {
        return Arrays.copyOf(srcMac, 6);
    }
    
    /**
     * 목적지 MAC 주소를 반환합니다.
     * @return 6바이트 MAC 주소
     */
    public byte[] getDstMac() {
        return Arrays.copyOf(dstMac, 6);
    }

    @Override
    public String GetLayerName() { return name; }

    @Override
    public BaseLayer GetUnderLayer() { return underLayer; }

    @Override
    public BaseLayer GetUpperLayer(int index) { 
        return (index>=0 && index<uppers.size()) ? uppers.get(index) : null; 
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
     * 상위 계층(ChatApp)으로부터 받은 데이터를 Ethernet 프레임으로 캡슐화하여 전송합니다.
     * 
     * 캡슐화 과정:
     * 1. 프레임 크기 계산: 최소 60바이트 (14바이트 헤더 + 46바이트 페이로드/패딩)
     * 2. Ethernet 헤더 생성:
     *    - 바이트 0-5: 목적지 MAC 주소
     *    - 바이트 6-11: 출발지 MAC 주소
     *    - 바이트 12-13: EtherType (0xFFFF)
     * 3. 페이로드 복사 (바이트 14부터)
     * 4. 필요시 0x00으로 패딩하여 최소 크기 맞춤
     * 5. 하위 계층(Physical)으로 전달
     * 
     * @param input 상위 계층의 페이로드 (예: UTF-8 인코딩된 메시지)
     * @param length 페이로드 길이
     * @return 전송 성공 여부
     */
    @Override
    public boolean Send(byte[] input, int length) {
        if (underLayer == null) return false;
        
        // Ethernet 프레임 최소 크기 60바이트 (FCS 제외)
        int minLen = 60;
        int frameLen = Math.max(minLen, 14 + length);
        byte[] frame = new byte[frameLen];
        
        // Ethernet 헤더 구성
        System.arraycopy(dstMac, 0, frame, 0, 6);    // 목적지 MAC (6바이트)
        System.arraycopy(srcMac, 0, frame, 6, 6);    // 출발지 MAC (6바이트)
        
        // EtherType (2바이트, 빅 엔디안)
        frame[12] = (byte) ((etherType >> 8) & 0xFF);  // 상위 바이트
        frame[13] = (byte) (etherType & 0xFF);         // 하위 바이트
        
        // 페이로드 복사 // ex) System.arraycopy(src, srcPos, dest, destPos, length);
        System.arraycopy(input, 0, frame, 14, length);
        
        // 나머지는 자동으로 0x00으로 초기화되어 패딩 역할
        
        // 하위 계층(Physical)으로 전송
        return underLayer.Send(frame, frame.length);
    }

    /**
     * 하위 계층(Physical)으로부터 Ethernet 프레임을 수신하여 처리합니다.
     * 
     * 필터링 과정 (순서대로 적용):
     * 1. 최소 헤더 크기 체크 (14바이트)
     * 2. 자기 수신 방지: 출발지 MAC이 자신과 같으면 드롭
     * 3. 목적지 필터: 목적지가 자신 또는 브로드캐스트인 경우만 수락
     * 4. EtherType 파싱
     * 5. 이더넷 역다중화: EtherType에 따라 적절한 상위 계층으로 전달
     *    - 0x0800 (IPv4) → IPLayer
     *    - 0x0806 (ARP) → ARPLayer
     * 
     * @param input 수신한 Ethernet 프레임 전체
     * @return 처리 성공 여부
     */
    @Override
    public boolean Receive(byte[] input) {
        // 1. 최소 헤더 크기 체크
        if (input.length < 14) return false;
        
        // 2. MAC 주소 분석
        
        // 브로드캐스트 체크 (목적지가 FF:FF:FF:FF:FF:FF인지)
        boolean isBroadcast = true;
        for (int i = 0; i < 6; i++) {
            if ((input[i] & 0xFF) != 0xFF) { 
                isBroadcast = false; 
                break; 
            }
        }
        
        // 목적지가 나인지 체크 (목적지 MAC == 내 MAC)
        boolean dstIsMe = true;
        for (int i = 0; i < 6; i++) {
            if (input[i] != srcMac[i]) {
                dstIsMe = false; 
                break; 
            }
        }
        
        // 출발지가 나인지 체크 (출발지 MAC == 내 MAC)
        boolean srcIsMe = true;
        for (int i = 0; i < 6; i++) {
            if (input[6 + i] != srcMac[i]) {
                srcIsMe = false; 
                break; 
            }
        }

        // 3. 자기 수신 방지: 내가 보낸 프레임은 드롭
        //    (같은 NIC에서 송신 → 캡처되는 현상 방지)
        if (srcIsMe) {
            // System.out.println("[Ethernet] 자기 수신 - 드롭");
            return false;
        }
        
        // 4. 목적지 필터: 나에게 온 것이거나 브로드캐스트만 수락
        if (!(dstIsMe || isBroadcast)) {
            // 디버깅: 필터링된 패킷 정보 출력
            String dstMacStr = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                input[0] & 0xFF, input[1] & 0xFF, input[2] & 0xFF,
                input[3] & 0xFF, input[4] & 0xFF, input[5] & 0xFF);
            String srcMacStr = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                input[6] & 0xFF, input[7] & 0xFF, input[8] & 0xFF,
                input[9] & 0xFF, input[10] & 0xFF, input[11] & 0xFF);
            String myMacStr = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                srcMac[0] & 0xFF, srcMac[1] & 0xFF, srcMac[2] & 0xFF,
                srcMac[3] & 0xFF, srcMac[4] & 0xFF, srcMac[5] & 0xFF);
            System.out.println("[Ethernet] 목적지 필터 - 드롭: " + srcMacStr + " -> " + dstMacStr + " (내 MAC: " + myMacStr + ")");
            return false;
        }

        // 5. EtherType 파싱 (빅 엔디안 → 정수 변환)
        int receivedEtherType = ((input[12] & 0xFF) << 8) | (input[13] & 0xFF);
        
        // 6. 필터 통과 → 헤더 제거 후 페이로드 추출
        byte[] payload = Arrays.copyOfRange(input, 14, input.length);
        
        // 7. 이더넷 역다중화: EtherType에 따라 상위 계층 선택
        boolean delivered = false;
        for (BaseLayer upper : uppers) {
            // IPLayer는 0x0800만 처리
            if (receivedEtherType == ETHERTYPE_IP && upper instanceof IPLayer) {
                upper.Receive(payload);
                delivered = true;
            }
            // ARPLayer는 0x0806만 처리
            else if (receivedEtherType == ETHERTYPE_ARP && upper instanceof ARPLayer) {
                upper.Receive(payload);
                delivered = true;
            }
            // 기타 상위 계층 (ChatAppLayer 등 - 하위 호환성)
            else if (!(upper instanceof IPLayer) && !(upper instanceof ARPLayer)) {
                upper.Receive(payload);
                delivered = true;
            }
        }
        
        if (!delivered) {
            System.out.println("[Ethernet] 처리되지 않은 EtherType: 0x" + 
                             String.format("%04X", receivedEtherType));
        }
        
        return delivered;
    }
}
