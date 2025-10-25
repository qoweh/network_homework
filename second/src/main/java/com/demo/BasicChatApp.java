package com.demo;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.jnetpcap.Pcap;
import org.jnetpcap.PcapException;
import org.jnetpcap.PcapIf;

/**
 * BasicChatApp - 패킷 기반 채팅 애플리케이션 메인 클래스
 * 
 * 프로그램 전체 구조:
 * 
 * ┌──────────────────────────────────────────────────────────────┐
 * │                      사용자 인터페이스 (Swing GUI)            │
 * │  - 장치 선택                                                  │
 * │  - MAC 주소 설정 (출발지/목적지)                              │
 * │  - 메시지 입력/전송                                           │
 * │  - 수신 메시지 표시                                           │
 * └──────────────────────┬───────────────────────────────────────┘
 *                        │
 *             ┌──────────▼──────────┐
 *             │   ChatAppLayer      │  응용 계층 (L7)
 *             │  UTF-8 인코딩/디코딩 │
 *             └──────────┬──────────┘
 *                        │
 *             ┌──────────▼──────────┐
 *             │  EthernetLayer      │  데이터링크 계층 (L2)
 *             │  - 프레임 생성/파싱  │
 *             │  - MAC 필터링        │
 *             └──────────┬──────────┘
 *                        │
 *             ┌──────────▼──────────┐
 *             │  PhysicalLayer      │  물리 계층 (L1)
 *             │  - jNetPcap 연동    │
 *             │  - 패킷 송수신       │
 *             └─────────────────────┘
 * 
 * 주요 기능:
 * 1. 네트워크 인터페이스 탐색 및 선택
 * 2. MAC 주소 자동 로드 및 목적지 주소 설정
 * 3. 계층 구조 초기화 및 연결
 * 4. 실시간 메시지 송수신
 * 5. 브로드캐스트 및 유니캐스트 지원
 * 
 * 프로토콜 스택:
 * - EtherType: 0xFFFF (사용자 정의 프로토콜)
 * - Non-promiscuous 모드 (자신 앞으로 온 패킷만 캡처)
 * - 200ms read timeout (낮은 지연 시간)
 * - 자기 수신 방지 (EthernetLayer에서 출발지 MAC 필터링)
 */
public class BasicChatApp {
	// ============= UI Components =============
	// Swing GUI 컴포넌트들
	private static JTextArea textArea;              // 메시지 표시 영역
	private static JTextField destinationMacField;  // 목적지 MAC 입력 필드
	private static JTextField messageField;         // 메시지 입력 필드
	private static JComboBox<String> deviceComboBox; // 네트워크 장치 선택 콤보박스

	// ============= State Variables =============
	// 애플리케이션 상태 변수
	private static PcapIf selectedDevice = null;           // 선택한 네트워크 인터페이스
	private static byte[] myMacAddress = new byte[6];      // 내 MAC 주소 (출발지)
	private static byte[] destinationMacAddress = new byte[6]; // 목적지 MAC 주소
	private static List<PcapIf> allDevices;                // 사용 가능한 모든 네트워크 장치

	// ============= Layer Objects =============
	// OSI 계층 구조 객체
	private static ChatAppLayer chatLayer;       // 응용 계층 (L7)
	private static EthernetLayer ethernetLayer;  // 데이터링크 계층 (L2)
	private static PhysicalLayer physicalLayer;  // 물리 계층 (L1)

	// ============= Constants =============
	// 프로토콜 및 성능 파라미터
	private static final int ETHER_TYPE = 0xFFFF;  // 사용자 정의 EtherType
	private static final long READ_TIMEOUT_MS = Duration.ofMillis(200).toMillis(); // 200ms 타임아웃
	private static final boolean PROMISCUOUS_MODE = false; // 비무차별 모드 (성능 최적화)

