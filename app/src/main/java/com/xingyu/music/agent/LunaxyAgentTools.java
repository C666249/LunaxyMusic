package com.xingyu.music.agent;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.xingyu.music.data.Http;
import com.xingyu.music.data.LibraryStore;
import com.xingyu.music.data.NeteaseApi;
import com.xingyu.music.data.PersonalizationStore;
import com.xingyu.music.data.QQMusicApi;
import com.xingyu.music.data.RecommendationEngine;
import com.xingyu.music.model.ImportedPlaylist;
import com.xingyu.music.model.Song;
import com.xingyu.music.voice.VoiceMusicSearch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Model-agnostic tool registry for Lunaxy Music.
 *
 * The registry exposes OpenAI-compatible function schemas and executes those functions against
 * Lunaxy's existing search, library, personalization, recommendation and PlaybackService layers.
 * It deliberately does not know about Agnes (or any other model vendor), so changing models does
 * not require rewriting the app's tools.
 *
 * Network/database tools should be invoked from a worker thread by the Agent loop.
 */
public final class LunaxyAgentTools implements AutoCloseable {
    public static final int SCHEMA_VERSION = 1;

    private static final int MAX_SONG_REFS = 256;
    private static final int MAX_WEB_TEXT = 18_000;

    private final Context appContext;
    private final PersonalizationStore personalization;
    private final LibraryStore library;
    private final RecommendationEngine recommendations;
    private final VoiceMusicSearch musicSearch;
    private final AgentPlaybackBridge playback;

