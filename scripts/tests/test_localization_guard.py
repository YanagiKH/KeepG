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
