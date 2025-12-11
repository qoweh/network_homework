package com.demo;

import java.awt.*;
import java.awt.event.*;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import org.jnetpcap.Pcap;
import org.jnetpcap.PcapException;
import org.jnetpcap.PcapIf;

/**
 * NetworkChatApp - 네트워크 프로토콜 스택 기반 채팅 애플리케이션
 * 
 * 프로그램 전체 구조:
 * 
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    NetworkChatApp.java (GUI)                    │
 * │                    사용자 인터페이스 (Swing)                    │
 * │   - 네트워크 장치 선택    - IP/MAC 주소 설정                    │
 * │   - 메시지 입력/전송      - 파일 전송                           │
 * │   - 암호화 On/Off         - 우선순위 선택                       │
 * │   - ARP 캐시 테이블       - 지연시간 표시                       │
 * └───────────────────────────┬─────────────────────────────────────┘
 *                             │
 *             ┌───────────────┴───────────────┐
 *             ▼                               ▼
 * ┌───────────────────────┐       ┌───────────────────────┐
 * │   ChatAppLayer.java   │       │   FileAppLayer.java   │
 * │   채팅 응용 계층 (L7)  │       │   파일 응용 계층 (L7)  │
 * │                       │       │                       │
 * │ • UTF-8 인코딩/디코딩  │       │ • 파일 분할/재조립     │
 * │ • Fragmentation       │       │ • 진행률 콜백         │
 * │ • XOR 암호화          │       │ • Thread 기반 전송    │
 * │ • 우선순위 큐         │       │                       │
 * │ • 타임스탬프 로깅     │       │                       │
 * └───────────┬───────────┘       └───────────┬───────────┘
 *             │                               │
 *             └───────────────┬───────────────┘
 *                             │ Protocol 번호로 구분
 *                             │ (253=Chat, 254=File)
 *                             ▼
 *             ┌───────────────────────────────┐
 *             │        IPLayer.java           │
 *             │     네트워크 계층 (L3)         │
 *             │                               │
 *             │ • IP 헤더 생성 (20바이트)      │
 *             │ • TOS 필드로 우선순위 전달     │
 *             │ • 프로토콜 역다중화            │
 *             │ • ARP 연동 (IP→MAC 변환)      │
 *             └───────────────┬───────────────┘
 *                             │
 *             ┌───────────────┴───────────────┐
 *             │                               │
 *             ▼                               ▼
 * ┌───────────────────────┐       ┌───────────────────────┐
 * │  EthernetLayer.java   │       │    ARPLayer.java      │
 * │  이더넷 계층 (L2)      │       │    ARP 계층 (L2)       │
 * │                       │       │                       │
 * │ • 이더넷 프레임 생성   │       │ • ARP Request/Reply   │
 * │ • MAC 주소 필터링      │       │ • ARP 캐시 관리       │
 * │ • EtherType 역다중화   │       │ • Proxy ARP           │
 * │   (0x0800=IP,         │       │ • Gratuitous ARP      │
 * │    0x0806=ARP)        │       │                       │
 * └───────────┬───────────┘       └───────────┬───────────┘
 *             │                               │
 *             └───────────────┬───────────────┘
 *                             ▼
 *             ┌───────────────────────────────┐
 *             │      PhysicalLayer.java       │
 *             │       물리 계층 (L1)           │
 *             │                               │
 *             │ • jNetPcap 연동               │
 *             │ • NIC에서 직접 패킷 송수신     │
 *             │ • 백그라운드 수신 스레드       │
 *             └───────────────────────────────┘
 *                             │
 *                             ▼
 *                     [ 네트워크 카드 (NIC) ]
 * 
 * 주요 기능:
 * 1. ARP Request/Reply 처리
 * 2. ARP 캐시 테이블 관리 및 표시
 * 3. Gratuitous ARP 전송
 * 4. Proxy ARP 기능
 * 5. IP 기반 메시지 송수신
 * 6. XOR 암호화 통신
 * 7. 우선순위 큐 (긴급 메시지 우선 처리)
 * 8. 타임스탬프 & 지연시간 측정
 * 9. 파일 전송 (분할/재조립)
 */
public class NetworkChatApp {
    // ============= UI Components =============
    private static JTextArea textArea;
    private static JTextField myIpField;
    private static JTextField myMacField;
    private static JTextField dstIpField;
    private static JTextField messageField;
    private static JComboBox<String> deviceComboBox;
    private static JTable arpTable;
    private static DefaultTableModel arpTableModel;
    private static JCheckBox proxyArpCheckbox;
    private static JTextField proxyIpField;
    private static JTextField proxyMacField;
    private static JTextField filePathField;
    private static JProgressBar fileProgressBar;
    private static JLabel fileStatusLabel;
    
    // ===== 새로운 기능: 암호화 & 우선순위 =====
    private static JCheckBox encryptCheckbox;
    private static JComboBox<String> priorityComboBox;
    private static JLabel latencyLabel;
    
    // ============= State Variables =============
    private static PcapIf selectedDevice = null;
    private static byte[] myMacAddress = new byte[6];
    private static byte[] myIpAddress = new byte[4];
    private static byte[] dstIpAddress = new byte[4];
    private static List<PcapIf> allDevices;
    
    // ============= Layer Objects =============
    private static ChatAppLayer chatLayer;
    private static FileAppLayer fileLayer;
    private static IPLayer ipLayer;
    private static ARPLayer arpLayer;
    private static EthernetLayer ethernetLayer;
    private static PhysicalLayer physicalLayer;
    
