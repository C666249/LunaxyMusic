from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / 'app/src/main/java/com/xingyu/music/MainActivity.java').read_text(encoding='utf-8')
GRADLE = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')

checks = []
def check(name, cond):
    checks.append((name, bool(cond)))

start = MAIN.find('private void configureSystemSplashExit')
end = MAIN.find('private void beginStartupScene')
block = MAIN[start:end]
check('android 12 splash exit remains SDK guarded', 'Build.VERSION.SDK_INT < 31' in block)
check('framework splash callback remains installed', 'getSplashScreen().setOnExitAnimationListener' in block)
check('invalid SplashScreenView getView accessor removed', '.getView()' not in block)
check('callback animates SplashScreenView directly', 'splashView.animate().cancel()' in block and 'splashView.setPivotX' in block and 'splashView.setPivotY' in block)
check('framework SplashScreenView remove used at handoff', 'withEndAction(splashView::remove)' in block)
check('startup starfield handoff still present', 'beginStartupScene();' in MAIN and 'finishStartupScene' in MAIN)
check('shared playlist hero implementation retained', 'startPlaylistSharedTransition' in MAIN and 'PlaylistHeroMorphView' in MAIN)
m = re.search(r'versionCode\s*=\s*(\d+)', GRADLE)
check('version chain remains beyond the failed v92.9.9 build', m is not None and int(m.group(1)) >= 985)

failed = [name for name, ok in checks if not ok]
if failed:
    print('FAIL:')
    for name in failed:
        print(' -', name)
    raise SystemExit(1)
print(f'PASS: {len(checks)} V92.9.10 splash compile regression checks')
