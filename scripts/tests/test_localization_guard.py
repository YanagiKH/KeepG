import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('check_localization', Path(__file__).parents[1] / 'check_localization.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class TranslationSourceGuardTest(unittest.TestCase):
    def test_accepts_rows_inside_a_single_kotlin_literal(self):
        rows = module.read_rows('val dictionaries = parseBundle(\n    """\nHello\\t你好\\tこんにちは\\t안녕\n    """\n)')
        self.assertEqual('Hello', rows[0][0])

    def test_rejects_translation_rows_before_opening_quote(self):
        malformed = 'val dictionaries = parseBundle(\nHello\\t你好\\tこんにちは\\t안녕\n"""\nOther\\t其他\\tその他\\t기타\n"""\n)'
        with self.assertRaisesRegex(AssertionError, 'literal'):
            module.read_rows(malformed)

    def test_rejects_missing_closing_quote(self):
        with self.assertRaisesRegex(AssertionError, 'literal'):
            module.read_rows('val dictionaries = parseBundle(\n"""\nHello\\t你好\\tこんにちは\\t안녕\n)')

class LiteralUiGuardTest(unittest.TestCase):
    def check_with_label(self, expression):
        import shutil
        import subprocess
        import sys
        import tempfile
        root = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory() as tmp:
            destination = Path(tmp)
            shutil.copytree(root / 'app/src', destination / 'app/src')
            (destination / 'scripts').mkdir()
            shutil.copy2(root / 'scripts/check_localization.py', destination / 'scripts/check_localization.py')
            probe = destination / 'app/src/main/java/com/yanagikh/keepg/ui/GuardProbe.kt'
            probe.write_text('package com.yanagikh.keepg.ui\n@Composable fun GuardProbe() { ' + expression + ' }')
            return subprocess.run([sys.executable, str(destination / 'scripts/check_localization.py')], capture_output=True, text=True)

    def test_rejects_hardcoded_english_in_composable(self):
        for expression in ('Text("Untranslated label")', 'Text(text = "Untranslated label")', 'Text("${count} items")'):
            with self.subTest(expression=expression):
                result = self.check_with_label(expression)
                self.assertNotEqual(0, result.returncode, 'Hardcoded UI label was accepted: ' + expression)
                self.assertIn('Unlocalized Text', result.stderr)

    def test_accepts_localized_text_brand_and_numeric_labels(self):
        result = self.check_with_label('Text(tr("Settings")); Text("KeepG"); Text("90°"); Text("${speed}×")')
        self.assertEqual(0, result.returncode, result.stderr)
