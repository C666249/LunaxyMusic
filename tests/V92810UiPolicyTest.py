from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/'app/src/main/java/com/xingyu/music/MainActivity.java').read_text(encoding='utf-8')
glass=(root/'app/src/main/java/com/xingyu/music/ui/RecommendationGlassDrawable.java').read_text(encoding='utf-8')
glow=(root/'app/src/main/java/com/xingyu/music/ui/CoverAmbientDrawable.java').read_text(encoding='utf-8')
checks=[]
def check(cond,msg):
    if not cond: raise AssertionError(msg)
    checks.append(msg)
check('STYLE_COUNT = 5' in glass,'five recommendation card styles')
for label in ['原版','柔光极光','棱镜边框','月雾磨砂','深空晶体']:
    check(label in main,f'style label {label}')
check('HOME_CARD_STYLE_KEY' in main and 'putInt(HOME_CARD_STYLE_KEY' in main,'style persisted')
check('recommendationSectionHeader()' in main and 'IconView.Type.PALETTE' in main,'icon-only style entry')
check('presentModal(sheet);' in main,'bottom drawer presentation')
check('new CoverAmbientDrawable' in main and 'recentCoverAmbientAccent(bitmap, accentColor)' in main,'recent cover colour halo')
check('installRecentCoverPhysics' in main and '1f - .035f * depth' in main and '1f - .10f * depth' in main,'subtle recent-cover physics')
check('setRotation' not in main[main.index('installRecentCoverPhysics'):main.index('private View searchPage')],'no 3D/rotation in recent-cover physics')
check('FluidTrackHaloDrawable homeSearchHalo' in main and 'bindPlaybackHalo(homeSearchHalo, quick)' in main,'home search uses playing-cover halo')
check('openSongTitleSearch(current.title)' in main,'V92.8.9 song-title direct search retained')
check('import android.graphics.RenderEffect' not in glass and 'import android.graphics.BlurMaskFilter' not in glass and 'import android.graphics.RenderEffect' not in glow,'no expensive real-time blur')
print(f'PASS: {len(checks)} V92.8.10 UI policy checks')
