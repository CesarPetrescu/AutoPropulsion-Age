import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from shell_checks import BODIES, POWERTRAINS, expected_images, validate


class ShellGates(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory(); self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.data = {'result': 'PASS complete', 'cases': [dict(body=b, powertrain=p, views=17)
                     for b in BODIES for p in POWERTRAINS], 'screenshots': sorted(expected_images())}
        self.log = 'SHELL_NATIVE_RESULT PASS complete\n' + '\n'.join('SHELL_SCREENSHOT ' + n for n in expected_images())
        (self.root / 'screenshots').mkdir()
        for name in expected_images():
            (self.root / 'screenshots' / name).write_bytes(b'\x89PNG\r\n\x1a\n' + bytes(8) + struct.pack('>II', 1440, 900) + bytes(1024))
        (self.root / 'shell-smoke-result.txt').write_text('PASS complete')
        self.save()

    def save(self):
        (self.root / 'shell-smoke-result.json').write_text(json.dumps(self.data))

    def test_complete_is_not_visual_approval(self):
        result = validate(self.log, self.root)
        self.assertEqual(result['native_screenshots'], 204)
        self.assertFalse(result['visual_approval'])

    def test_missing_frame(self):
        self.data['screenshots'].pop(); self.save()
        with self.assertRaises(ValueError): validate(self.log, self.root)

    def test_duplicate_case(self):
        self.data['cases'][-1] = self.data['cases'][0]; self.save()
        with self.assertRaises(ValueError): validate(self.log, self.root)

    def test_missing_capture_marker(self):
        with self.assertRaises(ValueError): validate(self.log.replace('SHELL_SCREENSHOT', 'UNVERIFIED', 1), self.root)

    def test_failure_cannot_be_hidden_by_pass(self):
        with self.assertRaises(ValueError): validate(self.log + '\nSHELL_NATIVE_RESULT FAIL timeout', self.root)

    def test_corrupt_image(self):
        (self.root / 'screenshots' / self.data['screenshots'][0]).write_bytes(b'invalid')
        with self.assertRaises(ValueError): validate(self.log, self.root)
