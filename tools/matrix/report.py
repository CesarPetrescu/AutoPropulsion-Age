#!/usr/bin/env python3
"""Merge generated simulation and native GameTest evidence into one fail-closed matrix report."""
from __future__ import annotations

import argparse
from collections import Counter
from html import escape
import json
from pathlib import Path
import re

REPO = Path(__file__).resolve().parents[2]
CASE_RE = re.compile(
    r"GENERATED_CAR_MATRIX_CASE_PASS case=(\d+) body=(\S+) powertrain=(\S+) "
    r"layout=(\S+) diff=(\S+) split=(\d+)"
)
SERVER_RE = re.compile(r"GENERATED_CAR_MATRIX_SERVER_PASS (\d+)")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def load_json(path: Path) -> dict:
    require(path.is_file(), f"Missing matrix evidence: {path}")
    try:
        value = json.loads(path.read_text())
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"Invalid matrix evidence {path}: {exc}") from exc
    require(isinstance(value, dict), f"Matrix evidence is not an object: {path}")
    return value


def parse_server(log: str, expected: int) -> list[dict]:
    pass_counts = [int(value) for value in SERVER_RE.findall(log)]
    require(pass_counts and pass_counts[-1] == expected,
            f"Missing complete generated native matrix PASS ({expected})")
    cases = []
    seen_ids = set()
    seen_tuples = set()
    for match in CASE_RE.finditer(log):
        case = {
            "case": int(match.group(1)),
            "body": match.group(2),
            "powertrain": match.group(3),
            "layout": match.group(4),
            "differential": match.group(5),
            "split": int(match.group(6)),
        }
        key = (case["body"], case["powertrain"], case["layout"], case["differential"])
        require(case["case"] not in seen_ids, f"Duplicate generated case id: {case['case']}")
        require(key not in seen_tuples, f"Duplicate generated configuration: {key}")
        seen_ids.add(case["case"])
        seen_tuples.add(key)
        cases.append(case)
    require(len(cases) == expected, f"Expected {expected} native matrix cases, got {len(cases)}")
    require(seen_ids == set(range(expected)), "Native matrix case ids are incomplete or non-contiguous")
    return sorted(cases, key=lambda c: c["case"])


def distribution(cases: list[dict], key: str) -> dict[str, int]:
    return dict(sorted(Counter(str(case[key]) for case in cases).items()))


def build_report(server_log: Path, output_dir: Path, repo: Path = REPO) -> dict:
    policy = load_json(repo / "testspec/car-matrix.json")
    expected_native = int(policy["expected"]["native_world_cases"])
    expected_sim = int(policy["expected"]["composed_sim_cases"])
    expected_exhaustive = int(policy["expected"]["engine_compatibility_combinations"])

    generated = load_json(repo / "sim/build/reports/generated-car-matrix.json")
    engine_states = load_json(repo / "sim/build/reports/engine-states.json")
    engine_driving = load_json(repo / "sim/build/reports/engine-driving-matrix.json")
    require(generated.get("cases_passed") == expected_sim,
            f"Generated simulation matrix incomplete: {generated.get('cases_passed')} != {expected_sim}")
    require(engine_states.get("combinations") == expected_exhaustive,
            f"Engine compatibility sweep incomplete: {engine_states.get('combinations')} != {expected_exhaustive}")
    require(engine_states.get("ready", 0) + engine_states.get("blocked", 0) == expected_exhaustive,
            "Engine compatibility ready/blocked accounting does not sum to the exhaustive total")
    require(engine_driving.get("drive_and_brake_cases_passed", 0) >= int(policy["expected"]["engine_drive_cases_min"]),
            "Targeted dynamic engine matrix regressed below its minimum")

    require(server_log.is_file(), f"Missing dedicated server log: {server_log}")
    cases = parse_server(server_log.read_text(errors="replace"), expected_native)
    report = {
        "schema": 1,
        "status": "PASS",
        "policy": policy,
        "coverage": {
            "engine_hardware_exhaustive": engine_states,
            "engine_dynamic_targeted": engine_driving,
            "composed_simulation": generated,
            "native_world": {
                "cases_passed": len(cases),
                "body_distribution": distribution(cases, "body"),
                "powertrain_distribution": distribution(cases, "powertrain"),
                "layout_distribution": distribution(cases, "layout"),
                "differential_distribution": distribution(cases, "differential"),
                "awd_split_distribution": dict(sorted(Counter(
                    str(case["split"]) for case in cases if case["layout"] == "AWD"
                ).items())),
            },
        },
        "native_cases": cases,
        "reproduction": {
            "simulation": "./gradlew --no-daemon :sim:test --tests '*GeneratedCarMatrixTest'",
            "native": "./gradlew --no-daemon -PwithGameTests runGameTestServer",
            "fuzz_seed": policy["fuzz"]["seed"],
        },
    }
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "matrix-report.json").write_text(json.dumps(report, indent=2) + "\n")
    (output_dir / "matrix-report.md").write_text(markdown(report))
    (output_dir / "matrix-report.html").write_text(html(report))
    return report


