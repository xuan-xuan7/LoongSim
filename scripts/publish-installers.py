"""Verify and replace release installers, with explicit opt-in for unsigned releases."""
import hashlib
import json
import os
from pathlib import Path, PureWindowsPath
import sys
import urllib.parse
import urllib.request


def main():
    repository = os.environ['GITHUB_REPOSITORY']
    token = os.environ['GITHUB_TOKEN']
    commit = os.environ['GITHUB_SHA']
    allow_unsigned = os.environ.get('ALLOW_UNSIGNED_RELEASE') == 'true'
    directory = Path(sys.argv[1])
    backup = Path(sys.argv[2])
    tag = os.environ.get('RELEASE_TAG', 'v1.0.0-logisim-only')
    base = f'https://api.github.com/repos/{repository}'
    headers = {'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json',
               'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'LoongSim-release'}

    def api(url, method='GET', payload=None, binary=None):
        request_headers = dict(headers)
        data = None
        if payload is not None:
            data = json.dumps(payload).encode()
            request_headers['Content-Type'] = 'application/json'
        if binary is not None:
            data = binary.read_bytes()
            request_headers['Content-Type'] = 'application/octet-stream'
        request = urllib.request.Request(url, data=data, headers=request_headers, method=method)
        with urllib.request.urlopen(request, timeout=600) as response:
            body = response.read()
            return json.loads(body) if body else None

    def leaf(name):
        return PureWindowsPath(name).name if '\\' in name else Path(name).name

    manifests = [json.loads(p.read_text(encoding='utf-8-sig')) for p in directory.rglob('*-build.json')]
    required = {'windows-x64', 'macos-x64', 'macos-arm64'}
    if {m['platform'] for m in manifests} != required or len(manifests) != 3:
        raise SystemExit('Expected Windows, Intel Mac and Apple Silicon Mac builds')
    if len({m['jarSha256'] for m in manifests}) != 1:
        raise SystemExit('Installers must contain the same application JAR')
    files = []
    for manifest in manifests:
        if manifest['sourceCommit'] != commit:
            raise SystemExit('Mismatched-source installers cannot be published')
        if not manifest.get('signed') and not allow_unsigned:
            raise SystemExit('Unsigned release requires ALLOW_UNSIGNED_RELEASE=true')
        if manifest['platform'].startswith('macos-') and manifest.get('signed') and not manifest.get('notarized'):
            raise SystemExit('Mac installer notarization must be accepted')
        names = manifest.get('installers', [manifest.get('installer')])
        for name in names:
            filename = leaf(name)
            if ('unsigned' in filename) != (not manifest.get('signed')):
                raise SystemExit('Installer filename does not match its signing status')
            matches = list(directory.rglob(filename))
            if len(matches) != 1:
                raise SystemExit(f'Missing or ambiguous installer: {filename}')
            file = matches[0]
            checksum = Path(str(file) + '.sha256')
            digest = hashlib.sha256(file.read_bytes()).hexdigest()
            if not checksum.exists() or checksum.read_text().split()[0] != digest:
                raise SystemExit(f'Installer checksum mismatch: {filename}')
            files.extend([file, checksum])

    clean_info = {'sourceCommit': commit, 'jarSha256': manifests[0]['jarSha256'], 'platforms': []}
    for m in manifests:
        clean_info['platforms'].append({key: m[key] for key in ('platform', 'version', 'signed', 'notarized') if key in m})
    info = directory / 'LoongSim-build-info.json'
    info.write_text(json.dumps(clean_info, indent=2) + '\n')
    files.append(info)
    release = api(f'{base}/releases/tags/{urllib.parse.quote(tag)}')
    backup.mkdir(parents=True, exist_ok=True)
    (backup / 'release.json').write_text(json.dumps(release, indent=2))
    new_names = {p.name for p in files}
    old = [a for a in release['assets'] if 'windows' in a['name'].lower() or a['name'] in new_names]
    for asset in old:
        request_headers = dict(headers, Accept='application/octet-stream')
        request = urllib.request.Request(f"{base}/releases/assets/{asset['id']}?download=1", headers=request_headers)
        with urllib.request.urlopen(request, timeout=600) as response:
            content = response.read()
        if len(content) != asset['size']:
            raise SystemExit(f"Backup incomplete: {asset['name']}")
        (backup / asset['name']).write_bytes(content)

    renamed, uploaded = [], []
    try:
        for asset in old:
            if asset['name'] in new_names:
                api(f"{base}/releases/assets/{asset['id']}", 'PATCH',
                    {'name': f"previous-{os.environ.get('GITHUB_RUN_ID', 'backup')}-{asset['name']}"})
                renamed.append(asset)
        upload_url = release['upload_url'].split('{')[0]
        for file in files:
            asset = api(upload_url + '?name=' + urllib.parse.quote(file.name), 'POST', binary=file)
            uploaded.append(asset)
            expected = hashlib.sha256(file.read_bytes()).hexdigest()
            if asset['size'] != file.stat().st_size or asset.get('digest', 'sha256:' + expected) != 'sha256:' + expected:
                raise RuntimeError(f'Remote upload verification failed: {file.name}')
        signed_release = all(m.get('signed') for m in manifests)
        signing_notes = (
            '- Windows 启动器和安装包均经过 Authenticode 签名及时间戳验证。\n'
            '- Mac 应用及 DMG 均经过 Developer ID 签名、Apple 公证和票据装订。\n'
            if signed_release else
            '- 本次安装包尚未进行可信代码签名；Mac 也尚未进行 Apple 公证。\n'
            '- Windows 首次安装可能显示发布者未知或 SmartScreen 提示。\n'
            '- Mac 首次打开可能被系统拦截；确认来源后，可在系统设置 → 隐私与安全性中允许打开。\n'
        )
        body = ('## LoongSim 教学界面更新\n\n'
                '包含新的应用图标、组件分类、简化属性、组件样式和五端口计数器。\n\n'
                '- Windows：下载 setup.exe 双击安装，或选择 MSI 安装包；内置 Java 运行时。\n'
                '- Mac：Apple Silicon 下载 arm64 DMG；Intel Mac 下载 x64 DMG，拖动应用到 Applications。\n'
                + signing_notes + '\n'
                f'安装包源码：[本次构建提交](https://github.com/{repository}/commit/{commit})。\n'
                'SHA256 校验文件和 LoongSim-build-info.json 与安装包一同提供。\n')
        api(f"{base}/releases/{release['id']}", 'PATCH', {'body': body})
    except Exception:
        for asset in uploaded:
            api(f"{base}/releases/assets/{asset['id']}", 'DELETE')
        for asset in renamed:
            api(f"{base}/releases/assets/{asset['id']}", 'PATCH', {'name': asset['name']})
        raise
    for asset in old:
        api(f"{base}/releases/assets/{asset['id']}", 'DELETE')
    print('Verified installers published; previous Windows assets removed.')


if __name__ == '__main__':
    main()
