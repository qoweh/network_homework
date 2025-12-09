package com.demo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jnetpcap.Pcap;
import org.jnetpcap.PcapException;
import org.jnetpcap.PcapIf;
import org.jnetpcap.constant.PcapDirection;

import java.nio.ByteBuffer;

/**
 * PhysicalLayer - 물리 계층 (OSI 1계층에 해당)
 * 
 * 역할:
 * - 네트워크 카드(NIC)와 직접 통신하여 실제 패킷 송수신
 * - jNetPcap 라이브러리를 사용하여 링크 계층(L2) 레벨에서 패킷 캡처 및 전송
 * - 백그라운드 스레드(Runnable)에서 지속적으로 패킷을 수신하여 상위 계층으로 전달
 * 
 * jNetPcap 핵심 개념:
 * - PcapIf: 네트워크 인터페이스(장치) 표현 객체
 * - Pcap: 패킷 캡처 세션 (장치 열기, 읽기, 쓰기, 필터링)
 * - PcapHandler: 패킷 수신 시 호출되는 콜백 함수 인터페이스
 * - dispatch(): 캡처된 패킷을 handler로 전달 (반복 호출 필요)
 * 
 * 성능 최적화 설정:
 * - Non-promiscuous 모드: 자신에게 온 패킷만 캡처 (무차별 모드 비활성화)
 * - 200ms timeout: 패킷이 없어도 200ms마다 dispatch가 리턴 (낮은 지연 시간)
 * - dispatch(1): 한 번에 1개 패킷만 처리 (실시간성 향상)
 * - snaplen=2048: 패킷당 최대 캡처 크기 2KB (이더넷 MTU 1500 충분히 커버)
 * 
 * 스레드 모델:
 * - Runnable 구현으로 백그라운드 수신 전담
 * - 메인 스레드: Send 호출
 * - 수신 스레드: run() 메서드에서 dispatch 루프 실행
 */
public class PhysicalLayer implements BaseLayer, Runnable {
    private final String name = "Physical";
    private BaseLayer underLayer; // 사용하지 않음 (최하위 계층)
    private final List<BaseLayer> uppers = new ArrayList<>(); // EthernetLayer

    private volatile Pcap pcap; // 패킷 캡처 세션 (스레드 간 공유, volatile 필요)
    private volatile Thread rxThread; // 백그라운드 수신 스레드

    /**
     * 네트워크 장치를 열고 패킷 캡처 세션을 시작합니다.
     * 
     * 파라미터 설명:
     * @param device 사용할 네트워크 인터페이스 (예: en0, eth0)
     * @param promiscuous 무차별 모드 여부
     *                    - true: 모든 패킷 캡처 (네트워크 분석용, CPU 부하 ↑)
     *                    - false: 자신 앞으로 온 패킷만 (일반 통신용, 성능 ↑)
     * @param timeoutMillis 패킷 읽기 타임아웃 (밀리초)
     *                      - 200ms 권장: 낮은 지연 시간 유지
     *                      - 너무 짧으면: CPU 사용률 증가
     *                      - 너무 길면: UI 반응 느려짐
     * 
     * Pcap 오픈 과정:
     * 1. 기존 세션이 있으면 종료 (close 호출)
     * 2. Pcap.openLive()로 새 세션 열기
     *    - snaplen=2048: 이더넷 프레임 전체 캡처 가능
     * 3. (선택적) PcapDirection 설정 (일부 버전에서 미지원)
     * 4. 백그라운드 수신 스레드 시작 (run 메서드 실행)
     * 
     * @return 성공 여부
     * @throws PcapException 장치 열기 실패 시
     */
    public boolean open(PcapIf device, boolean promiscuous, long timeoutMillis) throws PcapException {
        close(); // 기존 세션 정리
        
        int snaplen = 2048; // 충분한 크기, 복사 오버헤드 최소화
        this.pcap = Pcap.openLive(device, snaplen, promiscuous, timeoutMillis, TimeUnit.MILLISECONDS);
        
        // 일부 플랫폼이나 wrapper 버전에서는 PcapDirection.INOUT을 지원하지 않음
        // 방향 설정은 이 프로그램에서 선택적이므로 에러 무시
        try {
            // IN/OUT 패킷 모두 캡처 (가능한 경우)
            // this.pcap.setDirection(PcapDirection.INOUT);
        } catch (Throwable ignore) {}
        
        // 백그라운드 수신 스레드 시작
        rxThread = new Thread(this, "phys-rx");
        rxThread.setDaemon(true); // 메인 종료 시 자동 종료
        rxThread.start();
        return true;
    }