    // ============= Constants =============
    private static final long READ_TIMEOUT_MS = Duration.ofMillis(200).toMillis();
    private static final boolean PROMISCUOUS_MODE = false;
    
    /**
     * 프로그램 시작점
     */
    public static void main(String[] args) {
        // Look and Feel 설정 (크로스 플랫폼)
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            
            // 기본 색상 설정
            UIManager.put("Panel.background", Color.WHITE);
            UIManager.put("TextArea.background", Color.WHITE);
            UIManager.put("TextArea.foreground", Color.BLACK);
            UIManager.put("TextField.background", Color.WHITE);
            UIManager.put("TextField.foreground", Color.BLACK);
            UIManager.put("Button.background", new Color(240, 240, 240));
            UIManager.put("Button.foreground", Color.BLACK);
            UIManager.put("Label.foreground", Color.BLACK);
            UIManager.put("Table.background", Color.WHITE);
            UIManager.put("Table.foreground", Color.BLACK);
            UIManager.put("ComboBox.background", Color.WHITE);
            UIManager.put("ComboBox.foreground", Color.BLACK);
        } catch (Exception e) {
            System.err.println("Look and Feel 설정 실패: " + e.getMessage());
        }
        
        printBanner();
        
        if (!initializeDevices()) {
            System.err.println("네트워크 장치 초기화 실패");
            return;
        }
        
