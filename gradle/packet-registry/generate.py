#!/usr/bin/env python3
"""Extract packet IDs and conservative wire layouts from the local client sources."""

import argparse
import hashlib
import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "src/main/resources"
OUTPUT = RESOURCES / "registry/packet_ids"
STATES = ("handshaking", "status", "login", "configuration", "play")
FLOWS = ("serverbound", "clientbound")
IDENTIFIER = r"[A-Za-z_$][\w$]*"
CLASS_NAME = rf"{IDENTIFIER}(?:\.{IDENTIFIER})*"
WRITES = {
    "writeBoolean": "boolean",
    "writeByte": "byte",
    "writeShort": "short",
    "writeInt": "int",
    "writeLong": "long",
    "writeFloat": "float",
    "writeDouble": "double",
    "writeVarInt": "varint",
    "writeVarIntToBuffer": "varint",
    "writeVarLong": "varlong",
    "writeUuid": "uuid",
    "writeUniqueId": "uuid",
    "writeUtf": "utf8",
    "writeString": "utf8",
}
SCALAR_CODECS = {
    "BOOL": "boolean", "BYTE": "byte", "SHORT": "short", "INT": "int",
    "LONG": "long", "FLOAT": "float", "DOUBLE": "double", "VAR_INT": "varint",
    "VAR_LONG": "varlong", "STRING_UTF8": "utf8",
}


def without_comments(text):
    # Preserve quoted strings, including comment-like text inside them.
    return re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/',
                  lambda m: " " if m[0].startswith(("//", "/*")) else m[0],
                  text, flags=re.S)


def closing(text, start):
    pairs = {"(": ")", "{": "}", "[": "]"}
    stack = [pairs[text[start]]]
    quote = None
    escaped = False
    for offset in range(start + 1, len(text)):
        char = text[offset]
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
        elif char in "\"'":
            quote = char
        elif char in pairs:
            stack.append(pairs[char])
        elif char in ")}]":
            if char != stack.pop():
                raise ValueError("Unbalanced Java source")
            if not stack:
                return offset
    raise ValueError("Unterminated Java expression")


def arguments(text):
    result = []
    start = offset = 0
    while offset < len(text):
        if text[offset] in "({[":
            offset = closing(text, offset)
        elif text[offset] == ",":
            result.append(text[start:offset].strip())
            start = offset + 1
        offset += 1
    result.append(text[start:].strip())
    return result


def calls(text, names):
    for match in re.finditer(r"\.((?:" + "|".join(names) + r"))\s*\(", text):
        start = match.end() - 1
        yield match[1], arguments(text[start + 1:closing(text, start)])


def class_source(root, registration, name):
    text = registration.read_text(encoding="utf-8")
    imports = dict((item.rsplit(".", 1)[1], item) for item in
                   re.findall(r"^import (net\.minecraft\.[\w.]+);", text, re.M))
    if name.startswith("net."):
        parts = name.split(".")
        first = next(i for i, part in enumerate(parts) if part[0].isupper())
        short = ".".join(parts[first:])
        qualified = ".".join(parts[:first + 1])
    else:
        short = name
        outer = name.split(".")[0]
        package = re.search(r"^package ([\w.]+);", text, re.M)[1]
        qualified = imports.get(outer, package + "." + outer)
    return short, root / (qualified.replace(".", "/") + ".java")


