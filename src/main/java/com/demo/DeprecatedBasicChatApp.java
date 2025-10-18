package com.demo;

import java.awt.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;

import javax.swing.*;

import org.jnetpcap.Pcap;
import org.jnetpcap.PcapException;
import org.jnetpcap.PcapIf;

/**
 * jnetpcap 라이브러리 버전 차이로 인해 사용하지 않는 ChatApp --> 현재 사용 : BasicChatApp.java
 */
public class DeprecatedBasicChatApp {
	static String selectedDes = null;
	static PcapIf selectedDev = null;
	static int selectedIndex = 0;
	static Pcap pcap;
	static JTextArea ta;
	static byte[] macAddress = new byte[6];
	static byte[] dstMacAddress = new byte[6];
	static Thread obj;
	
	@SuppressWarnings("deprecation")
	public static void main(String[] args) {
		ArrayList<PcapIf> allDevs = new ArrayList<PcapIf>();
		StringBuilder errbuf = new StringBuilder();
		int r = Pcap.findAllDevs(allDevs, errbuf); 
		if (r == -1 || allDevs.isEmpty()) { 
			System.out.println("네트워크 장치를 찾을 수 없습니다." + errbuf.toString()); 
			return; } 
		System.out.println("네트워크 장비 탐색 성공!!");
		String[] devNames = new String[allDevs.size()];
		int i = 0;
		for (PcapIf device : allDevs) {
			devNames[i] = device.getDescription();
			i++;
		}
		
		JFrame frame = new JFrame();
		frame.setTitle("패킷 전송 예제");
		frame.setSize(800,500);
		frame.setLayout(new FlowLayout());
		
		JLabel lb_Devices = new JLabel("디바이스 목록");
		final JComboBox<String> cb_Devices = new JComboBox<String>(devNames);
		JLabel lb_DstMac = new JLabel("목적지 주소");
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
				try {
					selectedDes = cb_Devices.getSelectedItem().toString();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				
				for (PcapIf device : allDevs) {
					if (device.getDescription() == selectedDes) {
						selectedIndex = allDevs.indexOf(device);
						break;
					}
				}
				
				selectedDev = allDevs.get(selectedIndex);
				try {
					macAddress = selectedDev.getHardwareAddress();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				System.out.printf("선택된 장치: %s\n",
						(selectedDev.getDescription() != null) ? selectedDev.getDescription() : selectedDev.getName());
			}
		});
		
	    bt_Setting.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				
				String dst = tf_DstMac.getText();
				String[] dstBytes = dst.split(":");
				for (int i=0; i<6; i++)
					dstMacAddress[i] = (byte) Integer.parseInt(dstBytes[i],16); 
				
				if (pcap != null)
					pcap.close();
				
	    		int snaplen = 64 * 1024;
	    		int flags = Pcap.MODE_NON_PROMISCUOUS;
	    		int timeout = 10 * 1000;
	    		pcap = Pcap.openLive(selectedDev.getName(), snaplen, flags, timeout, errbuf);
	    		Receive_Thread thread = new Receive_Thread(pcap, ta, macAddress);
	    		obj = new Thread(thread);
	    		obj.start();
			}
	    });
	    
		bt_Send.addActionListener(new ActionListener() {
	    	public void actionPerformed(ActionEvent e) {
	    		
	    		String content = tf_Message.getText();
	    		int len = content.getBytes().length;
	    		
	    		byte[] data = null;
	    		if (len < 46) {
	    			data = new byte[60];
	    		}else {
	    			data = new byte[14+len];
	    		}
	    		
	    		System.arraycopy(dstMacAddress, 0, data, 0, 6);
	    		System.arraycopy(macAddress, 0, data, 6, 6);
	    		
	    		data[12] = (byte) 0xff;
	    		data[13] = (byte) 0xff;
	    		
	    		System.arraycopy(content.getBytes(), 0, data, 14, len);
	    		ta.append("[SENT] "+content+"\n");
	    		if (len<46)
	    			for (int i=len+14; i<60; i++)
	    				data[i] = (byte) 0x00;
	    		
	    		ByteBuffer buffer = ByteBuffer.wrap(data);
	    		if (pcap.sendPacket(buffer) != Pcap.OK)
	    			System.out.println(pcap.getErr());
	    	}
	    });
	    
	    bt_Exit.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (obj != null) {
					obj.interrupt();
				}
				System.exit(0);
			}
	    	
	    });
	}
}

class Receive_Thread implements Runnable {
	byte[] data;
	Pcap AdapterObject;
	JTextArea textArea;
	byte[] mac;

	public Receive_Thread(Pcap m_AdapterObject, JTextArea ta1, byte[] macAddr) {
		AdapterObject = m_AdapterObject;
		textArea = ta1;
		mac = macAddr;
	}

	@Override
	public void run() {
		try {
			while (true) {
				PcapPacketHandler<String> jpacketHandler = new PcapPacketHandler<String>() {
					public void nextPacket(PcapPacket packet, String user) {
						data = packet.getByteArray(0, packet.size());
						
						if (IsMyPacket(data)) {
							byte[] msg = new byte[data.length-14];
							System.arraycopy(data, 14, msg, 0, data.length-14);
							textArea.append("[RCVD] "+new String(msg)+"\n");
							textArea.setVisible(true);
						}
					}
				};

				AdapterObject.loop(10000, jpacketHandler, "");
				Thread.sleep(1);
			}
		}catch(InterruptedException e) {
			if (AdapterObject != null) {
				AdapterObject.close();
			}			
		}
		
	}
	
	public boolean IsMyPacket(byte[] input) {
		for (int i=0; i<6; i++) {
			
			if (mac[i] == input[i]) {
				continue;
			}
			else
				return false;
		}
		return true;
	}
}