        SwingUtilities.invokeLater(NetworkChatApp::createAndShowGUI);
    }
    
    private static void printBanner() {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║              네트워크 프로토콜 스택 채팅 프로그램 (Network Chat v2.0)         ║");
        System.out.println("║                                                                               ║");
        System.out.println("║  • 계층 구조: ChatApp → IP → Ethernet/ARP → Physical                          ║");
        System.out.println("║  • ARP 기능: Request, Reply, Gratuitous ARP, Proxy ARP                       ║");
        System.out.println("║  • IP 통신: IPv4 기반 메시지 송수신                                           ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }
    
    /**
     * 네트워크 장치 탐색 및 초기화
     */
    private static boolean initializeDevices() {
        try {
            allDevices = Pcap.findAllDevs();
            if (allDevices == null || allDevices.isEmpty()) {
                System.err.println("네트워크 장치를 찾을 수 없습니다.");
                return false;
            }
            
            System.out.println("사용 가능한 네트워크 장치:");
            for (int i = 0; i < allDevices.size(); i++) {
                PcapIf device = allDevices.get(i);
                System.out.println((i + 1) + ". " + device.name() + 
                                 " - " + (device.description() != null ? device.description() : "설명 없음"));
            }
            
            System.out.println();
            System.out.println("┌─────────────────────────────────────────────────────────────┐");
            System.out.println("│ VM과 Mac 간 연결을 위한 장치 선택 가이드:                  │");
            System.out.println("│                                                             │");
            System.out.println("│ [VM (Ubuntu)]                                               │");
            System.out.println("│   → enp0s1: VM의 기본 네트워크 어댑터 (권장)               │");
            System.out.println("│                                                             │");
            System.out.println("│ [Mac (호스트)]                                              │");
            System.out.println("│   → vmenet0 또는 bridge100: VM 브리지 네트워크 (권장)       │");
            System.out.println("│   → en0: Wi-Fi (같은 물리 네트워크 사용 시)                │");
            System.out.println("│                                                             │");
            System.out.println("│ 양쪽 장치가 같은 IP 대역(예: 192.168.64.x)에 있어야 합니다. │");
            System.out.println("└─────────────────────────────────────────────────────────────┘");
            System.out.println();
            
            return true;
        } catch (PcapException e) {
            System.err.println("Pcap 초기화 오류: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * GUI 생성 및 표시
     */
    private static void createAndShowGUI() {
        JFrame frame = new JFrame("ARP 채팅 프로그램");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(10, 10));
        
        // 프레임 배경색 설정
        frame.getContentPane().setBackground(Color.WHITE);
        
        // 상단: 장치 및 네트워크 설정
        frame.add(createConfigPanel(), BorderLayout.NORTH);
        
        // 중앙: 채팅 표시 및 ARP 테이블
        frame.add(createCenterPanel(), BorderLayout.CENTER);
        
        // 하단: 메시지 입력 및 ARP 기능
        frame.add(createBottomPanel(), BorderLayout.SOUTH);
        
        frame.setSize(900, 700);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
    
    /**
     * 설정 패널 생성 (장치 선택, IP 설정)
     */
    private static JPanel createConfigPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createTitledBorder("네트워크 설정"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // 장치 선택
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel deviceLabel = new JLabel("네트워크 장치:");
        deviceLabel.setForeground(Color.BLACK);
        panel.add(deviceLabel, gbc);
        
        gbc.gridx = 1; gbc.gridy = 0; gbc.gridwidth = 2;
        deviceComboBox = new JComboBox<>(getDeviceNames());
        deviceComboBox.setBackground(Color.WHITE);
        deviceComboBox.setForeground(Color.BLACK);
        deviceComboBox.addActionListener(e -> handleDeviceSelection());
        panel.add(deviceComboBox, gbc);
        
        // 내 MAC 주소
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        JLabel myMacLabel = new JLabel("내 MAC 주소:");
        myMacLabel.setForeground(Color.BLACK);
        panel.add(myMacLabel, gbc);
        
        gbc.gridx = 1; gbc.gridy = 1;
        myMacField = new JTextField("00:00:00:00:00:00", 15);
        myMacField.setBackground(Color.WHITE);
        myMacField.setForeground(Color.BLACK);
        panel.add(myMacField, gbc);
        
        // 내 IP 주소
        gbc.gridx = 0; gbc.gridy = 2;
        JLabel myIpLabel = new JLabel("내 IP 주소:");
        myIpLabel.setForeground(Color.BLACK);
        panel.add(myIpLabel, gbc);
        
        gbc.gridx = 1; gbc.gridy = 2;
        myIpField = new JTextField("169.254.", 15);
        myIpField.setBackground(Color.WHITE);
        myIpField.setForeground(Color.BLACK);
        panel.add(myIpField, gbc);
        
        // 목적지 IP 주소
        gbc.gridx = 0; gbc.gridy = 3;
        JLabel dstIpLabel = new JLabel("목적지 IP:");
        dstIpLabel.setForeground(Color.BLACK);
        panel.add(dstIpLabel, gbc);
        
        gbc.gridx = 1; gbc.gridy = 3;
        dstIpField = new JTextField("169.254", 15);
        dstIpField.setBackground(Color.WHITE);
        dstIpField.setForeground(Color.BLACK);
        panel.add(dstIpField, gbc);
        
        // 설정 버튼
        gbc.gridx = 2; gbc.gridy = 1; gbc.gridheight = 3;
        JButton setupButton = new JButton("설정");
        setupButton.addActionListener(e -> handleSetup());
        panel.add(setupButton, gbc);
        
        return panel;
    }
    
    /**
     * 중앙 패널 생성 (채팅 표시 + ARP 테이블)
     */
    private static JPanel createCenterPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 10, 0));
        panel.setBackground(Color.WHITE);
        
        // 채팅 표시 영역
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(Color.WHITE);
        chatPanel.setBorder(BorderFactory.createTitledBorder("메시지"));
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setBackground(Color.WHITE);
        textArea.setForeground(Color.BLACK);
        JScrollPane chatScroll = new JScrollPane(textArea);
        chatPanel.add(chatScroll, BorderLayout.CENTER);
        
        // ARP 테이블
        JPanel arpPanel = new JPanel(new BorderLayout());
        arpPanel.setBackground(Color.WHITE);
        arpPanel.setBorder(BorderFactory.createTitledBorder("ARP 캐시 테이블"));
        
        String[] columns = {"IP 주소", "MAC 주소"};
        arpTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        arpTable = new JTable(arpTableModel);
        arpTable.setBackground(Color.WHITE);
        arpTable.setForeground(Color.BLACK);
        arpTable.getTableHeader().setBackground(new Color(240, 240, 240));
        arpTable.getTableHeader().setForeground(Color.BLACK);
        JScrollPane tableScroll = new JScrollPane(arpTable);
        arpPanel.add(tableScroll, BorderLayout.CENTER);
        
        // ARP 테이블 새로고침 버튼
        JButton refreshButton = new JButton("새로고침");
        refreshButton.addActionListener(e -> updateArpTable());
        arpPanel.add(refreshButton, BorderLayout.SOUTH);
        
        panel.add(chatPanel);
        panel.add(arpPanel);
        
        return panel;
    }
    
    /**
     * 하단 패널 생성 (메시지 입력 + ARP 기능)
     */
    private static JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(Color.WHITE);
        
        // 메시지 입력 패널
        JPanel messagePanel = new JPanel(new BorderLayout(5, 5));
        messagePanel.setBackground(Color.WHITE);
        messagePanel.setBorder(BorderFactory.createTitledBorder("메시지 전송"));
        
        // 메시지 입력 + 옵션 패널
        JPanel msgInputPanel = new JPanel(new BorderLayout(5, 5));
        msgInputPanel.setBackground(Color.WHITE);
        
        messageField = new JTextField();
        messageField.setBackground(Color.WHITE);
        messageField.setForeground(Color.BLACK);
        messageField.addActionListener(e -> handleSendMessage());
        msgInputPanel.add(messageField, BorderLayout.CENTER);
        
        // 암호화 & 우선순위 옵션 패널
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        optionsPanel.setBackground(Color.WHITE);
        
        // 암호화 체크박스
        encryptCheckbox = new JCheckBox("🔒 암호화");
        encryptCheckbox.setBackground(Color.WHITE);
        encryptCheckbox.setForeground(Color.BLACK);
        encryptCheckbox.setToolTipText("XOR 암호화로 메시지를 보호합니다");
        encryptCheckbox.addActionListener(e -> {
            if (chatLayer != null) {
                chatLayer.setEncryptionEnabled(encryptCheckbox.isSelected());
                logToUI("[설정] 암호화 " + (encryptCheckbox.isSelected() ? "활성화" : "비활성화"));
            }
        });
        optionsPanel.add(encryptCheckbox);
        
        // 데모 모드 체크박스 (우선순위 시연용)
        JCheckBox demoModeCheckbox = new JCheckBox("🎬 데모모드");
        demoModeCheckbox.setBackground(Color.WHITE);
        demoModeCheckbox.setForeground(new Color(255, 87, 34)); // 주황색
        demoModeCheckbox.setToolTipText("우선순위 시연을 위해 메시지 처리에 1.5초 지연 추가");
        demoModeCheckbox.addActionListener(e -> {
            if (chatLayer != null) {
                chatLayer.setDemoMode(demoModeCheckbox.isSelected());
                logToUI("[설정] 데모 모드 " + (demoModeCheckbox.isSelected() ? "활성화 (처리 지연: 1.5초)" : "비활성화"));
            }
        });
        optionsPanel.add(demoModeCheckbox);
        
        // 우선순위 콤보박스
        optionsPanel.add(new JLabel("우선순위:"));
        priorityComboBox = new JComboBox<>(new String[]{"일반", "긴급", "낮음"});
        priorityComboBox.setBackground(Color.WHITE);
        priorityComboBox.setForeground(Color.BLACK);
        priorityComboBox.addActionListener(e -> {
            if (chatLayer != null && ipLayer != null) {
                int index = priorityComboBox.getSelectedIndex();
                ChatAppLayer.Priority priority = switch (index) {
                    case 1 -> ChatAppLayer.Priority.HIGH;
                    case 2 -> ChatAppLayer.Priority.LOW;
                    default -> ChatAppLayer.Priority.NORMAL;
                };
                chatLayer.setPriority(priority);
                ipLayer.setPriority(priority);
            }
        });
        optionsPanel.add(priorityComboBox);
        
        // 지연시간 표시 레이블
        latencyLabel = new JLabel("지연: -ms");
        latencyLabel.setForeground(Color.GRAY);
        optionsPanel.add(latencyLabel);
        
        messagePanel.add(optionsPanel, BorderLayout.NORTH);
        messagePanel.add(msgInputPanel, BorderLayout.CENTER);
        
        JButton sendButton = new JButton("전송");
        sendButton.addActionListener(e -> handleSendMessage());
        messagePanel.add(sendButton, BorderLayout.EAST);
        
        // 파일 전송 패널
        JPanel filePanel = new JPanel(new BorderLayout(5, 5));
        filePanel.setBackground(Color.WHITE);
        filePanel.setBorder(BorderFactory.createTitledBorder("파일 전송"));
        
        JPanel fileInputPanel = new JPanel(new BorderLayout(5, 5));
        fileInputPanel.setBackground(Color.WHITE);
        filePathField = new JTextField();
        filePathField.setBackground(Color.WHITE);
        filePathField.setForeground(Color.BLACK);
        filePathField.setEditable(false);
        fileInputPanel.add(filePathField, BorderLayout.CENTER);
        
        JPanel fileButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        fileButtonPanel.setBackground(Color.WHITE);
        JButton browseButton = new JButton("파일 선택");
        browseButton.addActionListener(e -> handleBrowseFile());
        fileButtonPanel.add(browseButton);
        
        JButton sendFileButton = new JButton("파일 전송");
        sendFileButton.addActionListener(e -> handleSendFile());
        fileButtonPanel.add(sendFileButton);
        fileInputPanel.add(fileButtonPanel, BorderLayout.EAST);
        
        filePanel.add(fileInputPanel, BorderLayout.NORTH);
        
        // 진행 표시
        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressPanel.setBackground(Color.WHITE);
        fileProgressBar = new JProgressBar(0, 100);
        fileProgressBar.setStringPainted(true);
        progressPanel.add(fileProgressBar, BorderLayout.CENTER);
        
        fileStatusLabel = new JLabel(" ");
        fileStatusLabel.setForeground(Color.BLACK);
        progressPanel.add(fileStatusLabel, BorderLayout.SOUTH);
        filePanel.add(progressPanel, BorderLayout.CENTER);
        
        JPanel combinedPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        combinedPanel.setBackground(Color.WHITE);
        combinedPanel.add(messagePanel);
        combinedPanel.add(filePanel);
        
        // ARP 기능 패널
        JPanel arpFunctionPanel = new JPanel(new GridBagLayout());
        arpFunctionPanel.setBorder(BorderFactory.createTitledBorder("ARP 기능"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // ARP Request 버튼
        gbc.gridx = 0; gbc.gridy = 0;
        JButton arpRequestButton = new JButton("ARP Request");
        arpRequestButton.addActionListener(e -> handleArpRequest());
        arpFunctionPanel.add(arpRequestButton, gbc);
        
        // Gratuitous ARP 버튼
        gbc.gridx = 1; gbc.gridy = 0;
        JButton gratuitousArpButton = new JButton("Gratuitous ARP");
        gratuitousArpButton.addActionListener(e -> handleGratuitousArp());
        arpFunctionPanel.add(gratuitousArpButton, gbc);
        
        // ARP 캐시 초기화 버튼
        gbc.gridx = 2; gbc.gridy = 0;
        JButton clearCacheButton = new JButton("캐시 초기화");
        clearCacheButton.addActionListener(e -> handleClearArpCache());
        arpFunctionPanel.add(clearCacheButton, gbc);
        
        // 로그 파일 보기 버튼
        gbc.gridx = 3; gbc.gridy = 0;
        JButton viewLogButton = new JButton("📋 로그 보기");
        viewLogButton.addActionListener(e -> handleViewLog());
        arpFunctionPanel.add(viewLogButton, gbc);
        
        // Proxy ARP 설정
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 3;
        proxyArpCheckbox = new JCheckBox("Proxy ARP 활성화");
        arpFunctionPanel.add(proxyArpCheckbox, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        arpFunctionPanel.add(new JLabel("Proxy IP:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 2;
        proxyIpField = new JTextField("192.168.0.200", 12);
        arpFunctionPanel.add(proxyIpField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 3;
        arpFunctionPanel.add(new JLabel("Proxy MAC:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 3;
        proxyMacField = new JTextField("AA:BB:CC:DD:EE:FF", 12);
        arpFunctionPanel.add(proxyMacField, gbc);
        
        gbc.gridx = 2; gbc.gridy = 2; gbc.gridheight = 2;
        JButton addProxyButton = new JButton("Proxy 추가");
        addProxyButton.addActionListener(e -> handleAddProxy());
        arpFunctionPanel.add(addProxyButton, gbc);
        
        // 종료 버튼
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 3; gbc.gridheight = 1;
        JButton exitButton = new JButton("종료");
        exitButton.addActionListener(e -> handleExit());
        arpFunctionPanel.add(exitButton, gbc);
        
        panel.add(combinedPanel, BorderLayout.NORTH);
        panel.add(arpFunctionPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 장치 이름 배열 반환
     */
    private static String[] getDeviceNames() {
        if (allDevices == null || allDevices.isEmpty()) {
            return new String[]{"장치 없음"};
        }
        
        String[] names = new String[allDevices.size()];
        for (int i = 0; i < allDevices.size(); i++) {
            PcapIf device = allDevices.get(i);
            names[i] = device.name() + " - " + "설명 없음";
//                      (device.description() != null ? device.description() : "설명 없음");
        }
        return names;
    }
    
    /**
     * 장치 선택 이벤트 처리
     */
    private static void handleDeviceSelection() {
        int index = deviceComboBox.getSelectedIndex();
        if (index >= 0 && index < allDevices.size()) {
            selectedDevice = allDevices.get(index);
            loadMacAddress();
            displayMacAddress();
        }
    }
    
    /**
     * 선택한 장치의 MAC 주소 로드
     */
    private static void loadMacAddress() {
        if (selectedDevice == null) return;
        
        // 기존 MAC 주소 초기화
        Arrays.fill(myMacAddress, (byte) 0);
        
        try {
            // 방법 1: NetworkInterface에서 MAC 주소 가져오기
            NetworkInterface ni = NetworkInterface.getByName(selectedDevice.name());
            if (ni != null && ni.isUp()) {
                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length >= 6) {
                    System.arraycopy(mac, 0, myMacAddress, 0, 6);
                    System.out.println("✅ MAC 주소 자동 로드 성공: " + formatMacAddress(myMacAddress));
                    logToUI("[시스템] MAC 주소 자동 로드: " + formatMacAddress(myMacAddress));
                    return;
                }
            }
            
            // 방법 2: jNetPcap에서 MAC 주소 가져오기 (VM에서 더 잘 작동)
            if (selectedDevice.addresses() != null && !selectedDevice.addresses().isEmpty()) {
                // 이 경우 수동 입력 필요
                System.err.println("⚠️  MAC 주소를 자동으로 찾을 수 없습니다 (VM 환경일 수 있음).");
            }
            
            // MAC 주소를 찾지 못한 경우
            System.err.println("\n─── MAC 주소 수동 입력 필요 ───");
            System.err.println("해결 방법:");
            System.err.println("  1) 터미널에서 실행:");
            System.err.println("     $ ifconfig " + selectedDevice.name());
            System.err.println("     또는");
            System.err.println("     $ ip link show " + selectedDevice.name());
            System.err.println("\n  2) 출력에서 MAC 주소 확인:");
            System.err.println("     ether 52:54:00:12:34:56  <- 이 부분 복사");
            System.err.println("\n  3) GUI의 '내 MAC 주소' 필드에 붙여넣기");
            System.err.println("\n  4) '설정' 버튼 클릭");
            System.err.println("─────────────────────────────\n");
            
            logToUI("[경고] MAC 주소 자동 로드 실패");
            logToUI("[안내] 터미널에서 'ifconfig " + selectedDevice.name() + "' 실행 후 MAC 주소를 수동 입력하세요");
            logToUI("[예시] 52:54:00:12:34:56 형식으로 입력");
        } catch (Exception e) {
            System.err.println("MAC 주소 로드 중 오류: " + e.getMessage());
            logToUI("[오류] MAC 주소 로드 실패: " + e.getMessage());
        }
    }
    
    /**
     * MAC 주소 표시
     */
    private static void displayMacAddress() {
        String macStr = formatMacAddress(myMacAddress);
        myMacField.setText(macStr);
        
        if (!isMacAddressZero(myMacAddress)) {
            logToUI("[시스템] 내 MAC 주소: " + macStr);
        } else {
            logToUI("[시스템] MAC 주소를 찾을 수 없습니다.");
            logToUI("[안내] 터미널에서 'ifconfig' 명령으로 MAC 주소를 확인 후 수동 입력하세요.");
            
            // 사용자에게 알림 표시
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, 
                    "MAC 주소를 자동으로 찾을 수 없습니다.\n\n" +
                    "해결 방법:\n" +
                    "1. 터미널에서 'ifconfig' 또는 'ip link' 명령 실행\n" +
                    "2. 선택한 네트워크 인터페이스의 MAC 주소 확인\n" +
                    "3. '내 MAC 주소' 필드에 수동으로 입력\n\n" +
                    "예시: 52:54:00:12:34:56",
                    "MAC 주소 입력 필요", 
                    JOptionPane.INFORMATION_MESSAGE);
            });
        }
    }
    
    /**
     * 설정 버튼 클릭 처리
     */
    private static void handleSetup() {
        try {
            // 장치 선택 확인
            if (selectedDevice == null) {
                logToUI("[오류] 네트워크 어댑터를 먼저 선택하세요");
                JOptionPane.showMessageDialog(null, 
                    "네트워크 어댑터를 먼저 선택하세요!",
                    "설정 오류", 
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // MAC 주소 파싱 및 검증
            String macStr = myMacField.getText().trim();
            
            // MAC 주소가 00:00:00:00:00:00인 경우 경고
            if (macStr.equals("00:00:00:00:00:00")) {
                logToUI("[오류] 유효한 MAC 주소를 입력하세요");
                JOptionPane.showMessageDialog(null, 
                    "MAC 주소가 설정되지 않았습니다!\n\n" +
                    "해결 방법:\n" +
                    "1. 터미널에서 'ifconfig' 실행\n" +
                    "2. 선택한 네트워크 인터페이스(" + selectedDevice.name() + ")의 MAC 주소 확인\n" +
                    "3. '내 MAC 주소' 필드에 입력 (예: 52:54:00:12:34:56)\n" +
                    "4. 다시 '설정' 버튼 클릭",
                    "MAC 주소 필요", 
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            byte[] parsedMac = parseMacAddress(macStr);
            if (parsedMac == null || parsedMac.length != 6) {
                logToUI("[오류] 잘못된 MAC 주소 형식: " + macStr);
                JOptionPane.showMessageDialog(null, 
                    "잘못된 MAC 주소 형식입니다!\n\n" +
                    "올바른 형식: XX:XX:XX:XX:XX:XX\n" +
                    "예시: 52:54:00:12:34:56",
                    "형식 오류", 
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // MAC 주소가 모두 0인지 확인
            boolean allZero = true;
            for (byte b : parsedMac) {
                if (b != 0) {
                    allZero = false;
                    break;
                }
            }
            
            if (allZero) {
                logToUI("[오류] 유효하지 않은 MAC 주소: 00:00:00:00:00:00");
                return;
            }
            
            System.arraycopy(parsedMac, 0, myMacAddress, 0, 6);
            logToUI("[시스템] 내 MAC: " + formatMacAddress(myMacAddress));
            
            // IP 주소 파싱
            parseIpAddress(myIpField.getText(), myIpAddress);
            parseIpAddress(dstIpField.getText(), dstIpAddress);
            
            logToUI("[시스템] 내 IP: " + formatIpAddress(myIpAddress));
            logToUI("[시스템] 목적지 IP: " + formatIpAddress(dstIpAddress));
            
            // 계층 초기화
            initializeLayers();
            
            // Physical 계층 열기
            openPhysicalLayer();
            
            // Gratuitous ARP 자동 전송 (네트워크 진입 알림)
            arpLayer.sendGratuitousArp();
            
            logToUI("[시스템] 설정 완료 - 통신 준비됨");
            logToUI("[안내] 이제 ARP Request를 먼저 실행하여 상대방 MAC 주소를 확보하세요");
            
        } catch (Exception e) {
            logToUI("[오류] 설정 실패: " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, 
                "설정 실패!\n\n오류: " + e.getMessage(),
                "설정 오류", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * IP 주소 문자열 파싱
     */
    private static void parseIpAddress(String ipStr, byte[] ipArray) {
        String[] parts = ipStr.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("잘못된 IP 주소 형식: " + ipStr);
        }
        
        for (int i = 0; i < 4; i++) {
            int value = Integer.parseInt(parts[i]);
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException("IP 주소 범위 오류: " + value);
            }
            ipArray[i] = (byte) value;
        }
    }
    
    /**
     * 계층 구조 초기화
     * 
     * 계층 연결:
     * ChatApp → IP → Ethernet (데이터 전송)
     *              ↘ ARP (주소 해석)
     *                  ↓
     *              Physical
     */
    private static void initializeLayers() {
        // 1. ChatAppLayer 생성
        chatLayer = new ChatAppLayer(message -> {
            logToUI("[수신] " + message);
        });
        
        // 지연시간 포함 콜백 설정
        chatLayer.setOnReceiveWithLatency((message, latency) -> {
            SwingUtilities.invokeLater(() -> {
                logToUI("[수신] " + message);
                latencyLabel.setText("지연: " + latency + "ms");
                
                // 지연시간에 따라 색상 변경
                if (latency < 50) {
                    latencyLabel.setForeground(new Color(0, 150, 0)); // 녹색
                } else if (latency < 100) {
                    latencyLabel.setForeground(new Color(200, 150, 0)); // 주황
                } else {
                    latencyLabel.setForeground(Color.RED); // 빨강
                }
            });
        });
        
        // 2. FileAppLayer 생성
        fileLayer = new FileAppLayer();
        fileLayer.setOnReceiveProgress((fileName, progress) -> {
            SwingUtilities.invokeLater(() -> {
                fileProgressBar.setValue(progress);
                if (progress == 0) {
                    fileStatusLabel.setText("수신 시작: " + fileName);
                } else if (progress == 100) {
                    fileStatusLabel.setText("수신 완료: " + fileName);
                    logToUI("[파일] 수신 완료: " + fileName);
                } else {
                    fileStatusLabel.setText("수신 중: " + fileName + " (" + progress + "%)");
                }
            });
        });
        
        // 3. IPLayer 생성 및 설정
        ipLayer = new IPLayer();
        ipLayer.setMyIp(myIpAddress);
        ipLayer.setDstIp(dstIpAddress);
        
        // 3. ARPLayer 생성 및 설정
        arpLayer = new ARPLayer();
        arpLayer.setMyMac(myMacAddress);
        arpLayer.setMyIp(myIpAddress);
        
        // 4. EthernetLayer 생성 및 설정
        ethernetLayer = new EthernetLayer();
        ethernetLayer.setSrcMac(myMacAddress);
        ethernetLayer.setEtherType(0x0800); // IPv4
        
        // 5. PhysicalLayer 생성
        physicalLayer = new PhysicalLayer();
        
        // 6. 계층 연결
        // ChatApp ↔ IP
        chatLayer.SetUnderLayer(ipLayer);
        ipLayer.SetUpperLayer(chatLayer);
        
        // FileApp ↔ IP
        fileLayer.SetUnderLayer(ipLayer);
        ipLayer.SetUpperLayer(fileLayer);
        
        // IP ↔ Ethernet (데이터 전송용)
        ipLayer.SetUnderLayer(ethernetLayer);
        ethernetLayer.SetUpperLayer(ipLayer);
        
        // ARP ↔ Ethernet (주소 해석용)
        arpLayer.SetUnderLayer(ethernetLayer);
        ethernetLayer.SetUpperLayer(arpLayer);
        
        // Ethernet ↔ Physical
        ethernetLayer.SetUnderLayer(physicalLayer);
        physicalLayer.SetUpperLayer(ethernetLayer);
        
        // IP와 ARP 연결 (IP가 ARP 사용)
        ipLayer.setArpLayer(arpLayer);
        
        System.out.println("[시스템] 계층 구조 초기화 완료");
    }
    
    /**
     * Physical 계층 열기
     */
    private static void openPhysicalLayer() {
        try {
            physicalLayer.open(selectedDevice, PROMISCUOUS_MODE, READ_TIMEOUT_MS);
            logToUI("[시스템] 네트워크 연결 성공");
        } catch (PcapException e) {
            logToUI("[오류] 네트워크 연결 실패: " + e.getMessage());
        }
    }
    
    /**
     * 메시지 전송 처리
     */
    private static void handleSendMessage() {
        String message = messageField.getText();
        if (message.isEmpty()) return;
        
        if (chatLayer == null) {
            logToUI("[오류] 네트워크가 설정되지 않았습니다.");
            return;
        }
        
        // 목적지 IP 업데이트
        try {
            parseIpAddress(dstIpField.getText(), dstIpAddress);
            ipLayer.setDstIp(dstIpAddress);
        } catch (Exception e) {
            logToUI("[오류] 잘못된 목적지 IP: " + e.getMessage());
            return;
        }
        
        // ChatApp 프로토콜 사용
        ipLayer.useChatProtocol();
        
        if (chatLayer.sendMessage(message)) {
            logToUI("[전송] " + message);
            messageField.setText("");
        } else {
            logToUI("[오류] 메시지 전송 실패 (ARP 캐시 확인 필요)");
        }
    }
    
    /**
     * 파일 선택 대화상자
     */
    private static void handleBrowseFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        
        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            java.io.File selectedFile = fileChooser.getSelectedFile();
            filePathField.setText(selectedFile.getAbsolutePath());
        }
    }
    
    /**
     * 파일 전송 처리
     */
    private static void handleSendFile() {
        String filePath = filePathField.getText();
        if (filePath.isEmpty()) {
            logToUI("[오류] 파일을 선택하세요.");
            return;
        }
        
        if (fileLayer == null) {
            logToUI("[오류] 네트워크가 설정되지 않았습니다.");
            return;
        }
        
        java.io.File file = new java.io.File(filePath);
        if (!file.exists() || !file.isFile()) {
            logToUI("[오류] 파일을 찾을 수 없습니다: " + filePath);
            return;
        }
        
        // 목적지 IP 업데이트
        try {
            parseIpAddress(dstIpField.getText(), dstIpAddress);
            ipLayer.setDstIp(dstIpAddress);
        } catch (Exception e) {
            logToUI("[오류] 잘못된 목적지 IP: " + e.getMessage());
            return;
        }
        
        // FileApp 프로토콜 사용
        ipLayer.useFileProtocol();
        
        // 진행 상태 초기화
        fileProgressBar.setValue(0);
        fileStatusLabel.setText("전송 준비 중...");
        
        logToUI("[파일] 전송 시작: " + file.getName() + " (" + file.length() + " bytes)");
        
        // 전송 진행 콜백 설정
        fileLayer.setOnSendProgress((fileName, progress) -> {
            SwingUtilities.invokeLater(() -> {
                fileProgressBar.setValue(progress);
                if (progress == 100) {
                    fileStatusLabel.setText("전송 완료: " + fileName);
                    logToUI("[파일] 전송 완료: " + fileName);
                    // 전송 완료 후 ChatApp 프로토콜로 복귀
                    ipLayer.useChatProtocol();
                } else {
                    fileStatusLabel.setText("전송 중: " + fileName + " (" + progress + "%)");
                }
            });
        });
        
        // 파일 전송 (별도 스레드에서 실행)
        new Thread(() -> {
            try {
            fileLayer.sendFile(filePath);
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    logToUI("[오류] 파일 전송 실패: " + e.getMessage());
                    fileStatusLabel.setText("전송 실패");
                    // 전송 실패 시에도 ChatApp 프로토콜로 복귀
                    ipLayer.useChatProtocol();
                });
            }
        }, "FileTransfer-UI").start();
    }
    
    /**
     * ARP Request 전송
     */
    private static void handleArpRequest() {
        if (arpLayer == null) {
            logToUI("[오류] ARP 계층이 초기화되지 않았습니다.");
            return;
        }
        
        try {
            byte[] targetIp = new byte[4];
            parseIpAddress(dstIpField.getText(), targetIp);
            
            // ARP용 이더넷 설정 (브로드캐스트)
            byte[] broadcast = new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF, 
                                         (byte)0xFF, (byte)0xFF, (byte)0xFF};
            ethernetLayer.setDstMac(broadcast);
            ethernetLayer.setEtherType(0x0806); // ARP
            
            arpLayer.sendArpRequest(targetIp);
            logToUI("[ARP] Request 전송: " + formatIpAddress(targetIp));
            
            // 이더넷 타입 복원
            ethernetLayer.setEtherType(0x0800); // IPv4
            
        } catch (Exception e) {
            logToUI("[오류] ARP Request 실패: " + e.getMessage());
        }
    }
    
    /**
     * Gratuitous ARP 전송
     */
    private static void handleGratuitousArp() {
        if (arpLayer == null) {
            logToUI("[오류] ARP 계층이 초기화되지 않았습니다.");
            return;
        }
        
        // ARP용 이더넷 설정 (브로드캐스트)
        byte[] broadcast = new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF, 
                                     (byte)0xFF, (byte)0xFF, (byte)0xFF};
        ethernetLayer.setDstMac(broadcast);
        ethernetLayer.setEtherType(0x0806); // ARP
        
        arpLayer.sendGratuitousArp();
        logToUI("[ARP] Gratuitous ARP 전송");
        
        // 이더넷 타입 복원
        ethernetLayer.setEtherType(0x0800); // IPv4
    }
    
    /**
     * ARP 캐시 초기화
     */
    private static void handleClearArpCache() {
        if (arpLayer == null) {
            logToUI("[오류] ARP 계층이 초기화되지 않았습니다.");
            return;
        }
        
        arpLayer.clearArpCache();
        updateArpTable();
        logToUI("[ARP] 캐시 초기화됨");
    }
    
    /**
     * Proxy ARP 엔트리 추가
     */
    private static void handleAddProxy() {
        if (arpLayer == null) {
            logToUI("[오류] ARP 계층이 초기화되지 않았습니다.");
            return;
        }
        
        try {
            String proxyIp = proxyIpField.getText();
            String proxyMacStr = proxyMacField.getText();
            
            byte[] proxyMac = parseMacAddress(proxyMacStr);
            arpLayer.addProxyArpEntry(proxyIp, proxyMac);
            arpLayer.setProxyArpEnabled(proxyArpCheckbox.isSelected());
            
            logToUI("[Proxy ARP] 추가: " + proxyIp + " -> " + proxyMacStr);
            
        } catch (Exception e) {
            logToUI("[오류] Proxy ARP 추가 실패: " + e.getMessage());
        }
    }
    
    /**
     * MAC 주소 문자열 파싱
     */
    private static byte[] parseMacAddress(String macStr) {
        String[] parts = macStr.split(":");
        if (parts.length != 6) {
            throw new IllegalArgumentException("잘못된 MAC 주소 형식");
        }
        
        byte[] mac = new byte[6];
        for (int i = 0; i < 6; i++) {
            mac[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return mac;
    }
    
    /**
     * ARP 테이블 업데이트
     */
    private static void updateArpTable() {
        if (arpLayer == null) return;
        
        arpTableModel.setRowCount(0);
        Map<String, byte[]> cache = arpLayer.getArpCache();
        
        for (Map.Entry<String, byte[]> entry : cache.entrySet()) {
            String ip = entry.getKey();
            String mac = formatMacAddress(entry.getValue());
            arpTableModel.addRow(new Object[]{ip, mac});
        }
    }
    
    /**
     * 종료 처리
     */
    private static void handleExit() {
        // ChatAppLayer 메시지 처리 중지
        if (chatLayer != null) {
            chatLayer.stopMessageProcessing();
        }
        
        if (physicalLayer != null) {
            physicalLayer.close();
        }
        System.exit(0);
    }
    
    /**
     * 로그 파일 보기
     */
    private static void handleViewLog() {
        try {
            String logPath = ChatAppLayer.getLogFilePath();
            java.io.File logFile = new java.io.File(logPath);
            
            if (!logFile.exists()) {
                JOptionPane.showMessageDialog(null, 
                    "로그 파일이 아직 생성되지 않았습니다.",
                    "로그 없음", 
                    JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            // 로그 파일 내용 읽기
            StringBuilder content = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(logFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            
            // 다이얼로그로 표시
            JTextArea logTextArea = new JTextArea(content.toString());
            logTextArea.setEditable(false);
            logTextArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
            JScrollPane scrollPane = new JScrollPane(logTextArea);
            scrollPane.setPreferredSize(new Dimension(700, 400));
            
            JOptionPane.showMessageDialog(null, scrollPane, 
                "패킷 로그 (" + logPath + ")", 
                JOptionPane.PLAIN_MESSAGE);
                
        } catch (Exception e) {
            logToUI("[오류] 로그 파일 읽기 실패: " + e.getMessage());
        }
    }
    
    /**
     * UI에 로그 출력
     */
    private static void logToUI(String message) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(message + "\n");
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }
    
    /**
     * MAC 주소 포맷팅
     */
    private static String formatMacAddress(byte[] mac) {
        if (mac == null || mac.length < 6) {
            return "00:00:00:00:00:00";
        }
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
            mac[0] & 0xFF, mac[1] & 0xFF, mac[2] & 0xFF,
            mac[3] & 0xFF, mac[4] & 0xFF, mac[5] & 0xFF);
    }
    
    /**
     * IP 주소 포맷팅
     */
    private static String formatIpAddress(byte[] ip) {
        if (ip == null || ip.length < 4) {
            return "0.0.0.0";
        }
        return String.format("%d.%d.%d.%d",
            ip[0] & 0xFF, ip[1] & 0xFF, ip[2] & 0xFF, ip[3] & 0xFF);
    }
    
    /**
     * MAC 주소가 0인지 확인
     */
    private static boolean isMacAddressZero(byte[] mac) {
        if (mac == null || mac.length < 6) return true;
        for (int i = 0; i < 6; i++) {
            if (mac[i] != 0) return false;
        }
        return true;
    }
}
