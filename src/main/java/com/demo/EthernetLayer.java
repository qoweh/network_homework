package com.demo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EthernetLayer implements BaseLayer {
    private final String name = "Ethernet";
    private BaseLayer underLayer; // Physical
    private final List<BaseLayer> uppers = new ArrayList<>(); // ChatApp

    private byte[] srcMac = new byte[6];
    private byte[] dstMac = new byte[6];
    private int etherType = 0xFFFF; // default for this chat

    public void setSrcMac(byte[] mac) { if (mac != null && mac.length >= 6) System.arraycopy(mac,0,srcMac,0,6); }
    public void setDstMac(byte[] mac) { if (mac != null && mac.length >= 6) System.arraycopy(mac,0,dstMac,0,6); }
    public void setEtherType(int et) { this.etherType = et & 0xFFFF; }

    @Override
    public String GetLayerName() { return name; }

    @Override
    public BaseLayer GetUnderLayer() { return underLayer; }

    @Override
    public BaseLayer GetUpperLayer(int index) { return (index>=0 && index<uppers.size()) ? uppers.get(index) : null; }

    @Override
    public void SetUnderLayer(BaseLayer pUnderLayer) { this.underLayer = pUnderLayer; }

    @Override
    public void SetUpperLayer(BaseLayer pUpperLayer) { if (!uppers.contains(pUpperLayer)) uppers.add(pUpperLayer); }

    @Override
    public boolean Send(byte[] input, int length) {
        if (underLayer == null) return false;
        int minLen = 60; // without FCS
        int frameLen = Math.max(minLen, 14 + length);
        byte[] frame = new byte[frameLen];
        // dst/src
        System.arraycopy(dstMac, 0, frame, 0, 6);
        System.arraycopy(srcMac, 0, frame, 6, 6);
        // EtherType
        frame[12] = (byte) ((etherType >> 8) & 0xFF);
        frame[13] = (byte) (etherType & 0xFF);
        // payload
        System.arraycopy(input, 0, frame, 14, length);
        return underLayer.Send(frame, frame.length);
    }

    @Override
    public boolean Receive(byte[] input) {
        // filter by EtherType and destination (allow broadcast or any for robustness)
        if (input.length < 14) return false;
        int et = ((input[12] & 0xFF) << 8) | (input[13] & 0xFF);
        if (et != (etherType & 0xFFFF)) return false;

        // decapsulate and deliver up
        byte[] payload = Arrays.copyOfRange(input, 14, input.length);
        for (BaseLayer upper : uppers) {
            upper.Receive(payload);
        }
        return true;
    }
}
