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

public class BasicChatApp {
	// UI Components
	private static JTextArea textArea;
	private static JTextField destinationMacField;
	private static JTextField messageField;
	private static JComboBox<String> deviceComboBox;

	// State
	private static PcapIf selectedDevice = null;
	private static byte[] myMacAddress = new byte[6];
	private static byte[] destinationMacAddress = new byte[6];
	private static List<PcapIf> allDevices;

	// Layers
	private static ChatAppLayer chatLayer;
	private static EthernetLayer ethernetLayer;
	private static PhysicalLayer physicalLayer;

	// Constants
	private static final int ETHER_TYPE = 0xFFFF;
	private static final long READ_TIMEOUT_MS = Duration.ofMillis(200).toMillis();
	private static final boolean PROMISCUOUS_MODE = false;

	public static void main(String[] args) {
		if (!initializeDevices()) {
			return;
		}
		
		SwingUtilities.invokeLater(BasicChatApp::createAndShowGUI);
	}
	private static boolean initializeDevices() {
		try {
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

	private static void parseDestinationMac() {
		String destinationMacText = destinationMacField.getText().trim();
		
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
				destinationMacAddress[i] = (byte) Integer.parseInt(segment, 16);
			}
		} catch (Exception ex) {
			logToUI("목적지 MAC 파싱 실패: " + ex.getMessage());
			throw new RuntimeException("Invalid MAC address format");
		}
	}

	private static void initializeLayers() {
		// Close existing physical layer if any
		if (physicalLayer != null) {
			physicalLayer.close();
		}

		// Create layers
		chatLayer = new ChatAppLayer(message -> 
			SwingUtilities.invokeLater(() -> {
				logToUI("[RCVD] " + message);
				textArea.setCaretPosition(textArea.getDocument().getLength());
			})
		);

		ethernetLayer = new EthernetLayer();
		ethernetLayer.setSrcMac(Arrays.copyOf(myMacAddress, 6));
		ethernetLayer.setDstMac(Arrays.copyOf(destinationMacAddress, 6));
		ethernetLayer.setEtherType(ETHER_TYPE);

		physicalLayer = new PhysicalLayer();

		// Wire layers together
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

	private static void handleSendMessage() {
		String content = messageField.getText();
		
		if (chatLayer == null) {
			logToUI("먼저 '설정'으로 장치를 활성화하세요.");
			return;
		}

		if (content.isEmpty()) {
			return;
		}

		logToUI("[SENT] " + content);
		
		// Update destination MAC in case user changed it after setup
		ethernetLayer.setDstMac(Arrays.copyOf(destinationMacAddress, 6));
		chatLayer.sendMessage(content);
		
		messageField.setText("");
	}

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