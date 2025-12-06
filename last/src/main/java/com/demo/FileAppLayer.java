package com.demo;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * FileAppLayer - 파일 전송 애플리케이션 계층
 * 
 * 역할:
 * - 파일을 작은 조각(Fragment)으로 나누어 전송 (Fragmentation)
 * - 수신한 Fragment를 재조립하여 파일 복원
 * - 파일 전송 중 채팅 메시지 전송 가능 (Thread 기반)
 * 
 * Fragment 헤더 구조 (10바이트):
 * ┌──────────┬──────────┬──────────┬──────────┬──────────┐
 * │   Type   │ Sequence │Total Seq │File Name │  Data    │
 * │ (1 byte) │ (4 bytes)│ (4 bytes)│  Length  │(가변)    │
 * │          │          │          │ (1 byte) │          │
 * └──────────┴──────────┴──────────┴──────────┴──────────┘
 * 
 * Type:
 * - 0x01: FILE_START (파일명, 파일크기 전송)
 * - 0x02: FILE_DATA (파일 데이터 전송)
 * - 0x03: FILE_END (전송 완료)
 */
public class FileAppLayer implements BaseLayer {
    private final String name = "FileApp";
    private BaseLayer underLayer; // 하위 계층: IPLayer
    private final List<BaseLayer> uppers = new ArrayList<>();
    
    // Fragment 타입
    private static final byte TYPE_FILE_START = 0x01;
    private static final byte TYPE_FILE_DATA = 0x02;
    private static final byte TYPE_FILE_END = 0x03;
    
    // Fragment 크기 (최대 페이로드)
    private static final int MAX_DATA_SIZE = 1024; // 1KB per fragment
    
    // 수신 중인 파일 정보 저장
    private final Map<String, FileReceiveContext> receivingFiles = new ConcurrentHashMap<>();
    
    // 파일 수신 콜백 (파일명, 진행률)
    private BiConsumer<String, Integer> onReceiveProgress;
    private BiConsumer<String, Boolean> onReceiveComplete; // 파일명, 성공여부
    
    // 파일 전송 콜백 (파일명, 진행률)
    private BiConsumer<String, Integer> onSendProgress;
    
    /**
     * 파일 수신 컨텍스트
     */
    private static class FileReceiveContext {
        String fileName;
        long totalSize;
        int totalSequences;
        byte[] buffer;
        int receivedSequences;
        Set<Integer> receivedSeqNumbers = new HashSet<>();
        
        FileReceiveContext(String fileName, long totalSize, int totalSequences) {
            this.fileName = fileName;
            this.totalSize = totalSize;
            this.totalSequences = totalSequences;
            this.buffer = new byte[(int) totalSize];
            this.receivedSequences = 0;
        }
        
        boolean isComplete() {
            return receivedSequences >= totalSequences;
        }
        
        int getProgress() {
            return totalSequences > 0 ? (receivedSequences * 100 / totalSequences) : 0;
        }
    }
    
    public FileAppLayer() {
    }
    
    public void setOnReceiveProgress(BiConsumer<String, Integer> callback) {
        this.onReceiveProgress = callback;
    }
    
    public void setOnReceiveComplete(BiConsumer<String, Boolean> callback) {
        this.onReceiveComplete = callback;
    }
    
