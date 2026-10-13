# Minecraft packet registry

`src/main/resources/registry/packet_ids/index.json` lists 32 local client
versions. Each `<version>.json` contains Java Edition
packet IDs and available wire layouts. `schema.json` describes the file format.

`additional_protocols.json` supplies complete master-name ID tables for the other
23 release protocols. Together these resources cover all 50 exact release
protocols in `src/main/resources/protocolversions`, from 1.8 (47) through 26.3 (777).
Several Minecraft releases share a protocol; their packet IDs are checked for
agreement. Snapshot protocols, pre-1.8 protocols and future releases are excluded.

## Runtime lookup

`de.jpx3.intave.packet.PacketIdRegistry` loads the bundled registries and master
translations once, on first use, without a server or network access:

```java
int rotation = PacketIdRegistry.serverbound().id(ConnectionState.PLAY, "ROTATION", 47); // 5
int position = PacketIdRegistry.clientbound().id(ConnectionState.PLAY, "POSITION", 47); // 8
Set<Integer> protocols = PacketIdRegistry.serverbound().supportedProtocolVersions();
```

Select the direction once; each lookup takes `(connectionState, typeName,
protocolVersion)`. Master names are case-sensitive. An absent packet or connection
state returns `-1`. An unsupported exact protocol throws `IllegalArgumentException`;
there is no nearest-version fallback. The shared registries and exposed protocol
set are immutable. The generator and `PacketIdRegistryTest` check the bundled
data; runtime loading only checks resource availability and schema compatibility.

## Native class registries

Look up an ID with `states.<state>.<direction>.ids.<name>`, for example, in
`26.3.json`:

```text
states.play.serverbound.ids.ServerboundKeepAlivePacket = 28
states.play.clientbound.ids.ClientboundBlockChangedAckPacket = 4
```

IDs are decimal integers, scoped to the exact protocol, connection state and
direction. `serverbound` means client to server; `clientbound` means server to
client. All registered states and both directions are included. The empty
handshaking/clientbound table is intentional. The older, unframed legacy server
list ping is outside these registrations.

Names are the mapped Java packet class names from that version's client source,
including nested classes such as `ServerboundMovePlayerPacket.Pos`. They can
change between mapping generations and are not ProtocolLib enum names. Select a
file by its exact `minecraftVersion` or `protocolVersion`; never use the nearest
version when decoding packets. Native class names and layouts are supplied only
for versions in the index; the supplement supplies IDs for the remaining protocols.

The bundle delimiter has a wire ID. The aggregate bundle object has none and is
excluded.

## Master packet names

`type_to_name.json` translates stable names to each version's mapped class name:
`states.<state>.<direction>.<masterName>.<version>`. For example:

```json
"ROTATION": {
  "1.8.9": "C03PacketPlayer.C05PacketPlayerLook",
  "1.9": "CPacketPlayer.C05PacketPlayerLook",
  "1.10.2": "CPacketPlayer.Rotation",
  "1.14.4": "CPlayerPacket.RotationPacket",
  "26.3": "ServerboundMovePlayerPacket.Rot"
}
```

This excerpt is from `states.play.serverbound.ROTATION`; the file includes every
indexed source version where that packet exists. Resolve the class for the exact
version, then use it in that version's `ids` or `layouts` map. Every registered
class has exactly one master name in its state and direction. A missing version
means that master packet does not exist there; it is not a fallback instruction.

The names take inspiration from ProtocolLib's `PacketType` and Intave's `PacketId`.
Player movement uses `POSITION`, `ROTATION`, `POSITION_ROTATION`, and `GROUND`.
Shared packets use consistent names across states, such as `KEEP_ALIVE`,
`CLIENT_SETTINGS`, and `RESOURCE_PACK_PUSH`. Split packets remain distinct, such
as `RECIPE_BOOK_DATA`, `RECIPE_SETTINGS`, and `RECIPE_DISPLAYED`.

Translations are explicit because a class's role can change between mappings.
For example, 1.9's `CPacketPlayerTryUseItem` is `USE_ITEM_ON`, while 1.10.2's
class with that spelling is `USE_ITEM`; 1.9's `USE_ITEM` is instead
`CPacketPlayerBlockPlacement`. Clientbound teleport/position packets also remain
separate from `LOOK_AT`, and entity sounds from positional sounds.

The source folder called `client/1.9.4` actually declares `VERSION_STRING = "1.9"`
and `NETWORK_PROTOCOL_VERSION = 107` in `RealmsSharedConstants`. Its registry and
translations are therefore attributed to 1.9. Protocol 110 (1.9.3/1.9.4) comes from
the supplement: it has no clientbound `UPDATE_SIGN` packet, so later IDs differ.
The misleading 1.9 class `SPacketUpdateEntityNBT` serializes boss-bar data and maps
to `BOSS`.

