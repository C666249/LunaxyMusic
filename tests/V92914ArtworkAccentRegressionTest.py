from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT/'app/src/main/java/com/xingyu/music/MainActivity.java').read_text(encoding='utf-8')
PLACEHOLDER = (ROOT/'app/src/main/java/com/xingyu/music/ui/FluidPlaceholderView.java').read_text(encoding='utf-8')
HERO = (ROOT/'app/src/main/java/com/xingyu/music/ui/PlaylistHeroMorphView.java').read_text(encoding='utf-8')
CURTAIN = (ROOT/'app/src/main/java/com/xingyu/music/ui/CurtainRevealFrame.java').read_text(encoding='utf-8')

checks=[]
def check(name, cond): checks.append((name, bool(cond)))

check('placeholder accent is mutable without shimmer restart',
      'private int accent;' in PLACEHOLDER and 'public void setAccent(int color)' in PLACEHOLDER
      and 'startShimmer();' not in PLACEHOLDER.split('public void setAccent(int color)',1)[1].split('}',1)[0])
check('song-row placeholders can be targeted specifically', 'public boolean isSongRow()' in PLACEHOLDER)
check('current playback artwork accent has identity key', 'playlistPlaybackAccentKey' in MAIN)
check('progressive loading rejects stale previous-song accent',
      'if (key.equals(playlistPlaybackAccentKey)) return playlistPlaybackAccent;' in MAIN
      and 'ImageLoader.peek(current.coverUrl)' in MAIN)
check('resolved playback cover retints visible staircase rows',
      'playlistPlaybackAccentKey = artworkKey;' in MAIN
      and 'retintProgressiveLoadingRows(pageHost, color);' in MAIN)
check('curtain seam itself is artwork-tintable',
      'public void setAccent(int color)' in CURTAIN and 'mix(accent, Color.WHITE' in CURTAIN
      and 'Color.argb(82, 172, 211, 255)' not in CURTAIN)
check('search all staircase uses playback artwork accent',
      'int revealAccent = progressiveLoadingAccent(Ui.CYAN);' in MAIN and 'frame.setAccent(revealAccent);' in MAIN)
check('smart collection staircase uses playback artwork accent',
      'int revealAccent = progressiveLoadingAccent(collection.accent);' in MAIN and 'frame.setAccent(revealAccent);' in MAIN)
check('playlist placeholder batches use playback artwork accent',
      'int shimmerAccent = progressiveLoadingAccent(accent);' in MAIN
      and 'frame.setAccent(shimmerAccent);' in MAIN and 'FluidPlaceholderView.SONG_ROW, shimmerAccent' in MAIN)
check('playlist hero seeds tint from cached playlist artwork',
      'private int playlistArtworkAccent(ImportedPlaylist p)' in MAIN
      and 'ImageLoader.accent(cached)' in MAIN)
check('playlist hero thumbnail resolves tint asynchronously',
      'playlistThumb(p, 88, (bitmap, color) -> applyPlaylistHeroArtworkAccent(p.id, hero, color))' in MAIN)
check('playlist hero tint updates through a calm color interpolation',
      'ValueAnimator.ofFloat(0f, 1f)' in MAIN and 'Ui.mix(from, color' in MAIN
      and 'Ui.tintedGlass(mixed, 23, this)' in MAIN)
check('shared playlist hero snapshot uses artwork accent', 'playlistArtworkAccent(p));' in MAIN)
check('shared hero overlay can retint in flight', 'public void setAccent(int color)' in HERO
      and 'activePlaylistHeroMorph.setAccent(color)' in MAIN)

failed=[n for n,ok in checks if not ok]
if failed:
    print('FAIL:')
    for n in failed: print(' -',n)
    raise SystemExit(1)
print(f'PASS: {len(checks)} V92.9.14 artwork-accent regression checks')
