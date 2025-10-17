package com.demo;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Arrays;

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

	static PcapIf selectedDev = null;
	static JTextArea ta;
	static byte[] macAddress = new byte[6];
	static byte[] dstMacAddress = new byte[6];

	// Layers
	static ChatAppLayer chatLayer;
	static EthernetLayer ethLayer;
	static PhysicalLayer phyLayer;

	public static void main(String[] args) {
		// 최신 jNetPcap API 사용: 장치 목록 조회
		List<PcapIf> allDevs;
		try {
			allDevs = Pcap.findAllDevs();
		} catch (PcapException e) {
			System.out.println("네트워크 장치를 찾을 수 없습니다. " + e.getMessage());
			return;
		}
		if (allDevs.isEmpty()) {
			System.out.println("네트워크 장치를 찾을 수 없습니다.");
			return;
		}
		System.out.println("네트워크 장비 탐색 성공!!");

		String[] devNames = new String[allDevs.size()];
		for (int i = 0; i < allDevs.size(); i++) {
			PcapIf device = allDevs.get(i);
			String desc = device.description().orElse("장비 설명 없음");
			devNames[i] = device.name() + " - " + desc;
		}

		JFrame frame = new JFrame();
		frame.setTitle("패킷 전송 예제");
		frame.setSize(800, 500);
		frame.setLayout(new FlowLayout());

		JLabel lb_Devices = new JLabel("디바이스 목록");
		final JComboBox<String> cb_Devices = new JComboBox<>(devNames);
		JLabel lb_DstMac = new JLabel("목적지 주소 (예: FF:FF:FF:FF:FF:FF)");
		JTextField tf_DstMac = new JTextField(20);
		JButton bt_Setting = new JButton("설정");
		JButton bt_Exit = new JButton("종료");

		JPanel panel1 = new JPanel();
		panel1.setLayout(new FlowLayout());
		panel1.add(lb_Devices);
		panel1.add(cb_Devices);
		panel1.add(lb_DstMac);
		panel1.add(tf_DstMac);
		panel1.add(bt_Setting);
		panel1.add(bt_Exit);

		JLabel lb_Message = new JLabel("메시지");
		JTextField tf_Message = new JTextField(20);
		JButton bt_Send = new JButton("전송");

		JPanel panel2 = new JPanel();
		panel2.setLayout(new FlowLayout());
		panel2.add(lb_Message);
		panel2.add(tf_Message);
		panel2.add(bt_Send);

	ta = new JTextArea(18, 80);

		JPanel panel3 = new JPanel();
		panel3.setLayout(new FlowLayout());
		panel3.add(new JScrollPane(ta));

		frame.add(panel1);
		frame.add(panel2);
		frame.add(panel3);
		frame.setVisible(true);

		cb_Devices.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int idx = cb_Devices.getSelectedIndex();
				if (idx < 0 || idx >= allDevs.size()) return;
				selectedDev = allDevs.get(idx);
				// 선택된 NIC의 MAC 주소를 java.net.NetworkInterface로 획득
				try {
					NetworkInterface nif = NetworkInterface.getByName(selectedDev.name());
					if (nif != null && nif.getHardwareAddress() != null) {
						byte[] hw = nif.getHardwareAddress();
						if (hw.length >= 6) {
							System.arraycopy(hw, 0, macAddress, 0, 6);
						}
					}
				} catch (SocketException ex) {
					// 무시하고 계속 진행 (송신에는 필수 아님)
				}
					System.out.printf("선택된 장치: %s\n", selectedDev.description().orElse(selectedDev.name()));
					// 디버깅 로그에 내 MAC 표시
					String myMacHex = String.format("%02X:%02X:%02X:%02X:%02X:%02X", macAddress[0], macAddress[1], macAddress[2], macAddress[3], macAddress[4], macAddress[5]);
					ta.append("내 MAC: " + myMacHex + "\n");
					boolean allZero = true; for (byte b: macAddress) { if (b != 0) { allZero = false; break; } }
					if (allZero) {
						ta.append("주의: 이 NIC의 MAC을 읽지 못했습니다. 브로드캐스트(FF:FF:FF:FF:FF:FF)로 테스트하거나 캡처 권한을 확인하세요.\n");
					}
			}
		});

		bt_Setting.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String dst = tf_DstMac.getText().trim();
				if (dst.isEmpty()) {
					// Default to broadcast if not provided
					Arrays.fill(dstMacAddress, (byte)0xFF);
					ta.append("목적지 MAC 미입력: 브로드캐스트로 설정합니다 (FF:FF:FF:FF:FF:FF)\n");
				} else {
					try {
						String[] dstBytes = dst.split(":");
						if (dstBytes.length != 6) throw new IllegalArgumentException("MAC 형식 오류");
						for (int i = 0; i < 6; i++) {
							String seg = dstBytes[i].trim();
							if (seg.length() == 1) seg = "0" + seg; // 한 자리 입력 보정
							dstMacAddress[i] = (byte) Integer.parseInt(seg, 16);
						}
					} catch (Exception ex) {
						ta.append("목적지 MAC 파싱 실패: " + ex.getMessage() + "\n");
						return;
					}
				}

				// Build layers (once)
				if (phyLayer != null) {
					phyLayer.close();
				}
				chatLayer = new ChatAppLayer(msg -> SwingUtilities.invokeLater(() -> {
					ta.append("[RCVD] " + msg + "\n");
					ta.setCaretPosition(ta.getDocument().getLength());
				}));
				ethLayer = new EthernetLayer();
				ethLayer.setSrcMac(Arrays.copyOf(macAddress, 6));
				ethLayer.setDstMac(Arrays.copyOf(dstMacAddress, 6));
				ethLayer.setEtherType(0xFFFF);
				phyLayer = new PhysicalLayer();

				// Bind layers: Chat <-> Eth <-> Phys
				chatLayer.SetUnderLayer(ethLayer);  ethLayer.SetUpperLayer(chatLayer);
				ethLayer.SetUnderLayer(phyLayer);   phyLayer.SetUpperLayer(ethLayer);

				boolean promiscuous = false; // capture only destined/broadcast frames to reduce noise
				long timeoutMillis = Duration.ofMillis(200).toMillis(); // shorter read timeout for snappier receive
				try {
					phyLayer.open(selectedDev, promiscuous, timeoutMillis);
					ta.append("장치 활성화 완료: " + selectedDev.name() + "\n");
					ta.append("수신 시작... (EtherType 0xFFFF)\n");
				} catch (PcapException ex) {
					ta.append("장치 활성화 실패: " + ex.getMessage() + "\n");
				}
			}
		});

		bt_Send.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String content = tf_Message.getText();
				if (chatLayer == null) {
					ta.append("먼저 '설정'으로 장치를 활성화하세요.\n");
					return;
				}
				ta.append("[SENT] " + content + "\n");
				// ensure Ethernet has up-to-date dst MAC in case user edits after setup
				ethLayer.setDstMac(Arrays.copyOf(dstMacAddress, 6));
				chatLayer.sendMessage(content);
			}
		});

		bt_Exit.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (phyLayer != null) phyLayer.close();
				System.exit(0);
			}
		});
	}
}