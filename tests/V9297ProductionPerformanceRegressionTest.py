from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root/'app/src/main/java/com/xingyu/music/MainActivity.java').read_text(encoding='utf-8')
dock = (root/'app/src/main/java/com/xingyu/music/ui/FluidToolDock.java').read_text(encoding='utf-8')
store = (root/'app/src/main/java/com/xingyu/music/data/LibraryStore.java').read_text(encoding='utf-8')
checks=[]
def check(c,m):
    if not c: raise AssertionError(m)
    checks.append(m)

# Drawer cadence: ordered, but overlapping enough to read as one continuous mechanism.
check('500L * Math.abs(target - start)' in dock, 'drawer total travel is shortened without losing retargetable progress')
check('float window = .78f' in dock and 'float stagger = .13f' in dock, 'drawer children use strongly-overlapping stagger windows')
check('animator.setInterpolator(input -> input)' in dock and 'easeOutCubic' in dock and 'smoothstep(clamp(local * 1.35f))' in dock, 'drawer uses one linear reversible clock with per-property easing instead of double-easing')
check('ValueAnimator.ofFloat(start, target)' in dock and 'animator.cancel()' in dock, 'drawer can reverse from current presentation state')

# Long playlist: V92.9.7 introduced a ListView branch, but V92.9.8 intentionally supersedes it
# after a real-device crash report. Preserve the production goals (bounded work + complete queue)
# without pinning the retired implementation mechanism.
playlist_detail = main[main.find('private View playlistDetailPage'):main.find('private View playlistDetailVirtualPage')]
if 'PLAYLIST_PLACEHOLDER_COUNT' in main:
    check('playlistDetailVirtualPage(p)' not in playlist_detail, 'superseded virtual branch is not dispatched for large playlists')
    check('materializePlaylistBatch' in playlist_detail and 'PLAYLIST_APPEND_BATCH = 10' in main, 'large playlists keep bounded progressive presentation work')
    check('playQueue(playlist.songs, liveIndex)' in main, 'progressive rows still install the complete playlist queue')
    check('playlistScrollRestoreY' in main, 'progressive long playlists preserve ScrollView position')
else:
    check('PLAYLIST_VIRTUAL_LIST_THRESHOLD = 120' in main, 'large playlists switch to virtual list before hundreds of Views accumulate')
    check('private View playlistDetailVirtualPage' in main and 'new ListView(this)' in main, 'large playlist detail uses a recycling platform list')
    check('private final class PlaylistDetailAdapter extends BaseAdapter' in main, 'playlist rows are adapter-backed and recycled')
    check('holder.row.setOnClickListener(v -> playQueue(playlist.songs, position))' in main, 'virtual rows still install the complete playlist queue')
    check('playlistListRestorePosition' in main and 'setSelectionFromTop' in main, 'large playlist restores its exact viewport without rebuilding to top')

# Playback position snapshots must not trigger expensive whole-page rebuilds.
start=main.find('private void refreshActivePlaylistHighlights()')
end=main.find('private void updatePlaylistLocateButtonAccent()', start)
highlight=main[start:end]
check('if (key.equals(activePlaylistHighlightedKey)' in highlight, 'playlist highlight refresh ignores unchanged progress snapshots')

# Local playlist search: background, cancellable, old rows retained until replacement is ready.
search=main[main.find('private void showPlaylistSearch'):main.find('private View songList', main.find('private void showPlaylistSearch'))]
check('playlistFilterExecutor.submit' in search and 'input.postDelayed(pending[0], 110L)' in search, 'playlist filtering is debounced and off the UI thread')
check('ticket != generation.get()' in search and 'return;' in search, 'stale large-playlist scans stop cooperatively instead of queuing behind typing')
check('筛选中… · 当前结果保持可用' in search, 'playlist search preserves old readable rows during background filtering')
check('PlaylistFilterAdapter' in search and 'ListView results' in search, 'playlist-search results are recycled rather than materializing every match')

# Global search: first visible rows freeze while providers enrich in the background.
check('List<Song> frozenHead = searchFirstPaintDelivered ? new ArrayList<>(searchResults)' in main and 'when the coalesced merge actually launches' in main, 'global search freezes the latest first paint before slower-provider merge launch')
check('appendSearchPageStable(keyword, frozenHead, catalogs)' in main, 'later provider results append/enrich without re-ranking visible head')
sig=main[main.find('private String searchContentSignature'):main.find('private void renderSearchResults')]
check('|s-visible:' in sig and 'append("|s:")' not in sig, 'background result-count growth alone does not rebuild visible search content')

# Persistent compatibility: exact existing prefs/key and JSON format stay unchanged; cache is memory-only.
check('getSharedPreferences("xingyu_library_v3"' in store, 'formal library SharedPreferences identity remains unchanged')
check('prefs.getString("playlists", "[]")' in store and 'putString("playlists",raw)' in store, 'playlist persistent key/JSON channel remains unchanged')
check('playlistCacheRaw' in store and 'playlistCache' in store, 'decoded playlists are reused in memory instead of reparsing large JSON repeatedly')
check('playlistFilterExecutor.shutdownNow()' in main, 'new background worker is lifecycle-cleaned')

print(f'PASS: {len(checks)} V92.9.7 production performance regression checks')