The translation file is curated, not rewritten by the generator. When adding a
client version, add its master-name translations too. Both generation and
`--check` validate complete, unambiguous coverage. To validate only the names
against the committed JSON, without any ignored client sources, run:

```powershell
python gradle/packet-registry/generate.py --check-names
```

## Layouts

`states.<state>.<direction>.layouts.<name>.fields` lists the complete payload's
fields in serialization order. Names come from the mapped source fields or record
accessors. Packet ID, packet length, compression and encryption framing are
excluded. An empty `fields` array means a verified empty payload. A missing layout
means **unresolved**, never an empty packet. The file's `layoutCoverage` is therefore
`partial` even though its ID tables cover every registered packet.

The generator accepts simple scalar writes, same-file inherited writes, scalar
`StreamCodec.composite` definitions and unit codecs. Conditional fields, bit masks,
collections, NBT, registry-dependent values and other complex codecs remain
unresolved. Layouts describe encoding structure; semantic units, enum values,
validation rules and the meaning of flags require the packet's source.

| Type | Wire representation |
| --- | --- |
| `boolean` | One byte, written as 0 or 1 |
| `byte`, `short`, `int`, `long` | 8, 16, 32, 64 bits; multi-byte values are big-endian |
| `float`, `double` | IEEE 754 binary32/binary64, big-endian |
| `varint`, `varlong` | Minecraft variable-length 32/64-bit integer |
| `uuid` | Two big-endian 64-bit values, most significant first |
| `utf8` | VarInt byte length followed by UTF-8 bytes |

Integer types describe the primitive write width; a receiver may interpret those
bits as unsigned. `maxLength`, when present, is the explicit string character
limit passed to the writer. Omission does not imply an unlimited string.

## Regeneration

Run from the repository root with Python 3.10 or later:

```powershell
python gradle/packet-registry/generate.py
python gradle/packet-registry/generate.py --check
```

Generation uses the indexed versions, the ignored `client/<version>/src/main/java` or `src/minecraft`
sources and the exact release mappings in `src/main/resources/protocolversions`.
It does not download data, run clients, or require client sources during the normal
Gradle build. All registrations must be available; for 26.3 the handshake, status,
login and configuration protocol classes were additionally decompiled from the
official cached client JAR (SHA-1 `e877b6a07acd633fb3bb475002175cec036e7b87`) using
Vineflower 1.12.0. No classes from a neighboring version are substituted.

`sourceRoot` and `registrationSources` record the inputs. Source SHA-256 hashes use
UTF-8 with normalized LF line endings. Layout `source` paths are relative to
`sourceRoot`; the bundle delimiter's empty layout follows directly from its
registration. Regenerate after changing source mappings or adding a client
version. `--check` compares all generated data with the committed resources and
fails for missing sources, unknown registrations, duplicate names or stale files.

## Supplemental release IDs

The supplement contains full tables, not diffs. It is imported from
[minecraft-data at dd01127457231e7da629d645b3c6738068018eb0](https://github.com/PrismarineJS/minecraft-data/tree/dd01127457231e7da629d645b3c6738068018eb0).
Each entry records its exact release aliases, source path and SHA-256.
`gradle/packet-registry/minecraft_data_names.json` translates that dataset's packet
symbols to this registry's master names. No native Java class names or layouts
are inferred for these releases.

With that revision checked out locally, regenerate or verify using:

```powershell
python gradle/packet-registry/import_protocols.py --source <minecraft-data-checkout>
python gradle/packet-registry/import_protocols.py --source <minecraft-data-checkout> --check
```

The importer checks 1,515 overlapping local-client IDs before writing. Supplemental
IDs from 1.13 through 1.19.3 were also checked against the packet registration
tables in the build's pinned Botcraft revision `3e54657a3ccd250657c887baa46a0e43cb0515bf`.
The two explicit shared-ID sources, 1.9.1 and 1.14.2, are confirmed by ViaVersion's
[1.9 to 1.9.1/1.9.2 adapter](https://github.com/ViaVersion/ViaVersion/blob/master/common/src/main/java/com/viaversion/viaversion/protocols/v1_9to1_9_1/Protocol1_9To1_9_1.java)
and [1.14.1 to 1.14.2 adapter](https://github.com/ViaVersion/ViaVersion/blob/master/common/src/main/java/com/viaversion/viaversion/protocols/v1_14_1to1_14_2/Protocol1_14_1To1_14_2.java).
Sharing IDs does not imply identical payload layouts.

`PacketIdRegistryTest` checks every bundled translation and supplemental ID,
release coverage, direction/state isolation, removed packets and known ID shifts.
Run it without the ignored source checkouts:

```powershell
.\gradlew.bat test --tests de.jpx3.intave.packet.PacketIdRegistryTest
```

The resources are packaged automatically by Gradle's main resource source set.
