#!/usr/bin/env python3
"""
Read JaCoCo's jacoco.xml and print project-level coverage (root <report> counters).
Run after: mvn test or mvn verify (report is generated in the test phase).
"""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def pct(covered: int, missed: int) -> float:
    total = covered + missed
    return 100.0 * covered / total if total else 0.0


def parse_report_counters(xml_path: Path) -> tuple[str, dict[str, tuple[int, int]]]:
    root = ET.parse(xml_path).getroot()
    if root.tag != "report":
        raise ValueError(f"Expected root <report>, got <{root.tag}>")
    name = root.get("name", "")
    counters: dict[str, tuple[int, int]] = {}
    for el in root.findall("counter"):
        kind = el.get("type")
        if not kind:
            continue
        missed = int(el.get("missed", "0"))
        covered = int(el.get("covered", "0"))
        counters[kind] = (missed, covered)
    return name, counters


def main() -> int:
    parser = argparse.ArgumentParser(description="Print JaCoCo project totals from jacoco.xml")
    parser.add_argument(
        "jacoco_xml",
        nargs="?",
        type=Path,
        default=None,
        help="Path to jacoco.xml (default: <backend>/target/site/jacoco/jacoco.xml)",
    )
    args = parser.parse_args()
    default_xml = Path(__file__).resolve().parent.parent / "target/site/jacoco/jacoco.xml"
    xml_path = args.jacoco_xml if args.jacoco_xml is not None else default_xml

    if not xml_path.is_file():
        print(
            f"Missing {xml_path}. Run from backend: mvn verify",
            file=sys.stderr,
        )
        return 1

    try:
        report_name, counters = parse_report_counters(xml_path)
    except (ET.ParseError, ValueError, OSError) as e:
        print(f"Failed to read {xml_path}: {e}", file=sys.stderr)
        return 1

    label = report_name or "project"
    print(f"JaCoCo summary ({label}) — {xml_path}")
    order = ("LINE", "BRANCH", "INSTRUCTION", "METHOD", "CLASS", "COMPLEXITY")
    for kind in order:
        if kind not in counters:
            continue
        missed, covered = counters[kind]
        total = missed + covered
        print(f"  {kind:14} {covered:6} / {total:6} covered  ({pct(covered, missed):5.1f}%)")

    return 0


if __name__ == "__main__":
    sys.exit(main())
