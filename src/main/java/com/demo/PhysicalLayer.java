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

public class PhysicalLayer implements BaseLayer, Runnable {
    private final String name = "Physical";
    private BaseLayer underLayer; // none
    private final List<BaseLayer> uppers = new ArrayList<>(); // Ethernet

    private volatile Pcap pcap;
    private volatile Thread rxThread;

    public boolean open(PcapIf device, boolean promiscuous, long timeoutMillis) throws PcapException {
        close();
        int snaplen = 64 * 1024;
        this.pcap = Pcap.openLive(device, snaplen, promiscuous, timeoutMillis, TimeUnit.MILLISECONDS);
        try { this.pcap.setDirection(PcapDirection.INOUT); } catch (Throwable ignore) {}
        // start receive thread
        rxThread = new Thread(this, "phys-rx");
        rxThread.setDaemon(true);
        rxThread.start();
        return true;
    }

    public void close() {
        if (rxThread != null) {
            try { rxThread.interrupt(); } catch (Exception ignore) {}
            rxThread = null;
        }
        if (pcap != null) {
            try { pcap.close(); } catch (Exception ignore) {}
            pcap = null;
        }
    }

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
        if (pcap == null) return false;
        try {
            pcap.sendPacket(ByteBuffer.wrap(input, 0, length));
            return true;
        } catch (PcapException e) {
            return false;
        }
    }

    @Override
    public boolean Receive() { return false; }

    @Override
    public boolean Receive(byte[] input) { return false; }

    @Override
    public void run() {
        if (pcap == null) return;
        org.jnetpcap.PcapHandler.OfByteBuffer handler = (header, buffer, user) -> {
            int pos = buffer.position();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            buffer.position(pos);
            for (BaseLayer upper : uppers) {
                upper.Receive(data);
            }
        };
        while (!Thread.currentThread().isInterrupted()) {
            try {
                pcap.dispatch(64, handler, null);
            } catch (PcapException ex) {
                break;
            }
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}
