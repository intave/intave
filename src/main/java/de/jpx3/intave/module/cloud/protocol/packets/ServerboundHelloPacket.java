package de.jpx3.intave.module.cloud.protocol.packets;

import de.jpx3.intave.module.cloud.protocol.BinaryPacket;
import de.jpx3.intave.module.cloud.protocol.PacketSpecification;
import de.jpx3.intave.module.cloud.protocol.listener.Serverbound;

import java.io.DataInput;
import java.io.DataOutput;
import java.util.*;
import java.util.stream.Collectors;

import static de.jpx3.intave.module.cloud.protocol.Direction.SERVERBOUND;

public final class ServerboundHelloPacket extends BinaryPacket<Serverbound> {
  private String identifierHead;
  private String identifierChecksum;
  private String jarFileHash;
  private String hwidHead;
  private String startId;
  private List<String> supportedEncryptionAlgorithms = new ArrayList<>();
  private List<Integer> supportedEncryptionKeySizes = new ArrayList<>();
  private List<String> supportedCompressionAlgorithms = new ArrayList<>();
  private List<String> supportedHMACAlgorithms = new ArrayList<>();
  private final Map<String, PacketSpecification> clientboundProtocol = new HashMap<>();
  private final Map<String, PacketSpecification> serverboundProtocol = new HashMap<>();

  public ServerboundHelloPacket() {
    super(SERVERBOUND, "HELLO", "0");
  }

  public ServerboundHelloPacket(
    String identifierHead, String identifierChecksum, String jarFileHash, String hwidHead, String startId,
    List<String> supportedEncryptionAlgorithms, List<Integer> supportedEncryptionKeySizes,
    List<String> supportedCompressionAlgorithms, List<String> supportedHMACAlgorithms,
    Map<String, ? extends PacketSpecification> clientboundProtocol,
    Map<String, ? extends PacketSpecification> serverboundProtocol
  ) {
    super(SERVERBOUND, "HELLO", "0");
    this.identifierHead = identifierHead;
    this.identifierChecksum = identifierChecksum;
    this.jarFileHash = jarFileHash;
    this.hwidHead = hwidHead;
    this.startId = startId;
    this.supportedEncryptionAlgorithms = supportedEncryptionAlgorithms;
    this.supportedEncryptionKeySizes = supportedEncryptionKeySizes;
    this.supportedCompressionAlgorithms = supportedCompressionAlgorithms;
    this.supportedHMACAlgorithms = supportedHMACAlgorithms;
    this.clientboundProtocol.putAll(clientboundProtocol);
    this.serverboundProtocol.putAll(serverboundProtocol);
  }

