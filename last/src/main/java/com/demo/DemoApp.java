package com.demo;

import java.nio.ByteBuffer;

/**
 * Docker Demo Application
 * Demonstrates all 3 new features: Encryption, Priority Queue, and Timestamp/Logging
 */
public class DemoApp {
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║     Network Chat Application - Feature Demonstration       ║");
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        System.out.println("║  1. XOR Encryption                                         ║");
        System.out.println("║  2. Priority Queue (HIGH/NORMAL/LOW)                       ║");
        System.out.println("║  3. Timestamp & Latency Logging                            ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        
        runEncryptionDemo();
        runPriorityDemo();
        runTimestampDemo();
        
        System.out.println("\n✅ All demonstrations completed successfully!");
    }
    
    private static void runEncryptionDemo() {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("🔐 [1] Encryption Demo");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        String original = "Hello, Encrypted World!";
        byte[] originalBytes = original.getBytes();
        byte[] encrypted = xorEncrypt(originalBytes);
        byte[] decrypted = xorEncrypt(encrypted); // XOR is symmetric
        String recovered = new String(decrypted);
        
        System.out.println("   Original Message: " + original);
        System.out.println("   Encrypted (hex) : " + bytesToHex(encrypted));
        System.out.println("   Decrypted       : " + recovered);
        System.out.println("   Verification    : " + (original.equals(recovered) ? "✓ PASS" : "✗ FAIL"));
        System.out.println();
    }
    
    private static void runPriorityDemo() {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("📊 [2] Priority Queue Demo");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // Simulate priority-based TOS values
        int highPriorityTOS = 0xE0;   // 224 - Internetwork Control
        int normalPriorityTOS = 0x00; // 0 - Best Effort
        int lowPriorityTOS = 0x20;    // 32 - Background
        
        System.out.println("   Priority Levels:");
        System.out.println("   ┌────────────┬──────────┬─────────────────────┐");
        System.out.println("   │ Priority   │ TOS Byte │ Description         │");
        System.out.println("   ├────────────┼──────────┼─────────────────────┤");
        System.out.printf("   │ [긴급] HIGH│   0x%02X   │ Internetwork Control│%n", highPriorityTOS);
        System.out.printf("   │ [일반] NORM│   0x%02X   │ Best Effort         │%n", normalPriorityTOS);
        System.out.printf("   │ [낮음] LOW │   0x%02X   │ Background Traffic  │%n", lowPriorityTOS);
        System.out.println("   └────────────┴──────────┴─────────────────────┘");
        System.out.println();
        
        // Simulate message processing order
        System.out.println("   Message Queue Simulation:");
        System.out.println("   ┌───┬───────────────────────────┬──────────┐");
        System.out.println("   │ # │ Message                   │ Priority │");
        System.out.println("   ├───┼───────────────────────────┼──────────┤");
        System.out.println("   │ 1 │ Normal message 1          │ [일반]   │");
        System.out.println("   │ 2 │ Low priority update       │ [낮음]   │");
        System.out.println("   │ 3 │ ⚠️ URGENT: Server alert!  │ [긴급]   │");
        System.out.println("   │ 4 │ Normal message 2          │ [일반]   │");
        System.out.println("   └───┴───────────────────────────┴──────────┘");
        System.out.println();
        System.out.println("   Processing Order: #3 → #1 → #4 → #2");
        System.out.println("   (HIGH priority messages processed first)");
        System.out.println();
    }
    
    private static void runTimestampDemo() {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("⏱️ [3] Timestamp & Latency Demo");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // Simulate message timestamps
        long sendTime = System.currentTimeMillis();
        
        // Simulate network delay
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long recvTime = System.currentTimeMillis();
        long latency = recvTime - sendTime;
        
        System.out.println("   Packet Header Structure:");
        System.out.println("   ┌──────────────┬──────────┬───────────────────────────┐");
        System.out.println("   │ Field        │ Size     │ Description               │");
        System.out.println("   ├──────────────┼──────────┼───────────────────────────┤");
        System.out.println("   │ Type+Flag    │ 1 byte   │ Message type & encryption │");
        System.out.println("   │ Priority     │ 1 byte   │ 0=HIGH, 1=NORMAL, 2=LOW   │");
        System.out.println("   │ Timestamp    │ 8 bytes  │ Send time (millis)        │");
        System.out.println("   │ Sequence     │ 4 bytes  │ Fragment sequence number  │");
        System.out.println("   │ Total        │ 4 bytes  │ Total fragment count      │");
        System.out.println("   │ Data         │ Variable │ Payload                   │");
        System.out.println("   └──────────────┴──────────┴───────────────────────────┘");
        System.out.println();
        
        java.time.format.DateTimeFormatter formatter = 
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        java.time.LocalDateTime sendDateTime = java.time.LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(sendTime), java.time.ZoneId.systemDefault());
        java.time.LocalDateTime recvDateTime = java.time.LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(recvTime), java.time.ZoneId.systemDefault());
        
        System.out.println("   Latency Calculation Example:");
        System.out.println("   ├─ Send Time : " + sendDateTime.format(formatter));
        System.out.println("   ├─ Recv Time : " + recvDateTime.format(formatter));
        System.out.println("   └─ Latency   : " + latency + "ms");
        System.out.println();
        
        System.out.println("   Log Format:");
        System.out.println("   [ChatApp:LOG] " + recvDateTime.format(formatter) + 
                          " [RECV] Hello! (send=" + sendTime + ", recv=" + recvTime + 
                          ", latency=" + latency + "ms)");
        System.out.println();
    }
    
    private static byte[] xorEncrypt(byte[] data) {
        byte key = 0x42;
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key);
        }
        return result;
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
