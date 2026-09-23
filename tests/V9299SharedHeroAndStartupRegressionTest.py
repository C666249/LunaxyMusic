from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / 'app/src/main/java/com/xingyu/music/MainActivity.java').read_text(encoding='utf-8')
HERO = (ROOT / 'app/src/main/java/com/xingyu/music/ui/PlaylistHeroMorphView.java').read_text(encoding='utf-8')
STAR = (ROOT / 'app/src/main/java/com/xingyu/music/ui/StarfieldView.java').read_text(encoding='utf-8')
THEME = (ROOT / 'app/src/main/res/values/themes.xml').read_text(encoding='utf-8')
THEME31 = (ROOT / 'app/src/main/res/values-v31/themes.xml').read_text(encoding='utf-8')
COLORS = (ROOT / 'app/src/main/res/values/colors.xml').read_text(encoding='utf-8')

checks = []
def check(name, cond):
    checks.append((name, bool(cond)))

# Shared playlist identity / geometry
check('playlist click uses shared hero entry', 'openPlaylistWithSharedHero(p, row, thumb, title, meta)' in MAIN)
check('source geometry captured in app-root coordinates', 'capturePlaylistHeroSnapshot' in MAIN and 'rectInAppRoot(row)' in MAIN)
check('playlist detail exposes hero card ref', 'activePlaylistHeroCard = hero;' in MAIN)
check('playlist detail exposes hero artwork ref', 'activePlaylistHeroThumb = heroThumb;' in MAIN)
check('playlist detail exposes title ref', 'activePlaylistHeroTitle = name;' in MAIN)
check('destination detail geometry persisted for reverse', 'snapshot.detailContainer.set(card);' in MAIN and 'snapshot.hasDetailGeometry = true;' in MAIN)
check('playlist detail bypasses redundant deferred skeleton', 'openPlaylist != null || openSmartCollection != null' in MAIN and 'searchAllResultsOpen' in MAIN and 'return false;' in MAIN)
check('shared navigation has dedicated render path', 'startPlaylistSharedTransition(outgoing, page, push);' in MAIN)
check('reverse during unfinished push retargets same progress', 'activePlaylistTransitionPush' in MAIN and 'animatePlaylistSharedProgress(0f);' in MAIN)
check('back captures detail geometry before root rebuild', 'capturePlaylistDetailHeroGeometry(playlistHeroSnapshot);' in MAIN)
check('reselecting playlist tab uses shared reverse', 'target == tab && target == 2 && openPlaylist != null' in MAIN and 'closePlaylistToLibrary();' in MAIN)
check('page content is staged around shared hero instead of full-screen crossfade', 'applyPlaylistDetailChoreography' in MAIN and 'detail.setAlpha(1f)' in MAIN and 'source recedes while individual detail elements arrive' in MAIN)
check('high-res artwork prefers ImageLoader cache', 'ImageLoader.peek(snapshot.artworkUrl)' in MAIN)
check('no tiny-thumbnail raster upscale in playlist helper', 'Never rasterize the 52dp thumbnail' in MAIN and 'captureViewBitmap(fallbackArtwork)' not in MAIN)
check('shared overlay owns full container cover and labels', all(x in HERO for x in ['sourceContainer', 'sourceArtwork', 'sourceTitle', 'sourceMeta']))
check('shared overlay uses bitmap shader', 'BitmapShader' in HERO and 'shader.setLocalMatrix' in HERO)
check('shared overlay geometry is continuous', 'lerpRect(sourceContainer, targetContainer' in HERO and 'lerpRect(sourceArtwork, targetArtwork' in HERO)

# Progressive detail behavior remains intact
check('playlist skeleton remains first paint', 'appendPlaylistPlaceholderBatch' in MAIN)
check('playlist rows still materialize progressively', 'materializePlaylistBatch' in MAIN and 'PLAYLIST_INITIAL_RENDER_COUNT' in MAIN)
check('curtain reveal remains row handoff', 'CurtainRevealFrame.reveal' in MAIN or 'CurtainRevealFrame' in MAIN)
check('heavy playlist detail does not use v9297 size ListView branch', 'if (p.songs.size() >=' not in MAIN[MAIN.find('private View playlistDetailPage'):MAIN.find('private View playlistDetailVirtualPage')])

# Startup continuity and cold-start IO
oncreate = MAIN[MAIN.find('@Override protected void onCreate'):MAIN.find('@Override protected void onNewIntent')]
check('onCreate builds shell before library hydration', oncreate.find('buildShell();') < oncreate.find('bootstrapLibraryAndFirstPage();'))
check('onCreate no synchronous reloadLibrary', 'reloadLibrary();' not in oncreate)
check('library bootstrap runs on io executor', 'io.submit(() -> {' in MAIN[MAIN.find('private void bootstrapLibraryAndFirstPage'):MAIN.find('private void finishStartupScene')])
check('playlist decode is off main thread', 'nextPlaylists = store.playlists();' in MAIN[MAIN.find('private void bootstrapLibraryAndFirstPage'):MAIN.find('private void finishStartupScene')])
check('startup begins as starfield scene', 'beginStartupScene();' in oncreate and 'stars.setMotionSpeedMultiplier' in MAIN)
check('startup star speed settles into home starfield', 'ValueAnimator.ofFloat(from, 1f)' in MAIN and 'stars.setMotionSpeedMultiplier((Float) a.getAnimatedValue())' in MAIN)
check('starfield supports launch speed multiplier', 'setMotionSpeedMultiplier' in STAR and 'forwardSpeed = .052f * motionSpeedMultiplier' in STAR)
check('base window background is deep-space not grey', '@color/lunaxy_launch_bg' in THEME and '#02040A' in COLORS)
check('android 12 splash is branded', 'windowSplashScreenBackground' in THEME31 and 'windowSplashScreenAnimatedIcon' in THEME31)
check('system splash exit has explicit handoff', 'setOnExitAnimationListener' in MAIN and '::remove' in MAIN)
check('reduced motion removes launch travel', 'SpringMotion.isReducedMotion() ? 1f : 5.2f' in MAIN)

failed = [name for name, ok in checks if not ok]
if failed:
    print('FAIL:')
    for name in failed: print(' -', name)
    raise SystemExit(1)
print(f'PASS: {len(checks)} V92.9.9 shared-hero/startup regression checks')