	/**
	 * 프로그램 시작점
	 * 
	 * 초기화 순서:
	 * 1. ASCII 아트 배너 출력
	 * 2. 네트워크 장치 탐색 (initializeDevices)
	 * 3. GUI 생성 (createAndShowGUI)
	 * 4. 사용자 입력 대기
	 */
	public static void main(String[] args) {
		printBanner();
		
		// 네트워크 장치 초기화
		if (!initializeDevices()) {
			return; // 장치가 없으면 프로그램 종료
		}
		
		// Swing GUI를 이벤트 디스패치 스레드에서 생성
		SwingUtilities.invokeLater(BasicChatApp::createAndShowGUI);
	}

	private static void printBanner() {
		System.out.println();
		System.out.println("  ,-.----.                                                                      ");
		System.out.println("  ,----..    ,---,                   ___                                            \\    /  \\                                                              ____   ");
		System.out.println(" /   /   \\ ,--.' |                 ,--.'|_    ,--,                                  |   :    \\                                                           ,'  , `. ");
		System.out.println("|   :     :|  |  :                 |  | :,' ,--.'|         ,---,                    |   |  .\\ :  __  ,-.   ,---.               __  ,-.                ,-+-,.' _ | ");
		System.out.println(".   |  ;. /:  :  :                 :  : ' : |  |,      ,-+-. /  |  ,----._,.        .   :  |: |,' ,'/ /|  '   ,'\\   ,----._,.,' ,'/ /|             ,-+-. ;   , || ");
		System.out.println(".   ; /--` :  |  |,--.  ,--.--.  .;__,'  /  `--'_     ,--.'|'   | /   /  ' /        |   |   \\ :'  | |' | /   /   | /   /  ' /'  | |' | ,--.--.    ,--.'|'   |  || ");
		System.out.println(";   | ;    |  :  '   | /       \\ |  |   |   ,' ,'|   |   |  ,\"' ||   :     |        |   : .   /|  |   ,'.   ; ,. :|   :     ||  |   ,'/       \\  |   |  ,', |  |, ");
		System.out.println("|   : |    |  |   /' :.--.  .-. |:__,'| :   '  | |   |   | /  | ||   | .\\  .        ;   | |`-' '  :  /  '   | |: :|   | .\\  .'  :  / .--.  .-. | |   | /  | |--'  ");
		System.out.println(".   | '___ '  :  | | | \\__\\/: . .  '  : |__ |  | :   |   | |  | |.   ; ';  |        |   | ;    |  | '   '   | .; :.   ; ';  ||  | '   \\__\\/: . . |   : |  | ,     ");
		System.out.println("'   ; : .'||  |  ' | : ,\" .--.; |  |  | '.'|'  : |__ |   | |  |/ '   .   . |        :   ' |    ;  : |   |   :    |'   .   . |;  : |   ,\" .--.; | |   : |  |/      ");
		System.out.println("'   | '/  :|  :  :_:,'/  /  ,.  |  ;  :    ;|  | '.'||   | |--'   `---`-'| |        :   : :    |  , ;    \\   \\  /  `---`-'| ||  , ;  /  /  ,.  | |   | |`-'       ");
		System.out.println("|   :    / |  | ,'   ;  :   .'   \\ |  ,   / ;  :    ;|   |/       .'__/\\_: |        |   | :     ---'      `----'   .'__/\\_: | ---'  ;  :   .'   \\|   ;/           ");
		System.out.println(" \\   \\ .'  `--''     |  ,     .-./  ---`-'  |  ,   / '---'        |   :    :        `---'.|                        |   :    :       |  ,     .-./'---'            ");
		System.out.println("  `---`               `--`---'               ---`-'                \\   \\  /           `---`                         \\   \\  /         `--`---'                     ");
		System.out.println("                                                                    `--`-'                                           `--`-'                                       ");
		System.out.println();
		System.out.println("╔═══════════════════════════════════════════════════════════════════════════════╗");
		System.out.println("║                  패킷 기반 채팅 프로그램 (Packet Chat v1.0)                   ║");
		System.out.println("║                                                                               ║");
		System.out.println("║  • EtherType: 0xFFFF (사용자 정의 프로토콜)                                   ║");
		System.out.println("║  • 계층 구조: ChatApp → Ethernet → Physical (jNetPcap)                        ║");
		System.out.println("║  • MAC 필터링: 자기 수신 방지 + 브로드캐스트/유니캐스트 지원                  ║");
		System.out.println("╚═══════════════════════════════════════════════════════════════════════════════╝");
		System.out.println();
	}

