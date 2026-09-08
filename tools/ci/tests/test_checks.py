"""Negative tests for release gating: success must be explicit and artifacts must match."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import checks


class ReleaseGates(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_missing_junit_is_failure(self):
        with self.assertRaises(ValueError):
            checks.junit(self.root)

    def test_junit_skips_and_failures_block_release(self):
        for child in ('<skipped/>', '<failure message="bad"/>', '<error/>'):
            (self.root / 'TEST-suite.xml').write_text(f'<testsuite><testcase>{child}</testcase></testsuite>')
            with self.assertRaises(ValueError):
                checks.junit(self.root, minimum=1)

    def test_junit_incomplete_suite_blocks_release(self):
        (self.root / 'TEST-suite.xml').write_text('<testsuite><testcase/></testsuite>')
        with self.assertRaises(ValueError):
            checks.junit(self.root)

    def test_junit_counts_real_cases(self):
        (self.root / 'TEST-suite.xml').write_text('<testsuite tests="99"><testcase/><testcase/></testsuite>')
        self.assertEqual(checks.junit(self.root, minimum=2)['junit_passed'], 2)

    def test_gradle_success_alone_does_not_pass_server(self):
        with self.assertRaises(ValueError):
            checks.server('BUILD SUCCESSFUL in 4s')

    def test_missing_server_tests_block_release(self):
        with self.assertRaises(ValueError):
            checks.server('All 3 required tests passed :)')

    def test_mixed_server_failure_and_pass_is_rejected(self):
        with self.assertRaises(ValueError):
            checks.server('2 required tests failed\nAll 26 required tests passed :)')

    def test_server_complete_pass(self):
        self.assertEqual(checks.server('All 30 required tests passed :)\nDRIVETRAIN_SERVER_MATRIX_PASS 294')['dedicated_gametests_passed'], 30)

    def test_server_missing_driving_matrix_blocks_release(self):
        with self.assertRaises(ValueError):
            checks.server('All 30 required tests passed :)')

    def test_native_handling_missing_layout_or_landing_blocks_release(self):
        for log in ('HANDLING_CLIENT_PASS AIRBORNE_LANDING_PASS DRIVE_LAYOUT_PASS RWD',
                    'HANDLING_CLIENT_PASS DRIVE_LAYOUT_PASS RWD DRIVE_LAYOUT_PASS FWD DRIVE_LAYOUT_PASS AWD'):
            with self.assertRaises(ValueError):
                checks.client('ALPHA_CLIENT_SMOKE PASS: ' + log, 'PASS: ok', 'handling')

    def test_native_handling_complete_pass(self):
        log = 'ALPHA_CLIENT_SMOKE PASS: HANDLING_CLIENT_PASS AIRBORNE_LANDING_PASS DRIVE_LAYOUT_PASS RWD DRIVE_LAYOUT_PASS FWD DRIVE_LAYOUT_PASS AWD'
        self.assertEqual(checks.client(log, 'PASS: ok', 'handling')['native_drive_layouts_passed'], ['AWD', 'FWD', 'RWD'])

    def test_client_missing_result_is_failure(self):
        with self.assertRaises(ValueError):
            checks.client('ALPHA_CLIENT_SMOKE PASS:', 'FAILED: timeout', 'mechanics')

    def test_mechanics_requires_audio_and_sender_evidence(self):
        with self.assertRaises(ValueError):
            checks.client('ALPHA_CLIENT_SMOKE PASS: MECHANICS_CLIENT_PASS', 'PASS: ok', 'mechanics')

    def test_mechanics_complete_pass(self):
        log = 'ALPHA_CLIENT_SMOKE PASS: MECHANICS_CLIENT_PASS AUDIO_CHANNELS_AND_CLEANUP_PASS INSTRUMENT_SENDER_PASS'
        self.assertTrue(checks.client(log, 'PASS: ok', 'mechanics')['native_mechanics_passed'])

    def test_duplicate_layout_logs_cannot_fake_coverage(self):
        log = 'ALPHA_CLIENT_SMOKE PASS:\n' + 'ENGINE_LAYOUT_PASS i4-natural\n' * 49 + 'HARDWARE_UI_PASS stock_intake\n' * 42
        with self.assertRaises(ValueError):
            checks.client(log, 'PASS: ok', 'matrix')

    def test_native_matrix_complete_pass(self):
        log = 'ALPHA_CLIENT_SMOKE PASS:\n'
        log += '\n'.join(f'ENGINE_LAYOUT_PASS layout-{i}' for i in range(49))
        log += '\n' + '\n'.join(f'HARDWARE_UI_PASS hardware-{i}' for i in range(42))
        self.assertEqual(len(checks.client(log, 'PASS: ok', 'matrix')['native_layouts_passed']), 49)

    def test_multiplayer_server_success_without_clients_is_failure(self):
        (self.root / 'result.json').write_text(json.dumps({'passed': True, 'clients': 2, 'checks': []}))
        with self.assertRaises(ValueError):
            checks.multiplayer(self.root)

    def make_jar(self, extra=None):
        path = self.root / 'mod.jar'
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('META-INF/neoforge.mods.toml', '[[mods]]\nmodId="sparkmotors"\nversion="0.4.0-alpha"\n')
            for name in ('com/photonspark/sparkmotors/entity/CarEntity.class',
                         'com/photonspark/sparkmotors/sim/MechanicalState.class',
                         'assets/sparkmotors/models/entity/sedan.mesh.gz'):
                archive.writestr(name, b'fixture')
            sounds = {}
            for i in range(48):
                archive.writestr(f'assets/sparkmotors/sounds/mechanical/layer{i}.ogg', b'fixture')
                sounds[str(i)] = {'sounds': [{'name': f'sparkmotors:mechanical/layer{i}'}]}
            archive.writestr('assets/sparkmotors/sounds.json', json.dumps(sounds))
            if extra:
                archive.writestr(extra, b'fixture')
        return path

    def test_harness_cannot_leak_into_jar(self):
        jar = self.make_jar('com/photonspark/sparkmotors/gametest/ClientSmoke.class')
        with self.assertRaises(ValueError):
            checks.inspect_jar(jar)

    def test_package_and_verify_matching_commit(self):
        output = self.root / 'release'
        checks.package(self.make_jar(), output, 'a' * 40, 'local-test')
        self.assertEqual(checks.verify_package(output, 'a' * 40)['version'], '0.4.0-alpha')

    def test_wrong_commit_blocks_release(self):
        output = self.root / 'release'
        checks.package(self.make_jar(), output, 'a' * 40, 'local-test')
        with self.assertRaises(ValueError):
            checks.verify_package(output, 'b' * 40)

    def test_tampered_jar_blocks_release(self):
        output = self.root / 'release'
        checks.package(self.make_jar(), output, 'a' * 40, 'local-test')
        (output / 'autopropulsion-age-latest.jar').write_bytes(b'replaced')
        with self.assertRaises(ValueError):
            checks.verify_package(output, 'a' * 40)

    def test_missing_checksum_blocks_release(self):
        output = self.root / 'release'
        checks.package(self.make_jar(), output, 'a' * 40, 'local-test')
        path = output / 'SHA256SUMS.txt'
        path.write_text('\n'.join(path.read_text().splitlines()[1:]))
        with self.assertRaises(ValueError):
            checks.verify_package(output, 'a' * 40)

    def test_unexpected_assets_block_release(self):
        output = self.root / 'release'
        checks.package(self.make_jar(), output, 'a' * 40, 'local-test')
        (output / 'unverified.jar').write_bytes(b'bad')
        with self.assertRaises(ValueError):
            checks.verify_package(output, 'a' * 40)

    def test_package_refuses_stale_output(self):
        output = self.root / 'release'
        jar = self.make_jar()
        checks.package(jar, output, 'a' * 40, 'local-test')
        with self.assertRaises(ValueError):
            checks.package(jar, output, 'a' * 40, 'local-test')


if __name__ == '__main__':
    unittest.main()