    /**
     * 패킷 캡처 세션을 닫고 수신 스레드를 종료합니다.
     * 
     * 종료 과정:
     * 1. 수신 스레드 인터럽트 (while 루프 탈출)
     * 2. Pcap 세션 close (네트워크 리소스 해제)
     * 3. 참조 변수 null 처리 (가비지 컬렉션 대상)
     */
    public void close() {
        // 수신 스레드 종료
        if (rxThread != null) {
            try { 
                rxThread.interrupt(); // Thread.currentThread().isInterrupted() → true
            } catch (Exception ignore) {}
            rxThread = null;
        }
        
        // Pcap 세션 닫기
        if (pcap != null) {
            try { 
                pcap.close(); 
            } catch (Exception ignore) {}
            pcap = null;
        }
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
     * 상위 계층(Ethernet)으로부터 받은 프레임을 네트워크로 전송합니다.
     * 
     * 전송 과정:
     * 1. Ethernet 프레임을 ByteBuffer로 래핑
     * 2. pcap.sendPacket(): 링크 계층으로 직접 주입 (소켓 바이패스)
     * 3. NIC가 FCS(프레임 체크 시퀀스) 자동 추가하여 물리적 전송
     * 
     * @param input 전송할 이더넷 프레임 전체
     * @param length 프레임 길이
     * @return 전송 성공 여부
     */
    @Override
    public boolean Send(byte[] input, int length) {
        if (pcap == null) return false;
        
        try {
            // ByteBuffer로 래핑하여 전송
            // wrap(array, offset, length): 복사 없이 배열 참조
            pcap.sendPacket(ByteBuffer.wrap(input, 0, length));
            return true;
        } catch (PcapException e) {
            return false; // 전송 실패 (NIC 오류, 세션 닫힘 등)
        }
    }

    /**
     * 이 계층은 최하위 계층이므로 Receive는 외부에서 호출되지 않습니다.
     * 패킷 수신은 백그라운드 스레드의 run() 메서드에서 처리됩니다.
     */
    @Override
    public boolean Receive() { return false; }

    /**
     * 이 계층은 최하위 계층이므로 Receive는 외부에서 호출되지 않습니다.
     * 패킷 수신은 백그라운드 스레드의 run() 메서드에서 처리됩니다.
     */
    @Override
    public boolean Receive(byte[] input) { return false; }

    /**
     * 백그라운드 수신 스레드의 메인 루프 (Runnable 인터페이스 구현)
     * 
     * 동작 원리:
     * 1. PcapHandler.OfArray 콜백 함수 정의
     *    - 파라미터 순서 중요: (U user, PcapHeader header, byte[] packet)
     *    - jNetPcap wrapper 2.3.1 JDK21 버전의 정확한 시그니처
     * 2. 무한 루프에서 pcap.dispatch(1, handler, this) 반복 호출
     *    - 1: 최대 1개 패킷만 처리 후 즉시 리턴 (낮은 지연 시간)
     *    - handler: 패킷 수신 시 호출될 콜백
     *    - this: 콜백에 전달될 user 객체 (PhysicalLayer 자신)
     * 3. 패킷이 캡처되면 handler 실행
     *    - 방어적 복사: native 버퍼 재사용 대비
     *    - 상위 계층(Ethernet)으로 전달
     * 4. Thread.interrupt() 호출 시 루프 탈출 (close 메서드에서 호출)
     * 
     * dispatch(1)의 효과:
     * - 패킷 있으면: handler 1번 호출 후 즉시 리턴 → 다음 루프
     * - 패킷 없으면: timeout(200ms) 후 리턴 → 다음 루프
     * - 결과: 최소 지연으로 패킷 처리, UI 블로킹 방지
     */
    @Override
    public void run() {
        if (pcap == null) return;

        // PcapHandler.OfArray 함수형 인터페이스
        // 중요: 파라미터 순서가 jNetPcap 버전마다 다를 수 있음
        // 이 버전(2.3.1 JDK21)의 정확한 시그니처:
        // (U user, PcapHeader hdr, byte[] pkt)
        org.jnetpcap.PcapHandler.OfArray<PhysicalLayer> handler = (
            PhysicalLayer self,           // dispatch의 3번째 인자 (this)
            org.jnetpcap.PcapHeader hdr,  // 패킷 메타데이터 (타임스탬프, 길이 등)
            byte[] pkt                     // 실제 패킷 데이터 (이더넷 프레임)
        ) -> {
            // 방어적 복사: native 버퍼가 재사용될 수 있으므로 복사 필요
            byte[] data = java.util.Arrays.copyOf(pkt, pkt.length);
            
            // 디버깅: 패킷 수신 로그 (MAC 주소와 길이)
            if (data.length >= 14) {
                String srcMac = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                    data[6] & 0xFF, data[7] & 0xFF, data[8] & 0xFF,
                    data[9] & 0xFF, data[10] & 0xFF, data[11] & 0xFF);
                String dstMac = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                    data[0] & 0xFF, data[1] & 0xFF, data[2] & 0xFF,
                    data[3] & 0xFF, data[4] & 0xFF, data[5] & 0xFF);
                System.out.println("[Physical] 프레임 수신: " + srcMac + " -> " + dstMac + " (" + data.length + " bytes)");
            }
            
            // 상위 계층(Ethernet)으로 전달
            // EthernetLayer에서 EtherType/MAC 필터링 수행
            for (BaseLayer upper : uppers) {
                upper.Receive(data);
            }
        };

        // 수신 루프: interrupt 될 때까지 반복
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // 한 번에 1개 패킷만 처리하여 지연 시간 최소화
                // timeout(200ms)에 의존하여 블로킹 방지
                pcap.dispatch(1, handler, this);
            } catch (PcapException ex) {
                break; // Pcap 에러 발생 시 루프 종료
            }
        }
    }
}
