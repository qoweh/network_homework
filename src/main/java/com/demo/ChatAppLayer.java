package com.demo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ChatAppLayer implements BaseLayer {
    private final String name = "ChatApp";
    private BaseLayer underLayer;
    private final List<BaseLayer> uppers = new ArrayList<>();
    private Consumer<String> onReceive;

    public ChatAppLayer(Consumer<String> onReceive) {
        this.onReceive = onReceive;
    }

    public void setOnReceive(Consumer<String> onReceive) {
        this.onReceive = onReceive;
    }

    public boolean sendMessage(String text) {
        if (underLayer == null) return false;
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return underLayer.Send(bytes, bytes.length);
    }

    @Override
    public String GetLayerName() { return name; }

    @Override
    public BaseLayer GetUnderLayer() { return underLayer; }

    @Override
    public BaseLayer GetUpperLayer(int index) { return (index >=0 && index < uppers.size()) ? uppers.get(index) : null; }

    @Override
    public void SetUnderLayer(BaseLayer pUnderLayer) { this.underLayer = pUnderLayer; }

    @Override
    public void SetUpperLayer(BaseLayer pUpperLayer) { if (!uppers.contains(pUpperLayer)) uppers.add(pUpperLayer); }

    @Override
    public boolean Send(byte[] input, int length) { return sendMessage(new String(input, 0, length, StandardCharsets.UTF_8)); }

    @Override
    public boolean Receive(byte[] input) {
        if (onReceive != null) {
            int end = input.length; while (end > 0 && input[end-1] == 0x00) end--; // strip trailing zeros
            String msg = new String(input, 0, end, StandardCharsets.UTF_8);
            onReceive.accept(msg);
        }
        return true;
    }
}
