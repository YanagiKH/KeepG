#!/usr/bin/env python3
"""Fail CI on untranslated literal UI keys, dynamic enums and resource key/format drift."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/yanagikh/keepg/ui/Localization.kt'
def read_rows(text):
    # Rows outside a Kotlin raw literal are not executable translations.
    match = re.search(r'parseBundle\(\s*"""(.*?)"""\s*\)', text, re.S)
    assert match and text.count('"""') == 2, 'Invalid Kotlin translation literal'
    outside = text[:match.start()] + text[match.end():]
    assert not any(r'\t' in line and 'split(' not in line for line in outside.splitlines()), 'Translation rows outside literal'
    return [line.strip().split(r'\t') for line in match.group(1).splitlines() if line.strip()]

rows = read_rows(source.read_text())
keys = set()
for row in rows:
    assert len(row) == 4 and all(row), row
    assert row[0] not in keys, f'Duplicate translation: {row[0]}'
    keys.add(row[0])
    for value in row[1:]:
        assert re.findall(r'%\d*\$?[sdf]', row[0]) == re.findall(r'%\d*\$?[sdf]', value), row
# App labels must use the same translation boundary as widget resources.
# Technical brands/units are intentional literals; user content is not translated.
def unlocalized_text_literals(text):
    allowed = {'KeepG', 'K', 'GIF', 'jpg · png · gif · mp4 · webm …'}
    found = []
    for match in re.finditer(r'(?<![\w.])Text\s*\(\s*(?:text\s*=\s*)?"((?:\\.|[^"\\])*)"', text):
        value = match.group(1)
        if value in allowed:
            continue
        if '$' in value:
            # Dynamic filenames/numbers are data. Natural-language count labels
            # still require trf rather than English suffix interpolation.
            if re.search(r'\}\s+items\b', value):
                found.append(value)
        elif re.search(r'[A-Za-z]', value):
            found.append(value)
    return found

required = set()
unlocalized = []
for path in (root / 'app/src').rglob('*.kt'):
    if any(part in ('test', 'androidTest') for part in path.parts):
        continue
    text = path.read_text()
    unlocalized.extend(f"{path.relative_to(root)}: {value}" for value in unlocalized_text_literals(text))
    required.update(re.findall(r'(?:trf?|localized|issue|EditorSlider|AgentFailure)\("([^"\n$]+)"', text))
    if path.name in ('AgentPolicy.kt', 'GallerySettingsScreen.kt', 'ProfessionalImageEditor.kt'):
        required.update(re.findall(r'\b[A-Z][A-Z_]+\("([^"\n]+)"', text))
assert not unlocalized, "Unlocalized Text:\n" + "\n".join(unlocalized)
# Dynamic labels are not fully inferable with regex; keep their catalog explicit.
required.update(['System', 'Light', 'Dark', 'Chat', 'Models', 'Skills', 'Gemma 4 E2B · Light', 'Gemma 4 E4B · Standard'])
missing = sorted(required - keys)
assert not missing, 'Missing UI translations:\n' + '\n'.join(missing)
res = root / 'app/src/main/res'
def names(path):
    return {item.attrib['name'] for item in ET.parse(path).getroot() if item.tag in ('string', 'plurals', 'string-array')}
base = names(res / 'values/strings.xml')
for directory in ('values-zh-rTW', 'values-ja', 'values-ko'):
    assert names(res / directory / 'strings.xml') == base, f'Resource key drift: {directory}'
print(f'Localization OK: {len(keys)} UI keys, 4 languages, literal/dynamic-label coverage and resource parity')
