#!/usr/bin/env python3
"""Import complete ID tables for releases without local client sources (no layouts)."""

import argparse
import hashlib
import json
from pathlib import Path

from generate import FLOWS, OUTPUT, protocol_versions, read_json, validate_master_names


REVISION = "dd01127457231e7da629d645b3c6738068018eb0"
REPOSITORY = "https://github.com/PrismarineJS/minecraft-data"
# These releases share packet IDs, not necessarily payload layouts. ViaVersion's
# Protocol1_9To1_9_1 and Protocol1_14_1To1_14_2 use the same packet enums on both sides.
SHARED_ID_SOURCES = {108: "pc/1.9.2", 485: "pc/1.14.1"}


def master_name(names, state, flow, name, protocol):
    # minecraft-data reuses these symbols across changes in packet semantics.
    if (state, flow, name) == ("play", "clientbound", "named_sound_effect") and protocol == 47:
        return "NAMED_SOUND_EFFECT"
    if (state, flow, name) == ("play", "clientbound", "acknowledge_player_digging") and protocol >= 759:
        return "BLOCK_CHANGED_ACK"
    return names[f"{state}/{flow}/{name}"]


def convert(source, names, protocol):
    states = {}
    for state, data in source.items():
        if state == "types":
            continue
        states[state] = {}
        for flow, source_flow in zip(FLOWS, ("toServer", "toClient")):
            mapper = data[source_flow]["types"]["packet"][1][0]["type"]
            if mapper[0] != "mapper" or mapper[1]["type"] != "varint":
                raise ValueError(f"Unexpected packet mapper: {protocol}/{state}/{flow}")
            ids = {}
            for identifier, name in mapper[1]["mappings"].items():
                # This belongs to the older, unframed ping protocol.
                if name == "legacy_server_list_ping":
                    continue
                master = master_name(names, state, flow, name, protocol)
                if master in ids:
                    raise ValueError(f"Ambiguous master name: {protocol}/{state}/{flow}/{master}")
                ids[master] = int(identifier, 0)
            if set(ids.values()) != set(range(len(ids))):
                raise ValueError(f"Incomplete packet table: {protocol}/{state}/{flow}")
            states[state][flow] = ids
    if set(states) != {"handshaking", "status", "login", "play"}:
        raise ValueError(f"Unexpected states for supplemental protocol {protocol}")
    return states


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True,
                        help=f"minecraft-data checkout at {REVISION} (no downloads performed)")
    parser.add_argument("--check", action="store_true", help="Compare without writing")
    args = parser.parse_args()
    paths = read_json(args.source / "data/dataPaths.json")["pc"]
    versions = protocol_versions()
    releases = {}
    for version, protocol in versions.items():
        releases.setdefault(protocol, []).append(version)
    registries = {v: read_json(OUTPUT / (v + ".json")) for v in read_json(OUTPUT / "index.json")}
    master_names = read_json(OUTPUT / "type_to_name.json")
    validate_master_names(master_names, registries)
    names = read_json(Path(__file__).with_name("minecraft_data_names.json"))
    covered = {registry["protocolVersion"] for registry in registries.values()}
    missing = set(releases) - covered
    result = {"schemaVersion": 1, "source": {"repository": REPOSITORY, "revision": REVISION}, "protocols": {}}
    checked = 0
    # Cross-check the imported name mapping against every available local client
    # in the same era, so ID/name shifts cannot silently enter the supplement.
    for protocol, release_versions in sorted(releases.items()):
        if protocol > max(missing):
            continue
        path = SHARED_ID_SOURCES.get(protocol)
        if path is None:
            path = next(paths[v]["protocol"] for v in reversed(release_versions) if v in paths)
        source_file = args.source / "data" / path / "protocol.json"
        states = convert(read_json(source_file), names, protocol)
        for version, registry in registries.items():
            if registry["protocolVersion"] != protocol:
                continue
            for state, flows in registry["states"].items():
                for flow, data in flows.items():
                    expected = {master: data["ids"][by_version[version]]
                                for master, by_version in master_names["states"][state][flow].items()
                                if version in by_version}
                    if expected != states[state][flow]:
                        raise ValueError(f"Packet ID disagreement: {version}/{state}/{flow}")
                    checked += len(expected)
        if protocol in missing:
            result["protocols"][str(protocol)] = {
                "minecraftVersions": release_versions,
                "sourcePath": "data/" + path + "/protocol.json",
                "sourceSha256": hashlib.sha256(source_file.read_bytes()).hexdigest(),
                "states": states,
            }
    data = json.dumps(result, indent=2) + "\n"
    target = OUTPUT / "additional_protocols.json"
    if args.check:
        if target.read_text(encoding="utf-8") != data:
            raise ValueError("Stale additional_protocols.json")
    else:
        target.write_text(data, encoding="utf-8", newline="\n")
    count = sum(len(ids) for p in result["protocols"].values() for flows in p["states"].values() for ids in flows.values())
    print(f"{'Verified' if args.check else 'Imported'} {len(missing)} exact protocols / {count} IDs; "
          f"cross-checked {checked} local-client IDs")


if __name__ == "__main__":
    main()
