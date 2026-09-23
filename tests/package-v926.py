from pathlib import Path
import hashlib, shutil, zipfile, sys, xml.etree.ElementTree as ET

stable = Path(__file__).resolve().parents[1]
beta = stable.with_name('LunaxyMusic-V92.6-Beta')
delivery = stable.parent / 'deliverables/LunaxyMusic-V92.6'
skip = {'build', '.gradle', '.idea', '.git', 'local.properties', 'SHA256SUMS.txt'}
def files(root):
    return [p for p in root.rglob('*') if p.is_file() and not any(x in skip for x in p.relative_to(root).parts)]
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
if '--prepare' in sys.argv:
    if beta.exists(): raise RuntimeError('Beta destination already exists')
    for p in files(stable):
        dst = beta / p.relative_to(stable); dst.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(p, dst)
    p = beta / 'app/build.gradle.kts'
    text = p.read_text(encoding='utf-8').replace('applicationId = "com.xingyu.music"', 'applicationId = "com.xingyu.music.beta1"').replace('versionCode = 934', 'versionCode = 935').replace('versionName = "92.6.0"', 'versionName = "92.6.0-beta32"').replace('Stable-92.6.0', 'Beta-92.6.0-beta32')
    p.write_text(text, encoding='utf-8')
    p = beta / 'app/src/main/res/values/strings.xml'
    p.write_text(p.read_text(encoding='utf-8').replace('>Lunaxy Music<', '>Lunaxy Music Beta<'), encoding='utf-8')
    p = beta / 'app/src/main/AndroidManifest.xml'
    p.write_text(p.read_text(encoding='utf-8').replace('@drawable/icon_stardust"', '@drawable/icon_stardust_beta"').replace('@drawable/icon_xm"', '@drawable/icon_stardust_beta"'), encoding='utf-8')
    shutil.copy2(stable.parent/'LunaxyMusic-V92.4-Beta/app/src/main/res/drawable-nodpi/icon_stardust_beta.png', beta/'app/src/main/res/drawable-nodpi/icon_stardust_beta.png')
    print(beta)
else:
    delivery.mkdir(parents=True, exist_ok=True)
    for p in (stable/'app/src/main/java').rglob('*.java'):
        assert sha(p) == sha(beta/p.relative_to(stable)), p
    for root, version, channel in [(stable, '92.6.0', 'Stable'), (beta, '92.6.0-beta32', 'Beta')]:
        for p in (root/'app/src/main').rglob('*.xml'): ET.parse(p)
        entries = sorted(files(root))
        manifest = root/'SHA256SUMS.txt'
        manifest.write_text(''.join(f'{sha(p)}  {p.relative_to(root).as_posix()}\n' for p in entries), encoding='utf-8')
        archive = delivery/(root.name+'.zip')
        with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
            for p in entries+[manifest]: z.write(p, root.name+'/'+p.relative_to(root).as_posix())
        with zipfile.ZipFile(archive) as z:
            assert z.testzip() is None
            for p in entries: assert hashlib.sha256(z.read(root.name+'/'+p.relative_to(root).as_posix())).hexdigest() == sha(p)
        shutil.copy2(root/'app/build/outputs/apk/debug/app-debug.apk', delivery/f'LunaxyMusic-{channel}-{version}-debug.apk')
    (delivery/'SHA256SUMS.txt').write_text(''.join(f'{sha(p)}  {p.name}\n' for p in sorted(delivery.iterdir()) if p.is_file() and p.name != 'SHA256SUMS.txt'), encoding='utf-8')
    print('Source parity, XML, ZIP CRC and SHA256 verified:', delivery)

