from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root/'app/src/main/java/com/xingyu/music/MainActivity.java').read_text(encoding='utf-8')
ui = (root/'app/src/main/java/com/xingyu/music/ui/Ui.java').read_text(encoding='utf-8')
recommendation = (root/'app/src/main/java/com/xingyu/music/ui/RecommendationGlassDrawable.java').read_text(encoding='utf-8')
halo = (root/'app/src/main/java/com/xingyu/music/ui/FluidTrackHaloDrawable.java').read_text(encoding='utf-8')
checks=[]
def check(cond,msg):
    if not cond: raise AssertionError(msg)
    checks.append(msg)

home = main[main.find('private View homePage()'):main.find('private void maybeStartOnboarding()')]
header = main[main.find('private View recommendationSectionHeader()'):main.find('private void showRecommendationStyleDrawer()')]
player = main[main.find('private void buildNowPlaying()'):main.find('private void setNowPlayingArtistText')]

check('showAppearanceSheet()' not in home, 'standalone theme action removed from search field')
check('IconView.Type.LAYERS' in header and 'IconView.Type.PALETTE' in header and 'Ui.text(this, "卡片"' not in header and 'Ui.text(this, "主题"' not in header, 'recommendation header keeps icon-only card/theme actions')
check('this::showRecommendationStyleDrawer' in header and 'this::showAppearanceSheet' in header, 'progressive drawer routes both card and appearance actions')
check('option.setBackground(i == selected' in main and 'Ui.contentSurface(16, this)' in main, 'recommendation style picker stays legible on Moonlight')
check('segmentedSurface' in ui, 'theme-aware segmented control surface token exists')
check('playerControlSurface' in ui, 'theme-aware player control surface token exists')
check('playerControlIconColor' in ui, 'theme-aware player icon color token exists')
check('Ui.isLightAppearance()' in recommendation, 'recommendation material has dedicated Moonlight rendering')
check('boolean lightAppearance = Ui.isLightAppearance()' in recommendation and 'Moonlight has its own material' in recommendation, 'recommendation light palette is not dark material reused')
check('Ui.isLightAppearance()' in halo, 'current-track halo has dedicated Moonlight rendering')
check('lightFill' in halo or 'LIGHT_APPEARANCE' in halo, 'home search halo keeps a light surface in Moonlight')
check('animatePlayerDirectionalTap' in main, 'previous/next semantic motion helper exists')
check('animatePlayerModeTap' in main, 'play-mode semantic motion helper exists')
check('animatePlayerQueueTap' in main, 'queue semantic motion helper exists')
check('animatePlayerAddTap' in main, 'add-to-playlist semantic motion helper exists')
check('animateDesktopLyricTap' in main, 'desktop-lyric semantic motion helper exists')
check('animateSeekInteraction' in main, 'seek direct-manipulation feedback helper exists')
check(('Ui.playerControlSurface(Ui.CYAN' in player) or ('Ui.playerIconRipple(Ui.CYAN' in player), 'desktop lyric uses cyan semantic feedback')
check(('Ui.playerControlSurface(Ui.PINK' in player) or ('Ui.playerIconRipple(Ui.PINK' in player), 'favorite uses pink semantic feedback')
check(('Ui.playerControlSurface(Ui.PURPLE' in player) or ('Ui.playerIconRipple(Ui.PURPLE' in player), 'add-to-playlist uses purple semantic feedback')
check('Ui.playerControlIconColor(Ui.BLUE)' in player, 'previous/next use a readable blue semantic icon colour')
check('Ui.playerControlIconColor(Ui.PURPLE)' in player, 'back/top actions keep a readable brand semantic icon colour')
check('nowModeButton' in main and (('nowModeButton.setBackground(Ui.playerControlSurface' in main) or ('nowModeButton.setBackground(Ui.playerIconRipple' in main)), 'play mode semantic feedback stays synchronized across player/queue surfaces')
check('onStartTrackingTouch(SeekBar b) { animateSeekInteraction(b, true); }' in player, 'seek feedback begins with direct touch')
check('animateSeekInteraction(b, false);' in player, 'seek feedback settles on release')
check('Ui.applyRipple(card, Color.TRANSPARENT)' in main[main.find('private View recentCarousel'):main.find('private int recentCoverAmbientAccent')], 'recent-play cards acknowledge touch-down')
print(f'PASS: {len(checks)} V92.9.2 light/player interaction regression checks')
