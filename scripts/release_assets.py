#!/usr/bin/env python3
"""Package signed CI APKs and unsigned bundles; verify before any public release."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

EDITIONS = ('full', 'lite')
ABIS = ('arm64-v8a', 'armeabi-v7a', 'x86_64', 'universal')

def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()

def expected_names(version):
    return {f'KeepG-{version}-{edition}-{abi}-debug.apk' for edition in EDITIONS for abi in ABIS} | {
        f'KeepG-{version}-{edition}-unsigned.aab' for edition in EDITIONS}

def verify_directory(directory, commit, run_id):
    info = json.loads((directory / 'BUILD-INFO.json').read_text())
    assert re.fullmatch(r'[0-9a-f]{40}', commit), 'Invalid source commit'
    assert info['commit'] == commit and str(info['ci_run_id']) == str(run_id), 'Wrong source commit or CI run'
    version = info['version']
    assert re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', version), 'Invalid release version'
    expected = expected_names(version)
    packages = info['packages']
    assert len(packages) == len(expected) and {p['name'] for p in packages} == expected, 'Incomplete package matrix'
    files = expected | {'BUILD-INFO.json', 'SIGNATURES.txt', 'SHA256SUMS.txt'}
    assert {p.name for p in directory.iterdir()} == files, 'Missing or unverified extra release file'
    assert all((directory / name).is_file() and not (directory / name).is_symlink() for name in files), 'Invalid asset'
    for item in packages:
        file = directory / item['name']
        assert file.stat().st_size == item['size'] > 0, f'Wrong size: {file.name}'
        assert sha256(file) == item['sha256'], f'Wrong digest: {file.name}'
    checksums = {}
    for line in (directory / 'SHA256SUMS.txt').read_text().splitlines():
        digest, name = line.split('  ', 1)
        assert re.fullmatch(r'[0-9a-f]{64}', digest) and name not in checksums
        checksums[name] = digest
    assert set(checksums) == files - {'SHA256SUMS.txt'}, 'Incomplete checksum manifest'
    for name, digest in checksums.items():
        assert sha256(directory / name) == digest, f'Checksum mismatch: {name}'
    return info

def package(root, directory):
    assert not directory.exists() or not any(directory.iterdir()), 'Output directory must be empty'
    directory.mkdir(parents=True, exist_ok=True)
    version = re.search(r'versionName = "([0-9]+\.[0-9]+\.[0-9]+)"', (root / 'app/build.gradle.kts').read_text()).group(1)
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
    packages, signatures = [], []
    for edition in EDITIONS:
        source = root / f'app/build/outputs/apk/{edition}/debug'
        metadata = json.loads((source / 'output-metadata.json').read_text())
        application_id = 'com.yanagikh.keepg' + ('.lite' if edition == 'lite' else '')
        assert metadata['applicationId'] == application_id
        seen = set()
        for element in metadata['elements']:
            filters = element['filters']
            assert not filters or (len(filters) == 1 and filters[0]['filterType'] == 'ABI')
            abi = filters[0]['value'] if filters else 'universal'
            assert abi in ABIS and abi not in seen
            seen.add(abi)
            filename = element['outputFile']
            assert Path(filename).name == filename
            file = source / filename
            report = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(file)], text=True)
            badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(file)], text=True)
            assert f"package: name='{application_id}'" in badging
            name = f'KeepG-{version}-{edition}-{abi}-debug.apk'
            shutil.copyfile(file, directory / name)
            signatures.append(name + '\n' + report)
            packages.append({'name': name, 'edition': edition, 'abi': abi, 'kind': 'apk', 'signing': 'debug'})
        assert seen == set(ABIS), f'Missing APK ABI for {edition}'
        bundle = root / f'app/build/outputs/bundle/{edition}Release/app-{edition}-release.aab'
        assert zipfile.is_zipfile(bundle), f'Invalid bundle: {bundle}'
        name = f'KeepG-{version}-{edition}-unsigned.aab'
        shutil.copyfile(bundle, directory / name)
        packages.append({'name': name, 'edition': edition, 'kind': 'aab', 'signing': 'unsigned'})
    for item in packages:
        file = directory / item['name']
        item.update(size=file.stat().st_size, sha256=sha256(file))
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    info = {'commit': commit, 'ci_run_id': os.environ['GITHUB_RUN_ID'], 'version': version,
            'release_type': 'prerelease-debug', 'packages': packages}
    (directory / 'BUILD-INFO.json').write_text(json.dumps(info, indent=2) + '\n')
    (directory / 'SIGNATURES.txt').write_text('\n'.join(signatures))
    (directory / 'SHA256SUMS.txt').write_text(''.join(f'{sha256(p)}  {p.name}\n' for p in sorted(directory.iterdir())))
    verify_directory(directory, commit, os.environ['GITHUB_RUN_ID'])
    print(f'Verified {len(packages)} installation assets for {commit}')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['package', 'verify'])
    parser.add_argument('--directory', type=Path, default=Path('dist'))
    parser.add_argument('--commit')
    parser.add_argument('--run-id')
    args = parser.parse_args()
    if args.command == 'package':
        package(Path(__file__).resolve().parents[1], args.directory)
    else:
        verify_directory(args.directory, args.commit, args.run_id)
        print('Release assets match the verified commit, run, matrix and SHA-256 manifest')
