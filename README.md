# Layered Ethernet Chat (network_w7)

A simple layered chat over raw Ethernet frames using jNetPcap 2.3.1 (JDK21). Messages are sent as Ethernet frames with EtherType 0xFFFF. The app showcases a clean separation of concerns across layers:

- **ChatApp Layer**: converts UI text to bytes and vice versa; strips trailing zeros on receive
- **Ethernet Layer**: encapsulates/decapsulates Ethernet frames with EtherType filter, self-reception filtering, and destination MAC filtering (unicast/broadcast)
- **Physical Layer**: opens the NIC via jNetPcap, sends frames, and captures frames with optimized snaplen and low-latency dispatch

## Key Features

- **Self-reception filtering**: frames sent by this host are dropped at the Ethernet layer to prevent echo
- **Destination filtering**: only frames destined to this host or broadcast are accepted; other unicast traffic is ignored
- **Performance tuning**:
  - Non-promiscuous mode by default to reduce noise
  - 200 ms read timeout for responsive receive
  - Single-packet dispatch per loop iteration for minimal latency
  - Reduced snaplen (2048 bytes) for lower overhead
- **Broadcast default**: if destination MAC is left empty, it defaults to FF:FF:FF:FF:FF:FF for easy peer discovery

## Project Layout

- `src/main/java/com/demo/BasicChatApp.java` — Swing UI; wires layers and logs [SENT]/[RCVD]
- `src/main/java/com/demo/ChatAppLayer.java` — Chat application layer (BaseLayer)
- `src/main/java/com/demo/EthernetLayer.java` — Ethernet layer with MAC filtering (BaseLayer)
- `src/main/java/com/demo/PhysicalLayer.java` — Physical layer backed by jNetPcap (BaseLayer, Runnable)
- `src/main/java/com/demo/BaseLayer.java` — Layer interface (provided)

## Requirements

- **Java 21** with preview features enabled
- **jNetPcap wrapper 2.3.1 (JDK21)** JAR and native libraries (.dylib on macOS, .so on Linux, .dll on Windows)
- **macOS**: Packet Capture permission (System Settings > Privacy & Security > Packet Capture) or run as root (not recommended)
- **libpcap** installed (e.g., `brew install libpcap` on macOS)

## Build

```bash
mvn -DskipTests compile
```

## Run

Replace the `-Djava.library.path` with the folder that holds the jNetPcap native .dylib files.

```bash
java --enable-preview \
     --enable-native-access=ALL-UNNAMED \
     -cp target/classes:lib/jnetpcap-wrapper-2.3.1-jdk21.jar \
     -Djava.library.path=/path/to/jnetpcap/native/libs \
     com.demo.BasicChatApp
```

## Usage

1. **Select a network device** from the combo box. The UI will display your MAC address.
2. **Set destination MAC**:
   - Leave empty to default to broadcast (FF:FF:FF:FF:FF:FF) for easy testing
   - Or enter the peer NIC MAC address for directed unicast (e.g., `AA:BB:CC:DD:EE:FF`)
3. **Click "설정"** to activate capturing/sending. The UI will confirm the device is active.
4. **Type a message** and click "전송".
5. **On the peer machine**, you should see `[RCVD] <message>` in the text area.

### Important Notes

- The UI logs your detected MAC address. If it shows all zeros, grant Packet Capture permission and/or test using broadcast first.
- Messages are sent with EtherType 0xFFFF and padded to minimum 60 bytes (without FCS).
- **Self-reception filtering**: your own sent frames won't appear as `[RCVD]`.
- **Destination filtering**: only frames destined to your MAC or broadcast are accepted; other unicast traffic is ignored.
- **Non-promiscuous mode by default**: reduces noise and improves performance; only frames addressed to this host or broadcast are captured.

## Wireshark / tcpdump Filters

- Wireshark display filter:
  - `eth.type == 0xFFFF`
- tcpdump example:

```bash
sudo tcpdump -i en0 -XX 'ether proto 0xffff'
```


## Architecture

```text
ChatApp (UI)  <--onReceive-->  ChatAppLayer  <-->  EthernetLayer  <-->  PhysicalLayer(jNetPcap)
                                  |                      |                      |
                              Send(byte[])        Send(byte[])          sendPacket(ByteBuffer)
                              Receive(byte[])     Receive(byte[])       dispatch() -> handler
                                                      |
                                                  MAC filtering:
                                                  - Drop self-sent frames
                                                  - Accept dest==me or broadcast
                                                  - EtherType filter (0xFFFF)
```

Each layer implements `BaseLayer` and communicates with adjacent layers through `Send`/`Receive`. The Ethernet layer performs MAC-based filtering to prevent self-reception and reduce noise. The Physical layer runs a background receive thread that dispatches captured packets upward.

## Performance Characteristics

- **Latency**: ~1-5 ms end-to-end on wired Ethernet with default settings (200 ms read timeout, dispatch(1))
- **Throughput**: Limited by single-packet dispatch; increase dispatch count to 32-64 for burst scenarios
- **CPU usage**: Low in non-promiscuous mode; higher if promiscuous is enabled or read timeout is reduced below 50 ms
- **Frame overhead**: Minimum 60 bytes (14-byte Ethernet header + payload + padding), plus 4-byte FCS added by NIC

## License

Educational project for network protocol stack learning. Use at your own risk.
