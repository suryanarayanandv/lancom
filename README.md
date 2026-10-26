# LanCom - LAN Communication

## Goal

Build a **LAN-based audio relay**: one device captures audio and streams it over the local network, and another device plays it through **Bluetooth headphones**.

> Source: `Bluetooth_Audio_Relay_Network_Prerequisite_Guide.docx`

---

## Project Roadmap

Finish each phase, and build a small prototype for it, before you start the next one.

- [x] **Phase 1** - Capture and play local audio (Java Sound API)
- [x] **Phase 2** - Stream raw PCM audio over LAN
- [x] **Phase 3** - Add buffering (jitter buffer) and latency control
- [ ] **Phase 4** - Integrate Opus compression
- [ ] **Phase 5** - Automatic peer discovery using JmDNS (mDNS / DNS-SD)
- [ ] **Phase 6** - Output to Bluetooth headphones (A2DP output device selection)
- [ ] **Phase 7** - Add relay nodes and routing
- [ ] **Phase 8** - Polish UI, security and configuration

---

## High-Level TODOs

- [ ] Peer discovery & connection (JmDNS, Bluetooth?)
- [x] Microphone and speaker handling
- [ ] Audio / message communication (UDP, TCP, WebRTC?)
- [ ] Call control logic

### UI

- [ ] List discovered peers
- [ ] Click action: connect to peer
- [ ] Call control buttons (hang up, mute, etc.)
- [ ] Call status display
- [ ] Message input and display

### Packaging

- [ ] Runnable `.exe`

---

## Prerequisite Learning Guide

Suggested learning order:

| # | Topic | Key Concepts | Resources |
|---|-------|--------------|-----------|
| 1 | Computer Networking | TCP vs UDP, sockets, latency, jitter, packet loss, multicast, mDNS | [Beej's Guide](https://beej.us/guide/bgnet/), [MDN Client-Server Overview](https://developer.mozilla.org/en-US/docs/Learn_web_development/Extensions/Server-side/First_steps/Client-Server_overview) |
| 2 | Java Networking | Java NIO, `SocketChannel`, `ByteBuffer`, selectors | [java.nio docs](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/package-summary.html) |
| 3 | Audio Fundamentals | PCM, sample rate, bit depth, stereo vs mono, buffers, latency | [Opus docs](https://opus-codec.org/docs/), [Windows Core Audio APIs](https://learn.microsoft.com/en-us/windows/win32/coreaudio/core-audio-apis-in-windows-vista) |
| 4 | Audio Capture & Playback | Java Sound API first, then WASAPI / PipeWire / CoreAudio | [Java Sound tutorial](https://docs.oracle.com/javase/tutorial/sound/) |
| 5 | Audio Compression | Why Opus is preferred for low-latency streaming | [opus-codec.org](https://opus-codec.org/) |
| 6 | Service Discovery | mDNS / DNS-SD, JmDNS | [JmDNS](https://jmdns.sourceforge.net/), [Apple Bonjour](https://developer.apple.com/bonjour/) |
| 7 | Bluetooth Audio | A2DP, HFP, Bluetooth Classic, output device selection | [Bluetooth specs](https://www.bluetooth.com/specifications/specs/) |
| 8 | Concurrency | Threads, producer-consumer, queues, synchronization | [Java Concurrency tutorial](https://docs.oracle.com/javase/tutorial/essential/concurrency/) |
| 9 | Distributed Systems Basics | Heartbeats, retries, buffering, reconnection, sequencing | [Patterns of Distributed Systems](https://martinfowler.com/articles/patterns-of-distributed-systems/) |

### Reading Strategy

Don't try to learn everything at once. Finish one phase, build a small prototype after each topic, and the full relay network will come together from those prototypes.

---

## Build & Run

Requires Java 11+ and Maven.

```bash
mvn clean verify                      # compile + run all unit and integration tests
java -jar target/lancom-1.jar --help
```

### Usage

```bash
# 1. On each machine, list the devices that support the chosen format
java -jar target/lancom-1.jar devices

# 2. On the machine with the Bluetooth headphones (pair and connect them in the OS first)
java -jar target/lancom-1.jar receive --device "Headphones" --allow 192.168.1.20

# 3. On the capturing machine
java -jar target/lancom-1.jar send --target 192.168.1.30:50005
```

Audio options (`--sample-rate`, `--channels`, `--frame-ms`) must match on both ends. Mismatched packets are dropped and a warning is logged. Use `-v` for debug logging. Stats are logged every 10 seconds.

Exit codes: `0` success, `1` runtime failure (device or network), `2` invalid usage.

### Architecture

| Package | Responsibility |
|---------|----------------|
| `protocol` | Versioned binary wire format (`PacketCodec`) and immutable `AudioPacket`. Decoding treats every datagram as untrusted. |
| `buffer` | `JitterBuffer`: reordering, loss concealment (silence), underrun re-buffering, overflow trimming, wrap-safe sequence numbers. |
| `audio` | `AudioSource` / `AudioSink` abstractions with Java Sound implementations and device lookup. |
| `net` | `AudioSender` / `AudioReceiver` over UDP, with a shared `StreamNode` lifecycle (single start, idempotent close, failure propagation). |
| `config` | Immutable, validated `AudioSettings`, `SenderConfig`, `ReceiverConfig`. |
| `cli` | Strict argument parser and config factory. |
| `metrics` | Thread-safe `StreamStats` counters. |

### Security notes

- Restrict receivers with `--allow`. Without it, any host on the LAN can stream to the receiver.
- The receiver locks onto one stream and ignores competing streams until the active one has been idle for `--stream-timeout-ms`.
- Every datagram field is range-checked, and declared lengths must match the real datagram size.
- Audio travels **unencrypted and unauthenticated**, and an allowlist alone doesn't stop IP spoofing. Encryption and authentication belong to Phase 8 and are not implemented yet.