    public void setOnSendProgress(BiConsumer<String, Integer> callback) {
        this.onSendProgress = callback;
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
    public void SetUnderLayer(BaseLayer layer) {
        this.underLayer = layer;
    }
    
    @Override
    public void SetUpperLayer(BaseLayer layer) {
        if (!uppers.contains(layer)) {
            uppers.add(layer);
        }
    }
    
    /**
     * 파일을 Fragment로 나누어 전송 (별도 Thread에서 실행)
     * 
     * @param filePath 전송할 파일 경로
     * @return 전송 성공 여부
     */
    public boolean sendFile(String filePath) {
        // 별도 스레드에서 파일 전송
        Thread sendThread = new Thread(() -> {
            try {
                File file = new File(filePath);
                if (!file.exists() || !file.isFile()) {
                    System.err.println("[FileApp] 파일을 찾을 수 없음: " + filePath);
                    return;
                }
                
                String fileName = file.getName();
                long fileSize = file.length();
                
                // 총 Fragment 개수 계산
                int totalSequences = (int) Math.ceil((double) fileSize / MAX_DATA_SIZE);
                
                System.out.println("[FileApp] 파일 전송 시작: " + fileName + 
                                 " (크기: " + fileSize + "바이트, " + 
                                 totalSequences + "개 Fragment)");
                
                // 1. FILE_START 전송
                sendFileStart(fileName, fileSize, totalSequences);
                
                // 2. FILE_DATA 전송
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[MAX_DATA_SIZE];
                    int sequence = 0;
                    int bytesRead;
                    
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        byte[] data = Arrays.copyOf(buffer, bytesRead);
                        sendFileData(sequence, totalSequences, data);
                        sequence++;
                        
                        // 진행률 출력
                        int progress = (sequence * 100 / totalSequences);
                        System.out.println("[FileApp] 전송 진행: " + progress + "% " +
                                         "(" + sequence + "/" + totalSequences + ")");
                        
                        // 네트워크 부하 방지 (약간의 딜레이)
                        Thread.sleep(10);
                    }
                }
                
                // 3. FILE_END 전송
                sendFileEnd(totalSequences);
                
                System.out.println("[FileApp] 파일 전송 완료: " + fileName);
                
            } catch (Exception e) {
                System.err.println("[FileApp] 파일 전송 중 오류: " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        sendThread.setName("FileTransfer-" + new File(filePath).getName());
        sendThread.start();
        
        return true;
    }
    
    /**
     * FILE_START Fragment 전송
     */
    private void sendFileStart(String fileName, long fileSize, int totalSequences) {
        byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        int fileNameLen = Math.min(fileNameBytes.length, 255);
        
        // Fragment 생성: Type(1) + Seq(4) + TotalSeq(4) + FileNameLen(1) + FileSize(8) + FileName(가변)
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 4 + 1 + 8 + fileNameLen);
        buffer.put(TYPE_FILE_START);
        buffer.putInt(0); // sequence = 0
        buffer.putInt(totalSequences);
        buffer.put((byte) fileNameLen);
        buffer.putLong(fileSize);
        buffer.put(fileNameBytes, 0, fileNameLen);
        
        Send(buffer.array(), buffer.position());
    }
    
    /**
     * FILE_DATA Fragment 전송
     */
    private void sendFileData(int sequence, int totalSequences, byte[] data) {
        // Fragment 생성: Type(1) + Seq(4) + TotalSeq(4) + DataLen(2) + Data(가변)
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 4 + 2 + data.length);
        buffer.put(TYPE_FILE_DATA);
        buffer.putInt(sequence);
        buffer.putInt(totalSequences);
        buffer.putShort((short) data.length);
        buffer.put(data);
        
        Send(buffer.array(), buffer.position());
    }
    
    /**
     * FILE_END Fragment 전송
     */
    private void sendFileEnd(int totalSequences) {
        // Fragment 생성: Type(1) + Seq(4) + TotalSeq(4)
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 4);
        buffer.put(TYPE_FILE_END);
        buffer.putInt(totalSequences); // 마지막 sequence
        buffer.putInt(totalSequences);
        
        Send(buffer.array(), buffer.position());
    }
    
    @Override
    public boolean Send(byte[] input, int length) {
        if (underLayer == null) {
            System.err.println("[FileApp] 하위 계층이 설정되지 않음");
            return false;
        }
        
        return underLayer.Send(input, length);
    }
    