def registrations(root):
    network = root / "net/minecraft/network"
    modern = network / "protocol/game/GameProtocols.java"
    result = {state: {flow: [] for flow in FLOWS} for state in STATES}
    sources = []

    def add(state, flow, registration, name, codec=None):
        short, path = class_source(root, registration, name)
        entries = result[state][flow]
        if any(entry[0] == short for entry in entries):
            raise ValueError(f"Duplicate packet: {state}/{flow}/{short}")
        entries.append((short, path, codec))

    if modern.exists():
        for state, directory, name in (
            ("handshaking", "handshake", "Handshake"), ("status", "status", "Status"),
            ("login", "login", "Login"), ("configuration", "configuration", "Configuration"),
            ("play", "game", "Game"),
        ):
            path = network / f"protocol/{directory}/{name}Protocols.java"
            text = without_comments(path.read_text(encoding="utf-8"))
            sources.append(path)
            flows = set()
            for match in re.finditer(
                r"\b(SERVERBOUND|CLIENTBOUND)(?:_\w+)?\s*=\s*ProtocolInfoBuilder\.\w+\s*\(", text
            ):
                flow = match[1].lower()
                if flow in flows:
                    raise ValueError(f"Duplicate flow in {path}: {flow}")
                flows.add(flow)
                start = match.end() - 1
                body = text[start + 1:closing(text, start)]
                if not re.match(r"\s*ConnectionProtocol\." + state.upper() + r"\s*,", body):
                    raise ValueError(f"Unexpected state in {path}")
                for method, args in calls(body, ("addPacket", "withBundlePacket")):
                    if method == "withBundlePacket":
                        packet = re.fullmatch(r"new (" + CLASS_NAME + r")\(\)", args[2])
                        if not packet:
                            raise ValueError(f"Unknown bundle registration: {args}")
                        add(state, flow, path, packet[1], "unit")
                    else:
                        codec = re.fullmatch(r"(" + CLASS_NAME + r")\.([A-Z_]+)", args[1])
                        if not codec:
                            raise ValueError(f"Unknown codec registration: {args}")
                        add(state, flow, path, codec[1], codec[2])
            expected = {"serverbound"} if state == "handshaking" else set(FLOWS)
            if flows != expected:
                raise ValueError(f"Missing protocol flow in {path}: {flows}")
    else:
        path = next((network / name for name in
                     ("EnumConnectionState.java", "ProtocolType.java", "ConnectionProtocol.java")
                     if (network / name).exists()), None)
        if path is None:
            raise ValueError(f"No packet registrations under {root}")
        sources.append(path)
        text = without_comments(path.read_text(encoding="utf-8"))
        for match in re.finditer(r"^\s*(HANDSHAKING|STATUS|LOGIN|CONFIGURATION|PLAY)\s*\(", text, re.M):
            state = match[1].lower()
            start = match.end() - 1
            end = closing(text, start)
            after = end + 1
            while text[after].isspace():
                after += 1
            if text[after] == "{":
                body = text[after + 1:closing(text, after)]
                packets = list(re.finditer(
                    r"\.registerPacket\(\s*(?:EnumPacketDirection|PacketDirection)\."
                    r"(SERVERBOUND|CLIENTBOUND),\s*(" + CLASS_NAME + r")\.class\s*\)", body
                ))
                if len(packets) != len(re.findall(r"\.registerPacket\s*\(", body)):
                    raise ValueError(f"Unrecognized registration in {path}: {state}")
                for packet in packets:
                    add(state, packet[1].lower(), path, packet[2])
            else:
                body = text[start + 1:end]
                flow = None
                count = 0
                for event in re.finditer(
                    r"(?:PacketFlow|PacketDirection)\.(SERVERBOUND|CLIENTBOUND)|"
                    r"\.(\w+)\(\s*(" + CLASS_NAME + r")\.class\s*,", body
                ):
                    if event[1]:
                        flow = event[1].lower()
                    elif event[2] == "withBundlePacket":
                        # The aggregate bundle has no wire ID. Its delimiter occupies this slot.
                        if "this.addPacket(BundleDelimiterPacket.class," not in text:
                            raise ValueError(f"Unknown bundle implementation in {path}")
                        add(state, flow, path, "BundleDelimiterPacket")
                        count += 1
                    else:
                        if flow is None:
                            raise ValueError(f"Packet outside a flow in {path}")
                        add(state, flow, path, event[3])
                        count += 1
                if count != len(re.findall(r"\.class\b", body)):
                    raise ValueError(f"Unrecognized registration in {path}: {state}")

    result = {state: flows for state, flows in result.items() if any(flows.values())}
    if not {"handshaking", "status", "login", "play"}.issubset(result):
        raise ValueError(f"Incomplete state registrations under {root}")
    for state, flows in result.items():
        for flow, entries in flows.items():
            if not entries and (state, flow) != ("handshaking", "clientbound"):
                raise ValueError(f"Empty {state}/{flow} under {root}")
    return result, sources


def class_body(text, name):
    body = text
    declaration = None
    for part in name.split("."):
        match = re.search(r"\b(?:class|record)\s+" + re.escape(part) + r"\b", body)
        if not match:
            return None, None
        start = body.find("{", match.end())
        declaration = body[match.start():start]
        body = body[start + 1:closing(body, start)]
    return body, declaration


