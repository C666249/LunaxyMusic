from pathlib import Path
import hashlib, os, subprocess, xml.etree.ElementTree as ET

stable = Path(__file__).resolve().parents[1]
beta = stable.with_name('LunaxyMusic-V92.8.1-Beta')
out = stable.parent / 'deliverables' / 'LunaxyMusic-V92.8.1'
skip_dirs = {'build', '.gradle', '.idea', '.git'}
skip_files = {'local.properties', 'SHA256SUMS.txt'}

def files(root):
    result=[]
    for p in root.rglob('*'):
        if not p.is_file(): continue
        rel=p.relative_to(root)
        if any(part in skip_dirs for part in rel.parts) or p.name in skip_files: continue
        result.append(p)
    return sorted(result, key=lambda p: os.fsencode(str(p.relative_to(root))))

def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024), b''): h.update(chunk)
    return h.hexdigest()

def write_manifest(root):
    entries=files(root)
    manifest=root/'SHA256SUMS.txt'
    with manifest.open('wb') as f:
        for p in entries:
            rel=os.fsencode(str(p.relative_to(root)))
            f.write(sha(p).encode('ascii')+b'  '+rel+b'\n')
    return entries, manifest

# Functional Java source must be byte-identical across channels.
for p in (stable/'app/src/main/java').rglob('*.java'):
    other=beta/p.relative_to(stable)
    assert other.is_file() and sha(p)==sha(other), f'Java parity failed: {p}'

for root in (stable,beta):
    for p in (root/'app/src/main').rglob('*.xml'): ET.parse(p)
    for required in ['WORKFLOW.md','DELIVERY_RULE.txt','PROJECT_STATE.md','RELEASE_NOTES_V92.8.1.md','APK_BUILD_STATUS.txt','dev-logs/V92.8.1-validation.md']:
        assert (root/required).is_file(), f'missing {required} in {root.name}'

main=(stable/'app/src/main/java/com/xingyu/music/MainActivity.java').read_text()
assert 'PLAYER_OPEN_MORPH_DURATION_MS = 900L' in main
assert 'PLAYER_GESTURE_OPEN_SETTLE_MS = 520L' in main
assert 'PLAYER_GESTURE_CLOSE_SETTLE_MS = 440L' in main
assert 'hero.setDuration(PLAYER_OPEN_MORPH_DURATION_MS)' in main

sgrad=(stable/'app/build.gradle.kts').read_text()
bgrad=(beta/'app/build.gradle.kts').read_text()
assert 'applicationId = "com.xingyu.music"' in sgrad
assert 'versionCode = 944' in sgrad and 'versionName = "92.8.1"' in sgrad
assert 'applicationId = "com.xingyu.music.beta1"' in bgrad
assert 'versionCode = 945' in bgrad and 'versionName = "92.8.1-beta37"' in bgrad
assert '>Lunaxy Music Beta<' in (beta/'app/src/main/res/values/strings.xml').read_text()
assert '@drawable/icon_stardust_beta' in (beta/'app/src/main/AndroidManifest.xml').read_text()
assert (beta/'app/src/main/res/drawable-nodpi/icon_stardust_beta.png').is_file()

out.mkdir(parents=True, exist_ok=True)
for old in out.glob('*'):
    if old.is_file(): old.unlink()

for root in (stable,beta):
    write_manifest(root)
    archive=out/(root.name+'.zip')
    cmd=['zip','-rq',str(archive),root.name,'-x','*/build/*','*/.gradle/*','*/.idea/*','*/.git/*','*/local.properties']
    subprocess.run(cmd,cwd=root.parent,check=True)
    test=subprocess.run(['unzip','-tqq',str(archive)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    assert test.returncode==0, test.stdout.decode('utf-8','replace')

with (out/'SHA256SUMS.txt').open('w',encoding='utf-8') as f:
    for p in sorted(out.glob('*.zip')):
        f.write(f'{sha(p)}  {p.name}\n')
print(out)
