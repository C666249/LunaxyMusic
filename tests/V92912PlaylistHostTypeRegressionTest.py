from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / 'app/src/main/java/com/xingyu/music/MainActivity.java').read_text(encoding='utf-8')
GRADLE = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')

checks=[]
def check(name, cond): checks.append((name, bool(cond)))

check('playlist detail song host is a ViewGroup-capable type', 'private LinearLayout activePlaylistDetailSongHost;' in MAIN)
check('shared choreography still enumerates song children', 'activePlaylistDetailSongHost.getChildCount()' in MAIN and 'activePlaylistDetailSongHost.getChildAt(i)' in MAIN)
view_fields=set(re.findall(r'private\s+View\s+(\w+)\s*;', MAIN))
bad=[]
for field in sorted(view_fields):
    if re.search(r'\b'+re.escape(field)+r'\.(?:getChildCount|getChildAt|addView|removeView|removeAllViews)\s*\(', MAIN):
        bad.append(field)
check('no field declared as plain View is directly used with ViewGroup child APIs', not bad)
check('V92.9.11 shared transition retained', 'preparePlaylistDetailForSharedEntrance' in MAIN and 'applyPlaylistDetailChoreography' in MAIN)
check('curtain loading retained', 'CurtainRevealFrame' in MAIN and 'appendPlaylistPlaceholderBatch' in MAIN)
version_code_match=re.search(r'versionCode\s*=\s*(\d+)', GRADLE)
version_name_match=re.search(r'versionName\s*=\s*"([^"]+)"', GRADLE)
version_code=int(version_code_match.group(1)) if version_code_match else 0
version_name=version_name_match.group(1) if version_name_match else ''
check('version chain remains advanced past V92.9.11', version_code >= 990 and version_name.startswith('92.9.'))

failed=[n for n,ok in checks if not ok]
if failed:
    print('FAIL:')
    for n in failed: print(' -',n)
    raise SystemExit(1)
print(f'PASS: {len(checks)} V92.9.12 playlist host type regression checks')