    private int nextSongRef = 1;
    private final LinkedHashMap<String, Song> songRefs =
            new LinkedHashMap<String, Song>(MAX_SONG_REFS + 1, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Song> eldest) {
                    return size() > MAX_SONG_REFS;
                }
            };

    public LunaxyAgentTools(Context context) {
        appContext = context.getApplicationContext();
        personalization = new PersonalizationStore(appContext);
        library = new LibraryStore(appContext, personalization);
        recommendations = new RecommendationEngine(
                appContext,
                new NeteaseApi(),
                new QQMusicApi(),
                personalization);
        musicSearch = new VoiceMusicSearch();
        playback = new AgentPlaybackBridge(appContext);
    }

    /** OpenAI-compatible tools array. The same schema can be sent to Agnes or another compatible LLM. */
    public JSONArray openAiTools() {
        JSONArray tools = new JSONArray();
        try {
            tools.put(tool(
                    "lunaxy_get_listening_profile",
                    "读取 Lunaxy 本地学习到的收听画像。返回学习成熟度、近期行为和 180天/30天/7天/当前会话的歌手偏好。用户问最近常听、口味分析、按口味推荐时优先调用。",
                    objectSchema(new JSONObject(), new JSONArray())));

            JSONObject recentProps = new JSONObject();
            recentProps.put("limit", integerProp("返回歌曲数量，1-50", 1, 50));
            tools.put(tool(
                    "lunaxy_get_recently_played",
                    "读取 Lunaxy 最近播放列表。只读，不联网。",
                    objectSchema(recentProps, new JSONArray())));

            JSONObject favoritesProps = new JSONObject();
            favoritesProps.put("limit", integerProp("返回收藏数量，1-100", 1, 100));
            tools.put(tool(
                    "lunaxy_get_favorites",
                    "读取用户在 Lunaxy 的收藏歌曲。只读。",
                    objectSchema(favoritesProps, new JSONArray())));

            tools.put(tool(
                    "lunaxy_list_playlists",
                    "列出 Lunaxy 本地/导入歌单及每个歌单的歌曲数量。只读。",
                    objectSchema(new JSONObject(), new JSONArray())));

            JSONObject searchProps = new JSONObject();
            searchProps.put("query", stringProp("歌曲、歌手或自然语言搜索关键词"));
            searchProps.put("limit", integerProp("候选数量，1-30", 1, 30));
            tools.put(tool(
                    "lunaxy_search_music",
                    "在 Lunaxy 已有的网易、QQ、酷我、酷狗目录搜索中查找真实歌曲。播放或推荐前用它验证歌曲确实存在，并取得 song_ref。",
                    objectSchema(searchProps, new JSONArray().put("query"))));

            JSONObject artistProps = new JSONObject();
            artistProps.put("artist", stringProp("歌手名称"));
            artistProps.put("limit", integerProp("候选数量，1-30", 1, 30));
            tools.put(tool(
                    "lunaxy_search_artist",
                    "跨 Lunaxy 音乐源搜索指定歌手的真实歌曲并返回 song_ref。",
                    objectSchema(artistProps, new JSONArray().put("artist"))));

            JSONObject dailyProps = new JSONObject();
            dailyProps.put("limit", integerProp("推荐数量，1-30", 1, 30));
            tools.put(tool(
                    "lunaxy_daily_recommendations",
                    "调用 Lunaxy 现有个性化引擎生成每日推荐。会综合收藏、歌单、最近播放、播完、跳过、重播和长期画像，并返回可继续播放的 song_ref。",
                    objectSchema(dailyProps, new JSONArray())));

            JSONObject radarProps = new JSONObject();
            radarProps.put("limit", integerProp("推荐数量，1-40", 1, 40));
            tools.put(tool(
                    "lunaxy_private_radar",
                    "生成当前会话更敏感的私人雷达/私人电台结果。适合“按我最近正在听的感觉继续推荐”。",
                    objectSchema(radarProps, new JSONArray())));

            JSONObject stateProps = new JSONObject();
            tools.put(tool(
                    "lunaxy_get_playback_state",
                    "读取 Agent 当前能看到的 Lunaxy 播放歌曲和队列概况。只读。",
                    objectSchema(stateProps, new JSONArray())));

            JSONObject playProps = new JSONObject();
            playProps.put("song_ref", stringProp("由搜索、推荐或历史工具返回的 song_ref"));
            tools.put(tool(
                    "lunaxy_play_song",
                    "立即播放一个前序 Lunaxy 工具返回的歌曲。必须使用真实 song_ref，不能自行编造。",
                    objectSchema(playProps, new JSONArray().put("song_ref"))));

            JSONObject queueProps = new JSONObject();
            queueProps.put("song_refs", arrayStringProp("按播放顺序排列的 song_ref 数组"));
            queueProps.put("start_index", integerProp("从第几首开始，0-based", 0, 100));
            tools.put(tool(
                    "lunaxy_play_queue",
                    "用前序工具返回的歌曲构建播放队列并开始播放。",
                    objectSchema(queueProps, new JSONArray().put("song_refs"))));

            JSONObject controlProps = new JSONObject();
            controlProps.put("action", enumStringProp(
                    "播放控制动作",
                    new String[]{"pause", "resume", "next", "previous"}));
            tools.put(tool(
                    "lunaxy_playback_control",
                    "控制 Lunaxy 已有播放器，不创建第二套播放器。",
                    objectSchema(controlProps, new JSONArray().put("action"))));

            JSONObject createPlaylistProps = new JSONObject();
            createPlaylistProps.put("name", stringProp("新歌单名称"));
            tools.put(tool(
                    "lunaxy_create_playlist",
                    "创建 Lunaxy 本地歌单。只有用户明确要求创建时才调用。",
                    objectSchema(createPlaylistProps, new JSONArray().put("name"))));

            JSONObject addPlaylistProps = new JSONObject();
            addPlaylistProps.put("playlist_id", stringProp("lunaxy_list_playlists 返回的歌单 ID"));
            addPlaylistProps.put("song_refs", arrayStringProp("要加入歌单的 song_ref 数组"));
            tools.put(tool(
                    "lunaxy_add_to_playlist",
                    "把前序搜索/推荐返回的真实歌曲加入指定 Lunaxy 歌单。只有用户明确要求写入时才调用。",
                    objectSchema(addPlaylistProps, new JSONArray().put("playlist_id").put("song_refs"))));

            JSONObject webSearchProps = new JSONObject();
            webSearchProps.put("query", stringProp("需要在公开网页中检索的查询词"));
            webSearchProps.put("max_chars", integerProp("最多返回给模型的网页文本字符数，2000-18000", 2000, MAX_WEB_TEXT));
            tools.put(tool(
                    "lunaxy_web_search",
                    "后台进行轻量公开网页检索并返回可供模型分析的文本。适合补充新鲜音乐资料；结果只是网页文本，不是 Lunaxy 音乐库事实，推荐后仍应 search_music 验证歌曲。",
                    objectSchema(webSearchProps, new JSONArray().put("query"))));

            JSONObject fetchProps = new JSONObject();
            fetchProps.put("url", stringProp("公开 http/https URL；本地地址和私网地址会被拒绝"));
            fetchProps.put("max_chars", integerProp("最多返回字符数，2000-18000", 2000, MAX_WEB_TEXT));
            tools.put(tool(
                    "lunaxy_fetch_web_page",
                    "读取一个明确公开 URL 的网页文本，供 Agent 分析。会拒绝 localhost、局域网和链路本地地址。",
                    objectSchema(fetchProps, new JSONArray().put("url"))));

            JSONObject browserSearchProps = new JSONObject();
            browserSearchProps.put("query", stringProp("要交给用户可见外部浏览器的搜索词"));
            tools.put(tool(
                    "lunaxy_open_browser_search",
                    "在系统外部浏览器打开搜索结果页。注意：此工具只负责打开页面，不会把外部浏览器内容返回给模型。",
                    objectSchema(browserSearchProps, new JSONArray().put("query"))));

            JSONObject openUrlProps = new JSONObject();
            openUrlProps.put("url", stringProp("要在系统外部浏览器打开的 http/https URL"));
            tools.put(tool(
                    "lunaxy_open_url",
                    "在用户可见的系统外部浏览器打开 URL。不会读取外部浏览器页面。",
                    objectSchema(openUrlProps, new JSONArray().put("url"))));
        } catch (Exception ignored) {
            // A malformed schema is a programmer error. Returning already-built tools is safer
            // than crashing the app while a model settings surface is being opened.
        }
        return tools;
    }

    /**
     * Execute one tool call and return a compact JSON string suitable for a role=tool message.
     * Call this from a worker thread; search/recommendation/web tools may perform network I/O.
     */
    public String execute(String toolName, String argumentsJson) {
        JSONObject args;
        try {
            args = new JSONObject(argumentsJson == null || argumentsJson.trim().isEmpty()
                    ? "{}" : argumentsJson);
        } catch (Exception e) {
            return error(toolName, "invalid_arguments", "参数不是合法 JSON").toString();
        }

        try {
            switch (safe(toolName)) {
                case "lunaxy_get_listening_profile": return getListeningProfile().toString();
                case "lunaxy_get_recently_played": return recent(args).toString();
                case "lunaxy_get_favorites": return favorites(args).toString();
                case "lunaxy_list_playlists": return playlists().toString();
                case "lunaxy_search_music": return searchMusic(args).toString();
                case "lunaxy_search_artist": return searchArtist(args).toString();
                case "lunaxy_daily_recommendations": return daily(args).toString();
                case "lunaxy_private_radar": return privateRadar(args).toString();
                case "lunaxy_get_playback_state": return playbackState().toString();
                case "lunaxy_play_song": return playSong(args).toString();
                case "lunaxy_play_queue": return playQueue(args).toString();
                case "lunaxy_playback_control": return playbackControl(args).toString();
                case "lunaxy_create_playlist": return createPlaylist(args).toString();
                case "lunaxy_add_to_playlist": return addToPlaylist(args).toString();
                case "lunaxy_web_search": return webSearch(args).toString();
                case "lunaxy_fetch_web_page": return fetchWebPage(args).toString();
                case "lunaxy_open_browser_search": return openBrowserSearch(args).toString();
                case "lunaxy_open_url": return openUrl(args).toString();
                default: return error(toolName, "unknown_tool", "未知 Lunaxy Agent 工具").toString();
            }
        } catch (Exception e) {
            return error(toolName, "execution_failed",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()).toString();
        }
    }

    private JSONObject getListeningProfile() throws Exception {
        RecommendationEngine.PersonalizationInsights x = recommendations.insights(
                library.favorites(), library.playlists(), library.history());

        JSONObject data = new JSONObject();
        data.put("maturity", x.maturity);
        data.put("learned_tracks", x.learnedTracks);
        data.put("recent_events", x.recentEvents);
        data.put("session_id_present", x.sessionId != null && !x.sessionId.trim().isEmpty());

        JSONObject session = new JSONObject();
        session.put("completes", x.completes);
        session.put("skips", x.skips);
        session.put("replays", x.replays);
        session.put("favorites_added", x.favoritesAdded);
        data.put("current_session", session);

        JSONArray artists = new JSONArray();
        for (RecommendationEngine.ArtistInsight artist : x.artists) {
            JSONObject a = new JSONObject();
            a.put("artist", artist.name);
            a.put("overall", artist.overall);
            a.put("long_180d", artist.long180);
            a.put("medium_30d", artist.medium30);
            a.put("short_7d", artist.short7);
            a.put("session", artist.session);
            artists.put(a);
        }
        data.put("top_artists", artists);

        if (x.daily != null) data.put("daily_summary", recommendationSummary(x.daily));
        if (x.privateRadio != null) data.put("private_radar_summary", recommendationSummary(x.privateRadio));
        return ok("lunaxy_get_listening_profile", data);
    }

    private JSONObject recent(JSONObject args) throws Exception {
        int limit = clamp(args.optInt("limit", 20), 1, 50);
        return ok("lunaxy_get_recently_played",
                new JSONObject().put("songs", rememberSongs(trim(library.history(), limit), null)));
    }

    private JSONObject favorites(JSONObject args) throws Exception {
        int limit = clamp(args.optInt("limit", 30), 1, 100);
        return ok("lunaxy_get_favorites",
                new JSONObject().put("songs", rememberSongs(trim(library.favorites(), limit), null)));
    }

    private JSONObject playlists() throws Exception {
        JSONArray out = new JSONArray();
        for (ImportedPlaylist p : library.playlists()) {
            if (p == null) continue;
            JSONObject item = new JSONObject();
            item.put("id", p.id);
            item.put("name", p.name);
            item.put("source", p.source);
            item.put("song_count", p.songs == null ? 0 : p.songs.size());
            out.put(item);
        }
        return ok("lunaxy_list_playlists", new JSONObject().put("playlists", out));
    }

    private JSONObject searchMusic(JSONObject args) throws Exception {
        String query = require(args, "query");
        int limit = clamp(args.optInt("limit", 12), 1, 30);
        List<Song> songs = musicSearch.search(query, limit);
        JSONObject data = new JSONObject();
        data.put("query", query);
        data.put("songs", rememberSongs(songs, null));
        return ok("lunaxy_search_music", data);
    }

    private JSONObject searchArtist(JSONObject args) throws Exception {
        String artist = require(args, "artist");
        int limit = clamp(args.optInt("limit", 16), 1, 30);
        List<Song> songs = musicSearch.searchArtist(artist, limit);
        JSONObject data = new JSONObject();
        data.put("artist", artist);
        data.put("songs", rememberSongs(songs, null));
        return ok("lunaxy_search_artist", data);
    }

    private JSONObject daily(JSONObject args) throws Exception {
        int limit = clamp(args.optInt("limit", 20), 1, 30);
        List<Song> fav = library.favorites();
        List<ImportedPlaylist> lists = library.playlists();
        List<Song> hist = library.history();
        List<Song> songs = recommendations.daily(fav, lists, hist, limit);
        return ok("lunaxy_daily_recommendations",
                new JSONObject().put("songs", rememberSongs(songs, "daily")));
    }

    private JSONObject privateRadar(JSONObject args) throws Exception {
        int limit = clamp(args.optInt("limit", 24), 1, 40);
        List<Song> fav = library.favorites();
        List<ImportedPlaylist> lists = library.playlists();
        List<Song> hist = library.history();
        List<Song> songs = recommendations.privateRadio(fav, lists, hist, limit);
        return ok("lunaxy_private_radar",
                new JSONObject().put("songs", rememberSongs(songs, "private")));
    }

    private JSONObject playbackState() throws Exception {
        JSONObject data = new JSONObject();
        Song current = playback.currentSong();
        if (current != null) data.put("current", songCard(current, rememberSong(current), null));
        else data.put("current", JSONObject.NULL);
        data.put("queue_size", playback.queueSnapshot().size());
        data.put("bridge_ready", playback.isReady());
        return ok("lunaxy_get_playback_state", data);
    }

    private JSONObject playSong(JSONObject args) throws Exception {
        String ref = require(args, "song_ref");
        Song song = songForRef(ref);
        if (song == null) return error("lunaxy_play_song", "unknown_song_ref",
                "song_ref 已过期或不存在，请先重新搜索/推荐");
        playback.playSong(song);
        return ok("lunaxy_play_song", new JSONObject()
                .put("accepted", true)
                .put("song", songCard(song, ref, null)));
    }

    private JSONObject playQueue(JSONObject args) throws Exception {
        JSONArray refs = args.optJSONArray("song_refs");
        if (refs == null || refs.length() == 0)
            return error("lunaxy_play_queue", "missing_song_refs", "song_refs 不能为空");

        List<Song> songs = new ArrayList<>();
        JSONArray invalid = new JSONArray();
        for (int i = 0; i < refs.length(); i++) {
            String ref = safe(refs.optString(i, ""));
            Song song = songForRef(ref);
            if (song == null) invalid.put(ref);
            else songs.add(song);
        }
        if (songs.isEmpty())
            return error("lunaxy_play_queue", "no_valid_songs", "没有可用的 song_ref");

        int start = clamp(args.optInt("start_index", 0), 0, songs.size() - 1);
        playback.playQueue(songs, start);
        JSONObject data = new JSONObject()
                .put("accepted", true)
                .put("queue_size", songs.size())
                .put("start_index", start);
        if (invalid.length() > 0) data.put("ignored_refs", invalid);
        return ok("lunaxy_play_queue", data);
    }

    private JSONObject playbackControl(JSONObject args) throws Exception {
        String action = require(args, "action").toLowerCase(Locale.ROOT);
        switch (action) {
            case "pause": playback.pause(); break;
            case "resume": playback.resume(); break;
            case "next": playback.next(); break;
            case "previous": playback.previous(); break;
            default:
                return error("lunaxy_playback_control", "invalid_action",
                        "action 必须是 pause/resume/next/previous");
        }
        return ok("lunaxy_playback_control",
                new JSONObject().put("accepted", true).put("action", action));
    }

    private JSONObject createPlaylist(JSONObject args) throws Exception {
        String name = require(args, "name");
        List<ImportedPlaylist> all = library.createPlaylist(name);
        ImportedPlaylist created = all.isEmpty() ? null : all.get(0);
        JSONObject data = new JSONObject().put("created", created != null);
        if (created != null) {
            data.put("playlist", new JSONObject()
                    .put("id", created.id)
                    .put("name", created.name)
                    .put("song_count", created.songs.size()));
        }
        return ok("lunaxy_create_playlist", data);
    }

    private JSONObject addToPlaylist(JSONObject args) throws Exception {
        String playlistId = require(args, "playlist_id");
        JSONArray refs = args.optJSONArray("song_refs");
        if (refs == null || refs.length() == 0)
            return error("lunaxy_add_to_playlist", "missing_song_refs", "song_refs 不能为空");

        List<Song> songs = new ArrayList<>();
        for (int i = 0; i < refs.length(); i++) {
            Song song = songForRef(refs.optString(i, ""));
            if (song != null) songs.add(song);
        }
        if (songs.isEmpty())
            return error("lunaxy_add_to_playlist", "no_valid_songs", "没有可加入的有效 song_ref");

        boolean exists = false;
        for (ImportedPlaylist p : library.playlists()) {
            if (p != null && playlistId.equals(p.id)) { exists = true; break; }
        }
        if (!exists)
            return error("lunaxy_add_to_playlist", "playlist_not_found", "找不到指定歌单");

        List<ImportedPlaylist> updated = library.addSongsToPlaylist(playlistId, songs);
        int size = 0;
        for (ImportedPlaylist p : updated) {
            if (p != null && playlistId.equals(p.id)) { size = p.songs.size(); break; }
        }
        return ok("lunaxy_add_to_playlist", new JSONObject()
                .put("added_requested", songs.size())
                .put("playlist_id", playlistId)
                .put("playlist_size", size));
    }

    private JSONObject webSearch(JSONObject args) throws Exception {
        String query = require(args, "query");
        int maxChars = clamp(args.optInt("max_chars", 12_000), 2_000, MAX_WEB_TEXT);
        String url = "https://html.duckduckgo.com/html/?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        String text = fetchPublicHtml(url, maxChars);
        return ok("lunaxy_web_search", new JSONObject()
                .put("query", query)
                .put("source", "DuckDuckGo HTML")
                .put("search_url", url)
                .put("text", text)
                .put("note", "网页检索结果用于发现/补充资料；歌曲可播放性仍需用 lunaxy_search_music 验证"));
    }

    private JSONObject fetchWebPage(JSONObject args) throws Exception {
        String url = require(args, "url");
        int maxChars = clamp(args.optInt("max_chars", 12_000), 2_000, MAX_WEB_TEXT);
        return ok("lunaxy_fetch_web_page", new JSONObject()
                .put("url", url)
                .put("text", fetchPublicHtml(url, maxChars)));
    }

    private JSONObject openBrowserSearch(JSONObject args) throws Exception {
        String query = require(args, "query");
        String url = "https://www.google.com/search?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        openExternal(url);
        return ok("lunaxy_open_browser_search", new JSONObject()
                .put("opened", true)
                .put("url", url)
                .put("readable_by_agent", false));
    }

    private JSONObject openUrl(JSONObject args) throws Exception {
        String url = require(args, "url");
        URL parsed = new URL(url);
        if (!"http".equalsIgnoreCase(parsed.getProtocol())
                && !"https".equalsIgnoreCase(parsed.getProtocol()))
            return error("lunaxy_open_url", "unsupported_scheme", "只允许 http/https URL");
        openExternal(url);
        return ok("lunaxy_open_url", new JSONObject()
                .put("opened", true)
                .put("url", url)
                .put("readable_by_agent", false));
    }

    private JSONArray rememberSongs(List<Song> songs, String recommendationKind) throws Exception {
        JSONArray out = new JSONArray();
        if (songs == null) return out;
        for (Song song : songs) {
            if (song == null) continue;
            String ref = rememberSong(song);
            RecommendationEngine.RecommendationReason reason = recommendationKind == null
                    ? null : recommendations.reasonFor(recommendationKind, song);
            out.put(songCard(song, ref, reason));
        }
        return out;
    }

    private synchronized String rememberSong(Song song) {
        for (Map.Entry<String, Song> entry : songRefs.entrySet()) {
            Song old = entry.getValue();
            if (old != null && old.key().equals(song.key())) {
                entry.setValue(song);
                return entry.getKey();
            }
        }
        String ref = "song_" + nextSongRef++;
        songRefs.put(ref, song);
        return ref;
    }

    private synchronized Song songForRef(String ref) {
        return songRefs.get(safe(ref));
    }

    private JSONObject songCard(Song song, String ref,
                                RecommendationEngine.RecommendationReason reason) throws Exception {
        JSONObject out = new JSONObject();
        out.put("song_ref", ref);
        out.put("title", song.title);
        out.put("artist", song.artist);
        out.put("album", song.album);
        out.put("duration_ms", song.durationMs);
        out.put("sources", song.sourceLabel());
        if (reason != null) {
            out.put("why", reason.why);
            out.put("unheard", reason.unheard);
            out.put("origin", reason.origin);
        }
        return out;
    }

    private static JSONObject recommendationSummary(
            RecommendationEngine.RecommendationSummary s) throws Exception {
        return new JSONObject()
                .put("total", s.total)
                .put("unheard", s.unheard)
                .put("unheard_percent", s.unheardPercent())
                .put("listenbrainz", s.listenBrainz)
                .put("familiar", s.familiar)
                .put("rediscovery", s.rediscovery)
                .put("local", s.local);
    }

    private String fetchPublicHtml(String rawUrl, int maxChars) throws Exception {
        assertPublicHttpUrl(rawUrl);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Android; LunaxyAgent/1.0) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7");
        Http.Response response = Http.get(rawUrl, headers, 7000, 12_000);
        if (response.code < 200 || response.code >= 400)
            throw new IllegalStateException("HTTP " + response.code);

        String text = htmlToText(response.body);
        if (text.length() > maxChars) text = text.substring(0, maxChars) + "\n[truncated]";
        return text;
    }

    private static void assertPublicHttpUrl(String rawUrl) throws Exception {
        URL url = new URL(rawUrl);
        String protocol = safe(url.getProtocol()).toLowerCase(Locale.ROOT);
        if (!"http".equals(protocol) && !"https".equals(protocol))
            throw new IllegalArgumentException("只允许 http/https URL");
        String host = safe(url.getHost()).toLowerCase(Locale.ROOT);
        if (host.isEmpty() || "localhost".equals(host) || host.endsWith(".local"))
            throw new IllegalArgumentException("不允许本地地址");

        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress() || isCarrierGradeNat(address)
                    || isIpv6UniqueLocal(address)) {
                throw new IllegalArgumentException("不允许访问局域网/本地网络地址");
            }
        }
    }

    private static boolean isCarrierGradeNat(InetAddress address) {
        byte[] b = address.getAddress();
        if (b.length != 4) return false;
        int first = b[0] & 0xff, second = b[1] & 0xff;
        return first == 100 && second >= 64 && second <= 127;
    }

    private static boolean isIpv6UniqueLocal(InetAddress address) {
        byte[] b = address.getAddress();
        return b.length == 16 && ((b[0] & 0xfe) == 0xfc);
    }

    private static String htmlToText(String raw) {
        String html = raw == null ? "" : raw;
        html = html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<noscript[^>]*>.*?</noscript>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|li|article|section|h[1-6]|a)>", "\n")
                .replaceAll("(?is)<[^>]+>", " ");
        html = html.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        return html.replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private void openExternal(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(intent);
    }

    private static JSONObject ok(String tool, JSONObject data) {
        JSONObject out = new JSONObject();
        try {
            out.put("success", true);
            out.put("tool", tool);
            out.put("data", data == null ? new JSONObject() : data);
        } catch (Exception ignored) { }
        return out;
    }

    private static JSONObject error(String tool, String code, String message) {
        JSONObject out = new JSONObject();
        try {
            out.put("success", false);
            out.put("tool", safe(tool));
            out.put("error", new JSONObject()
                    .put("code", safe(code))
                    .put("message", safe(message)));
        } catch (Exception ignored) { }
        return out;
    }

    private static JSONObject tool(String name, String description, JSONObject parameters) throws Exception {
        return new JSONObject()
                .put("type", "function")
                .put("function", new JSONObject()
                        .put("name", name)
                        .put("description", description)
                        .put("parameters", parameters));
    }

    private static JSONObject objectSchema(JSONObject properties, JSONArray required) throws Exception {
        return new JSONObject()
                .put("type", "object")
                .put("properties", properties == null ? new JSONObject() : properties)
                .put("required", required == null ? new JSONArray() : required)
                .put("additionalProperties", false);
    }

    private static JSONObject stringProp(String description) throws Exception {
        return new JSONObject().put("type", "string").put("description", description);
    }

    private static JSONObject integerProp(String description, int min, int max) throws Exception {
        return new JSONObject()
                .put("type", "integer")
                .put("description", description)
                .put("minimum", min)
                .put("maximum", max);
    }

    private static JSONObject enumStringProp(String description, String[] values) throws Exception {
        JSONArray items = new JSONArray();
        if (values != null) for (String value : values) items.put(value);
        return new JSONObject()
                .put("type", "string")
                .put("description", description)
                .put("enum", items);
    }

    private static JSONObject arrayStringProp(String description) throws Exception {
        return new JSONObject()
                .put("type", "array")
                .put("description", description)
                .put("items", new JSONObject().put("type", "string"))
                .put("minItems", 1);
    }

    private static String require(JSONObject args, String key) {
        String value = safe(args == null ? "" : args.optString(key, ""));
        if (value.isEmpty()) throw new IllegalArgumentException(key + " 不能为空");
        return value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static <T> List<T> trim(List<T> list, int max) {
        if (list == null) return new ArrayList<>();
        return list.size() <= max ? new ArrayList<>(list)
                : new ArrayList<>(list.subList(0, max));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    @Override public void close() {
        try { musicSearch.destroy(); } catch (Exception ignored) { }
        try { playback.close(); } catch (Exception ignored) { }
    }
}
