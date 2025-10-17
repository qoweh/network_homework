# Layered Ethernet Chat (network_w7)

A simple layered chat over raw Ethernet frames using jNetPcap 2.x. Messages are sent as Ethernet frames with EtherType 0xFFFF. The app showcases a clean separation of concerns across layers:

- ChatApp Layer: converts UI text to bytes and vice versa
- Ethernet Layer: encapsulates/decapsulates Ethernet frames, EtherType filter
- Physical Layer: opens the NIC via jNetPcap, sends frames, and captures frames

## Project layout

- `src/main/java/com/demo/BasicChatApp.java` — Swing UI; wires layers and logs [SENT]/[RCVD]
- `src/main/java/com/demo/ChatAppLayer.java` — Chat application layer (BaseLayer)
- `src/main/java/com/demo/EthernetLayer.java` — Ethernet layer (BaseLayer)
- `src/main/java/com/demo/PhysicalLayer.java` — Physical layer backed by jNetPcap (BaseLayer)
- `src/main/java/com/demo/BaseLayer.java` — Layer interface (provided)

## Requirements

- Java 21
- jNetPcap wrapper JAR and native libraries (.dylib on macOS)
- macOS Packet Capture permission (System Settings > Privacy & Security > Packet Capture) or run as root (not recommended)
- libpcap installed (e.g., `brew install libpcap`)

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

1. Select a network device from the combo box.
2. Set destination MAC (use peer NIC MAC or FF:FF:FF:FF:FF:FF for broadcast).
3. Click "설정" to activate capturing/sending.
4. Type a message and click "전송".
5. On the peer machine, you should see [RCVD] <message>.

Notes:
- The UI logs your detected MAC. If it shows all zeros, grant Packet Capture permission and/or test using broadcast first.
- Messages are sent with EtherType 0xFFFF and padded to minimum 60 bytes (without FCS).

## Wireshark / tcpdump filters

- Wireshark display filter:
  - `eth.type == 0xFFFF`
- tcpdump example:

```bash
sudo tcpdump -i en0 -XX 'ether proto 0xffff'
```

## Troubleshooting

- Error loading native library: verify `-Djava.library.path` points to directory with jNetPcap `.dylib` files and that `libpcap` is installed.
- No [RCVD] appears:
  - Ensure both machines are on the same L2 segment (same switch/VLAN).
  - Try broadcast destination MAC first.
  - Make sure Packet Capture permission is granted to your Terminal/IDE.
  - Confirm frames are on the wire using Wireshark with `eth.type == 0xFFFF`.

## Architecture

```text
ChatApp (UI)  <--onReceive-->  ChatAppLayer  <-->  EthernetLayer  <-->  PhysicalLayer(jNetPcap)
                                  |                                 |
                              Send(byte[])                      sendPacket()/dispatch()
```

Each layer implements `BaseLayer` and communicates with adjacent layers through Send/Receive.
