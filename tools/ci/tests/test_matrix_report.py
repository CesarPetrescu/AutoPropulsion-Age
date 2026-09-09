import json
from pathlib import Path
import tempfile
import unittest

from tools.matrix import report


class MatrixReportTest(unittest.TestCase):
    BODIES = ["classic_sedan", "hatchback", "sports_car", "suv", "van", "touring_sedan"]
    POWERTRAINS = ["combustion", "hybrid", "plug_in_hybrid", "electric_400", "electric_800"]
    LAYOUTS = ["RWD", "FWD", "AWD"]
    DIFFERENTIALS = ["OPEN", "LIMITED_SLIP", "LOCKED"]

    def fixture(self, root: Path):
        (root / "testspec").mkdir(parents=True)
        (root / "sim/build/reports").mkdir(parents=True)
        (root / "testspec/car-matrix.json").write_text(json.dumps({
            "schema": 1,
            "expected": {
                "engine_compatibility_combinations": 1835008,
                "engine_drive_cases_min": 7308,
                "composed_sim_cases": 15984,
                "native_world_cases": 270,
            },
            "fuzz": {"pull_request_sequences": 1000, "max_actions_per_sequence": 32, "seed": 424242},
        }))
        (root / "sim/build/reports/generated-car-matrix.json").write_text(json.dumps({"cases_passed": 15984}))
        (root / "sim/build/reports/engine-states.json").write_text(json.dumps({
            "combinations": 1835008, "ready": 692224, "blocked": 1142784,
        }))
        (root / "sim/build/reports/engine-driving-matrix.json").write_text(json.dumps({
            "drive_and_brake_cases_passed": 7308,
        }))
        lines = []
        case = 0
        for body in self.BODIES:
            for powertrain in self.POWERTRAINS:
                for layout in self.LAYOUTS:
                    for differential in self.DIFFERENTIALS:
                        split = 50 if layout != "AWD" else 20 + 20 * ((case // 3) & 3)
                        lines.append(
                            f"GENERATED_CAR_MATRIX_CASE_PASS case={case} body={body} powertrain={powertrain} "
                            f"layout={layout} diff={differential} split={split}"
                        )
                        case += 1
        self.assertEqual(case, 270)
        lines.append("GENERATED_CAR_MATRIX_SERVER_PASS 270")
        log = root / "server.log"
        log.write_text("\n".join(lines) + "\n")
        return log

    def test_complete_evidence_builds_json_markdown_and_html(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            log = self.fixture(root)
            result = report.build_report(log, root / "out", repo=root)
            self.assertEqual(result["status"], "PASS")
            self.assertEqual(result["coverage"]["native_world"]["cases_passed"], 270)
            self.assertEqual(set(result["coverage"]["native_world"]["layout_distribution"]), {"RWD", "FWD", "AWD"})
            self.assertTrue((root / "out/matrix-report.json").is_file())
            self.assertIn("1,835,008", (root / "out/matrix-report.md").read_text())
            self.assertIn("Generated car-system matrix", (root / "out/matrix-report.html").read_text())

    def test_missing_native_case_is_fail_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            log = self.fixture(root)
            lines = [line for line in log.read_text().splitlines()
                     if not line.startswith("GENERATED_CAR_MATRIX_CASE_PASS case=269 ")]
            log.write_text("\n".join(lines) + "\n")
            with self.assertRaisesRegex(ValueError, "Expected 270 native matrix cases"):
                report.build_report(log, root / "out", repo=root)

    def test_incomplete_simulation_matrix_is_fail_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            log = self.fixture(root)
            (root / "sim/build/reports/generated-car-matrix.json").write_text(json.dumps({"cases_passed": 15983}))
            with self.assertRaisesRegex(ValueError, "Generated simulation matrix incomplete"):
                report.build_report(log, root / "out", repo=root)


if __name__ == "__main__":
    unittest.main()