    /**
     * Fragment 수신 및 재조립
     */
    @Override
    public boolean Receive(byte[] input) {
        if (input == null || input.length < 9) {
            return false;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(input);
        byte type = buffer.get();
        int sequence = buffer.getInt();
        int totalSequences = buffer.getInt();
        
        switch (type) {
            case TYPE_FILE_START:
                handleFileStart(buffer, sequence, totalSequences);
                break;
                
            case TYPE_FILE_DATA:
                handleFileData(buffer, sequence, totalSequences);
                break;
                
            case TYPE_FILE_END:
                handleFileEnd(sequence, totalSequences);
                break;
                
            default:
                System.err.println("[FileApp] 알 수 없는 Fragment 타입: " + type);
                return false;
        }
        
        return true;
    }
    
    /**
     * FILE_START Fragment 처리
     */
    private void handleFileStart(ByteBuffer buffer, int sequence, int totalSequences) {
        byte fileNameLen = buffer.get();
        long fileSize = buffer.getLong();
        
        byte[] fileNameBytes = new byte[fileNameLen & 0xFF];
        buffer.get(fileNameBytes);
        String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
        
        System.out.println("[FileApp] 파일 수신 시작: " + fileName + 
                         " (크기: " + fileSize + "바이트, " + 
                         totalSequences + "개 Fragment)");
        
        // 수신 컨텍스트 생성
        FileReceiveContext context = new FileReceiveContext(fileName, fileSize, totalSequences);
        receivingFiles.put(fileName, context);
        
        if (onReceiveProgress != null) {
            onReceiveProgress.accept(fileName, 0);
        }
    }
    
    /**
     * FILE_DATA Fragment 처리
     */
    private void handleFileData(ByteBuffer buffer, int sequence, int totalSequences) {
        short dataLen = buffer.getShort();
        byte[] data = new byte[dataLen & 0xFFFF];
        buffer.get(data);
        
        // 해당 파일의 수신 컨텍스트 찾기
        FileReceiveContext context = findContextByTotalSeq(totalSequences);
        
        if (context == null) {
            System.err.println("[FileApp] 수신 컨텍스트를 찾을 수 없음 (Seq: " + sequence + ")");
            return;
        }
        
        // 중복 체크
        if (context.receivedSeqNumbers.contains(sequence)) {
            System.out.println("[FileApp] 중복 Fragment 무시: " + sequence);
            return;
        }
        
        // 데이터를 버퍼에 복사
        int offset = sequence * MAX_DATA_SIZE;
        int copyLen = Math.min(data.length, context.buffer.length - offset);
        System.arraycopy(data, 0, context.buffer, offset, copyLen);
        
        context.receivedSeqNumbers.add(sequence);
        context.receivedSequences++;
        
        // 진행률 업데이트
        int progress = context.getProgress();
        System.out.println("[FileApp] 수신 진행: " + progress + "% " +
                         "(" + context.receivedSequences + "/" + context.totalSequences + ")");
        
        if (onReceiveProgress != null) {
            onReceiveProgress.accept(context.fileName, progress);
        }
    }
    
    /**
     * FILE_END Fragment 처리
     */
    private void handleFileEnd(int sequence, int totalSequences) {
        FileReceiveContext context = findContextByTotalSeq(totalSequences);
        
        if (context == null) {
            System.err.println("[FileApp] 수신 컨텍스트를 찾을 수 없음");
            return;
        }
        
        System.out.println("[FileApp] 파일 수신 완료: " + context.fileName);
        
        // 파일 저장
        boolean success = saveFile(context);
        
        if (onReceiveComplete != null) {
            onReceiveComplete.accept(context.fileName, success);
        }
        
        // 컨텍스트 제거
        receivingFiles.remove(context.fileName);
    }
    
    /**
     * totalSequences로 수신 컨텍스트 찾기
     */
    private FileReceiveContext findContextByTotalSeq(int totalSequences) {
        for (FileReceiveContext context : receivingFiles.values()) {
            if (context.totalSequences == totalSequences) {
                return context;
            }
        }
        return null;
    }
    
    /**
     * 수신한 파일을 디스크에 저장
     */
    private boolean saveFile(FileReceiveContext context) {
        try {
            // 받은 파일을 저장할 디렉토리
            File receivedDir = new File("received_files");
            if (!receivedDir.exists()) {
                receivedDir.mkdirs();
            }
            
            File outputFile = new File(receivedDir, context.fileName);
            
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(context.buffer, 0, (int) context.totalSize);
            }
            
            System.out.println("[FileApp] 파일 저장 완료: " + outputFile.getAbsolutePath());
            return true;
            
        } catch (IOException e) {
            System.err.println("[FileApp] 파일 저장 실패: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