  @Override
  public void serialize(DataOutput buffer) {
    try {
      buffer.writeUTF(identifierHead);
      buffer.writeUTF(identifierChecksum);
      buffer.writeUTF(jarFileHash);
      buffer.writeUTF(hwidHead);
      buffer.writeUTF(startId);
      buffer.writeUTF(String.join(",", supportedEncryptionAlgorithms));
      buffer.writeUTF(supportedEncryptionKeySizes.stream().map(Object::toString).collect(Collectors.joining(",")));
      buffer.writeUTF(String.join(",", supportedCompressionAlgorithms));
      buffer.writeUTF(String.join(",", supportedHMACAlgorithms));
      buffer.writeInt(clientboundProtocol.size());
      for (Map.Entry<String, PacketSpecification> entry : clientboundProtocol.entrySet()) {
        buffer.writeUTF(entry.getKey());
        entry.getValue().serialize(buffer);
      }
      buffer.writeInt(serverboundProtocol.size());
      for (Map.Entry<String, PacketSpecification> entry : serverboundProtocol.entrySet()) {
        buffer.writeUTF(entry.getKey());
        entry.getValue().serialize(buffer);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void deserialize(DataInput buffer) {
    try {
      identifierHead = buffer.readUTF();
      identifierChecksum = buffer.readUTF();
      jarFileHash = buffer.readUTF();
      hwidHead = buffer.readUTF();
      startId = buffer.readUTF();
      supportedEncryptionAlgorithms = Arrays.asList(buffer.readUTF().split(","));
      supportedEncryptionKeySizes = Arrays.stream(buffer.readUTF().split(",")).map(Integer::parseInt).collect(Collectors.toList());
      supportedCompressionAlgorithms = Arrays.asList(buffer.readUTF().split(","));
      supportedHMACAlgorithms = Arrays.asList(buffer.readUTF().split(","));
      int size = buffer.readInt();
      for (int i = 0; i < size; i++) {
        clientboundProtocol.put(buffer.readUTF(), PacketSpecification.from(buffer));
      }
      size = buffer.readInt();
      for (int i = 0; i < size; i++) {
        serverboundProtocol.put(buffer.readUTF(), PacketSpecification.from(buffer));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public String identifierHead() {
    return identifierHead;
  }

  public String identifierChecksum() {
    return identifierChecksum;
  }

  public String jarFileHash() {
    return jarFileHash;
  }

  public String hwidHead() {
    return hwidHead;
  }

  public String startId() {
    return startId;
  }

  public List<String> supportedEncryptionAlgorithms() {
    return supportedEncryptionAlgorithms;
  }

  public List<String> supportedCompressionAlgorithms() {
    return supportedCompressionAlgorithms;
  }

  public List<String> supportedHMACAlgorithms() {
    return supportedHMACAlgorithms;
  }

  public Map<String, PacketSpecification> protocol() {
    return clientboundProtocol;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String identifierHead;
    private String identifierChecksum;
    private String jarFileHash;
    private String hwidHead;
    private String startId;
    private List<String> supportedEncryptionAlgorithms = new ArrayList<>();
    private List<Integer> supportedEncryptionKeySizes = new ArrayList<>();
    private List<String> supportedCompressionAlgorithms = new ArrayList<>();
    private List<String> supportedHMACAlgorithms = new ArrayList<>();
    private final Map<String, PacketSpecification> clientboundProtocol = new HashMap<>();
    private final Map<String, PacketSpecification> serverboundProtocol = new HashMap<>();

    public Builder identifierHead(String identifierHead) {
      this.identifierHead = identifierHead;
      return this;
    }

    public Builder identifierChecksum(String identifierChecksum) {
      this.identifierChecksum = identifierChecksum;
      return this;
    }

    public Builder jarFileHash(String jarFileHash) {
      this.jarFileHash = jarFileHash;
      return this;
    }

    public Builder hwid(String hwidHead) {
      this.hwidHead = hwidHead;
      return this;
    }

    public Builder gameId(String startId) {
      this.startId = startId;
      return this;
    }

    public Builder supportedEncryptionAlgorithms(List<String> supportedEncryptionAlgorithms) {
      this.supportedEncryptionAlgorithms = supportedEncryptionAlgorithms;
      return this;
    }

    public Builder supportedEncryptionKeySizes(List<Integer> supportedEncryptionKeySizes) {
      this.supportedEncryptionKeySizes = supportedEncryptionKeySizes;
      return this;
    }

    public Builder supportedCompressionAlgorithms(List<String> supportedCompressionAlgorithms) {
      this.supportedCompressionAlgorithms = supportedCompressionAlgorithms;
      return this;
    }

    public Builder supportedHMACAlgorithms(List<String> supportedHMACAlgorithms) {
      this.supportedHMACAlgorithms = supportedHMACAlgorithms;
      return this;
    }

    public Builder clientboundProtocol(Map<String, ? extends PacketSpecification> protocol) {
      this.clientboundProtocol.putAll(protocol);
      return this;
    }

    public Builder serverboundProtocol(Map<String, ? extends PacketSpecification> protocol) {
      this.serverboundProtocol.putAll(protocol);
      return this;
    }

    public ServerboundHelloPacket build() {
      if (identifierHead == null) {
        throw new IllegalStateException("identifierHead is null");
      }
      if (identifierChecksum == null) {
        throw new IllegalStateException("identifierChecksum is null");
      }
      if (jarFileHash == null) {
        throw new IllegalStateException("jarFileHash is null");
      }
      if (hwidHead == null) {
        throw new IllegalStateException("hwidHead is null");
      }
      if (startId == null) {
        throw new IllegalStateException("startId is null");
      }
      if (supportedEncryptionAlgorithms == null) {
        throw new IllegalStateException("supportedEncryptionAlgorithms is null");
      }
      if (supportedEncryptionKeySizes == null) {
        throw new IllegalStateException("supportedEncryptionKeySizes is null");
      }
      if (supportedCompressionAlgorithms == null) {
        throw new IllegalStateException("supportedCompressionAlgorithms is null");
      }
      if (supportedHMACAlgorithms == null) {
        throw new IllegalStateException("supportedHMACAlgorithms is null");
      }
      if (clientboundProtocol.isEmpty()) {
        throw new IllegalStateException("clientboundProtocol is empty");
      }
      if (serverboundProtocol.isEmpty()) {
        throw new IllegalStateException("serverboundProtocol is empty");
      }
      return new ServerboundHelloPacket(
        identifierHead, identifierChecksum, jarFileHash, hwidHead, startId,
        supportedEncryptionAlgorithms, supportedEncryptionKeySizes,
        supportedCompressionAlgorithms, supportedHMACAlgorithms,
        clientboundProtocol, serverboundProtocol
      );
    }
  }
}