	/**
	 * 네트워크 장치 탐색 및 초기화
	 * 
	 * jNetPcap의 Pcap.findAllDevs()를 사용하여 시스템의 모든 
	 * 네트워크 인터페이스를 탐색합니다.
	 * 
	 * @return 장치 탐색 성공 여부
	 */
	private static boolean initializeDevices() {
		try {
			// 시스템의 모든 네트워크 인터페이스 탐색
			// 예: en0, eth0, wlan0, lo 등
			allDevices = Pcap.findAllDevs();
		} catch (PcapException e) {
			System.out.println("네트워크 장치를 찾을 수 없습니다. " + e.getMessage());
			return false;
		}
		
		if (allDevices.isEmpty()) {
			System.out.println("네트워크 장치를 찾을 수 없습니다.");
			return false;
		}
		
		System.out.println("네트워크 장비 탐색 성공!!");
		return true;
	}

	/**
	 * Swing GUI 생성 및 표시
	 * 
	 * GUI 구성:
	 * - 장치 선택 패널 (devicePanel)
	 * - 메시지 입력 패널 (messagePanel)
	 * - 메시지 표시 패널 (displayPanel)
	 */

	private static void createAndShowGUI() {
		JFrame frame = new JFrame("패킷 전송 예제");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(800, 500);
		frame.setLayout(new FlowLayout());

		// Create panels
		JPanel devicePanel = createDevicePanel();
		JPanel messagePanel = createMessagePanel();
		JPanel displayPanel = createDisplayPanel();

		frame.add(devicePanel);
		frame.add(messagePanel);
		frame.add(displayPanel);
		frame.setVisible(true);
	}

	private static JPanel createDevicePanel() {
		JPanel panel = new JPanel(new FlowLayout());
		
		JLabel deviceLabel = new JLabel("디바이스 목록");
		deviceComboBox = new JComboBox<>(getDeviceNames());
		deviceComboBox.addActionListener(e -> handleDeviceSelection());

		JLabel destinationLabel = new JLabel("목적지 주소 (예: FF:FF:FF:FF:FF:FF)");
		destinationMacField = new JTextField(20);

		JButton settingButton = new JButton("설정");
		settingButton.addActionListener(e -> handleSetup());

		JButton exitButton = new JButton("종료");
		exitButton.addActionListener(e -> handleExit());

		panel.add(deviceLabel);
		panel.add(deviceComboBox);
		panel.add(destinationLabel);
		panel.add(destinationMacField);
		panel.add(settingButton);
		panel.add(exitButton);

		return panel;
	}

	private static JPanel createMessagePanel() {
		JPanel panel = new JPanel(new FlowLayout());
		
		JLabel messageLabel = new JLabel("메시지");
		messageField = new JTextField(20);
		
		JButton sendButton = new JButton("전송");
		sendButton.addActionListener(e -> handleSendMessage());

		panel.add(messageLabel);
		panel.add(messageField);
		panel.add(sendButton);

		return panel;
	}

	private static JPanel createDisplayPanel() {
		JPanel panel = new JPanel(new FlowLayout());
		textArea = new JTextArea(18, 80);
		textArea.setEditable(false);
		panel.add(new JScrollPane(textArea));
		return panel;
	}

	private static String[] getDeviceNames() {
		String[] names = new String[allDevices.size()];
		for (int i = 0; i < allDevices.size(); i++) {
			PcapIf device = allDevices.get(i);
			String description = device.description().orElse("장비 설명 없음");
			names[i] = device.name() + " - " + description;
		}
		return names;
	}

	private static void handleDeviceSelection() {
		int index = deviceComboBox.getSelectedIndex();
		if (index < 0 || index >= allDevices.size()) {
			return;
		}

		selectedDevice = allDevices.get(index);
		loadMacAddress();
		displayMacAddress();
	}

