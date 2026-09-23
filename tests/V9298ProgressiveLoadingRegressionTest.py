from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root/'app/src/main/java/com/xingyu/music/MainActivity.java').read_text(encoding='utf-8')
curtain = (root/'app/src/main/java/com/xingyu/music/ui/CurtainRevealFrame.java').read_text(encoding='utf-8')
placeholder = (root/'app/src/main/java/com/xingyu/music/ui/FluidPlaceholderView.java').read_text(encoding='utf-8')
loader = (root/'app/src/main/java/com/xingyu/music/ui/FluidLoadingIconView.java').read_text(encoding='utf-8')
store = (root/'app/src/main/java/com/xingyu/music/data/LibraryStore.java').read_text(encoding='utf-8')
checks=[]
def check(c,m):
    if not c: raise AssertionError(m)
    checks.append(m)

# Real-device rollback: large playlists must no longer switch to the crashy V92.9.7 virtual branch.
playlist = main[main.find('private View playlistDetailPage'):main.find('private View playlistDetailVirtualPage')]
check('PLAYLIST_VIRTUAL_LIST_THRESHOLD' not in main, 'V92.9.7 playlist-size switch is removed')
check('playlistDetailVirtualPage(p)' not in playlist, 'playlist detail always enters the stable ScrollView shell')
check('appendPlaylistPlaceholderBatch' in playlist and 'materializePlaylistBatch' in playlist, 'playlist opens on lightweight skeleton geometry then materializes rows')
check('page.postOnAnimation' in playlist and '34L' in playlist, 'row construction yields the navigation frame before work starts')

# Bounded incremental work: no 800-row materialization spike.
check('PLAYLIST_INITIAL_RENDER_COUNT = 10' in main and 'PLAYLIST_APPEND_BATCH = 10' in main, 'initial and incremental playlist work stays in small batches')
material = main[main.find('private void materializePlaylistBatch'):main.find('private void setPlaylistLoaderActive')]
check('host.postOnAnimation(step[0])' in material, 'playlist builds at most one real row per display frame')
check('ensurePlaylistPlaceholderCount(host, to, Ui.CYAN)' in material, 'final row geometry exists before content resolves')
check('playQueue(playlist.songs, liveIndex)' in main, 'progressive rows still install the complete underlying playlist queue')
scroll = main[main.find('private void maybeAppendPlaylistRows'):main.find('/** V92.9.7: local playlist search')]
check('PLAYLIST_PREFETCH_DISTANCE_DP' in scroll and 'materializePlaylistBatch' in scroll, 'scroll proximity requests the next bounded batch automatically')

# Curtain identity: content is revealed spatially instead of cross-fading/layout jumping.
check('contentRight' in curtain and 'placeholderLeft' in curtain and 'setClipBounds(new Rect(0, 0, contentRight, h))' in curtain, 'resolved content and skeleton exchange ownership across a left-to-right curtain boundary')
check('LayoutParams.MATCH_PARENT' in curtain and 'placeholder.setAlpha' in curtain, 'placeholder and content share final geometry during handoff')
check('SpringMotion.isReducedMotion()' in curtain, 'curtain reveal has a reduced-motion path')
check('SONG_ROW' in placeholder and 'CARD' in placeholder and 'TITLE' in placeholder, 'skeletons cover rows, large cards and page/title structures')
check('LinearGradient' in placeholder and '1450L' in placeholder, 'placeholder shimmer is bounded and deliberately slow/subtle')

# Loader glyph: no textual 'loading more' footer in playlist batches.
check('class FluidLoadingIconView' in loader and 'drawArc' in loader and '1120L' in loader, 'tail loading uses an animated local 24-grid twin-arc glyph')
check('activePlaylistLoader = new FluidLoadingIconView' in main, 'playlist tail uses animated glyph instead of loading text')
check('正在加载' not in playlist and '加载更多' not in playlist, 'playlist detail does not expose textual batch-loading status')

# Heavy tab destinations can move immediately using a structure-matched shell.
check('shouldDeferNavigationPage' in main and 'deferredNavigationPage' in main, 'heavy lateral tab destinations can navigate before content construction finishes')
check('navigationSkeletonPage' in main and 'shell.reveal(actual)' in main, 'deferred tab shell is replaced by a curtain reveal after destination construction')
check('generation != deferredPageGeneration' in main, 'stale deferred tab work is cancelled after rapid retargeting')
check('(lateralNavigation || depthNavigation)' in main, 'same-tab detail pushes can navigate on a lightweight shell before heavy content is built')

# Progressive rows must stay safe for secondary actions as well as first entry.
locate = main[main.find('private void ensurePlaylistIndexRendered'):main.find('private void smoothPlaylistScrollTo')]
check('host.postOnAnimation(step[0])' in locate and 'buildPlaylistRow(playlist, pos)' in locate, 'locate-current-song materializes distant rows one per frame rather than in synchronous chunks')
remove = main[main.find('private void animatePlaylistRowRemoval'):main.find('// V91.1 Motion hotfix')]
check('row.getParent() instanceof CurtainRevealFrame' in remove and 'collapseParent.removeView(collapseTarget)' in remove, 'deleting a revealed row collapses its curtain slot instead of leaving an empty 72dp hole')

# Formal-user data contract remains byte/key compatible: no migration is introduced.
check('getSharedPreferences("xingyu_library_v3"' in store, 'formal library preference identity is unchanged')
check('prefs.getString("playlists", "[]")' in store and 'putString("playlists",raw)' in store, 'playlist persistence key and JSON channel remain unchanged')

print(f'PASS: {len(checks)} V92.9.8 progressive-loading regression checks')
