from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT/'app/src/main/java/com/xingyu/music/MainActivity.java').read_text(encoding='utf-8')
HERO = (ROOT/'app/src/main/java/com/xingyu/music/ui/PlaylistHeroMorphView.java').read_text(encoding='utf-8')
CURTAIN = (ROOT/'app/src/main/java/com/xingyu/music/ui/CurtainRevealFrame.java').read_text(encoding='utf-8')

checks=[]
def check(name, cond):
    checks.append((name, bool(cond)))

check('playlist hero grows to visibly larger detail artwork', 'playlistThumb(p, 88)' in MAIN and '21.2f' in MAIN)
check('destination chrome is hidden before shared travel', 'preparePlaylistDetailForSharedEntrance' in MAIN and 'activePlaylistDetailTitleRow.setAlpha(0f)' in MAIN)
check('shared transition uses staged title subtitle back header', all(x in MAIN for x in ['titleP = playlistStage', 'subP = playlistStage', 'backP = playlistStage', 'headerP = playlistStage']))
check('song skeletons enter with staggered vertical travel', 'i * .035f' in MAIN and 'child.setTranslationY' in MAIN)
check('shared transition is deliberately readable', 'Math.round(840L * distance)' in MAIN)
check('detail stays opaque while pieces arrive', 'detail.setAlpha(1f)' in MAIN and 'applyPlaylistDetailChoreography(p)' in MAIN)
check('library root recedes only after card starts moving', 'playlistStage(p, .16f, .78f)' in MAIN)
check('hero overlay has reversible flight arc', 'Math.sin(Math.PI * p)' in HERO and 'offset is exactly zero at both endpoints' in HERO)
check('hero overlay carries mid-flight aura', 'airborne' in HERO and 'aura.setColor' in HERO)
check('real playlist rows wait for hero choreography', 'firstMaterializeDelay' in MAIN and '560L' in MAIN)
check('real rows cascade rather than resolve on consecutive frames', 'host.postDelayed(step[0], 62L)' in MAIN)
check('curtain is slow enough to read', 'animator.setDuration(470L)' in CURTAIN)
check('curtain exchanges placeholder and content spatially', 'placeholderLeft' in CURTAIN and 'contentRight' in CURTAIN)
check('curtain has a travelling feather seam', 'LinearGradient' in CURTAIN and 'seamPaint' in CURTAIN)
check('curtain combines wipe with subtle fade and vertical settle', 'view.setAlpha(.58f + .42f * progress)' in CURTAIN and 'view.setTranslationY(dp(9) * (1f - progress))' in CURTAIN)
check('reverse path reuses one shared progress', 'animatePlaylistSharedProgress(0f)' in MAIN and 'activePlaylistSharedProgress' in MAIN)

failed=[n for n,ok in checks if not ok]
if failed:
    print('FAIL:')
    for n in failed: print(' -',n)
    raise SystemExit(1)
print(f'PASS: {len(checks)} V92.9.11 playlist choreography regression checks')