def markdown(report: dict) -> str:
    c = report["coverage"]
    world = c["native_world"]
    lines = [
        "## Generated car-system matrix",
        "",
        "| Layer | Result | Coverage |",
        "|---|---:|---|",
        f"| Engine hardware compatibility | PASS | {c['engine_hardware_exhaustive']['combinations']:,} exhaustive configurations |",
        f"| Engine dynamic matrix | PASS | {c['engine_dynamic_targeted']['drive_and_brake_cases_passed']:,} drive/brake cases |",
        f"| Composed simulation | PASS | {c['composed_simulation']['cases_passed']:,} body/powertrain/drivetrain/engine combinations |",
        f"| Native Minecraft world | PASS | {world['cases_passed']:,} body × powertrain × layout × differential lifecycles |",
        f"| Stateful fuzz | PASS | {report['policy']['fuzz']['pull_request_sequences']:,} × {report['policy']['fuzz']['max_actions_per_sequence']} deterministic actions (seed `{report['policy']['fuzz']['seed']}`) |",
        "",
        "Native cases include real entity construction, body/powertrain configuration, torque-path validation, mount/ignition, drive, steering, braking, energy use, and pre/post-drive persistence.",
        "",
        "### Native distribution",
        "",
        f"- Bodies: `{json.dumps(world['body_distribution'], sort_keys=True)}`",
        f"- Powertrains: `{json.dumps(world['powertrain_distribution'], sort_keys=True)}`",
        f"- Layouts: `{json.dumps(world['layout_distribution'], sort_keys=True)}`",
        f"- Differentials: `{json.dumps(world['differential_distribution'], sort_keys=True)}`",
        f"- AWD split samples: `{json.dumps(world['awd_split_distribution'], sort_keys=True)}`",
        "",
        "Reproduce simulation: `" + report["reproduction"]["simulation"] + "`",
        "",
        "Reproduce native world: `" + report["reproduction"]["native"] + "`",
        "",
    ]
    return "\n".join(lines)


def html(report: dict) -> str:
    c = report["coverage"]
    world = c["native_world"]
    rows = "".join(
        f"<tr><td>{escape(name)}</td><td>{count}</td></tr>"
        for name, count in world["body_distribution"].items()
    )
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>AutoPropulsion Age matrix report</title>
<style>body{{font:15px system-ui,sans-serif;max-width:1000px;margin:40px auto;padding:0 20px}}table{{border-collapse:collapse;width:100%;margin:18px 0}}th,td{{border:1px solid #bbb;padding:8px;text-align:left}}code{{background:#eee;padding:2px 4px}}.pass{{font-weight:700}}</style></head>
<body><h1>Generated car-system matrix</h1><p class="pass">PASS</p>
<ul>
<li>{c['engine_hardware_exhaustive']['combinations']:,} exhaustive engine-hardware compatibility configurations</li>
<li>{c['engine_dynamic_targeted']['drive_and_brake_cases_passed']:,} targeted engine drive/brake cases</li>
<li>{c['composed_simulation']['cases_passed']:,} composed simulation cases</li>
<li>{world['cases_passed']:,} native Minecraft world lifecycles</li>
<li>{report['policy']['fuzz']['pull_request_sequences']:,} deterministic fuzz sequences, seed <code>{report['policy']['fuzz']['seed']}</code></li>
</ul><h2>Native cases by body</h2><table><tr><th>Body</th><th>Cases</th></tr>{rows}</table>
<p>Full machine-readable case data is in <code>matrix-report.json</code>.</p></body></html>\n"""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-log", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    report = build_report(args.server_log, args.output_dir)
    print(markdown(report))


if __name__ == "__main__":
    main()
