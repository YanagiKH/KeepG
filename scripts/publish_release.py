#!/usr/bin/env python3
"""Publish only complete artifacts from a successful, current-main Android CI run."""
import json
import os
from pathlib import Path
import re
import subprocess
from release_assets import sha256, verify_directory


def gh(*args):
    return subprocess.check_output(['gh', *args], text=True).strip()


def publish():
    repo, commit, run_id = (os.environ[key] for key in ('GITHUB_REPOSITORY', 'VERIFIED_SHA', 'VERIFIED_RUN_ID'))
    assert re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repo)
    directory = Path('dist')
    info = verify_directory(directory, commit, run_id)
    run = json.loads(gh('api', f'repos/{repo}/actions/runs/{run_id}'))
    assert run['conclusion'] == 'success' and run['event'] == 'push'
    assert run['head_branch'] == 'main' and run['head_sha'] == commit
    assert run['head_repository']['full_name'] == repo and run['path'] == '.github/workflows/android.yml'
    assert gh('api', f'repos/{repo}/git/ref/heads/main', '--jq', '.object.sha') == commit
    tag = f"v{info['version']}-verified-{commit[:12]}"
    endpoint = f'repos/{repo}/releases/tags/{tag}'
    result = subprocess.run(['gh', 'api', endpoint], text=True, capture_output=True)
    if result.returncode:
        if '404' not in result.stderr:
            raise RuntimeError('Could not safely inspect release status')
        notes = f'''KeepG {info['version']} — 自動驗證測試版

來源提交：`{commit}`；Android CI：{run_id}。

包含 Full／Lite 各四種 APK（arm64-v8a、armeabi-v7a、x86_64、universal），以及兩個未簽署 AAB。發布檔案直接取自通過驗證的 CI 產物，不另行重新建置。

APK 使用 CI 臨時 debug 憑證簽署，供側載測試，不是正式商店簽署版。不同 CI 執行的簽章可能不同；更新前請先匯出保險箱及備份資料。不要為解決簽章衝突直接解除安裝或清除資料，否則裝置金鑰與保險箱內容可能無法復原。AAB 需正式簽署後才能送交商店，不能直接安裝。

BUILD-INFO.json 記錄來源與版本，SIGNATURES.txt 記錄 APK 簽章驗證，SHA256SUMS.txt 用於下載完整性檢查。自動測試通過不代表所有實機、編解碼器或模型組合均已驗證；ARM 手機 Gemma 推論與 GPU 相容性仍須依 AI 手冊實機驗收。
'''
        Path('release-notes.md').write_text(notes)
        gh('release', 'create', tag, '--target', commit, '--title', f"KeepG {info['version']} verified ({commit[:7]})", '--notes-file', 'release-notes.md', '--draft', '--prerelease')
        release = json.loads(gh('api', endpoint))
    else:
        release = json.loads(result.stdout)
    assert release['target_commitish'] == commit, 'Existing release targets another commit'
    if release['draft']:
        gh('release', 'upload', tag, *[str(file) for file in sorted(directory.iterdir())], '--clobber')
    assets = json.loads(gh('api', f"repos/{repo}/releases/{release['id']}/assets?per_page=100"))
    assert {a['name'] for a in assets} == {p.name for p in directory.iterdir()}, 'Incomplete uploaded release'
    for asset in assets:
        file = directory / asset['name']
        assert asset['state'] == 'uploaded' and asset['size'] == file.stat().st_size
        assert asset['digest'] == 'sha256:' + sha256(file), f"Uploaded checksum mismatch: {file.name}"
    assert gh('api', f'repos/{repo}/git/ref/heads/main', '--jq', '.object.sha') == commit, 'Main advanced during upload'
    if release['draft']:
        gh('release', 'edit', tag, '--draft=false', '--prerelease')
    final = json.loads(gh('api', endpoint))
    assert not final['draft'] and final['prerelease'] and final['target_commitish'] == commit
    print(f"Published {len(assets)} verified assets: {final['html_url']}")


if __name__ == '__main__':
    publish()
