#!/usr/bin/env python3
"""Генерация XML seed-снапшотов мок-режима бэкенда из реальных логов PrintSrv.

Читает printsrv-logs/<Автомат>_<Устройство>.jsonl (последняя строка = последний
QueryAll-ответ), забирает Units.u1.Properties и пишет XML в формате PrintSrv
DeviceUnit — тот же формат, что читает XmlSnapshotLoader
(backend/src/main/resources/mock-snapshots/<instanceId>/<Device>___Unit0.xml).

Маппинг автомат -> директория мок-снапшота:
  Bosch_*    -> bosch
  Grunwald5_* -> grunwald5
  Hassia1_*  -> hassia1

Значения очищаются от управляющих символов (0x00-0x1F), недопустимых в XML 1.0.

Запуск из корня репозитория:
  python scripts/generate_mock_snapshots.py
"""
from __future__ import annotations

import glob
import html
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOGS_DIR = os.path.join(ROOT, "printsrv-logs")
OUT_BASE = os.path.join(ROOT, "backend", "src", "main", "resources", "mock-snapshots")

INSTANCE_DIR = {
    "Bosch": "bosch",
    "Grunwald5": "grunwald5",
    "Hassia1": "hassia1",
}

CONTROL_CHARS = re.compile(r"[\x00-\x1F\x7F]")

HEADER = (
    '<DeviceUnit\n'
    '    xmlns="http://schemas.datacontract.org/2004/07/MarkPrintServer.Classes">\n'
    '    <counterloginterval>1000</counterloginterval>\n'
    '    <properties xmlns:d2p1="http://schemas.microsoft.com/2003/10/Serialization/Arrays">\n'
)
FOOTER = "    </properties>\n</DeviceUnit>\n"

# Кодировка utf-8-sig при записи сама ставит BOM, как в оригинальных файлах PrintSrv


def sanitize(value: str) -> str:
    return CONTROL_CHARS.sub("", str(value))


def last_properties(jsonl_path: str) -> dict[str, str]:
    last: dict[str, str] = {}
    with open(jsonl_path, encoding="utf-8-sig") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                outer = json.loads(line)
                inner = json.loads(outer["data"])
                props = inner["Units"]["u1"]["Properties"]
            except (json.JSONDecodeError, KeyError, TypeError):
                continue
            last = {str(k): sanitize(v) for k, v in props.items()}
    return last


def write_snapshot(out_dir: str, device: str, props: dict[str, str]) -> str:
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, f"{device}___Unit0.xml")
    with open(path, "w", encoding="utf-8-sig", newline="") as fh:
        fh.write(HEADER)
        for key, value in props.items():
            fh.write(
                "        <d2p1:KeyValueOfstringstring>\n"
                f"            <d2p1:Key>{html.escape(key)}</d2p1:Key>\n"
                f"            <d2p1:Value>{html.escape(value)}</d2p1:Value>\n"
                "        </d2p1:KeyValueOfstringstring>\n"
            )
        fh.write(FOOTER)
    return path


def main() -> None:
    written = 0
    for jsonl in sorted(glob.glob(os.path.join(LOGS_DIR, "*.jsonl"))):
        name = os.path.basename(jsonl)[: -len(".jsonl")]
        instance, _, device = name.partition("_")
        out_dir_name = INSTANCE_DIR.get(instance)
        if out_dir_name is None:
            continue
        props = last_properties(jsonl)
        if not props:
            print(f"skip {name}: no parsable QueryAll lines")
            continue
        path = write_snapshot(os.path.join(OUT_BASE, out_dir_name), device, props)
        print(f"{name} -> {os.path.relpath(path, ROOT)} ({len(props)} keys)")
        written += 1
    print(f"done, {written} snapshot(s) written")


if __name__ == "__main__":
    main()
