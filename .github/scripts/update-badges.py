#!/usr/bin/env python3
"""Generate Shields.io coverage endpoints from a JaCoCo XML report."""

import argparse
import json
from decimal import Decimal
from pathlib import Path
import xml.etree.ElementTree as ET


def coverage(root, counter_type):
    # Only report-level counters: package/class counters would duplicate totals.
    counters = root.findall(f"counter[@type='{counter_type}']")
    if len(counters) != 1:
        raise ValueError(f"Expected one report-level {counter_type} counter")
    covered = int(counters[0].attrib["covered"])
    missed = int(counters[0].attrib["missed"])
    if covered < 0 or missed < 0 or covered + missed == 0:
        raise ValueError(f"Invalid or empty {counter_type} coverage counter")
    return Decimal(covered) * 100 / Decimal(covered + missed)


def color(percent):
    if percent >= 90:
        return "brightgreen"
    if percent >= 80:
        return "green"
    if percent >= 70:
        return "yellowgreen"
    if percent >= 50:
        return "yellow"
    return "red"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    root = ET.parse(args.report).getroot()
    if root.tag != "report":
        raise ValueError("Expected a JaCoCo report")
    results = [
        ("line-coverage.json", "Line Coverage", coverage(root, "LINE")),
        ("branch-coverage.json", "Branch Coverage", coverage(root, "BRANCH")),
    ]
    args.output.mkdir(parents=True, exist_ok=True)
    for filename, label, percent in results:
        badge = {
            "schemaVersion": 1,
            "label": label,
            "message": f"{percent:.2f}%",
            "color": color(percent),
        }
        (args.output / filename).write_text(
            json.dumps(badge, indent=2) + "\n", encoding="utf-8"
        )
        print(f"{label}: {badge['message']}")


if __name__ == "__main__":
    main()
