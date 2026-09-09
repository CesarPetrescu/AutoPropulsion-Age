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
        log='All 64 required tests passed :)\nDRIVETRAIN_SERVER_MATRIX_PASS 294\nELECTRIC_SERVER_MATRIX_PASS 12\nORIENTED_COLLISION_SERVER_PASS empty_corner\nHEADING_RECOVERY_SERVER_PASS crash\nHEADING_RECOVERY_SERVER_PASS spin'
        log+='\n'+'\n'.join('CONFIGURED_COMPONENTS_SERVER_PASS '+case for case in ('motor_transfer','internals_transfer','hv_interlock','crate_transfer','service_recipes'))
        log+='\nBODY_CRATE_CONVERSION_SERVER_PASS 150\nBODY_PERSISTENCE_SERVER_PASS 30\nBODY_DRIVING_SERVER_MATRIX_PASS 30'
        log+='\nCHARGER_NEIGHBOR_VISIBILITY_PASS 96\nCHARGER_MODEL_SHAPE_PASS 16'
        self.assertEqual(checks.server(log)['dedicated_gametests_passed'], 64)
        for marker in ('ORIENTED_COLLISION_SERVER_PASS empty_corner','HEADING_RECOVERY_SERVER_PASS crash','HEADING_RECOVERY_SERVER_PASS spin','CHARGER_NEIGHBOR_VISIBILITY_PASS 96','CHARGER_MODEL_SHAPE_PASS 16'):
            with self.assertRaises(ValueError):checks.server(log.replace(marker,''))

    def test_server_missing_driving_matrix_blocks_release(self):
        with self.assertRaises(ValueError):
            checks.server('All 38 required tests passed :)')

    def test_native_handling_missing_layout_or_landing_blocks_release(self):
        for log in ('HANDLING_CLIENT_PASS AIRBORNE_LANDING_PASS DRIVE_LAYOUT_PASS RWD',
                    'HANDLING_CLIENT_PASS DRIVE_LAYOUT_PASS RWD DRIVE_LAYOUT_PASS FWD DRIVE_LAYOUT_PASS AWD'):
            with self.assertRaises(ValueError):
                checks.client('ALPHA_CLIENT_SMOKE PASS: ' + log, 'PASS: ok', 'handling')

    def test_native_handling_complete_pass(self):
        log = 'ALPHA_CLIENT_SMOKE PASS: HANDLING_CLIENT_PASS AIRBORNE_LANDING_PASS DRIVE_LAYOUT_PASS RWD DRIVE_LAYOUT_PASS FWD DRIVE_LAYOUT_PASS AWD HEADING_RECOVERY_CLIENT_PASS RWD HEADING_RECOVERY_CLIENT_PASS FWD HEADING_RECOVERY_CLIENT_PASS AWD'
        with self.assertRaises(ValueError):checks.client(log,'PASS: ok','handling')
        log+='\nHANDLING_RESET_MATRIX_PASS 8\nHANDLING_RESET_CONVERGED FWD\nHANDLING_RESET_CONVERGED AWD\n'
        log+='\n'.join('HANDLING_RESET_CASE_PASS '+str(i) for i in range(8))
        packets=['INTERPOLATION_PACKET_CLIENT_PASS '+case for case in ('position_preserves_heading','rotation_preserves_position','settled_targets')]
        log+='\n'+'\n'.join(packets)
        self.assertEqual(checks.client(log, 'PASS: ok', 'handling')['native_drive_layouts_passed'], ['AWD', 'FWD', 'RWD'])
        for layout in ('RWD','FWD','AWD'):
            with self.assertRaises(ValueError):checks.client(log.replace('HEADING_RECOVERY_CLIENT_PASS '+layout,''),'PASS: ok','handling')
        for marker in ['HANDLING_RESET_CASE_PASS '+str(i) for i in range(8)]+['HANDLING_RESET_CONVERGED FWD','HANDLING_RESET_CONVERGED AWD','HANDLING_RESET_MATRIX_PASS 8']+packets:
            with self.assertRaises(ValueError):checks.client(log.replace(marker,''),'PASS: ok','handling')

    def test_native_graphics_requires_all_modes_and_rendered_frames(self):
        cases=('startup-fabulous','menu-fast','menu-fancy','menu-fabulous','menu-fast-again')
        log='ALPHA_CLIENT_SMOKE PASS:\nGRAPHICS_CLIENT_PASS 5\n'+'\n'.join('GRAPHICS_CASE_PASS '+case+' frames=45' for case in cases)
        self.assertEqual(checks.client(log,'PASS: ok','graphics')['native_graphics_cases'],sorted(cases))
        for case in cases:
            with self.assertRaises(ValueError):checks.client(log.replace('GRAPHICS_CASE_PASS '+case+' frames=45',''),'PASS: ok','graphics')
        with self.assertRaises(ValueError):checks.client(log.replace('frames=45','frames=44'),'PASS: ok','graphics')
        with self.assertRaises(ValueError):checks.client(log.replace('GRAPHICS_CLIENT_PASS 5',''),'PASS: ok','graphics')
        with self.assertRaises(ValueError):checks.client(log+'\nALPHA_CLIENT_SMOKE FAILED: crash','PASS: ok','graphics')

    def test_client_missing_result_is_failure(self):
        with self.assertRaises(ValueError):
            checks.client('ALPHA_CLIENT_SMOKE PASS:', 'FAILED: timeout', 'mechanics')

    def test_electric_requires_native_success(self):
        for log, result in [('BUILD SUCCESSFUL', 'PASS: ok'), ('ELECTRIC_CLIENT_SMOKE PASS:', 'FAILED: drive'), ('ELECTRIC_CLIENT_SMOKE FAILED\nELECTRIC_CLIENT_SMOKE PASS:', 'PASS: ok')]:
            with self.assertRaises(ValueError):
                checks.client(log, result, 'electric')
        log='ELECTRIC_CLIENT_SMOKE PASS:\nCHARGER_UI_PASS 16 cable_return=1\n'+'\n'.join('CHARGER_UI_CASE_PASS '+str(i) for i in range(16))
        self.assertTrue(checks.client(log, 'PASS: ok', 'electric')['native_electric_charging_driving'])
        with self.assertRaises(ValueError):checks.client(log.replace('CHARGER_UI_CASE_PASS 4',''), 'PASS: ok', 'electric')
        with self.assertRaises(ValueError):checks.client(log.replace('cable_return=1','cable_return=0'), 'PASS: ok', 'electric')

    def test_server_requires_electric_matrix(self):
        with self.assertRaises(ValueError):
            checks.server('All 38 required tests passed\nDRIVETRAIN_SERVER_MATRIX_PASS 294')

    def test_mechanics_requires_audio_and_sender_evidence(self):
        with self.assertRaises(ValueError):
            checks.client('ALPHA_CLIENT_SMOKE PASS: MECHANICS_CLIENT_PASS', 'PASS: ok', 'mechanics')

    def test_mechanics_complete_pass(self):
        log = 'ALPHA_CLIENT_SMOKE PASS: MECHANICS_CLIENT_PASS AUDIO_CHANNELS_AND_CLEANUP_PASS INSTRUMENT_SENDER_PASS COOLANT_REFILL_CLIENT_PASS'
        self.assertTrue(checks.client(log, 'PASS: ok', 'mechanics')['native_mechanics_passed'])
        with self.assertRaises(ValueError):checks.client(log.replace('COOLANT_REFILL_CLIENT_PASS',''),'PASS: ok','mechanics')

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
            archive.writestr('META-INF/neoforge.mods.toml', '[[mods]]\nmodId="sparkmotors"\nversion="0.4.0-alpha"\nlogoFile="logo.png"\n')
            for name in ('com/photonspark/sparkmotors/entity/CarEntity.class',
                         'com/photonspark/sparkmotors/sim/MechanicalState.class',
                         'com/photonspark/sparkmotors/sim/PowertrainTopology.class',
                         'com/photonspark/sparkmotors/sim/InternalMechanics.class',
                         'assets/sparkmotors/models/entity/mechanical-models.json',
                         'com/photonspark/sparkmotors/sim/electric/ElectricDynamics.class',
                         'com/photonspark/sparkmotors/charging/ChargerBlockEntity.class',
                         'com/photonspark/sparkmotors/client/ElectricScreen.class',
                         'com/photonspark/sparkmotors/client/ChargerScreen.class',
                         'com/photonspark/sparkmotors/client/ChargerRenderer.class',
                         'com/photonspark/sparkmotors/client/CarGlass.class',
                         'com/photonspark/sparkmotors/charging/ChargerShapes.class',
                         'assets/sparkmotors/models/entity/sedan.mesh.gz',
                         'assets/sparkmotors/models/entity/sedan-lod1.mesh.gz',
                         'assets/sparkmotors/models/entity/sedan-lod2.mesh.gz',
                         'assets/sparkmotors/models/entity/lod-manifest.json'):
                archive.writestr(name, b'fixture')
            archive.writestr('com/photonspark/sparkmotors/sim/BodyStyle.class',b'fixture')
            for body in ('hatchback','sports_car','suv','van','touring_sedan'):
                archive.writestr(f'assets/sparkmotors/models/entity/bodies/{body}.mesh.gz',b'fixture')
            archive.writestr('assets/sparkmotors/models/entity/bodies/shell-surfaces.json', '{}')
            archive.writestr('logo.png', bytes.fromhex('89504e470d0a1a0a'))
            sounds = {}
            for i in range(48):
                archive.writestr(f'assets/sparkmotors/sounds/mechanical/layer{i}.ogg', b'fixture')
                sounds[str(i)] = {'sounds': [{'name': f'sparkmotors:mechanical/layer{i}'}]}
            archive.writestr('assets/sparkmotors/sounds.json', json.dumps(sounds))
            if extra:
                archive.writestr(extra, b'fixture')
        return path

    def test_incomplete_ui_or_missing_logo_blocks_release(self):
        log = 'ALPHA_CLIENT_SMOKE PASS: WORKSHOP_UI_PASS 192\n'
        log += '\n'.join(f'WORKSHOP_UI_CASE_PASS {i} page' for i in range(192))
        with self.assertRaises(ValueError):
            checks.client(log, 'PASS: ok', 'ui')
        self.assertEqual(checks.client(log+' MOD_LOGO_CLIENT_PASS CONFIGURED_GEOMETRY_CLIENT_PASS cases=105', 'PASS: ok', 'ui')['workshop_page_scale_cases'], 192)
        with self.assertRaises(ValueError):
            checks.client('ALPHA_CLIENT_SMOKE PASS: MOD_LOGO_CLIENT_PASS WORKSHOP_UI_PASS 192', 'PASS: ok', 'ui')

    def test_missing_packaged_logo_blocks_release(self):
        jar = self.make_jar()
        broken = self.root / 'no-logo.jar'
        with zipfile.ZipFile(jar) as source, zipfile.ZipFile(broken, 'w') as output:
            for name in source.namelist():
                if name != 'logo.png':
                    output.writestr(name, source.read(name))
        with self.assertRaises(ValueError):
            checks.inspect_jar(broken)

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