def direct_writer(text, name, seen=()):
    if name in seen:
        return None
    body, declaration = class_body(text, name)
    if body is None:
        return None
    for match in re.finditer(
        r"\bvoid\s+(write|writePacketData)\(\s*(?:final\s+)?"
        r"(?:FriendlyByteBuf|RegistryFriendlyByteBuf|PacketBuffer|ByteBuf)\s+(" + IDENTIFIER + r")\s*\)"
        r"(?:\s+throws\s+[\w., ]+)?\s*\{", body
    ):
        # A nested packet's writer is not the outer packet's writer.
        prefix = body[:match.start()]
        if prefix.count("{") != prefix.count("}"):
            continue
        start = match.end() - 1
        method = body[start + 1:closing(body, start)].strip()
        # Some older MCP decompilations render the sole buffer argument as a snowman.
        method = method.replace("\u2603", match[2])
        fields = []
        for statement in method.split(";"):
            statement = statement.strip()
            if not statement:
                continue
            if re.fullmatch(r"super\." + match[1] + r"\(" + re.escape(match[2]) + r"\)", statement):
                parent = re.search(r"\bextends\s+(" + CLASS_NAME + r")", declaration)
                inherited = direct_writer(text, parent[1], (*seen, name)) if parent else None
                if inherited is None:
                    return None
                fields.extend(inherited[1])
                continue
            boolean = re.fullmatch(
                re.escape(match[2]) + r"\.writeByte\(\s*this\.(" + IDENTIFIER + r")\s*\?\s*1\s*:\s*0\s*\)",
                statement,
            )
            if boolean:
                fields.append({"name": boolean[1], "type": "boolean"})
                continue
            write = re.fullmatch(
                re.escape(match[2]) + r"\.(\w+)\(\s*this\.(" + IDENTIFIER + r")"
                r"(?:\s*,\s*(\d+))?\s*\)", statement
            )
            if not write or write[1] not in WRITES:
                return None
            field = {"name": write[2], "type": WRITES[write[1]]}
            if write[3]:
                if field["type"] != "utf8":
                    return None
                field["maxLength"] = int(write[3])
            fields.append(field)
        return match[1], fields
    return None


def wire_layout(root, name, path, codec):
    if codec == "unit":
        return {"fields": []}
    if not path.exists():
        return None
    text = without_comments(path.read_text(encoding="utf-8"))
    body, _ = class_body(text, name)
    if body is None:
        return None
    source = path.relative_to(root).as_posix()
    if codec:
        declaration = re.search(r"\b" + re.escape(codec) + r"\s*=\s*([^;]+);", body)
        if not declaration:
            return None
        expression = declaration[1].strip()
        if re.fullmatch(r"StreamCodec\.unit\(.*\)", expression, re.S):
            return {"source": source, "fields": []}
        composite = re.fullmatch(r"StreamCodec\.composite\((.*)\)", expression, re.S)
        if composite:
            parts = arguments(composite[1])
            if len(parts) % 2 != 1 or parts[-1] != name + "::new":
                return None
            fields = []
            for type_part, accessor in zip(parts[0:-1:2], parts[1:-1:2]):
                wire_type = SCALAR_CODECS.get(type_part.removeprefix("ByteBufCodecs."))
                field = re.fullmatch(re.escape(name) + r"::(" + IDENTIFIER + r")", accessor)
                if not type_part.startswith("ByteBufCodecs.") or wire_type is None or not field:
                    return None
                fields.append({"name": field[1], "type": wire_type})
            return {"source": source, "fields": fields}
        if not re.match(r"Packet\.codec\(\s*" + re.escape(name) + r"::write\s*,", expression):
            return None
    writer = direct_writer(text, name)
    if writer is None:
        return None
    return {"source": source, "fields": writer[1]}


def protocol_versions():
    result = {}
    for protocol, releases in re.findall(r"^release (\d+) is ([\w.,]+)$",
                                         (RESOURCES / "protocolversions").read_text(), re.M):
        for release in releases.split(","):
            if release in result:
                raise ValueError(f"Duplicate protocol release: {release}")
            result[release] = int(protocol)
    return result


def unique_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_keys)


def validate_master_names(master_names, registries):
    if set(master_names) != {"schemaVersion", "states"} or master_names["schemaVersion"] != 1:
        raise ValueError("Unsupported type_to_name.json format")
    states = master_names["states"]
    expected_states = {state for registry in registries.values() for state in registry["states"]}
    if not isinstance(states, dict) or set(states) != expected_states:
        raise ValueError("Master packet names must cover exactly the registered states")
    translated = {}
    masters = translations = 0
    for state, flows in states.items():
        if not isinstance(flows, dict) or set(flows) != set(FLOWS):
            raise ValueError(f"Master packet names require both directions: {state}")
        for flow, groups in flows.items():
            if not isinstance(groups, dict):
                raise ValueError(f"Invalid master packet names: {state}/{flow}")
            for master, versions in groups.items():
                location = f"{state}/{flow}/{master}"
                if not re.fullmatch(r"[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*", master):
                    raise ValueError(f"Invalid master packet name: {location}")
                if not isinstance(versions, dict) or not versions:
                    raise ValueError(f"Missing class translations: {location}")
                masters += 1
                for version, name in versions.items():
                    if version not in registries:
                        raise ValueError(f"Unknown packet version: {location}/{version}")
                    registered = registries[version]["states"].get(state, {}).get(flow, {}).get("ids", {})
                    if not isinstance(name, str) or name not in registered:
                        raise ValueError(f"Unregistered packet class: {location}/{version}: {name}")
                    key = (version, state, flow)
                    classes = translated.setdefault(key, {})
                    if name in classes:
                        raise ValueError(f"Ambiguous packet class: {location}/{version}: {name} "
                                         f"already belongs to {classes[name]}")
                    classes[name] = master
                    translations += 1
    for version, registry in registries.items():
        for state, flows in registry["states"].items():
            for flow, data in flows.items():
                missing = set(data["ids"]) - translated.get((version, state, flow), {}).keys()
                if missing:
                    raise ValueError(f"Missing master names for {version}/{state}/{flow}: {sorted(missing)}")
    return masters, translations


