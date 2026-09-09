import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from body_checks import BODIES,POWERTRAINS,expected_images,validate

class BodyGates(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
        cases=[f'body-{b}-{p}' for b in BODIES for p in POWERTRAINS]
        self.data={'result':'PASS complete','cases':cases,'screenshots':sorted(expected_images())}
        self.log='BODY_NATIVE_RESULT PASS complete\n'+'\n'.join('BODY_NATIVE_CASE_PASS '+c for c in cases)+'\n'+'\n'.join('BODY_NATIVE_DRIVE_PASS '+b for b in BODIES)
        (self.root/'screenshots').mkdir()
        for name in expected_images():(self.root/'screenshots'/name).write_bytes(b'\x89PNG\r\n\x1a\n'+bytes(8)+struct.pack('>II',1440,900)+bytes(1024))
        (self.root/'body-smoke-result.txt').write_text('PASS complete');self.save()
    def save(self):(self.root/'body-smoke-result.json').write_text(json.dumps(self.data))
    def test_complete(self):self.assertEqual(validate(self.log,self.root)['native_screenshots'],125)
    def test_duplicate_case(self):
        self.data['cases'][-1]=self.data['cases'][0];self.save()
        with self.assertRaises(ValueError):validate(self.log,self.root)
    def test_missing_view(self):
        self.data['screenshots'].pop();self.save()
        with self.assertRaises(ValueError):validate(self.log,self.root)
    def test_corrupt_view(self):
        (self.root/'screenshots'/self.data['screenshots'][0]).write_bytes(b'bad')
        with self.assertRaises(ValueError):validate(self.log,self.root)
    def test_failed_run(self):
        with self.assertRaises(ValueError):validate(self.log+'\nBODY_NATIVE_RESULT FAIL timeout',self.root)
    def test_missing_drive(self):
        with self.assertRaises(ValueError):validate(self.log.replace('BODY_NATIVE_DRIVE_PASS van',''),self.root)
