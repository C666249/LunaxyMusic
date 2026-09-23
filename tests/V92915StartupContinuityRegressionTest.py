from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / 'app/src/main/java/com/xingyu/music/MainActivity.java').read_text(encoding='utf-8')
THEME31 = (ROOT / 'app/src/main/res/values-v31/themes.xml').read_text(encoding='utf-8')
GRADLE = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
checks = []
def check(name, cond): checks.append((name, bool(cond)))
oncreate = MAIN[MAIN.find('@Override protected void onCreate'):MAIN.find('private void scheduleRuntimeInitializationAfterFirstFrame')]
finish = MAIN[MAIN.find('private void finishStartupScene'):MAIN.find('private void loadUiPreferences')]
check('launch shell before runtime init', oncreate.find('buildShell();') >= 0 and oncreate.find('scheduleRuntimeInitializationAfterFirstFrame();') > oncreate.find('buildShell();'))
check('heavy init not before first Activity frame', 'new LibraryStore' not in oncreate and 'bindService(' not in oncreate)
check('runtime init waits two frames', 'appRoot.postOnAnimation(() -> appRoot.postOnAnimation(this::initializeRuntimeAfterFirstFrame))' in MAIN)
check('real Home starfield plus logo is first app scene', 'stars.setMotionSpeedMultiplier(SpringMotion.isReducedMotion() ? 1f : 4.8f);' in MAIN and 'startupLogo = new ImageView(this);' in MAIN)
check('framework splash handoff is short', 'setDuration(SpringMotion.isReducedMotion() ? 45L : 110L)' in MAIN and 'windowSplashScreenAnimationDuration">90<' in THEME31)
check('intentional app-owned starfield scene', '760L - Math.max(0L, SystemClock.uptimeMillis() - startupSceneStartedAtMs)' in MAIN)
check('no whole safeLayer refresh fade', 'safeLayer.animate().alpha(1f)' not in finish and 'safeLayer.setAlpha(1f);' in finish)
check('independent Home grow targets', 'prepareStartupHomeEntrance();' in finish and 'addStartupEntranceTarget(sections.getChildAt(j));' in MAIN)
check('bounded stagger growth', 'Math.min(i, 8) * 58L' in MAIN and '.scaleX(1f).scaleY(1f).translationY(0f)' in MAIN)
check('logo fades softly while Home grows', '.setStartDelay(70L).setDuration(590L).setInterpolator(SpringMotion.SOFT)' in finish)
check('starfield settles continuously', 'ValueAnimator.ofFloat(from, 1f)' in finish and 'startupStarSpeedAnimator.setDuration(1080L);' in finish)
check('voice and onboarding continue after handoff', 'continueAfterStartupScene();' in finish and 'handleVoiceLaunchIntent(getIntent());' in MAIN[MAIN.find('private void continueAfterStartupScene'):MAIN.find('private void prepareStartupHomeEntrance')])
stable = 'versionName = "92.9.15"' in GRADLE and 'versionCode = 995' in GRADLE
beta = 'versionName = "92.9.15-beta63"' in GRADLE and 'versionCode = 996' in GRADLE
check('V92.9.15 channel identity', stable or beta)
failed=[n for n,ok in checks if not ok]
if failed:
    print('FAIL:')
    [print(' -', n) for n in failed]
    raise SystemExit(1)
print(f'PASS: {len(checks)} V92.9.15 startup-continuity regression checks')