def generate(version, protocol):
    # This historical source folder is mislabeled; RealmsSharedConstants declares 1.9 / 107.
    source_version = "1.9.4" if version == "1.9" else version
    roots = [ROOT / f"client/{source_version}/src/{suffix}" for suffix in ("main/java", "minecraft")]
    root = next((path for path in roots if (path / "net/minecraft/network").is_dir()), None)
    if root is None:
        raise ValueError(f"Missing client source for {version}")
    constants = root / "net/minecraft/realms/RealmsSharedConstants.java"
    if constants.is_file():
        declared = re.search(r"NETWORK_PROTOCOL_VERSION\s*=\s*(\d+)", constants.read_text(encoding="utf-8"))
        if declared and int(declared[1]) != protocol:
            raise ValueError(f"Client source protocol {declared[1]} does not match {version} / {protocol}")
    states, sources = registrations(root)
    expected_states = {"handshaking", "status", "login", "play"}
    if protocol >= 764:
        expected_states.add("configuration")
    if set(states) != expected_states:
        raise ValueError(f"Unexpected states for {version}: {set(states)}")
    result = {
        "schemaVersion": 1,
        "minecraftVersion": version,
        "protocolVersion": protocol,
        "sourceRoot": root.relative_to(ROOT).as_posix(),
        "registrationSources": [{"path": path.relative_to(root).as_posix(),
                                 "sha256": hashlib.sha256(path.read_text(encoding="utf-8").encode("utf-8")).hexdigest()}
                                for path in sources],
        "layoutCoverage": "partial",
        "states": {},
    }
    packets = layouts = 0
    for state, flows in states.items():
        result["states"][state] = {}
        for flow, entries in flows.items():
            ids = {}
            known_layouts = {}
            for identifier, (name, path, codec) in enumerate(entries):
                ids[name] = identifier
                layout = wire_layout(root, name, path, codec)
                if layout is not None:
                    known_layouts[name] = layout
            result["states"][state][flow] = {"ids": ids, "layouts": known_layouts}
            packets += len(ids)
            layouts += len(known_layouts)
    return result, packets, layouts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true", help="Verify committed resources without writing")
    mode.add_argument("--check-names", action="store_true", help="Validate master names against JSON registries without client sources")
    args = parser.parse_args()
    master_names = read_json(OUTPUT / "type_to_name.json")
    if args.check_names:
        versions = read_json(OUTPUT / "index.json")
        registries = {version: read_json(OUTPUT / (version + ".json")) for version in versions}
        masters, translations = validate_master_names(master_names, registries)
        print(f"Verified {masters} master packet names and {translations} translations across {len(versions)} versions")
        return
    versions = read_json(OUTPUT / "index.json")
    protocols = protocol_versions()
    generated = {"index.json": versions}
    total_packets = total_layouts = 0
    # Validate all inputs before writing any resource.
    for version in versions:
        registry, packets, layouts = generate(version, protocols[version])
        generated[version + ".json"] = registry
        total_packets += packets
        total_layouts += layouts
        print(f"{version}: {packets} packet IDs, {layouts} complete layouts")
    masters, translations = validate_master_names(
        master_names, {version: generated[version + ".json"] for version in versions}
    )
    for name, value in generated.items():
        data = json.dumps(value, indent=2, ensure_ascii=False) + "\n"
        path = OUTPUT / name
        if args.check:
            if not path.exists() or path.read_text(encoding="utf-8") != data:
                raise ValueError(f"Stale or missing registry: {path.relative_to(ROOT)}")
        else:
            OUTPUT.mkdir(parents=True, exist_ok=True)
            path.write_text(data, encoding="utf-8", newline="\n")
    print(f"{'Verified' if args.check else 'Generated'} {len(versions)} versions: "
          f"{total_packets} packet IDs, {total_layouts} complete layouts")
    print(f"Verified {masters} master packet names and {translations} class translations")


if __name__ == "__main__":
    main()