	private static void loadMacAddress() {
		try {
			NetworkInterface networkInterface = NetworkInterface.getByName(selectedDevice.name());
			if (networkInterface != null && networkInterface.getHardwareAddress() != null) {
				byte[] hardwareAddress = networkInterface.getHardwareAddress();
				if (hardwareAddress.length >= 6) {
					System.arraycopy(hardwareAddress, 0, myMacAddress, 0, 6);
				}
			}
		} catch (SocketException ex) {
			// Continue with zeros if MAC cannot be read
		}
	}

	private static void displayMacAddress() {
		String deviceName = selectedDevice.description().orElse(selectedDevice.name());
		logToUI("선택된 장치: " + deviceName);
		
		String macHex = formatMacAddress(myMacAddress);
		logToUI("내 MAC: " + macHex);
		
		if (isMacAddressZero(myMacAddress)) {
			logToUI("주의: 이 NIC의 MAC을 읽지 못했습니다. 브로드캐스트(FF:FF:FF:FF:FF:FF)로 테스트하거나 캡처 권한을 확인하세요.");
		}
	}

	/**
	 * "설정" 버튼 클릭 이벤트 처리
	 * 
	 * 수행 작업:
	 * 1. 목적지 MAC 주소 파싱
	 * 2. 3계층 객체 생성 및 연결
	 * 3. PhysicalLayer 오픈 (패킷 캡처 시작)
	 */
	private static void handleSetup() {
		if (selectedDevice == null) {
			logToUI("먼저 디바이스를 선택하세요.");
			return;
		}

		try {
			parseDestinationMac();
			initializeLayers();
			openPhysicalLayer();
		} catch (Exception ex) {
			// Error already logged in parseDestinationMac
		}
	}

	/**
	 * 목적지 MAC 주소 파싱
	 * 
	 * 입력 형식: "AA:BB:CC:DD:EE:FF" (대소문자 무관)
	 * 
	 * 특수 처리:
	 * - 빈 문자열: 브로드캐스트(FF:FF:FF:FF:FF:FF)로 자동 설정
	 * - 한 자리 16진수: 앞에 0 패딩 (예: "A" → "0A")
	 * 
	 * @throws RuntimeException MAC 형식 오류 시
	 */
	private static void parseDestinationMac() {
		String destinationMacText = destinationMacField.getText().trim();
		
		// 빈 문자열: 브로드캐스트로 설정
		if (destinationMacText.isEmpty()) {
			Arrays.fill(destinationMacAddress, (byte) 0xFF);
			logToUI("목적지 MAC 미입력: 브로드캐스트로 설정합니다 (FF:FF:FF:FF:FF:FF)");
			return;
		}

		try {
			String[] macBytes = destinationMacText.split(":");
			if (macBytes.length != 6) {
				throw new IllegalArgumentException("MAC 주소는 6개의 바이트여야 합니다");
			}

			for (int i = 0; i < 6; i++) {
				String segment = macBytes[i].trim();
				if (segment.length() == 1) {
					segment = "0" + segment; // Pad single digit
				}
				// 16진수 문자열을 바이트로 변환
				destinationMacAddress[i] = (byte) Integer.parseInt(segment, 16);
			}
		} catch (Exception ex) {
			logToUI("목적지 MAC 파싱 실패: " + ex.getMessage());
			throw new RuntimeException("Invalid MAC address format");
		}
	}

