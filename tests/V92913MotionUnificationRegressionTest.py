from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT/'app/src/main/java/com/xingyu/music/MainActivity.java').read_text(encoding='utf-8')
CURTAIN = (ROOT/'app/src/main/java/com/xingyu/music/ui/CurtainRevealFrame.java').read_text(encoding='utf-8')
PLACEHOLDER = (ROOT/'app/src/main/java/com/xingyu/music/ui/FluidPlaceholderView.java').read_text(encoding='utf-8')

checks=[]
def check(name, cond):
    checks.append((name, bool(cond)))

check('daily/search detail bypass full-screen curtain',
      'openPlaylist != null || openSmartCollection != null' in MAIN and '(targetTab == 1 && searchAllResultsOpen)' in MAIN)
check('smart collections use row curtain staircase',
      'private View smartCollectionSongList(SmartCollection collection)' in MAIN and 'frame.reveal(row, 48L + stagedIndex * 62L)' in MAIN)
check('smart collection enrichment does not replay resolved rows',
      'smartCollectionRevealKeys' in MAIN and 'smartCollectionRevealScope' in MAIN)
check('search all rows use the same CurtainRevealFrame primitive',
      'CurtainRevealFrame frame;' in MAIN and 'frame.reveal(holder.wrapper' in MAIN and 'position * 58L' in MAIN)
check('search recycler can settle recycled rows immediately', 'showImmediately(holder.wrapper)' in MAIN and 'public void showImmediately(View view)' in CURTAIN)
check('placeholder shimmer supports per-row phase staggering', 'setShimmerStartDelay' in PLACEHOLDER and 'setStartDelay(shimmerStartDelayMs)' in PLACEHOLDER)
check('shared playlist waits for destination geometry', 'geometryAttempt[0]++ < 5' in MAIN and 'incoming.postOnAnimation(prepareRef[0])' in MAIN)
check('shared proxy takes ownership before source is hidden',
      MAIN.find('morph.setProgress(push ? 0f : 1f);') < MAIN.find('if (listRow != null) listRow.setAlpha(0f);'))
check('shared playlist growth is slower/readable', 'Math.round(840L * distance)' in MAIN)

failed=[n for n,ok in checks if not ok]
if failed:
    print('FAIL:')
    for n in failed: print(' -', n)
    raise SystemExit(1)
print(f'PASS: {len(checks)} V92.9.13 motion-unification regression checks')
