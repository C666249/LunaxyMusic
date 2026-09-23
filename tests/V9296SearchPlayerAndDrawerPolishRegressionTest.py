from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root/'app/src/main/java/com/xingyu/music/MainActivity.java').read_text(encoding='utf-8')
ui = (root/'app/src/main/java/com/xingyu/music/ui/Ui.java').read_text(encoding='utf-8')
dock = (root/'app/src/main/java/com/xingyu/music/ui/FluidToolDock.java').read_text(encoding='utf-8')
loader = (root/'app/src/main/java/com/xingyu/music/ui/ImageLoader.java').read_text(encoding='utf-8')
checks=[]
def check(c,m):
    if not c: raise AssertionError(m)
    checks.append(m)

home = main[main.find('private View homePage()'):main.find('private void maybeStartOnboarding()')]
search = main[main.find('private View searchPage()'):main.find('private void scheduleSearchSuggestions')]
history = main[main.find('private View searchHistoryStrip'):main.find('private View searchIntro()')]
player = main[main.find('private void buildNowPlaying()'):main.find('private void updateNowPlaying')]
hero = main[main.find('private void openNowPlayingFrom'):main.find('private Bitmap captureViewBitmap')]

check('recommendationHeader, marginTop(10)' in home, 'Home tightens recent-playback to recommendations vertical rhythm')
check('LinearLayout.LayoutParams rh = marginTop(9)' in search, 'Search recent content begins directly after recent-search divider')
check('resultsHolder.addView(searchIntro())' not in search, 'Search removes the large decorative empty-state island')
check('songList(history.subList(0, Math.min(6, history.size())), history), marginTop(1)' in search, 'Search empty state flows straight into recent playback rows')
check('AppearanceSystem.isLight()' in history and 'Color.argb(24, 70, 78, 96)' in history, 'Search divider remains visible in Moonlight')

check('playerIconRipple' in ui and 'new RippleDrawable' in ui, 'Player quiet controls use bounded accent ripple instead of permanent circles')
for label in ['back','starStudio','more','desktopLyricQuick','nowFavoriteButton','addToPlaylist','modeButton','prev','next','queueButton']:
    check(f'{label}.setBackground(Ui.playerIconRipple' in player, f'{label} rests as an icon-first player control')
check('play.setBackground(Ui.primaryFill' in player, 'Play/pause remains the single dominant filled transport')
check('playerControlSurface' not in player, 'Full player no longer renders a wall of persistent translucent utility circles')

check('Math.abs(target - start)' in dock and 'setPresentation' in dock, 'Tool drawer full travel remains trackable and interruptible')
check('float stagger =' in dock and 'float window =' in dock, 'Drawer children retain ordered progressive disclosure')
check('ValueAnimator.ofFloat(start, target)' in dock and 'Math.abs(target - start)' in dock, 'Drawer still retargets from current presentation state')

check('public static Bitmap peek(String url)' in loader, 'ImageLoader exposes already-cached original artwork for shared transitions')
check('ImageLoader.peek(heroSong.coverUrl)' in hero, 'Mini-to-full player hero prefers original cached artwork over low-resolution view capture')
check('int morphSize = Math.max(1, Math.round(artDiameter))' in hero and 'Ui.frame(morphSize, morphSize' in hero, 'Hero hardware layer is allocated at final record resolution instead of Mini resolution')
check('float startScaleX = sw / artDiameter' in hero and 'startScaleX + (1f - startScaleX) * artP' in hero, 'Final-resolution Hero scales up from Mini geometry without magnifying a low-resolution layer')
check('captureViewBitmap' in hero, 'Mini-to-full player retains a safe snapshot fallback when artwork is not cached')
check('appRoot.postOnAnimation' in hero and 'one final display frame' in hero, 'Mini-to-full artwork performs a one-frame sharp proxy-to-vinyl ownership handoff')

print(f'PASS: {len(checks)} V92.9.6 search/player/drawer polish regression checks')
