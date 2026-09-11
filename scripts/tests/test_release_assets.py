import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('release_assets', Path(__file__).parents[1] / 'release_assets.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class ReleaseBoundaryTest(unittest.TestCase):
    def make_fixture(self, root):
        packages = []
        for name in sorted(module.expected_names('0.7.0')):
            file = root / name
            file.write_bytes(b'synthetic integrity-test fixture')
            packages.append({'name': name, 'sha256': module.sha256(file), 'size': file.stat().st_size})
        info = {'version': '0.7.0', 'commit': 'a' * 40, 'ci_run_id': '123', 'packages': packages}
        (root / 'BUILD-INFO.json').write_text(json.dumps(info))
        (root / 'SIGNATURES.txt').write_text('Signing is exercised by apksigner in Android CI, not this fixture.')
        files = sorted(root.iterdir())
        (root / 'SHA256SUMS.txt').write_text(''.join(f'{module.sha256(p)}  {p.name}\n' for p in files))
        return info

    def test_complete_unchanged_artifacts_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); self.make_fixture(root)
            module.verify_directory(root, 'a' * 40, '123')

    def test_missing_installer_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); info = self.make_fixture(root)
            (root / info['packages'][0]['name']).unlink()
            with self.assertRaises((AssertionError, ValueError, FileNotFoundError)):
                module.verify_directory(root, 'a' * 40, '123')

    def test_changed_package_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); info = self.make_fixture(root)
            (root / info['packages'][0]['name']).write_bytes(b'tampered')
            with self.assertRaises((AssertionError, ValueError)):
                module.verify_directory(root, 'a' * 40, '123')

    def test_different_source_or_run_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); self.make_fixture(root)
            with self.assertRaises((AssertionError, ValueError)):
                module.verify_directory(root, 'b' * 40, '123')
            with self.assertRaises((AssertionError, ValueError)):
                module.verify_directory(root, 'a' * 40, '124')

    def test_unlisted_extra_file_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); self.make_fixture(root)
            (root / 'unexpected.apk').write_bytes(b'unverified')
            with self.assertRaises((AssertionError, ValueError)):
                module.verify_directory(root, 'a' * 40, '123')