	/**
	 * 계층 구조 초기화 및 연결
	 * 
	 * 계층 생성 순서:
	 * 1. ChatAppLayer: 메시지 콜백 설정 (수신 시 UI 업데이트)
	 * 2. EthernetLayer: 출발지/목적지 MAC, EtherType 설정
	 * 3. PhysicalLayer: 장치 연결 준비
	 * 
	 * 계층 연결 (양방향):
	 * ┌──────────────┐
	 * │  ChatApp     │ ← SetUnderLayer(Ethernet)
	 * └──────┬───────┘
	 *        ↕ SetUpperLayer
	 * ┌──────▼───────┐
	 * │  Ethernet    │ ← SetUnderLayer(Physical)
	 * └──────┬───────┘
	 *        ↕ SetUpperLayer
	 * ┌──────▼───────┐
	 * │  Physical    │
	 * └──────────────┘
	 * 
	 * 데이터 흐름:
	 * - 송신: Chat → Ethernet → Physical → NIC
	 * - 수신: NIC → Physical → Ethernet → Chat
	 */
	private static void initializeLayers() {
		// Close existing physical layer if any
		// 기존 PhysicalLayer가 있으면 종료
		if (physicalLayer != null) {
			physicalLayer.close();
		}

		// Create layers
		// 1. ChatAppLayer 생성
		// 수신 콜백: 메시지를 UI 스레드에서 표시
		chatLayer = new ChatAppLayer(message -> 
			SwingUtilities.invokeLater(() -> {
				logToUI("[RCVD] " + message);
				textArea.setCaretPosition(textArea.getDocument().getLength());
			})
		);

		// 2. EthernetLayer 생성 및 설정
		ethernetLayer = new EthernetLayer();
		ethernetLayer.setSrcMac(Arrays.copyOf(myMacAddress, 6));
		ethernetLayer.setDstMac(Arrays.copyOf(destinationMacAddress, 6));
		ethernetLayer.setEtherType(ETHER_TYPE);

		// 3. PhysicalLayer 생성
		physicalLayer = new PhysicalLayer();

		// Wire layers together
		// 4. 계층 연결 (상위 ↔ 하위)
		chatLayer.SetUnderLayer(ethernetLayer);
		ethernetLayer.SetUpperLayer(chatLayer);
		ethernetLayer.SetUnderLayer(physicalLayer);
		physicalLayer.SetUpperLayer(ethernetLayer);
	}

	private static void openPhysicalLayer() {
		try {
			physicalLayer.open(selectedDevice, PROMISCUOUS_MODE, READ_TIMEOUT_MS);
			logToUI("장치 활성화 완료: " + selectedDevice.name());
			logToUI("수신 시작... (EtherType 0x" + Integer.toHexString(ETHER_TYPE).toUpperCase() + ")");
		} catch (PcapException ex) {
			logToUI("장치 활성화 실패: " + ex.getMessage());
		}
	}

	/**
	 * "전송" 버튼 클릭 이벤트 처리
	 * 
	 * 전송 과정:
	 * 1. 메시지 필드에서 텍스트 읽기
	 * 2. ChatAppLayer.sendMessage() 호출
	 *    → UTF-8 인코딩
	 *    → EthernetLayer에서 프레임 생성
	 *    → PhysicalLayer에서 NIC로 전송
	 * 3. 전송 메시지 UI에 표시
	 * 4. 입력 필드 초기화
	 */
	private static void handleSendMessage() {
		String content = messageField.getText();
		
		// 계층이 초기화되지 않았으면 경고
		if (chatLayer == null) {
			logToUI("먼저 '설정'으로 장치를 활성화하세요.");
			return;
		}

		if (content.isEmpty()) {
			return;
		}

		logToUI("[SENT] " + content);
		
		// Update destination MAC in case user changed it after setup
		// 목적지 MAC 업데이트 (사용자가 설정 후 변경했을 수 있음)
		ethernetLayer.setDstMac(Arrays.copyOf(destinationMacAddress, 6));
		chatLayer.sendMessage(content);
		
		messageField.setText("");
	}

	/**
	 * "종료" 버튼 클릭 이벤트 처리
	 * 
	 * 종료 과정:
	 * 1. PhysicalLayer 닫기 (Pcap 세션 종료, 스레드 정리)
	 * 2. 프로그램 종료
	 */
	private static void handleExit() {
		if (physicalLayer != null) {
			physicalLayer.close();
		}
		System.exit(0);
	}

	private static void logToUI(String message) {
		if (textArea != null) {
			textArea.append(message + "\n");
		}
	}

	private static String formatMacAddress(byte[] mac) {
		return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
			mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
	}

	private static boolean isMacAddressZero(byte[] mac) {
		for (byte b : mac) {
			if (b != 0) {
				return false;
			}
		}
		return true;
	}
}