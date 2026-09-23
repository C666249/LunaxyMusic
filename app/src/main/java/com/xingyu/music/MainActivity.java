package com.xingyu.music;

import android.Manifest;
import android.app.Activity;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.ContentResolver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.net.Uri;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import android.speech.RecognizerIntent;
import android.os.Environment;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.TextWatcher;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.VelocityTracker;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.TextView;

import com.xingyu.music.data.LibraryStore;
import com.xingyu.music.data.LunaxyPlaylistCodec;
import com.xingyu.music.download.OfflineStore;
import com.xingyu.music.local.LocalMusicScanner;
import com.xingyu.music.download.OfflineDownloadService;
import com.xingyu.music.download.DownloadPlanStore;
import com.xingyu.music.desktop.DesktopLyricService;
import com.xingyu.music.download.RingtoneHelper;
import com.xingyu.music.download.AudioClipper;
import com.xingyu.music.data.ExactTrackMatcher;
import com.xingyu.music.data.ResolverHttpDiagnostics;
import com.xingyu.music.data.LyricCacheStore;
import com.xingyu.music.data.LyricMatchStore;
import com.xingyu.music.data.LyricMatcher;
import com.xingyu.music.data.LyricParser;
import com.xingyu.music.data.NeteaseApi;
import com.xingyu.music.data.KuwoMusicApi;
import com.xingyu.music.data.KuwoPlaylistImportApi;
import com.xingyu.music.data.KugouMusicApi;
import com.xingyu.music.data.KugouPlaylistImportApi;
import com.xingyu.music.data.TrackVariantStore;
import com.xingyu.music.data.TrackVariantEnricher;
import com.xingyu.music.data.NeteaseLyricSearchApi;
import com.xingyu.music.data.QQMusicApi;
import com.xingyu.music.data.QQMusicLyricSearchApi;
import com.xingyu.music.data.QQLyricApi;
import com.xingyu.music.data.RecommendationEngine;
import com.xingyu.music.data.PersonalizationStore;
import com.xingyu.music.data.PlaybackBehaviorTracker;
import com.xingyu.music.data.SearchRanker;
import com.xingyu.music.data.SearchPaginationApi;
import com.xingyu.music.data.SearchCacheStore;
import com.xingyu.music.data.SearchPerformanceStore;
import com.xingyu.music.data.SourceHealthStore;
import com.xingyu.music.data.V14PlaybackStateMigration;
import com.xingyu.music.data.WeatherMoodProvider;
import com.xingyu.music.model.ImportedPlaylist;
import com.xingyu.music.model.LyricLine;
import com.xingyu.music.model.LyricWord;
import com.xingyu.music.model.PlaybackSnapshot;
import com.xingyu.music.model.Song;
import com.xingyu.music.playback.PlaybackService;
import com.xingyu.music.ui.AppearanceSystem;
import com.xingyu.music.ui.AudioLevelProvider;
import com.xingyu.music.ui.ArtworkMorphView;
import com.xingyu.music.ui.ColorPickerView;
import com.xingyu.music.ui.CoverAmbientDrawable;
import com.xingyu.music.ui.ContextCoachOverlay;
import com.xingyu.music.ui.CurtainRevealFrame;
import com.xingyu.music.ui.FluidPlaceholderView;
import com.xingyu.music.ui.FluidLoadingIconView;
import com.xingyu.music.ui.IconView;
import com.xingyu.music.ui.FluidTrackHaloDrawable;
import com.xingyu.music.ui.FluidToolDock;
import com.xingyu.music.ui.PlaybackHighlightState;
import com.xingyu.music.ui.RecommendationGlassDrawable;
import com.xingyu.music.ui.PlayerSurfaceMorphView;
import com.xingyu.music.ui.PlaylistHeroMorphView;
import com.xingyu.music.ui.VinylStackGeometry;
import com.xingyu.music.ui.ImageLoader;
import com.xingyu.music.ui.LyricLineView;
import com.xingyu.music.ui.OnboardingOverlay;
import com.xingyu.music.ui.RingtoneLyricSelectorView;
import com.xingyu.music.ui.LyricGestureScrollView;
import com.xingyu.music.ui.StarfieldView;
import com.xingyu.music.ui.Starfield3DView;
import com.xingyu.music.ui.StarfieldBackdropView;
import com.xingyu.music.ui.SpringMotion;
import com.xingyu.music.ui.ThemeRevealOverlay;
import com.xingyu.music.ui.SourceFlowView;
import com.xingyu.music.ui.SwipeAwareScrollView;
import com.xingyu.music.ui.Ui;
import com.xingyu.music.ui.VinylRecordView;
import com.xingyu.music.ui.VinylStackView;
import com.xingyu.music.ui.VoiceAssistantPanel;
import com.xingyu.music.ui.VoiceOrbView;
import com.xingyu.music.voice.VoiceAssistantContract;
import com.xingyu.music.voice.VoiceAssistantService;

import java.text.Normalizer;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lunaxy Music – Smart Lyric Search with streaming incremental lyric results.
 *
 * V47 search, playlists, lyrics, recommendation UI and queue interaction remain intentionally stable.
 * V3 extends the experimental playback domain into a multi-catalog route matrix: TX/KW/KG/WY
 * canonical identities feed replaceable providers while Exact Track Gate and Media3 READY remain mandatory.
 */
public final class MainActivity extends Activity implements PlaybackService.Listener {
    private final ExecutorService io = Executors.newFixedThreadPool(6);
    // V3.2: canonical merge/ranking is CPU work, not UI work. Keep it serialized off the main
    // thread so four returning catalogs cannot stall scrolling/taps while ExactTrackMatcher runs.
    private final ExecutorService searchMergeExecutor = Executors.newSingleThreadExecutor();
    // Local playlist filtering can scan thousands of titles without blocking the UI thread.
    private final ExecutorService playlistFilterExecutor = Executors.newSingleThreadExecutor();
    // Beta V7: lyric-index discovery/verification is network-only and isolated from catalog/playback workers.
    // Remote lyric discovery must never queue behind LRC verification work.
    private final ExecutorService lyricIndexExecutor = Executors.newFixedThreadPool(4);
    private final ExecutorService lyricSearchExecutor = Executors.newFixedThreadPool(4);
    private final ScheduledExecutorService variantPrefetch = Executors.newSingleThreadScheduledExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final NeteaseApi netease = new NeteaseApi();
    private final NeteaseLyricSearchApi neteaseLyricSearch = new NeteaseLyricSearchApi();
    private final QQMusicApi qq = new QQMusicApi();
    private final QQMusicLyricSearchApi qqLyricSearch = new QQMusicLyricSearchApi();
    private final KuwoMusicApi kuwo = new KuwoMusicApi();
    private final KugouMusicApi kugou = new KugouMusicApi();
    private final KuwoPlaylistImportApi kuwoPlaylistImport = new KuwoPlaylistImportApi();
    private final KugouPlaylistImportApi kugouPlaylistImport = new KugouPlaylistImportApi();
    private final SearchPaginationApi searchPagination = new SearchPaginationApi();
    private final QQLyricApi qqLyrics = new QQLyricApi();

    private LibraryStore store;
    private OfflineStore offlineStore;
    private TrackVariantStore trackVariantStore;
    private TrackVariantEnricher backgroundVariantEnricher;
    private LyricCacheStore lyricCache;
    private LyricMatchStore lyricMatches;
    private RecommendationEngine recommendations;
    private PersonalizationStore personalizationStore;
    private SearchCacheStore searchCache;
    private SearchPerformanceStore searchPerformance;
    private PlaybackBehaviorTracker playbackBehaviorTracker;
    private WeatherMoodProvider weatherMood;
    private PlaybackService playback;
    private boolean bound;
    private int sourceDashboardTab = 0;
    private int personalizationDashboardTab = 0;
    private int engineMatrixPage = 0; // 0 route, 1 personalization, 2 search
    private RecommendationEngine.PersonalizationInsights activePersonalizationInsights;
    private int sourceMatrixFilter = 0;
    private static final String[][] SOURCE_UI_PROVIDERS = {
            {"qdy", "QDY"}, {"huibq", "Huibq"}, {"juhe", "Juhe"}, {"lx", "LX"},
            {"flower", "Flower"}, {"grass", "Grass"}, {"sixyin", "SixYin"}, {"ikun", "ikun"}
    };
    private static final String[] SOURCE_UI_PLATFORMS = {"tx", "kw", "kg", "wy"};
    private static final String[] SOURCE_UI_PLATFORM_LABELS = {"QQ", "酷我", "酷狗", "网易"};

    private FrameLayout appRoot;
    private FrameLayout safeLayer;
    private FrameLayout pageHost;
    private FrameLayout miniBar;
    private FrameLayout playerOverlay;
    private Starfield3DView playerStars;
    private StarfieldView playerStars2D;
    private StarfieldBackdropView playerStarBackdrop;
    private FrameLayout busyOverlay;
    private FrameLayout modalOverlay;
    // V92 modal/reorder safety: a drag release must never be reinterpreted as an outside-sheet tap.
    private boolean reorderGestureActive;
    private ReorderGestureController activeReorderController;
    private long modalDismissSuppressedUntil;
    // Optional one-shot completion for sheets that must refresh their parent only after dismissal.
    private Runnable modalDismissCompletion;
    private TextView snackbar;
    private VoiceAssistantPanel voicePanel;
    private Runnable voicePanelHideRunnable;
    private StarfieldView stars;
    private ImageView startupLogo;
    private ValueAnimator startupStarSpeedAnimator;
    private boolean startupComplete;
    private boolean startupFinishScheduled;
    private boolean runtimeInitStarted;
    private long startupSceneStartedAtMs;
    private final ArrayList<View> startupEntranceTargets = new ArrayList<>();
    private int libraryBootstrapGeneration;
    private FrameLayout navHost;
    private LinearLayout navBar;
    private View navSelectionPill;
    private final LinearLayout[] navItems = new LinearLayout[4];
    private final IconView[] navIcons = new IconView[4];
    private final TextView[] navLabels = new TextView[4];
    private final int[] rootTabScrollY = new int[4];
    private final int[] rootTabDetailScrollY = new int[4];
    private final ImportedPlaylist[] rootTabOpenPlaylist = new ImportedPlaylist[4];
    private final boolean[] rootTabOfflinePlaylistOpen = new boolean[4];
    private final SmartCollection[] rootTabSmartCollection = new SmartCollection[4];
    private final boolean[] rootTabSearchAllResultsOpen = new boolean[4];
    private ImageView miniCover;
    private TextView miniTitle;
    private TextView miniArtist;
    private FrameLayout miniPlayButton;
    private IconView miniPlayIcon;
    private IconView miniQueueIcon;
    private View miniProgress;
    private FrameLayout playlistLocateButton;
    private int playlistPlaybackAccent = Ui.CYAN;
    private final LinkedHashMap<String, PlaybackHighlightState> songHighlights = new LinkedHashMap<>();
    private final WeakHashMap<FluidTrackHaloDrawable, Boolean> highlightRenderers = new WeakHashMap<>();
    private java.lang.ref.WeakReference<QueueAdapter> visibleQueueAdapter = new java.lang.ref.WeakReference<>(null);
    private final ImageView[] stackCoverLoaders = new ImageView[5];
    private final String[] stackArtworkKeys = new String[]{"", "", "", "", ""};
    private int trackSwipeGeneration;
    private String trackSwipeSourceKey = "";
    private int playerOpenGeneration;
    private ValueAnimator activePlayerHeroAnimator;
    private View activePlayerHeroSource;
    private boolean miniMorphGeometryReady;
    private float miniMorphDx, miniMorphDy, miniMorphStartScale;
    private final WeakHashMap<View, FluidTrackHaloDrawable> playbackRowHalos = new WeakHashMap<>();
    private ValueAnimator playlistLocateAnimator;
    private ScrollView activePlaylistScroll;
    private ListView activePlaylistList;
    private PlaylistDetailAdapter activePlaylistAdapter;
    private LinearLayout activePlaylistSongHost;
    private int[] activePlaylistLoadedRef;
    private ImportedPlaylist activePlaylistPageModel;
    private View activePlaylistHeroCard;
    private int activePlaylistHeroAccent = Ui.CYAN;
    private ValueAnimator activePlaylistHeroAccentAnimator;
    private View activePlaylistHeroThumb;
    private TextView activePlaylistHeroTitle;
    private TextView activePlaylistHeroMeta;
    private View activePlaylistDetailTitleRow;
    private View activePlaylistDetailSubtitle;
    private View activePlaylistDetailBack;
    private View activePlaylistDetailSongHeader;
    private LinearLayout activePlaylistDetailSongHost;
    private TextView activePlaylistSongCount;
    private PlaylistHeroSnapshot playlistHeroSnapshot;
    private View playlistHeroSourceRow;
    private View playlistHeroSourceThumb;
    private View playlistHeroReturnRow;
    private View playlistHeroReturnThumb;
    private TextView playlistHeroReturnTitle;
    private TextView playlistHeroReturnMeta;
    private PlaylistHeroMorphView activePlaylistHeroMorph;
    private ValueAnimator activePlaylistSharedAnimator;
    private View activePlaylistTransitionOutgoing;
    private View activePlaylistTransitionIncoming;
    private float activePlaylistSharedProgress;
    private boolean activePlaylistTransitionPush;
    private boolean pendingPlaylistHeroPush;
    private boolean pendingPlaylistHeroPop;
    private FluidLoadingIconView activePlaylistLoader;
    private int deferredPageGeneration;
    private int playlistLoadGeneration;
    // Detail collections reuse the playlist row choreography. Keep already-resolved identities so
    // a recommendation enrichment refresh does not replay the whole entrance from the beginning.
    private final LinkedHashSet<String> smartCollectionRevealKeys = new LinkedHashSet<>();
    private String smartCollectionRevealScope = "";
    private String activePlaylistHighlightedKey = "";
    private int activePlaylistHighlightedAccent;
    private final LinkedHashMap<String, ArrayList<View>> activePlaylistRows = new LinkedHashMap<>();
    private String miniArtworkKey = "";
    private String playlistPlaybackAccentKey = "";
    private int systemTopInset;
    private int systemBottomInset;
    private int previousSoftInputMode;
    private boolean inputModalOpen;

    private int tab = 0;
    private List<Song> searchResults = new ArrayList<>();
    private List<Song> favorites = new ArrayList<>();
    private List<Song> history = new ArrayList<>();
    private List<String> searchHistory = new ArrayList<>();
    private List<ImportedPlaylist> playlists = new ArrayList<>();
    private ImportedPlaylist openPlaylist;
    private boolean offlinePlaylistOpen;
    private String offlineLibraryFilter = "all"; // all / download / local
    private boolean pendingLocalMusicScan;
    private ImportedPlaylist pendingPlaylistExport;
    private String pendingRingtoneSongKey = "";
    private int pendingRingtoneType = android.media.RingtoneManager.TYPE_RINGTONE;
    private long pendingRingtoneStartMs = 0L;
    private long pendingRingtoneEndMs = 0L;
    private boolean pendingDesktopLyricsEnable;
    private boolean pendingDesktopLyricsDirectEnable;
    // 0 none, 1 direct enable, 2 settings enable, 3 compatibility-only request. Android 13+ notification permission is
    // requested only from a user-initiated desktop-lyrics action, never at app startup.
    private int pendingDesktopLyricsNotificationMode;
    private android.media.MediaPlayer localMusicPreviewPlayer;
    private String localMusicPreviewKey = "";
    private boolean localMusicPreviewResumePlayback;
    private String localMusicPreviewResumeSongKey = "";
    private Runnable localMusicPreviewUiRefresh;
    private boolean downloadCenterOpen;
    private int downloadCenterGeneration;
    // V64 Ghost Swipe: keep the 900ms download-center refresh from rebuilding a card
    // while the user is physically dragging it. This is UI-only state and never enters
    // DownloadPlanStore or the download/playback scheduling planes.
    private boolean downloadPlanGestureActive;
    private int lastOfflineRecordCount = -1;
    private android.media.MediaPlayer ringtoneClipPreview;
    private android.media.Ringtone systemTonePreview;
    private Runnable ringtonePreviewStop;
    private int ringtonePreviewToken;
    private SmartCollection openSmartCollection;
    private int homeScrollY;
    private int homeRecommendationScrollX;
    private boolean restoreHomePosition;
    private HorizontalScrollView homeRecommendationCarousel;
    private static final String HOME_CARD_STYLE_PREFS = "xingyu_ui_settings";
    private static final String HOME_CARD_STYLE_KEY = "home_recommendation_card_style_v1";
    private TextView homeLyricQuoteView;
    private final List<String> homeLyricQuotes = new ArrayList<>();
    private final LinkedHashSet<String> homeQuotePrefetchAttempted = new LinkedHashSet<>();
    private Runnable homeLyricQuoteTicker;
    private int homeLyricQuoteIndex;
    private int homeLyricQuoteGeneration;
    // V46 playlist-detail rendering buffer. Keep enough rows ahead of the viewport so local UI
    // construction happens well before the user reaches the current tail, while keeping each
    // append small enough to avoid a large main-thread spike. Playback always receives the full
    // playlist; these constants affect presentation only.
    private static final int PLAYLIST_INITIAL_RENDER_COUNT = 10;
    private static final int PLAYLIST_APPEND_BATCH = 10;
    private static final int PLAYLIST_PREFETCH_DISTANCE_DP = 980;
    // V92.9.8 intentionally rolls back the V92.9.7 long-playlist ListView branch after a real-device
    // crash report.  Every playlist now enters through the same ScrollView shell, but song rows are
    // materialized in tiny frame-spaced batches behind structure-matched placeholders.
    private static final int PLAYLIST_PLACEHOLDER_COUNT = 6;
    private int playlistVisibleCount = PLAYLIST_INITIAL_RENDER_COUNT;
    private int playlistScrollRestoreY;
    private int playlistListRestorePosition;
    private int playlistListRestoreTop;
    private String playlistListRestoreId = "";
    private boolean restorePlaylistPosition;
    private boolean suppressNextPageAnimation;

    // V27 first-run coach marks. UI-only: never touches playback/source routing.
    private OnboardingOverlay onboardingOverlay;
    // V4.4 scene-triggered hidden-feature teaching. UI-only and persisted separately from onboarding.
    private ContextCoachOverlay contextCoachOverlay;
    private String activeContextCoachId = "";
    private final LinkedHashSet<String> deferredContextCoachThisSession = new LinkedHashSet<>();
    private static final String COACH_PREFS = "lunaxy_context_coach_v1";
    private static final String COACH_LYRIC_SEEK = "lyric_seek";
    private static final String COACH_PLAYLIST_QUEUE = "playlist_queue_takeover";
    private static final String COACH_IMPORT_PLAYLIST = "import_playlist";
    private static final String COACH_OFFLINE_SYSTEM = "offline_system_sound";
    private static final String COACH_OFFLINE_RINGTONE = "offline_ringtone";
    private static final String COACH_RINGTONE_CLIP = "ringtone_clip";
    private static final String COACH_STARFIELD_STUDIO = "starfield_studio_v2";
    private static final String COACH_DESKTOP_LYRIC = "desktop_lyric_v2";
    private static final String COACH_DOWNLOAD_CENTER = "download_center_v2";
    private static final String COACH_LOCAL_SCAN = "local_music_scan_v1";
    private static final String COACH_PLAYLIST_EXPORT = "playlist_export_v1";
    private static final String COACH_PLAYLIST_LOCATE = "playlist_locate_v1";
    private static final String COACH_LOCAL_PREVIEW = "local_music_preview_v1";
    private static final String COACH_DOWNLOAD_WAITING = "download_waiting_route_v2";
    private static final String COACH_DOWNLOAD_DETAIL = "download_plan_detail_v2";
    private static final String COACH_DOWNLOAD_MATRIX = "download_matrix_budget_v1";
    private static final String COACH_DESKTOP_LYRIC_CONTROLS = "desktop_lyric_controls_v1";
    private static final String COACH_SLEEP_TIMER = "sleep_timer_v1";
    private boolean onboardingStarted;
    private int onboardingStep;
    private ScrollView onboardingHomeScroll;
    private View onboardingSearchTarget;
    private View onboardingSourceTarget;
    private View onboardingRecentTarget;
    private View onboardingRecommendationTarget;
    private android.window.OnBackInvokedCallback backInvokedCallback;
    private Song lastHistorySong;
    private String lastErrorShown;
    // V92.5: a confirmed playback failure advances after 3s only if the same song is still current.
    private Runnable playbackErrorAutoSkipRunnable;
    private String playbackErrorAutoSkipSongKey = "";
    private List<Song> pendingQueue;
    private int pendingIndex;
    private Song pendingPickedSong;
    private final List<Song> pendingNextSongs = new ArrayList<>();

    private static final int REQ_WEATHER_LOCATION = 2107;
    private static final int REQ_LOCAL_AUDIO = 2108;
    private static final int REQ_EXPORT_PLAYLIST = 2109;
    private static final int REQ_IMPORT_LUNAXY_PLAYLIST = 2110;
    private static final int REQ_DESKTOP_LYRIC_NOTIFICATIONS = 2111;
    private static final int REQ_VOICE_AUDIO = 2112;
    private boolean pendingWeatherRadioOpen;
    private boolean pendingVoiceEnable;

    private static final class SmartCollection {
        final String title;
        final String subtitle;
        final String kind;
        final int accent;
        final List<Song> songs;
        SmartCollection(String title, String subtitle, String kind, int accent, List<Song> songs) {
            this.title = title == null ? "推荐歌单" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.kind = kind == null ? "smart" : kind;
            this.accent = accent;
            this.songs = songs == null ? new ArrayList<>() : new ArrayList<>(songs);
        }
    }

    private static final class PlaylistHeroSnapshot {
        final String playlistId;
        final String title;
        final String subtitle;
        final String artworkUrl;
        final RectF sourceContainer;
        final RectF sourceArtwork;
        final RectF sourceTitle;
        final RectF sourceMeta;
        final float sourceTitleSizePx;
        final float sourceMetaSizePx;
        int accent;
        final RectF detailContainer = new RectF();
        final RectF detailArtwork = new RectF();
        final RectF detailTitle = new RectF();
        final RectF detailMeta = new RectF();
        float detailTitleSizePx;
        float detailMetaSizePx;
        boolean hasDetailGeometry;

        PlaylistHeroSnapshot(String playlistId, String title, String subtitle, String artworkUrl,
                             RectF sourceContainer, RectF sourceArtwork, RectF sourceTitle, RectF sourceMeta,
                             float sourceTitleSizePx, float sourceMetaSizePx, int accent) {
            this.playlistId = playlistId == null ? "" : playlistId;
            this.title = title == null ? "" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.artworkUrl = artworkUrl == null ? "" : artworkUrl;
            this.sourceContainer = new RectF(sourceContainer);
            this.sourceArtwork = new RectF(sourceArtwork);
            this.sourceTitle = new RectF(sourceTitle);
            this.sourceMeta = new RectF(sourceMeta);
            this.sourceTitleSizePx = sourceTitleSizePx;
            this.sourceMetaSizePx = sourceMetaSizePx;
            this.accent = accent;
        }
    }

    // V88 recommendation fast-path request generation. Older async enrichments may finish,
    // but they are never allowed to reopen or overwrite a newer recommendation page.
    private int recommendationOpenToken;

    private String lastQuery = "";
    private String pendingSearchQuery = "";
    private ListView activeSearchAllList;
    private int searchAllSavedPosition;
    private int searchAllSavedTop;
    private int lastWyCount;
    private int lastTxCount;
    private int lastKwCount;
    private int lastKgCount;
    private String lastWyError = "";
    private String lastTxError = "";
    private String lastKwError = "";
    private String lastKgError = "";
    private List<Song> lastWySearchSongs = new ArrayList<>();
    private List<Song> lastTxSearchSongs = new ArrayList<>();
    private List<Song> lastKwSearchSongs = new ArrayList<>();
    private List<Song> lastKgSearchSongs = new ArrayList<>();
    private List<LyricSearchResult> lyricSearchResults = new ArrayList<>();
    private String searchSourceFilter = ""; // ""=all, wy, tx, kw, kg
    private boolean searchMetadataExpanded = true;
    private boolean searchLyricsExpanded = true;
    private boolean searchMetadataPending;
    private boolean lyricSearchPending;
    private boolean lyricVerificationPending;
    private int lyricIndexJobs;
    private int lyricVerificationJobs;
    private boolean lyricCatalogFallbackStarted;
    private boolean searchWyFinished;
    private boolean searchTxFinished;
    private int searchResultMode; // 0=综合 1=单曲 2=歌词
    private int searchModeAnimationToken; // Beta V8: guards delayed tab transition commits.
    private int searchIntentMode; // 0=歌曲/歌手 1=混合 2=歌词句子
    private boolean searchMoreLoading;
    private boolean searchWyHasMore;
    private boolean searchTxHasMore;
    private boolean searchKwHasMore;
    private boolean searchKgHasMore;
    private int searchWyNextOffset = 30;
    private int searchTxNextPage = 2;
    private int searchKwNextPage = 2;
    private int searchKgNextPage = 2;
    private static final int SEARCH_PAGE_SIZE = 20;
    private static final int SEARCH_SUMMARY_COUNT = 12;
    private static final long SEARCH_MERGE_COALESCE_MS = 105L;
    private static final long SEARCH_LYRIC_DEFER_MS = 420L;
    private static final long LYRIC_UI_COALESCE_MS = 96L;
    private static final long LYRIC_UI_DRAIN_MS = 118L;
    private static final int LYRIC_UI_BATCH_SIZE = 3;
    private static final int SEARCH_INTENT_METADATA = 0;
    private static final int SEARCH_INTENT_MIXED = 1;
    private static final int SEARCH_INTENT_LYRIC = 2;
    private static final int LYRIC_INDEX_LIMIT = 24;
    private static final int LYRIC_VERIFY_LIMIT = 12;
    private static final int LYRIC_CATALOG_FALLBACK_LIMIT = 12;
    // Lyric discovery/verification rely on their HTTP clients' own timeouts. UI state follows real
    // job completion instead of an unrelated batch stopwatch, preventing false "no hit" states.
    private int searchRequestToken;
    private int searchSnapshotVersion;
    private boolean searchFirstPaintDelivered;
    private boolean searchFirstMergeInFlight;
    private Runnable pendingSearchMergeRunnable;
    private Runnable pendingLyricSearchRunnable;
    private Runnable pendingLyricUiRefreshRunnable;
    private Runnable pendingLyricUiDrainRunnable;
    private LinearLayout activeSearchResultsHolder;
    private LyricSectionUi activeLyricSectionUi;
    private boolean lyricFirstUiPaintDelivered;
    private boolean lyricFirstVerifiedPaintDelivered;
    private int lyricUiVisibleLimit;
    private boolean searchAllResultsOpen;
    private long searchAutoLoadNotBeforeMs;
    // V87 Search Engine V2: stale-while-revalidate local first paint + adaptive two-wave catalog scheduling.
    private final List<Future<?>> activeSearchNetworkJobs = new ArrayList<>();
    private Future<?> activeSuggestionJob;
    private Runnable pendingSearchEnrichmentRunnable;
    private boolean lyricSearchStarted;
    private boolean searchCacheHit;
    private long searchStartedAtMs;
    private long searchCachePaintMs;
    private long searchFirstNetworkMs;
    private long searchStableMs;
    private final LinkedHashMap<String, Long> currentSearchLatencyMs = new LinkedHashMap<>();
    private LinearLayout activeSearchContentHost;
    private LinearLayout activeSearchSourceHost;
    private FrameLayout activeSearchTabs;
    private TextView[] activeSearchTabViews;
    private String lastSearchContentSignature = "";

    private static final class LyricSearchResult {
        final Song song;
        final String snippet;
        final boolean verified;
        final int evidenceMask; // 1=candidate LRC cache 2=NetEase remote 4=QQ remote
        LyricSearchResult(Song song, String snippet) { this(song, snippet, false, 0); }
        LyricSearchResult(Song song, String snippet, boolean verified, int evidenceMask) {
            this.song = song;
            this.snippet = snippet == null ? "" : snippet.trim();
            this.verified = verified;
            this.evidenceMask = evidenceMask;
        }
    }

    private static final class LyricRowUi {
        final LinearLayout card;
        final ImageView cover;
        final TextView title;
        final TextView artist;
        final TextView snippet;
        final FrameLayout more;
        String coverKey = "";
        LyricRowUi(LinearLayout card, ImageView cover, TextView title, TextView artist, TextView snippet, FrameLayout more) {
            this.card = card; this.cover = cover; this.title = title; this.artist = artist; this.snippet = snippet; this.more = more;
        }
    }

    private static final class LyricSectionUi {
        final LinearLayout root;
        final LinearLayout content;
        final TextView meta;
        final TextView arrow;
        final LinkedHashMap<String, LyricRowUi> rows = new LinkedHashMap<>();
        boolean showingRows;
        LyricSectionUi(LinearLayout root, LinearLayout content, TextView meta, TextView arrow) {
            this.root = root; this.content = content; this.meta = meta; this.arrow = arrow;
        }
    }

    // Search suggestion dropdown state. The token prevents stale async responses from repainting a newer query.
    private int suggestionRequestToken;
    private Runnable pendingSuggestionRunnable;
    private LinearLayout activeSearchSuggestions;


    // Now playing
    private VinylRecordView nowVinyl;
    private VinylStackView nowVinylStack;
    private ImageView nowCoverLoader;
    private TextView nowTitle;
    private TextView nowArtist;
    private TextView nowTime;
    private TextView nowDuration;
    private IconView nowModeIcon;
    private FrameLayout nowModeButton;
    private SeekBar nowSeek;
    private IconView nowPlayIcon;
    private FrameLayout nowPlayButton;
    private IconView nowFavoriteIcon;
    private FrameLayout nowFavoriteButton;
    private LyricGestureScrollView lyricScroll;
    private FrameLayout lyricPanel;
    private LinearLayout lyricBox;
    private TextView lyricEmpty;
    private int lyricGradientStart = Ui.LYRIC_ICE_START;
    private int lyricGradientEnd = Ui.LYRIC_ICE_END;
    private boolean lyricUseCoverColor;
    private long lyricsManualUntil;
    private Runnable lyricsResumeRunnable;
    private Runnable lyricScrollSettleRunnable;
    private LinearLayout lyricSeekGuide;
    private TextView lyricSeekGuideTime;
    private View lyricSeekGuideLeft;
    private View lyricSeekGuideRight;
    private FrameLayout lyricSeekGuidePlay;
    private boolean lyricManualGesture;
    private boolean lyricSeekMode;
    private boolean lyricGestureMoved;
    private int lyricSeekCandidate = -1;
    private int lyricManualSelection = -1;
    private List<LyricLine> lyricLines = new ArrayList<>();
    private int activeLyric = -2;
    private String lyricSongKey = "";
    private final Runnable lyricKaraokeTicker = new Runnable() {
        @Override public void run() {
            if (playerOverlay != null && playerOverlay.getVisibility() == View.VISIBLE
                    && playback != null && !lyricLines.isEmpty()) {
                updateLyricKaraokeProgress(playback.snapshot().positionMs);
            }
            main.postDelayed(this, 72L);
        }
    };
    private int nowAccent = Ui.PURPLE;
    private String nowArtworkKey = "";

    // V92 Motion System 2.0: shared artwork + Mini -> Full direct-manipulation state.
    private boolean miniPlayerMorphDragging;
    private float miniPlayerMorphProgress;
    private float miniPlayerDownX;
    private float miniPlayerDownY;
    private VelocityTracker miniPlayerVelocity;
    private ValueAnimator miniPlayerMorphAnimator;
    // V92.8.2: keep the proven V92.8 morph geometry and expose only its visual timing.
    // Playback timing remains fully owned by PlaybackService and is never scaled by this setting.
    private static final int PLAYER_OPEN_DURATION_MIN_MS = 450;
    private static final int PLAYER_OPEN_DURATION_MAX_MS = 1000;
    private static final int PLAYER_OPEN_DURATION_DEFAULT_MS = 900;
    private int playerOpenMorphDurationMs = PLAYER_OPEN_DURATION_DEFAULT_MS;
    private ArtworkMorphView activeArtworkMorph;
    private PlayerSurfaceMorphView activePlayerSurfaceMorph;
    private boolean trackSwipeAnimating;
    private boolean trackSwipeAdoptionStarted;
    private int trackSwipeDirection; // +1 next (left swipe), -1 previous (right swipe)
    // V92.5 queue scrub: one gesture can walk across several retained queue slots.
    private boolean trackBrowseGestureActive;
    private int trackBrowseStartIndex = -1;
    private int trackBrowseWindowOffset;
    private float trackBrowsePosition;
    private boolean trackBrowseCommitInFlight;
    private String trackBrowseExpectedKey = "";
    private SourceFlowView activeSourceFlowView;
    private String lastVisualPlaybackRoute = "";
    private long lastVisualRoutePulseAt;

    // Semantic page-motion bookkeeping. Root tabs move laterally; child pages move on the depth axis.
    private int lastRenderedMotionTab = 0;
    private int lastRenderedMotionDepth = 0;
    // Root-tab motion owns one continuous physical velocity. Retargeting a rapid tap keeps that
    // velocity instead of restarting a canned easing curve, which preserves spatial continuity.
    private int rootTabPhysicsToken;
    private float rootTabVelocityPxPerSec;

    // V55 visual-only starfield studio.  Stored separately from playback/library preferences.
    private static final String STARFIELD_PREFS = "lunaxy_starfield_v1";
    private boolean starfieldUse3D = true;
    private float starfieldSpeed = 1f;
    private float starfieldSensitivity = 1f;
    private int starfieldMotionProfile = Starfield3DView.PROFILE_LIVING;
    private int starfieldColorMode = 0;       // 0 cover, 1..6 preset
    private int starfieldBackgroundMode = 0;  // 0 black, 1 cover, 2..5 preset
    private float starfieldBackgroundGlow = .24f;
    // V56 official default: stronger visual presence while keeping V55's refined light texture.
    private float starfieldStarSize = 1.18f;
    private float starfieldStarBrightness = 1.20f;
    private float starfieldStarDensity = 1.16f;
    // V92.5: side-record clarity lives in Starfield Studio because it is purely visual.
    private float starfieldVinylClarity = 1f;
    private float vinylSwipeSensitivity = 1f;
    private SwipeAwareScrollView vinylGestureScroll;
    private com.xingyu.music.ui.ArtworkFlipView artworkFlip;
    private com.xingyu.music.ui.MetadataParticlesView metadataParticles;
    private float lyricFontScale=1f;
    private boolean lyricTapMayFlip;
    private long lyricTapStartedAt, lyricLastScrollAt;
    private int starfieldStudioTab;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override public void onReceive(android.content.Context context, Intent intent) {
            if (intent == null || !OfflineDownloadService.ACTION_CHANGED.equals(intent.getAction())) return;
            // Progress broadcasts are frequent. Only rebuild the offline page when the actual number
            // of completed offline files changes; Download Center observes progress independently.
            if (tab == 2 && offlinePlaylistOpen && !downloadCenterOpen && offlineStore != null) {
                int count = offlineStore.all().size();
                if (count != lastOfflineRecordCount) {
                    lastOfflineRecordCount = count;
                    suppressNextPageAnimation = true;
                    renderTab();
                }
            }
        }
    };

    private final BroadcastReceiver voiceReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || !VoiceAssistantContract.ACTION_STATE.equals(intent.getAction())) return;
            handleVoiceAssistantState(intent);
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            playback = ((PlaybackService.LocalBinder) binder).getService();
            playback.setListener(MainActivity.this);
            bound = true;
            onPlaybackChanged(playback.snapshot());
            if (pendingQueue != null) {
                List<Song> q = pendingQueue;
                int idx = pendingIndex;
                pendingQueue = null;
                playback.playQueue(q, idx);
            }
            if (pendingPickedSong != null) {
                Song picked = pendingPickedSong;
                pendingPickedSong = null;
                playback.insertAndPlay(picked);
            }
            if (!pendingNextSongs.isEmpty()) {
                List<Song> waiting = new ArrayList<>(pendingNextSongs);
                pendingNextSongs.clear();
                if (playback.queueSnapshot().isEmpty()) {
                    playback.enqueueNext(waiting.get(0));
                    for (int i = 1; i < waiting.size(); i++) playback.enqueueEnd(waiting.get(i));
                } else {
                    // Preserve tap order: repeatedly inserting "next" would reverse it, so enqueue from the tail.
                    for (int i = waiting.size() - 1; i >= 0; i--) playback.enqueueNext(waiting.get(i));
                }
            }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            bound = false;
            playback = null;
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemSplashExit();

        // V92.9.15: submit the real Lunaxy visual frame before data/service setup.
        AppearanceSystem.load(this);
        loadUiPreferences();
        loadStarfieldPreferences();
        configureWindow();
        buildShell();
        beginStartupScene();
        installSystemBackHandler();
        scheduleRuntimeInitializationAfterFirstFrame();
    }

    private void scheduleRuntimeInitializationAfterFirstFrame() {
        if (runtimeInitStarted) return;
        if (appRoot == null) {
            initializeRuntimeAfterFirstFrame();
            return;
        }
        appRoot.postOnAnimation(() -> appRoot.postOnAnimation(this::initializeRuntimeAfterFirstFrame));
    }

    private void initializeRuntimeAfterFirstFrame() {
        if (runtimeInitStarted || isFinishing() || isDestroyed()) return;
        runtimeInitStarted = true;
        V14PlaybackStateMigration.runOnce(this);
        personalizationStore = new PersonalizationStore(this);
        playbackBehaviorTracker = new PlaybackBehaviorTracker(personalizationStore);
        store = new LibraryStore(this, personalizationStore);
        offlineStore = new OfflineStore(this);
        trackVariantStore = new TrackVariantStore(this);
        backgroundVariantEnricher = new TrackVariantEnricher(trackVariantStore);
        lyricCache = new LyricCacheStore(this);
        lyricMatches = new LyricMatchStore(this);
        recommendations = new RecommendationEngine(this, netease, qq, personalizationStore);
        searchCache = new SearchCacheStore(this);
        searchPerformance = new SearchPerformanceStore(this);
        weatherMood = new WeatherMoodProvider(this);

        IntentFilter downloadFilter = new IntentFilter(OfflineDownloadService.ACTION_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(downloadReceiver, downloadFilter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(downloadReceiver, downloadFilter);
        IntentFilter voiceFilter = new IntentFilter(VoiceAssistantContract.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(voiceReceiver, voiceFilter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(voiceReceiver, voiceFilter);
        bindService(new Intent(this, PlaybackService.class), connection, BIND_AUTO_CREATE);
        bootstrapLibraryAndFirstPage();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (startupComplete) handleVoiceLaunchIntent(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        if (pendingDesktopLyricsDirectEnable) {
            pendingDesktopLyricsDirectEnable = false;
            if (Settings.canDrawOverlays(this)) enableDesktopLyricsAfterOverlay(true);
            else toast("未获得悬浮窗权限 · 桌面歌词未开启");
        }
        if (pendingDesktopLyricsEnable) {
            pendingDesktopLyricsEnable = false;
            if (Settings.canDrawOverlays(this)) enableDesktopLyricsAfterOverlay(false);
            else toast("未获得悬浮窗权限 · 桌面歌词未开启");
        }
        boolean resumedPendingTone = false;
        if (!pendingRingtoneSongKey.isEmpty() && offlineStore != null && RingtoneHelper.canWriteSystemSettings(this)) {
            OfflineStore.Record record = offlineStore.getBySongKey(pendingRingtoneSongKey);
            int type = pendingRingtoneType; long startMs = pendingRingtoneStartMs; long endMs = pendingRingtoneEndMs;
            pendingRingtoneSongKey = ""; pendingRingtoneStartMs = 0L; pendingRingtoneEndMs = 0L;
            resumedPendingTone = true;
            if (record != null) applySystemTone(record, type, startMs, endMs);
        }
        if (!resumedPendingTone && offlinePlaylistOpen && pageHost != null) {
            suppressNextPageAnimation = true;
            renderTab();
        }
    }

    /**
     * Once a long-press reorder starts, keep receiving MOVE/UP at Activity level. A ListView may
     * detach the original drag handle during a long edge-scroll; routing the active gesture here
     * prevents that detach from cancelling the interaction or leaking ACTION_UP to the sheet.
     */
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        ReorderGestureController controller = activeReorderController;
        if (controller != null && controller.isActive()) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                try {
                    controller.consumeGlobalTouch(event);
                } catch (Throwable dragFailure) {
                    // Gesture rendering is non-authoritative. Any OEM/ListView edge failure must
                    // abort the visual reorder instead of terminating the Activity/process.
                    controller.emergencyAbort();
                }
                return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void configureSystemSplashExit() {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            getSplashScreen().setOnExitAnimationListener(splashView -> {
                splashView.animate().cancel();
                splashView.setPivotX(splashView.getWidth() * .5f);
                splashView.setPivotY(splashView.getHeight() * .5f);
                splashView.animate().alpha(0f).scaleX(1.01f).scaleY(1.01f)
                        .setDuration(SpringMotion.isReducedMotion() ? 45L : 110L)
                        .setInterpolator(SpringMotion.PLAYER_OPEN)
                        .withEndAction(splashView::remove).start();
            });
        } catch (Throwable ignored) { }
    }

    private void beginStartupScene() {
        if (appRoot == null || safeLayer == null || stars == null) return;
        startupComplete = false;
        startupFinishScheduled = false;
        startupSceneStartedAtMs = SystemClock.uptimeMillis();
        startupEntranceTargets.clear();
        safeLayer.animate().cancel();
        safeLayer.setVisibility(View.INVISIBLE);
        safeLayer.setAlpha(1f);
        safeLayer.setTranslationY(0f);
        stars.setAccentColor(playlistPlaybackAccent);
        stars.setMotionSpeedMultiplier(SpringMotion.isReducedMotion() ? 1f : 4.8f);

        startupLogo = new ImageView(this);
        startupLogo.setImageResource(getApplicationInfo().icon);
        startupLogo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        startupLogo.setAlpha(1f);
        startupLogo.setScaleX(.90f);
        startupLogo.setScaleY(.90f);
        startupLogo.setContentDescription(null);
        startupLogo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams lp = Ui.frame(Ui.dp(this, 92), Ui.dp(this, 92), Gravity.CENTER);
        appRoot.addView(startupLogo, lp);
        startupLogo.bringToFront();
        if (!SpringMotion.isReducedMotion()) {
            startupLogo.animate().scaleX(1f).scaleY(1f).setDuration(620L)
                    .setInterpolator(SpringMotion.SOFT).start();
        }
    }

    private void bootstrapLibraryAndFirstPage() {
        final int generation = ++libraryBootstrapGeneration;
        io.submit(() -> {
            // Read each collection independently. A malformed auxiliary list must never make a
            // healthy formal playlist library appear empty, and no catch path writes back to prefs.
            List<Song> nextFavorites;
            List<Song> nextHistory;
            List<String> nextSearchHistory;
            List<ImportedPlaylist> nextPlaylists;
            try { nextFavorites = store.favorites(); } catch (RuntimeException ignored) { nextFavorites = new ArrayList<>(); }
            try { nextHistory = store.history(); } catch (RuntimeException ignored) { nextHistory = new ArrayList<>(); }
            try { nextSearchHistory = store.searchHistory(); } catch (RuntimeException ignored) { nextSearchHistory = new ArrayList<>(); }
            try { nextPlaylists = store.playlists(); } catch (RuntimeException ignored) { nextPlaylists = new ArrayList<>(); }
            final List<Song> f = nextFavorites;
            final List<Song> h = nextHistory;
            final List<String> sh = nextSearchHistory;
            final List<ImportedPlaylist> pl = nextPlaylists;
            main.post(() -> {
                if (generation != libraryBootstrapGeneration || isFinishing()) return;
                favorites = f;
                // PlaybackService can reconnect before the large Library bootstrap finishes and may
                // already have inserted the currently playing song into in-memory history. Merge
                // rather than overwrite that newer session event with an older background snapshot.
                history = mergeStartupHistory(h, history);
                searchHistory = sh;
                playlists = pl;
                renderTab();
                finishStartupScene();
            });
        });
    }

    private List<Song> mergeStartupHistory(List<Song> background, List<Song> live) {
        if (live == null || live.isEmpty()) return background == null ? new ArrayList<>() : background;
        LinkedHashMap<String, Song> merged = new LinkedHashMap<>();
        for (Song song : live) if (song != null) merged.put(song.key(), song);
        if (background != null) for (Song song : background) if (song != null) merged.putIfAbsent(song.key(), song);
        ArrayList<Song> out = new ArrayList<>(merged.values());
        return out.size() <= 50 ? out : new ArrayList<>(out.subList(0, 50));
    }

    private void finishStartupScene() {
        if (startupComplete || isFinishing() || isDestroyed()) return;
        if (!SpringMotion.isReducedMotion()) {
            long remaining = 760L - Math.max(0L, SystemClock.uptimeMillis() - startupSceneStartedAtMs);
            if (remaining > 0L) {
                if (!startupFinishScheduled) {
                    startupFinishScheduled = true;
                    main.postDelayed(() -> {
                        startupFinishScheduled = false;
                        finishStartupScene();
                    }, remaining);
                }
                return;
            }
        }
        startupFinishScheduled = false;
        startupComplete = true;

        prepareStartupHomeEntrance();
        if (safeLayer != null) {
            safeLayer.animate().cancel();
            safeLayer.setAlpha(1f);
            safeLayer.setTranslationY(0f);
            safeLayer.setVisibility(View.VISIBLE);
        }
        runStartupHomeEntrance();

        if (stars != null) {
            if (startupStarSpeedAnimator != null) startupStarSpeedAnimator.cancel();
            if (SpringMotion.isReducedMotion()) {
                stars.setMotionSpeedMultiplier(1f);
            } else {
                final float from = Math.max(1f, stars.motionSpeedMultiplier());
                startupStarSpeedAnimator = ValueAnimator.ofFloat(from, 1f);
                startupStarSpeedAnimator.setDuration(1080L);
                startupStarSpeedAnimator.setInterpolator(SpringMotion.PLAYER_OPEN);
                startupStarSpeedAnimator.addUpdateListener(a -> {
                    if (stars != null) stars.setMotionSpeedMultiplier((Float) a.getAnimatedValue());
                });
                startupStarSpeedAnimator.start();
            }
        }
        if (startupLogo != null) {
            final ImageView logo = startupLogo;
            logo.animate().cancel();
            if (SpringMotion.isReducedMotion()) {
                if (logo.getParent() instanceof ViewGroup) ((ViewGroup) logo.getParent()).removeView(logo);
                startupLogo = null;
            } else {
                logo.animate().alpha(0f).scaleX(1.055f).scaleY(1.055f).translationY(-Ui.dp(this, 6))
                        .setStartDelay(70L).setDuration(590L).setInterpolator(SpringMotion.SOFT)
                        .withEndAction(() -> {
                            if (logo.getParent() instanceof ViewGroup) ((ViewGroup) logo.getParent()).removeView(logo);
                            if (startupLogo == logo) startupLogo = null;
                        }).start();
            }
        }

        continueAfterStartupScene();
    }

    private void continueAfterStartupScene() {
        handleVoiceLaunchIntent(getIntent());
        if (VoiceAssistantContract.enabled(this)
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            main.postDelayed(() -> startVoiceAssistant(false), 920L);
        main.postDelayed(this::maybeStartOnboarding, 1280L);
    }

    private void prepareStartupHomeEntrance() {
        startupEntranceTargets.clear();
        if (SpringMotion.isReducedMotion() || tab != 0 || openPlaylist != null
                || openSmartCollection != null || offlinePlaylistOpen || pageHost == null
                || pageHost.getChildCount() == 0) return;

        View page = pageHost.getChildAt(pageHost.getChildCount() - 1);
        if (page instanceof ScrollView) {
            ScrollView scroll = (ScrollView) page;
            if (scroll.getChildCount() > 0 && scroll.getChildAt(0) instanceof ViewGroup) {
                ViewGroup scaffold = (ViewGroup) scroll.getChildAt(0);
                int count = scaffold.getChildCount();
                for (int i = 0; i < count; i++) {
                    View child = scaffold.getChildAt(i);
                    if (i == count - 1 && child instanceof ViewGroup) {
                        ViewGroup sections = (ViewGroup) child;
                        for (int j = 0; j < sections.getChildCount(); j++)
                            addStartupEntranceTarget(sections.getChildAt(j));
                    } else {
                        addStartupEntranceTarget(child);
                    }
                }
            }
        }
        addStartupEntranceTarget(miniBar);
        addStartupEntranceTarget(navHost);

        float lift = Ui.dp(this, 12);
        for (View target : startupEntranceTargets) {
            target.animate().cancel();
            target.setAlpha(0f);
            target.setScaleX(.92f);
            target.setScaleY(.92f);
            target.setTranslationY(lift);
        }
    }

    private void addStartupEntranceTarget(View target) {
        if (target == null || target.getVisibility() != View.VISIBLE || startupEntranceTargets.contains(target)) return;
        startupEntranceTargets.add(target);
    }

    private void runStartupHomeEntrance() {
        if (SpringMotion.isReducedMotion()) {
            for (View target : startupEntranceTargets) {
                target.setAlpha(1f);
                target.setScaleX(1f);
                target.setScaleY(1f);
                target.setTranslationY(0f);
            }
            startupEntranceTargets.clear();
            return;
        }
        if (startupEntranceTargets.isEmpty() || appRoot == null) return;
        final ArrayList<View> targets = new ArrayList<>(startupEntranceTargets);
        startupEntranceTargets.clear();
        appRoot.postOnAnimation(() -> {
            for (int i = 0; i < targets.size(); i++) {
                View target = targets.get(i);
                if (target == null || !target.isAttachedToWindow()) continue;
                long delay = 110L + Math.min(i, 8) * 58L;
                target.animate().cancel();
                target.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                        .setStartDelay(delay).setDuration(560L)
                        .setInterpolator(SpringMotion.SOFT).start();
            }
        });
    }

    private void loadUiPreferences() {
        SharedPreferences prefs = getSharedPreferences("xingyu_ui_settings", MODE_PRIVATE);
        lyricGradientStart = prefs.getInt("lyric_gradient_start", Ui.LYRIC_ICE_START);
        lyricGradientEnd = prefs.getInt("lyric_gradient_end", Ui.LYRIC_ICE_END);
        lyricUseCoverColor = prefs.getBoolean("lyric_gradient_cover", false);
        lyricFontScale=clampFloat(prefs.getFloat("lyric_font_scale",1f),.8f,1.6f);
    }

    private void loadStarfieldPreferences() {
        SharedPreferences p = getSharedPreferences(STARFIELD_PREFS, MODE_PRIVATE);
        starfieldUse3D = p.getBoolean("mode_3d", true);
        starfieldSpeed = clampFloat(p.getFloat("speed", 1f), .45f, 1.90f);
        starfieldSensitivity = clampFloat(p.getFloat("sensitivity", 1f), .35f, 1.85f);
        int savedMotion = p.getInt("motion", Starfield3DView.PROFILE_LIVING);
        starfieldMotionProfile = savedMotion == Starfield3DView.PROFILE_CLASSIC_FORWARD
                ? Starfield3DView.PROFILE_CLASSIC_FORWARD
                : savedMotion == Starfield3DView.PROFILE_DEEP_SPACE
                ? Starfield3DView.PROFILE_DEEP_SPACE : Starfield3DView.PROFILE_LIVING;
        starfieldColorMode = Math.max(0, Math.min(6, p.getInt("particle_color", 0)));
        starfieldBackgroundMode = Math.max(0, Math.min(5, p.getInt("background_mode", 0)));
        starfieldBackgroundGlow = clampFloat(p.getFloat("background_glow", .24f), 0f, 1f);
        starfieldStarSize = clampFloat(p.getFloat("star_size", 1.18f), .70f, 1.55f);
        starfieldStarBrightness = clampFloat(p.getFloat("star_brightness", 1.20f), .65f, 1.55f);
        starfieldStarDensity = clampFloat(p.getFloat("star_density", 1.16f), .60f, 1.35f);
        starfieldVinylClarity = clampFloat(p.getFloat("vinyl_side_clarity", 1f), 0f, 1f);
        vinylSwipeSensitivity = clampFloat(p.getFloat("vinyl_swipe_sensitivity", 1f), .5f, 1.5f);
        playerOpenMorphDurationMs = Math.max(PLAYER_OPEN_DURATION_MIN_MS, Math.min(PLAYER_OPEN_DURATION_MAX_MS,
                p.getInt("player_open_duration_ms", PLAYER_OPEN_DURATION_DEFAULT_MS)));
    }

    private void saveStarfieldPreferences() {
        getSharedPreferences(STARFIELD_PREFS, MODE_PRIVATE).edit()
                .putBoolean("mode_3d", starfieldUse3D)
                .putFloat("speed", starfieldSpeed)
                .putFloat("sensitivity", starfieldSensitivity)
                .putInt("motion", starfieldMotionProfile)
                .putInt("particle_color", starfieldColorMode)
                .putInt("background_mode", starfieldBackgroundMode)
                .putFloat("background_glow", starfieldBackgroundGlow)
                .putFloat("star_size", starfieldStarSize)
                .putFloat("star_brightness", starfieldStarBrightness)
                .putFloat("star_density", starfieldStarDensity)
                .putFloat("vinyl_side_clarity", starfieldVinylClarity)
                .putFloat("vinyl_swipe_sensitivity", vinylSwipeSensitivity)
                .putInt("player_open_duration_ms", playerOpenMorphDurationMs)
                .apply();
    }

    private void resetStarfieldPreferences() {
        starfieldUse3D = true;
        starfieldSpeed = 1f;
        starfieldSensitivity = 1f;
        starfieldMotionProfile = Starfield3DView.PROFILE_LIVING;
        starfieldColorMode = 0;
        starfieldBackgroundMode = 0;
        starfieldBackgroundGlow = .24f;
        starfieldStarSize = 1.18f;
        starfieldStarBrightness = 1.20f;
        starfieldStarDensity = 1.16f;
        starfieldVinylClarity = 1f;
        vinylSwipeSensitivity = 1f;
        playerOpenMorphDurationMs = PLAYER_OPEN_DURATION_DEFAULT_MS;
        saveStarfieldPreferences();
        applyStarfieldPreferences();
    }

    private int resolvedStarfieldColor() {
        switch (starfieldColorMode) {
            case 1: return Color.rgb(151, 220, 255);   // ice blue
            case 2: return Color.rgb(255, 214, 143);   // warm gold
            case 3: return Color.rgb(191, 166, 255);   // stellar violet
            case 4: return Color.rgb(142, 232, 198);   // mint
            case 5: return Color.rgb(255, 167, 210);   // rose
            case 6: return Color.rgb(226, 235, 248);   // silver
            case 0:
            default: return nowAccent;
        }
    }

    private int resolvedStarfieldBackgroundColor() {
        switch (starfieldBackgroundMode) {
            case 1: return nowAccent;
            case 2: return Color.rgb(62, 113, 182);
            case 3: return Color.rgb(126, 92, 201);
            case 4: return Color.rgb(198, 133, 72);
            case 5: return Color.rgb(180, 81, 132);
            case 0:
            default: return Color.BLACK;
        }
    }

    private void applyPlayerStarfieldAccent() {
        int color = resolvedStarfieldColor();
        if (playerStars != null) playerStars.setAccentColor(color);
        if (playerStars2D != null) playerStars2D.setAccentColor(color);
        if (playerStarBackdrop != null) {
            playerStarBackdrop.setAmbientColor(resolvedStarfieldBackgroundColor());
            playerStarBackdrop.setStrength(starfieldBackgroundMode == 0 ? 0f : starfieldBackgroundGlow);
        }
    }

    private void applyStarfieldPreferences() {
        if (vinylGestureScroll != null) vinylGestureScroll.setSwipeSensitivity(vinylSwipeSensitivity);
        if (playerStars != null) {
            playerStars.setSpeedMultiplier(starfieldSpeed);
            playerStars.setSensitivity(starfieldSensitivity);
            playerStars.setMotionProfile(starfieldMotionProfile);
            playerStars.setStarSizeMultiplier(starfieldStarSize);
            playerStars.setStarBrightnessMultiplier(starfieldStarBrightness);
            playerStars.setStarDensityMultiplier(starfieldStarDensity);
            playerStars.setVisibility(starfieldUse3D ? View.VISIBLE : View.GONE);
        }
        if (playerStars2D != null) playerStars2D.setVisibility(starfieldUse3D ? View.GONE : View.VISIBLE);
        if (nowVinylStack != null) nowVinylStack.setNeighbourClarity(starfieldVinylClarity);
        applyPlayerStarfieldAccent();
    }

    private static float clampFloat(float value, float lo, float hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    private void configureWindow() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Ui.BG);
        getWindow().getDecorView().setBackgroundColor(Ui.BG);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setNavigationBarDividerColor(Color.TRANSPARENT);
        }
        applyImmersiveStatusBar();
    }

    private void applySystemBarAppearance() {
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(AppearanceSystem.isLight() ? mask : 0, mask);
            }
        } else {
            int flags = decor.getSystemUiVisibility();
            int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) light |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            flags = AppearanceSystem.isLight() ? (flags | light) : (flags & ~light);
            decor.setSystemUiVisibility(flags);
        }
    }

    private void applyAppearanceToShell() {
        getWindow().setNavigationBarColor(Ui.BG);
        getWindow().getDecorView().setBackgroundColor(Ui.BG);
        applySystemBarAppearance();
        if (appRoot != null) appRoot.setBackgroundColor(Ui.BG);
        if (stars != null) {
            stars.setBackgroundColor(Ui.BG);
            stars.setAccentColor(playlistPlaybackAccent);
            stars.invalidate();
        }
        if (playerOverlay != null) playerOverlay.setBackgroundColor(Ui.BG);
        if (playerStars != null) {
            playerStars.setBackgroundColor(Ui.BG);
            playerStars.setAccentColor(resolvedStarfieldColor());
            playerStars.invalidate();
        }
        if (playerStars2D != null) {
            playerStars2D.setBackgroundColor(Ui.BG);
            playerStars2D.setAccentColor(resolvedStarfieldColor());
            playerStars2D.invalidate();
        }
        if (navHost != null) navHost.setBackground(Ui.functionalGlass(30, this));
        if (miniBar != null) miniBar.setBackground(Ui.functionalGlass(21, this));
        if (miniTitle != null) miniTitle.setTextColor(Ui.TEXT);
        if (miniArtist != null) miniArtist.setTextColor(Ui.DIM);
        if (miniPlayIcon != null) miniPlayIcon.setIconColor(Ui.TEXT);
        if (miniQueueIcon != null) miniQueueIcon.setIconColor(Ui.TEXT_2);
        if (miniPlayButton != null) miniPlayButton.setBackground(Ui.playerControlSurface(Ui.PURPLE, 21, this));
        if (miniProgress != null) miniProgress.setBackground(Ui.round(Ui.PURPLE, 1.5f, this));
        if (modalOverlay != null) modalOverlay.setBackgroundColor(AppearanceSystem.isLight()
                ? Color.argb(88, 30, 34, 44) : Color.argb(175, 0, 0, 0));
        if (snackbar != null) {
            snackbar.setTextColor(Ui.TEXT);
            snackbar.setBackground(Ui.functionalGlass(18, this));
        }
    }

    /**
     * Default immersive mode: hide only the top status bar, keep Android's bottom navigation
     * available, and allow a deliberate swipe from the edge to reveal the status bar transiently.
     */
    private void applyImmersiveStatusBar() {
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= 30) {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.statusBars());
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            if (AppearanceSystem.isLight()) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
        applySystemBarAppearance();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) main.postDelayed(this::applyImmersiveStatusBar, 80L);
    }

    private void buildShell() {
        appRoot = new FrameLayout(this);
        appRoot.setBackgroundColor(Ui.BG);
        stars = new StarfieldView(this);
        appRoot.addView(stars, Ui.frame(-1, -1, Gravity.FILL));

        safeLayer = new FrameLayout(this);
        appRoot.addView(safeLayer, Ui.frame(-1, -1, Gravity.FILL));
        appRoot.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets sys = insets.getInsets(WindowInsets.Type.systemBars());
                android.graphics.Insets cutout = insets.getInsets(WindowInsets.Type.displayCutout());
                systemTopInset = Math.max(sys.top, cutout.top);
                systemBottomInset = sys.bottom;
            } else {
                systemTopInset = insets.getSystemWindowInsetTop();
                if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null)
                    systemTopInset = Math.max(systemTopInset, insets.getDisplayCutout().getSafeInsetTop());
                systemBottomInset = insets.getSystemWindowInsetBottom();
            }
            safeLayer.setPadding(0, systemTopInset, 0, systemBottomInset);
            if (modalOverlay != null) modalOverlay.setPadding(0, systemTopInset, 0, systemBottomInset);
            if (snackbar != null) {
                FrameLayout.LayoutParams sp = (FrameLayout.LayoutParams) snackbar.getLayoutParams();
                sp.topMargin = systemTopInset + Ui.dp(this, 12);
                snackbar.setLayoutParams(sp);
            }
            if (voicePanel != null) {
                FrameLayout.LayoutParams vp = (FrameLayout.LayoutParams) voicePanel.getLayoutParams();
                vp.topMargin = systemTopInset + Ui.dp(this, 16);
                voicePanel.setLayoutParams(vp);
            }
            return insets;
        });

        pageHost = new FrameLayout(this);
        safeLayer.addView(pageHost, Ui.frame(-1, -1, Gravity.FILL));
        buildMiniPlayer();
        buildNav();
        buildBusy();
        buildModalLayer();
        buildSnackbar();
        buildVoiceLayer();
        setContentView(appRoot);
    }

    private void buildNav() {
        navHost = new FrameLayout(this);
        navHost.setPadding(Ui.dp(this, 6), Ui.dp(this, 5), Ui.dp(this, 6), Ui.dp(this, 5));
        navHost.setBackground(Ui.functionalGlass(30, this));
        navHost.setElevation(Ui.dp(this, 8));

        navSelectionPill = new View(this);
        navSelectionPill.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        navHost.addView(navSelectionPill, Ui.frame(Ui.dp(this, 62), Ui.dp(this, 44), Gravity.TOP | Gravity.START));

        navBar = Ui.row(this);
        navBar.setBackgroundColor(Color.TRANSPARENT);
        navHost.addView(navBar, Ui.frame(-1, -1, Gravity.FILL));

        IconView.Type[] icons = {IconView.Type.HOME, IconView.Type.SEARCH, IconView.Type.PLAYLIST, IconView.Type.HEART};
        String[] labels = {"首页", "搜索", "歌单", "收藏"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = Ui.column(this);
            item.setGravity(Gravity.CENTER);
            item.setClickable(true);
            item.setFocusable(true);
            item.setContentDescription(labels[i]);

            FrameLayout iconShell = new FrameLayout(this);
            IconView icon = new IconView(this, icons[i], Ui.DIM);
            iconShell.addView(icon, Ui.frame(Ui.dp(this, 23), Ui.dp(this, 23), Gravity.CENTER));
            item.addView(iconShell, Ui.lp(-1, Ui.dp(this, 30)));

            TextView label = Ui.text(this, labels[i], 10.5f, Ui.DIM, false);
            label.setGravity(Gravity.CENTER);
            item.addView(label, Ui.lp(-1, Ui.dp(this, 24)));
            Ui.applyRipple(item, Color.TRANSPARENT);
            installNavPressFeedback(item);
            item.setOnClickListener(v -> switchRootTab(idx, item));

            navItems[i] = item;
            navIcons[i] = icon;
            navLabels[i] = label;
            navBar.addView(item, new LinearLayout.LayoutParams(0, -1, 1f));
        }

        FrameLayout.LayoutParams fp = Ui.frame(-1, Ui.dp(this, 72), Gravity.BOTTOM);
        Ui.margins(fp, 16, 0, 16, 10, this);
        safeLayer.addView(navHost, fp);
        refreshNav();
    }

    /**
     * Tab contact is a real interaction state, not an after-click decoration.  ACTION_DOWN responds
     * immediately; releasing/cancelling retargets from the current presentation state.  Returning
     * false keeps Android's normal click/accessibility dispatch intact.
     */
    private void installNavPressFeedback(View item) {
        if (item == null) return;
        item.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (SpringMotion.isReducedMotion()) {
                if (action == MotionEvent.ACTION_DOWN) v.setAlpha(.88f);
                else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) v.setAlpha(1f);
                return false;
            }
            if (action == MotionEvent.ACTION_DOWN) {
                v.animate().cancel();
                v.animate().scaleX(.962f).scaleY(.962f).alpha(.86f)
                        .setDuration(SpringMotion.pressDownDuration())
                        .setInterpolator(SpringMotion.SNAPPY).start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.animate().cancel();
                v.animate().scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(SpringMotion.pressUpDuration())
                        .setInterpolator(SpringMotion.PRESS).start();
            }
            return false;
        });
    }

    private void switchRootTab(int target, View source) {
        target = Math.max(0, Math.min(3, target));
        boolean currentHasDetail = openPlaylist != null || offlinePlaylistOpen || openSmartCollection != null
                || (searchAllResultsOpen && tab == 1);
        if (target == tab && !currentHasDetail) {
            // Reselect does not rebuild the page.  The touch state above is the immediate feedback;
            // this small icon pulse confirms that the already-selected destination was addressed.
            IconView icon = target >= 0 && target < navIcons.length ? navIcons[target] : null;
            if (icon != null && !SpringMotion.isReducedMotion()) {
                icon.animate().cancel();
                icon.setScaleX(1.12f); icon.setScaleY(1.12f);
                icon.animate().scaleX(1.08f).scaleY(1.08f)
                        .setDuration(SpringMotion.selectionDuration())
                        .setInterpolator(SpringMotion.PRESS).start();
            }
            return;
        }

        // Top-level tabs own independent navigation stacks. Switching away saves both the root
        // scroll and any one-level detail destination; switching back restores that exact context.
        // Re-tapping the active Library tab while a playlist is open uses the same shared-object
        // reverse path as Back instead of silently bypassing the card identity.
        if (target == tab && target == 2 && openPlaylist != null) {
            closePlaylistToLibrary();
            return;
        }
        rememberRootTabContext();
        if (target == tab && currentHasDetail) {
            clearRootTabDetailState(target);
            openPlaylist = null;
            offlinePlaylistOpen = false;
            openSmartCollection = null;
            searchAllResultsOpen = false;
        } else {
            tab = target;
            restoreRootTabDetailState(target);
        }
        refreshNav();
        renderTab();
    }

    private void clearRootTabDetailState(int target) {
        if (target < 0 || target >= rootTabScrollY.length) return;
        rootTabOpenPlaylist[target] = null;
        rootTabOfflinePlaylistOpen[target] = false;
        rootTabSmartCollection[target] = null;
        rootTabSearchAllResultsOpen[target] = false;
        rootTabDetailScrollY[target] = 0;
        if (target == 1) {
            activeSearchAllList = null;
            searchAllSavedPosition = 0;
            searchAllSavedTop = 0;
        }
    }

    private void restoreRootTabDetailState(int target) {
        if (target < 0 || target >= rootTabScrollY.length) return;
        openPlaylist = rootTabOpenPlaylist[target];
        offlinePlaylistOpen = rootTabOfflinePlaylistOpen[target];
        openSmartCollection = rootTabSmartCollection[target];
        searchAllResultsOpen = target == 1 && rootTabSearchAllResultsOpen[target];
    }

    private void refreshNav() {
        if (navBar == null) return;
        int[] accents = {Ui.PURPLE, Ui.CYAN, Ui.GREEN, Ui.PINK};
        int idleNav = AppearanceSystem.isLight() ? Ui.TEXT_2 : Ui.DIM;
        for (int i = 0; i < 4; i++) {
            boolean selected = tab == i;
            if (navIcons[i] != null) {
                navIcons[i].setIconColor(selected ? accents[i] : idleNav);
                navIcons[i].animate().cancel();
                navIcons[i].animate().scaleX(selected ? 1.08f : 1f).scaleY(selected ? 1.08f : 1f)
                        .setDuration(SpringMotion.selectionDuration()).setInterpolator(SpringMotion.SNAPPY).start();
            }
            if (navLabels[i] != null) {
                navLabels[i].setTextColor(selected ? Ui.TEXT : idleNav);
                navLabels[i].setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            }
            if (navItems[i] != null) navItems[i].setSelected(selected);
        }
        if (navHost != null) navHost.setBackground(Ui.functionalGlass(30, this));
        navBar.post(this::animateNavSelectionPill);
    }

    private void animateNavSelectionPill() {
        if (navSelectionPill == null || navHost == null || navBar == null || tab < 0 || tab >= navItems.length) return;
        View target = navItems[tab];
        if (target == null || target.getWidth() <= 0 || target.getHeight() <= 0) return;
        int accent = new int[]{Ui.PURPLE, Ui.CYAN, Ui.GREEN, Ui.PINK}[tab];

        // Size from the actual tab cell rather than a hard-coded shell guess.  The carrier is a
        // visual selection plane behind the icon+label, so it should be concentric with that cell.
        int width = Math.max(Ui.dp(this, 54), target.getWidth() - Ui.dp(this, 14));
        int height = Math.max(Ui.dp(this, 48), target.getHeight() - Ui.dp(this, 10));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) navSelectionPill.getLayoutParams();
        int topMargin = Math.max(0, (target.getHeight() - height) / 2);
        if (lp.width != width || lp.height != height || lp.topMargin != topMargin) {
            lp.width = width;
            lp.height = height;
            lp.topMargin = topMargin;
            navSelectionPill.setLayoutParams(lp);
        }
        navSelectionPill.setBackground(Ui.stroke(
                Color.argb(AppearanceSystem.reduceTransparency() ? 255 : (AppearanceSystem.isLight() ? 44 : 48),
                        Color.red(accent), Color.green(accent), Color.blue(accent)),
                19, Color.argb(AppearanceSystem.isLight() ? 34 : 42,
                        Color.red(accent), Color.green(accent), Color.blue(accent)), this));

        // Convert the target center into navHost coordinates, then subtract the pill's own layout
        // center.  TranslationX is relative to the pill's laid-out position; adding navBar.getLeft()
        // directly used to count navHost padding twice and visibly shifted the color block.
        float targetCenterInHost = navBar.getX() + target.getX() + target.getWidth() * .5f;
        float pillBaseCenter = navSelectionPill.getLeft() + width * .5f;
        float x = targetCenterInHost - pillBaseCenter;

        navSelectionPill.animate().cancel();
        navSelectionPill.setScaleX(1f);
        navSelectionPill.setScaleY(1f);
        navSelectionPill.setAlpha(1f);
        if (SpringMotion.isReducedMotion()) {
            SpringMotion.cancelTranslationX(navSelectionPill);
            navSelectionPill.setTranslationX(x);
        } else {
            // Selection is one persistent physical carrier.  The spring keeps its current velocity
            // when a second tab is tapped mid-flight, so the pill glides through the intermediate
            // position instead of cancelling and restarting a separate tween.
            SpringMotion.springTranslationX(navSelectionPill, x);
        }
    }

    private void rememberRootTabContext() {
        if (tab < 0 || tab >= rootTabScrollY.length || pageHost == null || pageHost.getChildCount() == 0) return;
        rootTabOpenPlaylist[tab] = openPlaylist;
        rootTabOfflinePlaylistOpen[tab] = offlinePlaylistOpen;
        rootTabSmartCollection[tab] = openSmartCollection;
        rootTabSearchAllResultsOpen[tab] = tab == 1 && searchAllResultsOpen;

        View page = pageHost.getChildAt(pageHost.getChildCount() - 1);
        boolean detail = openPlaylist != null || offlinePlaylistOpen || openSmartCollection != null
                || (searchAllResultsOpen && tab == 1);
        if (detail) {
            if (page instanceof ScrollView) rootTabDetailScrollY[tab] = ((ScrollView) page).getScrollY();
            if (openPlaylist != null && activePlaylistList != null) {
                playlistListRestorePosition = Math.max(0, activePlaylistList.getFirstVisiblePosition());
                View first = activePlaylistList.getChildCount() > 0 ? activePlaylistList.getChildAt(0) : null;
                playlistListRestoreTop = first == null ? 0 : first.getTop();
                playlistListRestoreId = openPlaylist.id;
                restorePlaylistPosition = true;
            }
            if (tab == 1 && searchAllResultsOpen && activeSearchAllList != null) {
                searchAllSavedPosition = Math.max(0, activeSearchAllList.getFirstVisiblePosition());
                View first = activeSearchAllList.getChildCount() > 0 ? activeSearchAllList.getChildAt(0) : null;
                searchAllSavedTop = first == null ? 0 : first.getTop();
            }
            return;
        }
        if (page instanceof ScrollView) rootTabScrollY[tab] = ((ScrollView) page).getScrollY();
        if (tab == 0) {
            homeScrollY = rootTabScrollY[0];
            if (homeRecommendationCarousel != null) homeRecommendationScrollX = homeRecommendationCarousel.getScrollX();
        }
    }

    private void restoreRootTabContext(View page, int renderedTab, int depth) {
        if (renderedTab < 0 || renderedTab >= rootTabScrollY.length) return;
        if (page instanceof ScrollView) {
            int y = Math.max(0, depth == 0 ? rootTabScrollY[renderedTab] : rootTabDetailScrollY[renderedTab]);
            ScrollView scroll = (ScrollView) page;
            scroll.post(() -> scroll.scrollTo(0, y));
            return;
        }
        if (page instanceof ListView && openPlaylist != null && openPlaylist.id.equals(playlistListRestoreId)) {
            ListView list = (ListView) page;
            final int pos = Math.max(0, playlistListRestorePosition);
            final int top = playlistListRestoreTop;
            list.post(() -> list.setSelectionFromTop(Math.min(pos, Math.max(0, list.getCount() - 1)), top));
            restorePlaylistPosition = false;
        }
    }

    private void buildMiniPlayer() {
        miniBar = new FrameLayout(this);
        miniBar.setVisibility(View.GONE);
        miniBar.setBackground(Ui.functionalGlass(21, this));
        miniBar.setElevation(Ui.dp(this, 8));
        miniBar.setClickable(true);
        Ui.applyRipple(miniBar, Color.argb(30, 255, 255, 255));
        miniBar.setOnClickListener(v -> openNowPlayingFrom(miniCover));

        miniCover = new ImageView(this);
        miniCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniCover.setBackground(Ui.round(Color.rgb(22, 22, 28), 12, this));
        miniCover.setClipToOutline(true);
        FrameLayout.LayoutParams cp = Ui.frame(Ui.dp(this, 48), Ui.dp(this, 48), Gravity.START | Gravity.CENTER_VERTICAL);
        Ui.margins(cp, 8, 0, 0, 0, this);
        miniBar.addView(miniCover, cp);

        LinearLayout texts = Ui.column(this);
        miniTitle = Ui.text(this, "", 14, Ui.TEXT, true);
        miniTitle.setSingleLine(true);
        miniTitle.setEllipsize(TextUtils.TruncateAt.END);
        miniArtist = Ui.text(this, "", 11.2f, Ui.DIM, false);
        miniArtist.setSingleLine(true);
        miniArtist.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(miniTitle, new LinearLayout.LayoutParams(-1, 0, 1f));
        texts.addView(miniArtist, new LinearLayout.LayoutParams(-1, 0, 1f));
        FrameLayout.LayoutParams tp = Ui.frame(-1, Ui.dp(this, 44), Gravity.CENTER_VERTICAL);
        tp.leftMargin = Ui.dp(this, 68);
        tp.rightMargin = Ui.dp(this, 98);
        miniBar.addView(texts, tp);

        FrameLayout play = Ui.iconButton(this, IconView.Type.PLAY, 42, Ui.TEXT, Color.argb(18, 255, 255, 255));
        miniPlayButton = play;
        miniPlayIcon = (IconView) play.getChildAt(0);
        play.setOnClickListener(v -> { if (playback != null) playback.toggle(); });
        FrameLayout.LayoutParams pp = Ui.frame(Ui.dp(this, 42), Ui.dp(this, 42), Gravity.END | Gravity.CENTER_VERTICAL);
        pp.rightMargin = Ui.dp(this, 50);
        miniBar.addView(play, pp);

        FrameLayout queue = Ui.iconButton(this, IconView.Type.PLAYLIST, 38, Ui.TEXT_2, Color.TRANSPARENT);
        miniQueueIcon = (IconView) queue.getChildAt(0);
        queue.setContentDescription("打开播放列表");
        queue.setOnClickListener(v -> showPlaybackQueue());
        FrameLayout.LayoutParams np = Ui.frame(Ui.dp(this, 38), Ui.dp(this, 38), Gravity.END | Gravity.CENTER_VERTICAL);
        np.rightMargin = Ui.dp(this, 7);
        miniBar.addView(queue, np);

        miniProgress = new View(this);
        miniProgress.setBackground(Ui.round(Ui.PURPLE, 1.5f, this));
        FrameLayout.LayoutParams prog = Ui.frame(0, Ui.dp(this, 2), Gravity.BOTTOM | Gravity.START);
        prog.leftMargin = Ui.dp(this, 22);
        prog.bottomMargin = 0;
        miniBar.addView(miniProgress, prog);

        FrameLayout.LayoutParams fp = Ui.frame(-1, Ui.dp(this, 62), Gravity.BOTTOM);
        Ui.margins(fp, 16, 0, 16, 91, this);
        safeLayer.addView(miniBar, fp);
        installMiniPlayerMorphGesture();

        // Playlist-only locator: floats just above the mini player without changing its layout.
        playlistLocateButton = Ui.iconButton(this, IconView.Type.LOCATE, 38, Ui.CYAN, Color.argb(222, 10, 12, 17));
        playlistLocateButton.setBackground(Ui.tintedGlass(Ui.CYAN, 19, this));
        playlistLocateButton.setContentDescription("定位到当前播放歌曲");
        playlistLocateButton.setVisibility(View.GONE);
        playlistLocateButton.setOnClickListener(v -> locateCurrentSongInOpenPlaylist());
        FrameLayout.LayoutParams locateLp = Ui.frame(Ui.dp(this, 38), Ui.dp(this, 38), Gravity.END | Gravity.BOTTOM);
        locateLp.rightMargin = Ui.dp(this, 24);
        locateLp.bottomMargin = Ui.dp(this, 160);
        safeLayer.addView(playlistLocateButton, locateLp);
    }

    private void buildBusy() {
        busyOverlay = new FrameLayout(this);
        busyOverlay.setBackgroundColor(Color.argb(72, 0, 0, 0));
        LinearLayout chip = Ui.column(this);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(Ui.dp(this, 18), Ui.dp(this, 13), Ui.dp(this, 18), Ui.dp(this, 12));
        chip.setBackground(Ui.glass(244, 22, 28, this));
        TextView dots = Ui.text(this, "•••", 20f, Ui.PURPLE, true);
        dots.setGravity(Gravity.CENTER);
        chip.addView(dots, Ui.lp(Ui.dp(this, 84), Ui.dp(this, 28)));
        TextView label = Ui.text(this, "正在连接音乐", 10.8f, Ui.TEXT_2, false);
        label.setGravity(Gravity.CENTER);
        chip.addView(label, Ui.lp(Ui.dp(this, 120), Ui.dp(this, 24)));
        busyOverlay.addView(chip, Ui.frame(Ui.dp(this, 154), Ui.dp(this, 74), Gravity.CENTER));
        busyOverlay.setVisibility(View.GONE);
        safeLayer.addView(busyOverlay, Ui.frame(-1, -1, Gravity.FILL));
    }

    private void showBusy(boolean show) { busyOverlay.setVisibility(show ? View.VISIBLE : View.GONE); }

    private interface SimpleAction { void run(); }
    private interface InputAction { void run(String value); }
    private interface ViewAction { void run(View view); }
    private interface OptionAction { void run(int index); }
    private interface FloatAction { void run(float value); }
    private interface PlaylistAction { void run(ImportedPlaylist playlist); }

    private void buildModalLayer() {
        modalOverlay = new FrameLayout(this);
        modalOverlay.setBackgroundColor(AppearanceSystem.isLight()
                ? Color.argb(44, 42, 50, 66) : Color.argb(175, 0, 0, 0));
        modalOverlay.setClickable(true);
        modalOverlay.setFocusable(true);
        modalOverlay.setVisibility(View.GONE);
        appRoot.addView(modalOverlay, Ui.frame(-1, -1, Gravity.FILL));
        modalOverlay.setPadding(0, systemTopInset, 0, systemBottomInset);
    }

    private void buildSnackbar() {
        snackbar = Ui.text(this, "", 12.5f, Ui.TEXT, true);
        snackbar.setGravity(Gravity.CENTER_VERTICAL);
        snackbar.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        snackbar.setBackground(Ui.functionalGlass(18, this));
        snackbar.setVisibility(View.GONE);
        FrameLayout.LayoutParams sp = Ui.frame(-1, Ui.dp(this, 48), Gravity.TOP);
        Ui.margins(sp, 18, 12, 18, 0, this);
        sp.topMargin = systemTopInset + Ui.dp(this, 12);
        appRoot.addView(snackbar, sp);
    }

    private void buildVoiceLayer() {
        voicePanel = new VoiceAssistantPanel(this);
        voicePanel.setVisibility(View.GONE);
        voicePanel.setAlpha(0f);
        voicePanel.setCloseAction(this::hideVoicePanel);
        FrameLayout.LayoutParams vp = Ui.frame(-1, Ui.dp(this, 120), Gravity.TOP);
        Ui.margins(vp, 14, 0, 14, 0, this);
        vp.topMargin = systemTopInset + Ui.dp(this, 16);
        appRoot.addView(voicePanel, vp);
    }

    private void handleVoiceAssistantState(Intent intent) {
        if (voicePanel == null || intent == null) return;
        String phase = intent.getStringExtra(VoiceAssistantContract.EXTRA_PHASE);
        String heard = intent.getStringExtra(VoiceAssistantContract.EXTRA_TRANSCRIPT);
        String detail = intent.getStringExtra(VoiceAssistantContract.EXTRA_DETAIL);
        float level = intent.getFloatExtra(VoiceAssistantContract.EXTRA_LEVEL, 0f);
        String query = intent.getStringExtra(VoiceAssistantContract.EXTRA_QUERY);
        boolean openSearch = intent.getBooleanExtra(VoiceAssistantContract.EXTRA_OPEN_SEARCH, false);
        if (VoiceAssistantContract.PHASE_ARMED.equals(phase) || VoiceAssistantContract.PHASE_OFF.equals(phase)) {
            if (voicePanelHideRunnable != null) main.removeCallbacks(voicePanelHideRunnable);
            voicePanelHideRunnable = this::hideVoicePanel;
            main.postDelayed(voicePanelHideRunnable, 420L);
            return;
        }
        if (voicePanelHideRunnable != null) main.removeCallbacks(voicePanelHideRunnable);
        voicePanel.render(phase, heard, detail, level);
        voicePanel.setPrimaryAction("", null);
        if (VoiceAssistantContract.PHASE_ERROR.equals(phase)) {
            voicePanel.setPrimaryAction("语音设置", this::showVoiceAssistantSettings);
        }
        showVoicePanel();
        if (VoiceAssistantContract.PHASE_RESULT.equals(phase) && openSearch && query != null && !query.trim().isEmpty()) {
            final String q = query.trim();
            voicePanel.setPrimaryAction("查看搜索", () -> openVoiceSearch(q));
            main.postDelayed(() -> openVoiceSearch(q), 360L);
        }
    }

    private void showVoicePanel() {
        if (voicePanel == null) return;
        voicePanel.refreshAppearance();
        voicePanel.bringToFront();
        final boolean reduced = SpringMotion.isReducedMotion();
        if (voicePanel.getVisibility() != View.VISIBLE) {
            voicePanel.setVisibility(View.VISIBLE);
            voicePanel.setAlpha(0f);
            voicePanel.setTranslationY(reduced ? 0f : -Ui.dp(this, 12));
            voicePanel.setScaleX(reduced ? 1f : .985f);
            voicePanel.setScaleY(reduced ? 1f : .985f);
        }
        // Retarget from the current presentation state; voice updates can arrive rapidly while
        // SpeechRecognizer moves through wake/listening/processing/result phases.
        voicePanel.animate().cancel();
        voicePanel.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(reduced ? SpringMotion.fadeDuration() : SpringMotion.sheetDuration())
                .setInterpolator(reduced ? SpringMotion.TAB_PAGE : SpringMotion.SOFT).start();
    }

    private void hideVoicePanel() {
        if (voicePanel == null || voicePanel.getVisibility() != View.VISIBLE) return;
        final boolean reduced = SpringMotion.isReducedMotion();
        voicePanel.animate().cancel();
        voicePanel.animate().alpha(0f).translationY(reduced ? 0f : -Ui.dp(this, 8))
                .scaleX(reduced ? 1f : .992f).scaleY(reduced ? 1f : .992f)
                .setDuration(SpringMotion.fadeDuration()).setInterpolator(SpringMotion.SNAPPY)
                .withEndAction(() -> {
                    voicePanel.setVisibility(View.GONE);
                    voicePanel.setTranslationY(0f);
                    voicePanel.setScaleX(1f);
                    voicePanel.setScaleY(1f);
                    voicePanel.setAlpha(0f);
                }).start();
    }

    private void handleVoiceLaunchIntent(Intent intent) {
        if (intent == null) return;
        if (intent.getBooleanExtra(VoiceAssistantContract.EXTRA_OPEN_VOICE_SETTINGS, false)) {
            intent.removeExtra(VoiceAssistantContract.EXTRA_OPEN_VOICE_SETTINGS);
            main.postDelayed(this::showVoiceAssistantSettings, 220L);
            return;
        }
        String query = intent.getStringExtra(VoiceAssistantContract.EXTRA_QUERY);
        if (query == null || query.trim().isEmpty()) return;
        final String q = query.trim();
        intent.removeExtra(VoiceAssistantContract.EXTRA_QUERY);
        main.postDelayed(() -> openVoiceSearch(q), 220L);
    }

    private void openVoiceSearch(String query) {
        String clean = query == null ? "" : query.trim();
        if (clean.isEmpty() || isFinishing()) return;
        pendingSearchQuery = clean;
        // Voice search is a new search intent, not a request to resurrect an old Search detail.
        // Preserve the current tab context first, then land on Search root so the existing opaque
        // adjacent-page transition still shows where the command took the user.
        rememberRootTabContext();
        clearRootTabDetailState(1);
        tab = 1;
        openPlaylist = null;
        offlinePlaylistOpen = false;
        openSmartCollection = null;
        searchAllResultsOpen = false;
        refreshNav();
        renderTab();
    }

    private boolean voiceMicGranted() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void startVoiceAssistant(boolean testListen) {
        if (!voiceMicGranted()) {
            pendingVoiceEnable = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_VOICE_AUDIO);
            return;
        }
        VoiceAssistantContract.prefs(this).edit().putBoolean(VoiceAssistantContract.KEY_ENABLED, true).apply();
        Intent service = VoiceAssistantContract.serviceIntent(this,
                testListen ? VoiceAssistantContract.ACTION_TEST_LISTEN : VoiceAssistantContract.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        } catch (Exception e) {
            VoiceAssistantContract.prefs(this).edit().putBoolean(VoiceAssistantContract.KEY_ENABLED, false).apply();
            toast("语音唤醒启动失败 · 请保持 Lunaxy 在前台后重试");
        }
    }

    private void stopVoiceAssistant() {
        VoiceAssistantContract.prefs(this).edit().putBoolean(VoiceAssistantContract.KEY_ENABLED, false).apply();
        try { stopService(new Intent(this, VoiceAssistantService.class)); } catch (Exception ignored) { }
        hideVoicePanel();
        toast("Lunaxy Voice 已关闭");
    }

    private void showVoiceAssistantSettings() {
        hideVoicePanel();
        boolean enabled = VoiceAssistantContract.enabled(this);
        boolean mic = voiceMicGranted();
        boolean overlayAllowed = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        boolean overlayEnabled = VoiceAssistantContract.backgroundOverlayEnabled(this);
        boolean localEngine = false;
        if (Build.VERSION.SDK_INT >= 31) {
            try { localEngine = SpeechRecognizer.isOnDeviceRecognitionAvailable(this); } catch (Throwable ignored) { }
        }
        String mode = VoiceAssistantContract.recognitionMode(this);

        LinearLayout card = modalCard("Lunaxy Voice", "说一句，音乐就动起来");

        LinearLayout hero = Ui.row(this);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        hero.setBackground(Ui.tintedGlass(enabled ? Ui.GREEN : Ui.PURPLE, 20, this));
        VoiceOrbView voiceOrb = new VoiceOrbView(this);
        voiceOrb.setVoiceState(enabled ? VoiceAssistantContract.PHASE_ARMED : VoiceAssistantContract.PHASE_OFF);
        voiceOrb.setAudioLevel(enabled ? .12f : .04f);
        hero.addView(voiceOrb, Ui.lp(Ui.dp(this, 58), Ui.dp(this, 58)));
        LinearLayout heroText = Ui.column(this);
        heroText.setPadding(Ui.dp(this, 12), 0, 0, 0);
        TextView state = Ui.text(this, enabled ? "后台待命中" : "语音唤醒已关闭", 16.2f, Ui.TEXT, true);
        heroText.addView(state, Ui.lp(-1, Ui.dp(this, 29)));
        String engineLine = VoiceAssistantContract.recognitionModeLabel(this)
                + " · " + (localEngine ? "本机支持离线识别" : "未检测到本地识别引擎");
        TextView engine = Ui.text(this, engineLine, 10.8f, Ui.TEXT_2, false);
        engine.setSingleLine(true); engine.setEllipsize(TextUtils.TruncateAt.END);
        heroText.addView(engine, Ui.lp(-1, Ui.dp(this, 25)));
        hero.addView(heroText, new LinearLayout.LayoutParams(0, Ui.dp(this, 58), 1f));
        card.addView(hero, marginTop(12));

        TextView modeTitle = Ui.text(this, "识别方式", 11.2f, Ui.TEXT_2, true);
        card.addView(modeTitle, marginTop(16));
        LinearLayout modes = Ui.row(this);
        TextView auto = voiceModeButton("本地优先", VoiceAssistantContract.MODE_AUTO, mode);
        TextView local = voiceModeButton("仅本地", VoiceAssistantContract.MODE_LOCAL, mode);
        TextView system = voiceModeButton("系统识别", VoiceAssistantContract.MODE_SYSTEM, mode);
        modes.addView(auto, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f); mlp.leftMargin = Ui.dp(this, 7); modes.addView(local, mlp);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f); slp.leftMargin = Ui.dp(this, 7); modes.addView(system, slp);
        card.addView(modes, marginTop(7));
        TextView modeHint = Ui.text(this,
                VoiceAssistantContract.MODE_LOCAL.equals(mode)
                        ? "仅本地不会主动回退到联网识别；中文模型首次可能需要下载。"
                        : VoiceAssistantContract.MODE_SYSTEM.equals(mode)
                            ? "系统识别兼容性最高，但部分系统服务会联网处理语音。"
                            : "默认先用设备本地识别；不可用时才临时回退系统识别。",
                9.9f, Ui.DIM, false);
        modeHint.setLineSpacing(0f, 1.14f);
        card.addView(modeHint, marginTop(7));

        LinearLayout primary = Ui.row(this);
        TextView toggle = modalButton(enabled ? "关闭后台唤醒" : (mic ? "开启后台唤醒" : "授权并开启"), !enabled);
        toggle.setTextSize(13.8f);
        toggle.setOnClickListener(v -> {
            boolean wasEnabled = VoiceAssistantContract.enabled(this);
            dismissModalNow();
            if (wasEnabled) {
                stopVoiceAssistant();
                main.postDelayed(this::showVoiceAssistantSettings, 320L);
            } else if (voiceMicGranted()) {
                startVoiceAssistant(false);
                main.postDelayed(this::showVoiceAssistantSettings, 320L);
            } else startVoiceAssistant(false);
        });
        primary.addView(toggle, new LinearLayout.LayoutParams(0, Ui.dp(this, 56), 1f));
        Space gap = new Space(this); primary.addView(gap, Ui.lp(Ui.dp(this, 8), 1));
        TextView test = modalButton("测试一句", false);
        test.setTextSize(13.8f);
        test.setAlpha(enabled ? 1f : .42f);
        test.setOnClickListener(v -> {
            if (!VoiceAssistantContract.enabled(this)) { toast("请先开启后台唤醒"); return; }
            dismissModalNow();
            startVoiceAssistant(true);
        });
        primary.addView(test, new LinearLayout.LayoutParams(0, Ui.dp(this, 56), .78f));
        card.addView(primary, marginTop(16));

        TextView wake = modalButton("唤醒词  ·  " + VoiceAssistantContract.wakeLabel(this), false);
        wake.setGravity(Gravity.CENTER_VERTICAL); wake.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        wake.setOnClickListener(v -> {
            dismissModalNow();
            String current = VoiceAssistantContract.customWake(this);
            showGlassInput("自定义唤醒词", "例如：小星同学", false, "保存", value -> {
                VoiceAssistantContract.prefs(this).edit().putString(VoiceAssistantContract.KEY_CUSTOM_WAKE, value.trim()).apply();
                toast("唤醒词已加入 · 默认 Lunaxy 唤醒词仍可用");
                if (VoiceAssistantContract.enabled(this)) startVoiceAssistant(false);
            }, view -> {
                if (view instanceof EditText && current != null && !current.isEmpty()) {
                    ((EditText) view).setText(current);
                    ((EditText) view).setSelection(((EditText) view).length());
                }
            });
        });
        card.addView(wake, heightWithTop(54, 9));

        TextView offline = modalButton(localEngine ? "准备中文离线模型" : "本机未检测到离线识别引擎", false);
        offline.setGravity(Gravity.CENTER_VERTICAL); offline.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        offline.setAlpha(localEngine && Build.VERSION.SDK_INT >= 33 ? 1f : .42f);
        offline.setOnClickListener(v -> requestVoiceOfflineModel());
        card.addView(offline, heightWithTop(54, 8));

        TextView overlay = modalButton(overlayAllowed
                ? (overlayEnabled ? "后台液态浮窗  ·  已开启" : "后台液态浮窗  ·  已关闭")
                : "后台液态浮窗  ·  需要系统授权", false);
        overlay.setGravity(Gravity.CENTER_VERTICAL); overlay.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        overlay.setOnClickListener(v -> {
            if (!overlayAllowed && Build.VERSION.SDK_INT >= 23) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
                    toast("授权后，后台唤醒可显示液态语音浮窗");
                } catch (Exception e) { toast("无法打开悬浮窗权限设置"); }
            } else {
                VoiceAssistantContract.prefs(this).edit().putBoolean(VoiceAssistantContract.KEY_OVERLAY, !overlayEnabled).apply();
                dismissModalNow();
                main.postDelayed(this::showVoiceAssistantSettings, 140L);
            }
        });
        card.addView(overlay, heightWithTop(54, 8));

        TextView help = Ui.text(this, "为什么有时需要联网？  ·  隐私与兼容说明", 10.5f, Ui.CYAN, true);
        help.setGravity(Gravity.CENTER_VERTICAL);
        help.setClickable(true); Ui.applyRipple(help, Color.TRANSPARENT);
        help.setOnClickListener(v -> showGlassMessage("语音识别说明",
                "Lunaxy 不保存原始录音。Android 的系统 SpeechRecognizer 由手机里的识别服务实现，部分厂商会把音频交给在线服务，因此“系统识别”可能需要联网。“本地优先”会先尝试设备本地引擎；“仅本地”不会主动回退到系统在线识别。首次使用中文离线识别时，系统可能需要下载语言模型。后台唤醒仍会持续占用麦克风并显示 Android 麦克风指示。",
                "知道了", () -> {}, "", null));
        card.addView(help, heightWithTop(38, 10));

        TextView done = modalButton("完成", true);
        done.setTextSize(14f);
        done.setOnClickListener(v -> hideModal());
        card.addView(done, heightWithTop(56, 12));
        presentModal(card);
    }

    private TextView voiceModeButton(String label, String mode, String currentMode) {
        boolean selected = mode.equals(currentMode);
        TextView button = Ui.text(this, label, 11.4f, selected ? Ui.TEXT : Ui.TEXT_2, true);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true); button.setFocusable(true);
        button.setBackground(selected ? Ui.playerControlSurface(Ui.CYAN, 15, this) : Ui.contentSurface(15, this));
        Ui.applyRipple(button, Color.TRANSPARENT);
        button.setOnClickListener(v -> {
            VoiceAssistantContract.prefs(this).edit().putString(VoiceAssistantContract.KEY_RECOGNITION_MODE, mode).apply();
            if (VoiceAssistantContract.enabled(this)) {
                try {
                    Intent reload = VoiceAssistantContract.serviceIntent(this, VoiceAssistantContract.ACTION_RELOAD_ENGINE);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(reload); else startService(reload);
                } catch (Exception ignored) { }
            }
            dismissModalNow();
            main.postDelayed(this::showVoiceAssistantSettings, 120L);
        });
        return button;
    }

    private LinearLayout.LayoutParams heightWithTop(int heightDp, int topDp) {
        LinearLayout.LayoutParams p = Ui.lp(-1, Ui.dp(this, heightDp));
        p.topMargin = Ui.dp(this, topDp);
        return p;
    }

    private void requestVoiceOfflineModel() {
        if (Build.VERSION.SDK_INT < 31) { toast("当前 Android 版本没有系统本地语音识别接口"); return; }
        boolean available;
        try { available = SpeechRecognizer.isOnDeviceRecognitionAvailable(this); }
        catch (Throwable ignored) { available = false; }
        if (!available) { toast("本机没有可用的系统本地语音识别引擎"); return; }
        if (Build.VERSION.SDK_INT < 33) {
            toast("请到系统语音设置中下载中文离线语言包");
            return;
        }
        SpeechRecognizer local = null;
        try {
            local = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            Intent request = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            local.triggerModelDownload(request);
            toast("已请求系统准备中文离线模型");
            final SpeechRecognizer cleanup = local;
            main.postDelayed(() -> { try { cleanup.destroy(); } catch (Exception ignored) { } }, 8000L);
        } catch (Throwable e) {
            if (local != null) try { local.destroy(); } catch (Exception ignored) { }
            toast("无法请求离线模型 · 请到系统语音设置中下载中文语言包");
        }
    }

    private void installModalOutsideDismiss() {
        if (modalOverlay == null) return;
        modalOverlay.setOnClickListener(v -> {
            if (reorderGestureActive || SystemClock.uptimeMillis() < modalDismissSuppressedUntil) return;
            hideModal();
        });
    }

    /** Transparent grab zone for fractional Bottom Sheets: finger-driven drag + velocity settle. */
    private void installFractionSheetDrag(FrameLayout shell) {
        if (shell == null) return;
        View grab = new View(this);
        grab.setContentDescription("拖动关闭");
        FrameLayout.LayoutParams gp = Ui.frame(-1, Ui.dp(this, 30), Gravity.TOP);
        shell.addView(grab, gp);
        final float[] downY = {0f};
        final VelocityTracker[] velocity = {null};
        grab.setOnTouchListener((v, e) -> {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downY[0] = e.getRawY();
                if (velocity[0] != null) velocity[0].recycle();
                velocity[0] = VelocityTracker.obtain();
                velocity[0].addMovement(e);
                shell.animate().cancel();
                if (shell.getParent() != null) shell.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            if (velocity[0] != null) velocity[0].addMovement(e);
            if (action == MotionEvent.ACTION_MOVE) {
                float dy = Math.max(0f, e.getRawY() - downY[0]);
                float h = Math.max(1f, shell.getHeight());
                float p = Math.min(1f, dy / h);
                shell.setTranslationY(dy * .88f);
                shell.setScaleX(1f - p * .012f);
                shell.setScaleY(1f - p * .012f);
                if (modalOverlay != null) modalOverlay.setAlpha(1f - p * .36f);
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                float dy = Math.max(0f, e.getRawY() - downY[0]);
                float vy = 0f;
                if (velocity[0] != null) {
                    velocity[0].computeCurrentVelocity(1000);
                    vy = velocity[0].getYVelocity();
                    velocity[0].recycle(); velocity[0] = null;
                }
                if (shell.getParent() != null) shell.getParent().requestDisallowInterceptTouchEvent(false);
                boolean dismiss = action == MotionEvent.ACTION_UP
                        && (dy > shell.getHeight() * .20f || vy > Ui.dp(this, 920));
                if (dismiss) {
                    modalDismissSuppressedUntil = SystemClock.uptimeMillis() + 260L;
                    shell.animate().cancel();
                    shell.animate().translationY(Math.max(shell.getHeight() * .46f, dy + Ui.dp(this, 90)))
                            .alpha(.20f).scaleX(.985f).scaleY(.985f).setDuration(SpringMotion.fadeDuration())
                            .withEndAction(this::hideModal).start();
                } else {
                    shell.animate().cancel();
                    shell.animate().translationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
                            .setDuration(SpringMotion.sheetDuration()).setInterpolator(SpringMotion.LAND).start();
                    if (modalOverlay != null) modalOverlay.animate().alpha(1f).setDuration(SpringMotion.fadeDuration()).start();
                }
                return true;
            }
            return true;
        });
    }

    private void hideModal() {
        stopRingtonePreview();
        stopLocalMusicPreview(true);
        downloadCenterOpen = false;
        downloadCenterGeneration++;
        if (modalOverlay == null) return;
        modalOverlay.animate().alpha(0f).setDuration(SpringMotion.fadeDuration()).withEndAction(() -> {
            modalOverlay.removeAllViews();
            modalOverlay.setVisibility(View.GONE);
            modalOverlay.setAlpha(1f);
            restoreSoftInputMode();
            Runnable completion = modalDismissCompletion;
            modalDismissCompletion = null;
            if (completion != null) main.post(completion);
        }).start();
    }

    private void dismissModalNow() {
        stopLocalMusicPreview(true);
        downloadCenterOpen = false;
        downloadCenterGeneration++;
        if (modalOverlay == null) return;
        modalOverlay.animate().cancel();
        modalOverlay.removeAllViews();
        modalOverlay.setAlpha(1f);
        modalOverlay.setVisibility(View.GONE);
        restoreSoftInputMode();
        Runnable completion = modalDismissCompletion;
        modalDismissCompletion = null;
        if (completion != null) main.post(completion);
    }

    private void restoreSoftInputMode() {
        if (!inputModalOpen) return;
        inputModalOpen = false;
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && getWindow().getDecorView().getWindowToken() != null) {
                imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
            }
        } catch (Exception ignored) { }
        getWindow().setSoftInputMode(previousSoftInputMode);
    }

    private LinearLayout modalCard(String title, String subtitle) {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 18));
        card.setBackground(Ui.transientGlass(25, this));
        card.setElevation(Ui.dp(this, 10));
        TextView t = Ui.text(this, title, 18.5f, Ui.TEXT, true);
        card.addView(t, Ui.lp(-1, Ui.dp(this, 34)));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = Ui.text(this, subtitle, 11.7f, Ui.TEXT_2, false);
            sub.setLineSpacing(0f, 1.18f);
            LinearLayout.LayoutParams pp = Ui.lp(-1, -2); pp.topMargin = Ui.dp(this, 4);
            card.addView(sub, pp);
        }
        return card;
    }

    private void presentModal(View card) {
        modalOverlay.removeAllViews();
        modalOverlay.setVisibility(View.VISIBLE);
        modalOverlay.bringToFront();
        if (snackbar != null) snackbar.bringToFront();
        modalOverlay.setAlpha(0f);
        installModalOutsideDismiss();
        card.setOnClickListener(v -> {});

        // V92: even compact bottom sheets share the same direct-manipulation physics as tall sheets.
        FrameLayout shell = new FrameLayout(this);
        shell.setClipChildren(false); shell.setClipToPadding(false);
        shell.addView(card, Ui.frame(-1, -2, Gravity.BOTTOM));
        installFractionSheetDrag(shell);
        FrameLayout.LayoutParams cp = Ui.frame(-1, -2, Gravity.BOTTOM);
        Ui.margins(cp, 14, 0, 14, 16, this);
        modalOverlay.addView(shell, cp);
        shell.setTranslationY(Ui.dp(this, 28));
        shell.setScaleX(.985f); shell.setScaleY(.985f); shell.setAlpha(.94f);
        modalOverlay.animate().alpha(1f).setDuration(SpringMotion.fadeDuration()).start();
        shell.animate().translationY(0f).scaleX(1f).scaleY(1f).alpha(1f)
                .setInterpolator(SpringMotion.SOFT).setDuration(SpringMotion.sheetDuration()).start();
    }

    private void presentFractionModal(View card, float fraction) {
        modalOverlay.removeAllViews();
        modalOverlay.setVisibility(View.VISIBLE);
        modalOverlay.bringToFront();
        modalOverlay.setAlpha(0f);
        installModalOutsideDismiss();
        card.setOnClickListener(v -> { });
        int available = Math.max(Ui.dp(this, 320), getResources().getDisplayMetrics().heightPixels - systemTopInset - systemBottomInset);
        int height = Math.max(Ui.dp(this, 300), Math.round(available * Math.max(.35f, Math.min(.90f, fraction))));

        FrameLayout shell = new FrameLayout(this);
        shell.setClipChildren(false); shell.setClipToPadding(false);
        shell.addView(card, Ui.frame(-1, -1, Gravity.FILL));
        installFractionSheetDrag(shell);

        FrameLayout.LayoutParams cp = Ui.frame(-1, height, Gravity.BOTTOM);
        Ui.margins(cp, 10, 0, 10, 8, this);
        modalOverlay.addView(shell, cp);
        shell.setTranslationY(Ui.dp(this, 30));
        shell.setScaleX(.985f); shell.setScaleY(.985f); shell.setAlpha(.94f);
        modalOverlay.animate().alpha(1f).setDuration(SpringMotion.fadeDuration()).start();
        shell.animate().translationY(0f).scaleX(1f).scaleY(1f).alpha(1f)
                .setInterpolator(SpringMotion.SOFT).setDuration(SpringMotion.sheetDuration()).start();
    }

    private TextView modalButton(String label, boolean primary) {
        TextView b = Ui.text(this, label, 13.1f, primary ? Color.rgb(14, 19, 24) : Ui.TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true); b.setFocusable(true);
        b.setBackground(primary
                ? Ui.primaryFill(Ui.CYAN, 16, this)
                : Ui.glass(86, 16, 24, this));
        Ui.applyRipple(b, Color.TRANSPARENT);
        return b;
    }

    private void showGlassMessage(String title, String message, String primaryLabel, SimpleAction primary,
                                  String secondaryLabel, SimpleAction secondary) {
        LinearLayout card = modalCard(title, "");
        TextView msg = Ui.text(this, message == null ? "" : message, 12.5f, Ui.TEXT_2, false);
        msg.setLineSpacing(0, 1.28f);
        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false); scroll.addView(msg);
        LinearLayout.LayoutParams mp = Ui.lp(-1, message != null && message.length() > 520 ? Ui.dp(this, 360) : -2); mp.topMargin = Ui.dp(this, 10);
        card.addView(scroll, mp);
        LinearLayout actions = Ui.row(this); actions.setGravity(Gravity.END);
        if (secondaryLabel != null && !secondaryLabel.isEmpty()) {
            TextView s = modalButton(secondaryLabel, false);
            s.setOnClickListener(v -> { dismissModalNow(); if (secondary != null) secondary.run(); });
            actions.addView(s, Ui.lp(Ui.dp(this, 106), Ui.dp(this, 46)));
            Space g = new Space(this); actions.addView(g, Ui.lp(Ui.dp(this, 8), 1));
        }
        TextView p = modalButton(primaryLabel == null ? "知道了" : primaryLabel, true);
        p.setOnClickListener(v -> { dismissModalNow(); if (primary != null) primary.run(); });
        actions.addView(p, Ui.lp(Ui.dp(this, 116), Ui.dp(this, 46)));
        LinearLayout.LayoutParams ap = Ui.lp(-1, Ui.dp(this, 46)); ap.topMargin = Ui.dp(this, 16);
        card.addView(actions, ap);
        presentModal(card);
    }

    private void showGlassInput(String title, String hint, boolean multiline, String primaryLabel, InputAction action) {
        showGlassInput(title, hint, multiline, primaryLabel, action, null);
    }

    private void showGlassInput(String title, String hint, boolean multiline, String primaryLabel, InputAction action, ViewAction onInputReady) {
        LinearLayout card = modalCard(title, "输入后会保存在本机，不经过 Lunaxy Music 服务器");
        EditText input = new EditText(this);
        input.setTypeface(Typeface.DEFAULT);
        input.setHint(hint); input.setHintTextColor(Color.rgb(105,105,123)); input.setTextColor(Ui.TEXT);
        input.setTextSize(13.5f); input.setBackground(Ui.glass(176,17,24,this));
        input.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12));
        input.setSingleLine(!multiline); if (multiline) input.setMinLines(3);
        LinearLayout.LayoutParams ip = Ui.lp(-1, multiline ? Ui.dp(this, 104) : Ui.dp(this, 52)); ip.topMargin = Ui.dp(this, 14);
        card.addView(input, ip);
        LinearLayout actions=Ui.row(this); actions.setGravity(Gravity.END);
        TextView cancel=modalButton("取消",false); cancel.setOnClickListener(v->hideModal());
        actions.addView(cancel,Ui.lp(Ui.dp(this,94),Ui.dp(this,46))); Space g=new Space(this);actions.addView(g,Ui.lp(Ui.dp(this,8),1));
        TextView ok=modalButton(primaryLabel,true); ok.setOnClickListener(v->{String value=input.getText().toString().trim(); if(value.isEmpty()){toast("请输入内容");return;} dismissModalNow(); if(action!=null)action.run(value);});
        actions.addView(ok,Ui.lp(Ui.dp(this,112),Ui.dp(this,46)));
        LinearLayout.LayoutParams ap=Ui.lp(-1,Ui.dp(this,46));ap.topMargin=Ui.dp(this,14);card.addView(actions,ap);
        presentInputModal(card); input.requestFocus();
        if (onInputReady != null) input.postDelayed(() -> onInputReady.run(input), 180L);
    }

    private void presentInputModal(View card) {
        previousSoftInputMode = getWindow().getAttributes().softInputMode;
        inputModalOpen = true;
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        modalOverlay.removeAllViews();
        modalOverlay.setVisibility(View.VISIBLE);
        modalOverlay.bringToFront();
        modalOverlay.setAlpha(0f);
        installModalOutsideDismiss();
        card.setOnClickListener(v -> { });
        FrameLayout.LayoutParams cp = Ui.frame(-1, -2, Gravity.CENTER);
        Ui.margins(cp, 18, 0, 18, 0, this);
        modalOverlay.addView(card, cp);
        card.setScaleX(.97f); card.setScaleY(.97f);
        modalOverlay.animate().alpha(1f).setDuration(SpringMotion.fadeDuration()).start();
        card.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
    }

    private void showGlassOptions(String title, String subtitle, String[] items, OptionAction action) {
        LinearLayout card=modalCard(title,subtitle);
        for(int i=0;i<items.length;i++){
            final int idx=i;
            TextView row=Ui.text(this,items[i],13.5f,Ui.TEXT,true); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(this,14),0,Ui.dp(this,14),0); row.setClickable(true); row.setFocusable(true);
            row.setBackground(Ui.glass(94,16,18,this)); Ui.applyRipple(row,Color.TRANSPARENT);
            LinearLayout.LayoutParams rp=Ui.lp(-1,Ui.dp(this,50));rp.topMargin=Ui.dp(this,8);card.addView(row,rp);
            row.setOnClickListener(v->{dismissModalNow();if(action!=null)action.run(idx);});
        }
        presentModal(card);
    }

    /**
     * V67 Route Intelligence Dashboard. Observation-only UI: it reads V66 Learning V4 telemetry
     * through SourceHealthStore peek/getter APIs and never writes routing preference, READY cache,
     * provider cooldown, resolver order or playback state.
     */
    private void showSourceStatus() { showEngineMatrixCenter(0); }

    /** V90 single engine center. The top selector is swipeable; Back always dismisses one level. */
    private void showEngineMatrixCenter(int startPage) {
        engineMatrixPage = Math.max(0, Math.min(2, startPage));
        LinearLayout card = modalCard("引擎矩阵", "播放链 · 个性化 · 搜索 · 左右滑动顶部切换");
        LinearLayout pageHost = Ui.column(this);
        LinearLayout selector = engineMatrixSelector(pageHost);
        LinearLayout.LayoutParams hp = Ui.lp(-1, Ui.dp(this, 58)); hp.topMargin = Ui.dp(this, 8);
        card.addView(selector, hp);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, 0, 1f); pp.topMargin = Ui.dp(this, 6);
        card.addView(pageHost, pp);
        renderEngineMatrixPage(pageHost, false);
        presentFractionModal(card, .90f);
    }

    private LinearLayout engineMatrixSelector(LinearLayout pageHost) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        row.setBackground(Ui.glass(70, 18, 18, this));
        String[] labels = {"播放链", "个性化", "搜索"};
        int[] accents = {Ui.CYAN, Ui.PURPLE, Ui.GREEN};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView cell = Ui.text(this, labels[i], 12.2f, i == engineMatrixPage ? Ui.TEXT : Ui.DIM, i == engineMatrixPage);
            cell.setGravity(Gravity.CENTER); cell.setTag(i);
            cell.setBackground(i == engineMatrixPage ? Ui.tintedGlass(accents[i], 24, this) : new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            cell.setClickable(true); cell.setFocusable(true);
            cell.setOnTouchListener(matrixPagerTouch(row, pageHost, index));
            row.addView(cell, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        row.setOnTouchListener(matrixPagerTouch(row, pageHost, -1));
        return row;
    }

    /** Finger-driven pager: content follows distance, release chooses page from distance + velocity. */
    private View.OnTouchListener matrixPagerTouch(LinearLayout selector, LinearLayout pageHost, int tapIndex) {
        final float[] downX = {0f};
        final float[] downY = {0f};
        final float[] lastDx = {0f};
        final boolean[] dragging = {false};
        final VelocityTracker[] velocity = {null};
        return (v, e) -> {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downX[0] = e.getRawX(); downY[0] = e.getRawY(); lastDx[0] = 0f; dragging[0] = false;
                if (velocity[0] != null) velocity[0].recycle();
                velocity[0] = VelocityTracker.obtain(); velocity[0].addMovement(e);
                pageHost.animate().cancel();
                return true;
            }
            if (velocity[0] != null) velocity[0].addMovement(e);
            if (action == MotionEvent.ACTION_MOVE) {
                float dx = e.getRawX() - downX[0];
                float dy = e.getRawY() - downY[0];
                if (!dragging[0] && Math.abs(dx) > Ui.dp(this, 5) && Math.abs(dx) > Math.abs(dy) * 1.08f) dragging[0] = true;
                if (dragging[0]) {
                    // Edge resistance keeps first/last page feeling physical rather than allowing empty overscroll.
                    boolean outward = (engineMatrixPage == 0 && dx > 0f) || (engineMatrixPage == 2 && dx < 0f);
                    float resisted = outward ? dx * .24f : dx * .58f;
                    lastDx[0] = resisted;
                    float width = Math.max(Ui.dp(this, 220), pageHost.getWidth());
                    float p = Math.min(1f, Math.abs(resisted) / width);
                    pageHost.setTranslationX(resisted);
                    pageHost.setAlpha(1f - p * .26f);
                    pageHost.setScaleX(1f - p * .008f); pageHost.setScaleY(1f - p * .008f);
                    if (selector.getParent() != null) selector.getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                float vx = 0f;
                if (velocity[0] != null) {
                    velocity[0].computeCurrentVelocity(1000); vx = velocity[0].getXVelocity();
                    velocity[0].recycle(); velocity[0] = null;
                }
                if (selector.getParent() != null) selector.getParent().requestDisallowInterceptTouchEvent(false);
                if (dragging[0] && action == MotionEvent.ACTION_UP) {
                    float width = Math.max(Ui.dp(this, 220), pageHost.getWidth());
                    boolean commit = Math.abs(lastDx[0]) > width * .15f || Math.abs(vx) > Ui.dp(this, 620);
                    int direction = lastDx[0] < 0f || vx < -Ui.dp(this, 620) ? 1 : -1;
                    int next = Math.max(0, Math.min(2, engineMatrixPage + direction));
                    if (commit && next != engineMatrixPage) {
                        switchEngineMatrixPage(next, selector, pageHost, direction);
                    } else {
                        pageHost.animate().cancel();
                        pageHost.animate().translationX(0f).alpha(1f).scaleX(1f).scaleY(1f)
                                .setDuration(235L).setInterpolator(SpringMotion.PAGE).start();
                    }
                } else if (!dragging[0] && action == MotionEvent.ACTION_UP && tapIndex >= 0 && tapIndex != engineMatrixPage) {
                    switchEngineMatrixPage(tapIndex, selector, pageHost, tapIndex > engineMatrixPage ? 1 : -1);
                } else {
                    pageHost.animate().translationX(0f).alpha(1f).scaleX(1f).scaleY(1f)
                            .setDuration(200L).setInterpolator(SpringMotion.PAGE).start();
                }
                return true;
            }
            return true;
        };
    }

    private void switchEngineMatrixPage(int next, LinearLayout selector, LinearLayout pageHost, int direction) {
        if (next == engineMatrixPage) return;
        engineMatrixPage = next;
        for (int i = 0; i < selector.getChildCount(); i++) {
            if (!(selector.getChildAt(i) instanceof TextView)) continue;
            TextView cell = (TextView) selector.getChildAt(i);
            boolean on = i == engineMatrixPage;
            int accent = i == 0 ? Ui.CYAN : i == 1 ? Ui.PURPLE : Ui.GREEN;
            cell.setTextColor(on ? Ui.TEXT : Ui.DIM);
            cell.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            cell.setBackground(on ? Ui.tintedGlass(accent, 24, this) : new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }
        pageHost.animate().cancel();
        float out = Math.min(Ui.dp(this, 54), Math.max(Ui.dp(this, 24), pageHost.getWidth() * .12f)) * (direction >= 0 ? -1f : 1f);
        pageHost.animate().alpha(0f).translationX(out).scaleX(.992f).scaleY(.992f).setDuration(105).withEndAction(() -> {
            renderEngineMatrixPage(pageHost, true);
            pageHost.setTranslationX(-out * .82f);
            pageHost.setScaleX(.994f); pageHost.setScaleY(.994f);
            pageHost.animate().alpha(1f).translationX(0f).scaleX(1f).scaleY(1f)
                    .setInterpolator(SpringMotion.PAGE).setDuration(245L).start();
        }).start();
    }

    private void renderEngineMatrixPage(LinearLayout pageHost, boolean switched) {
        pageHost.removeAllViews();
        if (engineMatrixPage == 0) buildSourceMatrixPanel(pageHost);
        else if (engineMatrixPage == 1) buildPersonalizationMatrixPanel(pageHost);
        else buildSearchMatrixPanel(pageHost);
    }

    private void buildSourceMatrixPanel(LinearLayout panel) {
        panel.addView(Ui.text(this, "播放链矩阵", 19f, Ui.TEXT, true), marginTop(4));
        panel.addView(Ui.text(this, "Route Intelligence · 观察当前选路、矩阵与学习，不干预播放", 10.7f, Ui.TEXT_2, false), marginTop(2));
        SourceFlowView routeFlow = new SourceFlowView(this);
        activeSourceFlowView = routeFlow;
        Song routeSong = playback == null ? null : playback.currentSong();
        routeFlow.setRouteLabel(routeSong == null ? "当前线路" : (playback.isUsingFallback() ? "回退 · " : "") + Song.providerLabel(routeSong.source));
        routeFlow.setAccent(Ui.CYAN);
        LinearLayout.LayoutParams rfp = Ui.lp(-1, Ui.dp(this, 58)); rfp.topMargin = Ui.dp(this, 7);
        panel.addView(routeFlow, rfp);
        if (SystemClock.uptimeMillis() - lastVisualRoutePulseAt < 1600L) routeFlow.post(routeFlow::pulse);
        LinearLayout content = Ui.column(this);
        LinearLayout tabs = sourceDashboardTabs(content);
        LinearLayout.LayoutParams tp = Ui.lp(-1, Ui.dp(this, 46)); tp.topMargin = Ui.dp(this, 8); panel.addView(tabs, tp);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        renderSourceDashboard(content);
    }

    private LinearLayout sourceDashboardTabs(LinearLayout content) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        String[] labels = {"概览", "矩阵", "学习", "工具"};
        for (int i = 0; i < labels.length; i++) {
            final int tabIndex = i;
            LinearLayout cell = Ui.column(this);
            cell.setGravity(Gravity.CENTER);
            cell.setTag(i);
            TextView text = Ui.text(this, labels[i], 12.2f, i == sourceDashboardTab ? Ui.TEXT : Ui.DIM, i == sourceDashboardTab);
            text.setGravity(Gravity.CENTER);
            cell.addView(text, new LinearLayout.LayoutParams(-1, 0, 1f));
            View line = new View(this);
            line.setBackgroundColor(i == sourceDashboardTab ? Ui.CYAN : Color.TRANSPARENT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 2));
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            cell.addView(line, lp);
            cell.setClickable(true); cell.setFocusable(true);
            cell.setOnClickListener(v -> {
                if (sourceDashboardTab == tabIndex) return;
                sourceDashboardTab = tabIndex;
                refreshSourceDashboardTabs(row);
                renderSourceDashboard(content);
            });
            Ui.applyRipple(cell, Color.TRANSPARENT);
            row.addView(cell, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        return row;
    }

    private void refreshSourceDashboardTabs(LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View raw = row.getChildAt(i);
            if (!(raw instanceof LinearLayout)) continue;
            LinearLayout cell = (LinearLayout) raw;
            boolean selected = i == sourceDashboardTab;
            if (cell.getChildCount() > 0 && cell.getChildAt(0) instanceof TextView) {
                TextView text = (TextView) cell.getChildAt(0);
                text.setTextColor(selected ? Ui.TEXT : Ui.DIM);
                text.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            }
            if (cell.getChildCount() > 1) cell.getChildAt(1).setBackgroundColor(selected ? Ui.CYAN : Color.TRANSPARENT);
        }
    }

    private void renderSourceDashboard(LinearLayout host) {
        host.removeAllViews();
        if (playback == null) {
            host.addView(compactEmpty("播放器正在连接，稍后再查看路线学习状态"), marginTop(8));
            return;
        }
        SourceHealthStore health = new SourceHealthStore(this);
        if (sourceDashboardTab == 0) renderSourceOverview(host, health);
        else if (sourceDashboardTab == 1) renderSourceMatrix(host, health);
        else if (sourceDashboardTab == 2) renderSourceLearning(host, health);
        else renderSourceToolsTab(host);
    }

    private void renderSourceOverview(LinearLayout host, SourceHealthStore health) {
        String bestProvider = "等待新证据", bestPlatform = "";
        float bestScore = -999f;
        int readyTotal = 0, failTotal = 0, provenPlatforms = 0, coolingCells = 0;
        for (String[] provider : SOURCE_UI_PROVIDERS) {
            readyTotal += health.totalOk(provider[0]);
            failTotal += health.totalFail(provider[0]);
            for (String platform : SOURCE_UI_PLATFORMS) {
                float score = health.peekPlatformLearningScore(provider[0], platform);
                if (score > bestScore) { bestScore = score; bestProvider = provider[1]; bestPlatform = platform; }
                if (health.cooling(provider[0], platform, "320k") || health.cooling(provider[0], platform, "128k")) coolingCells++;
            }
        }
        for (String platform : SOURCE_UI_PLATFORMS) {
            int ok = 0;
            for (String[] provider : SOURCE_UI_PROVIDERS) ok += health.platformOk(provider[0], platform);
            if (ok > 0) provenPlatforms++;
        }

        LinearLayout hero = Ui.column(this);
        hero.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        hero.setBackground(Ui.tintedGlass(bestScore > .35f ? Ui.CYAN : Ui.PURPLE, 20, this));
        TextView state = Ui.text(this, sourceEnvironmentLabel(bestScore, coolingCells), 16.0f, Ui.TEXT, true);
        hero.addView(state, Ui.lp(-1, Ui.dp(this, 28)));
        TextView subtitle = Ui.text(this, "近期结果决定优先级，久远成功/失败会自动衰减回中性", 11.1f, Ui.TEXT_2, false);
        hero.addView(subtitle, Ui.lp(-1, Ui.dp(this, 26)));
        LinearLayout stats = Ui.row(this);
        stats.addView(sourceStat("已验证平台", provenPlatforms + "/4"), new LinearLayout.LayoutParams(0, Ui.dp(this, 54), 1f));
        stats.addView(sourceStat("历史 READY", String.valueOf(readyTotal)), new LinearLayout.LayoutParams(0, Ui.dp(this, 54), 1f));
        stats.addView(sourceStat("冷却格子", String.valueOf(coolingCells)), new LinearLayout.LayoutParams(0, Ui.dp(this, 54), 1f));
        hero.addView(stats, marginTop(6));
        if (bestScore > -998f) {
            TextView best = Ui.text(this, "当前最强近期证据 · " + bestProvider + (bestPlatform.isEmpty() ? "" : " × " + sourcePlatformLabel(bestPlatform))
                    + "  " + sourceScore(bestScore), 11.4f, bestScore >= 0f ? Ui.CYAN : Ui.PINK, true);
            hero.addView(best, marginTop(6));
        }
        host.addView(hero, marginTop(4));

        TextView section = Ui.text(this, "当前 Provider", 13.5f, Ui.TEXT, true);
        host.addView(section, marginTop(18));
        List<String[]> providers = sourceSortedProviders(health, false);
        int shown = 0;
        for (String[] provider : providers) {
            if (sourceProviderLowActivity(health, provider[0])) continue;
            host.addView(sourceProviderCard(health, provider[0], provider[1]), marginTop(8));
            if (++shown >= 4) break;
        }
        int low = 0;
        for (String[] provider : SOURCE_UI_PROVIDERS) if (sourceProviderLowActivity(health, provider[0])) low++;
        if (low > 0) {
            TextView hint = Ui.text(this, "还有 " + low + " 个低活跃 Provider 收纳在「矩阵 → 低活跃」。这只是观察筛选，后台并未静默它们。", 10.7f, Ui.DIM, false);
            hint.setLineSpacing(0f, 1.20f);
            host.addView(hint, marginTop(10));
        }

        TextView history = Ui.text(this, "历史统计只是履历，不直接决定今天的 Matrix 排序 · fail " + failTotal, 10.5f, Ui.DIM, false);
        host.addView(history, marginTop(16));
    }

    private View sourceStat(String label, String value) {
        LinearLayout box = Ui.column(this); box.setGravity(Gravity.CENTER);
        TextView v = Ui.text(this, value, 15.2f, Ui.TEXT, true); v.setGravity(Gravity.CENTER);
        TextView l = Ui.text(this, label, 9.6f, Ui.DIM, false); l.setGravity(Gravity.CENTER);
        box.addView(v, Ui.lp(-1, Ui.dp(this, 28))); box.addView(l, Ui.lp(-1, Ui.dp(this, 20)));
        return box;
    }

    private View sourceProviderCard(SourceHealthStore health, String providerId, String label) {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        card.setBackground(Ui.glass(132, 18, 20, this));
        LinearLayout top = Ui.row(this);
        float best = sourceProviderBestScore(health, providerId);
        TextView name = Ui.text(this, label, 13.5f, Ui.TEXT, true);
        top.addView(name, new LinearLayout.LayoutParams(0, Ui.dp(this, 28), 1f));
        TextView badge = Ui.pill(this, sourceProviderState(health, providerId), sourceProviderLowActivity(health, providerId) ? Ui.PINK : (best > .45f ? Ui.CYAN : Ui.PURPLE));
        top.addView(badge, Ui.lp(-2, Ui.dp(this, 24)));
        card.addView(top, Ui.lp(-1, Ui.dp(this, 30)));
        TextView score = Ui.text(this, "最佳近期分 " + sourceScore(best) + "   ·   历史 READY " + health.totalOk(providerId) + "   ·   fail " + health.totalFail(providerId), 10.8f, Ui.TEXT_2, false);
        card.addView(score, Ui.lp(-1, Ui.dp(this, 24)));
        String route = health.lastRoute(providerId);
        String reason = health.lastReason(providerId);
        String detail = !route.isEmpty() ? "最近成功路线 " + sourceRouteLabel(route) : (reason.isEmpty() ? "等待新的真实播放证据" : "最近状态 " + reason);
        TextView d = Ui.text(this, detail, 10.2f, Ui.DIM, false); d.setSingleLine(true); d.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(d, Ui.lp(-1, Ui.dp(this, 22)));
        return card;
    }

    private void renderSourceMatrix(LinearLayout host, SourceHealthStore health) {
        TextView expl = Ui.text(this, "二维矩阵展示 Provider × Catalog 的近期学习分。默认「当前」会把已有充分负面证据的 Provider 收起；这只是 UI 观察分类，不改变后台选路。", 10.8f, Ui.TEXT_2, false);
        expl.setLineSpacing(0f, 1.20f); host.addView(expl, marginTop(4));

        LinearLayout filters = Ui.row(this);
        String[] f = {"当前", "全部", "低活跃"};
        for (int i = 0; i < f.length; i++) {
            final int idx = i;
            TextView t = Ui.text(this, f[i], 11.5f, i == sourceMatrixFilter ? Ui.TEXT : Ui.DIM, i == sourceMatrixFilter);
            t.setGravity(Gravity.CENTER); t.setClickable(true); t.setFocusable(true);
            t.setBackground(i == sourceMatrixFilter ? Ui.tintedGlass(Ui.CYAN, 13, this) : Ui.glass(36, 13, 10, this));
            t.setOnClickListener(v -> { sourceMatrixFilter = idx; renderSourceDashboard(host); });
            Ui.applyRipple(t, Color.TRANSPARENT);
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, Ui.dp(this, 36), 1f); if (i > 0) fp.leftMargin = Ui.dp(this, 7);
            filters.addView(t, fp);
        }
        host.addView(filters, marginTop(12));

        // V68 compact matrix: the first frame must show Provider + all four Catalog columns on
        // phone width. Keep the information density, but use weighted columns instead of a table
        // wider than the modal (V67 required horizontal scrolling and clipped the WY column).
        LinearLayout table = Ui.column(this);
        LinearLayout header = Ui.row(this);
        TextView empty = Ui.text(this, "Provider", 9.3f, Ui.DIM, true); empty.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(empty, Ui.lp(Ui.dp(this, 54), Ui.dp(this, 36)));
        for (String label : SOURCE_UI_PLATFORM_LABELS) {
            TextView h = Ui.text(this, label, 9.8f, Ui.TEXT_2, true); h.setGravity(Gravity.CENTER);
            header.addView(h, new LinearLayout.LayoutParams(0, Ui.dp(this, 36), 1f));
        }
        table.addView(header, Ui.lp(-1, Ui.dp(this, 36)));

        List<String[]> providers = sourceSortedProviders(health, false);
        int count = 0;
        for (String[] provider : providers) {
            boolean low = sourceProviderLowActivity(health, provider[0]);
            if (sourceMatrixFilter == 0 && low) continue;
            if (sourceMatrixFilter == 2 && !low) continue;
            table.addView(sourceMatrixRow(health, provider[0], provider[1], low), marginTop(6)); count++;
        }
        if (count == 0) {
            TextView none = Ui.text(this, sourceMatrixFilter == 2 ? "暂无低活跃 Provider" : "暂无可展示 Provider", 11.3f, Ui.DIM, false);
            none.setGravity(Gravity.CENTER); table.addView(none, Ui.lp(-1, Ui.dp(this, 70)));
        }
        host.addView(table, marginTop(10));

        TextView legend = Ui.text(this, "近期分：正值=最近更可信 · 接近0=中性/等待新证据 · 负值=近期降权。历史 READY 只在 Cell 详情里作为履历展示。", 10.2f, Ui.DIM, false);
        legend.setLineSpacing(0f, 1.18f); host.addView(legend, marginTop(12));
    }

    private View sourceMatrixRow(SourceHealthStore health, String providerId, String label, boolean low) {
        LinearLayout row = Ui.row(this);
        TextView name = Ui.text(this, label, 9.9f, low ? Ui.DIM : Ui.TEXT_2, true);
        name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(name, Ui.lp(Ui.dp(this, 54), Ui.dp(this, 48)));
        for (int i = 0; i < SOURCE_UI_PLATFORMS.length; i++) {
            String platform = SOURCE_UI_PLATFORMS[i];
            float score = health.peekPlatformLearningScore(providerId, platform);
            boolean cooling = health.cooling(providerId, platform, "320k") || health.cooling(providerId, platform, "128k");
            TextView cell = Ui.text(this, (cooling ? "◷" : "") + sourceScore(score), 9.8f, sourceScoreColor(score), true);
            cell.setGravity(Gravity.CENTER); cell.setClickable(true); cell.setFocusable(true);
            cell.setSingleLine(true);
            int accent = cooling ? Ui.GOLD : (score > .35f ? Ui.CYAN : (score < -.35f ? Ui.PINK : Ui.PURPLE));
            cell.setBackground(Ui.tintedGlass(accent, 12, this));
            String pl = SOURCE_UI_PLATFORM_LABELS[i];
            cell.setOnClickListener(v -> showSourceCellDetail(providerId, label, platform, pl));
            Ui.applyRipple(cell, Color.TRANSPARENT);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f);
            cp.leftMargin = Ui.dp(this, 3);
            row.addView(cell, cp);
        }
        return row;
    }

    private void showSourceCellDetail(String providerId, String providerLabel, String platform, String platformLabel) {
        SourceHealthStore health = new SourceHealthStore(this);
        float p = health.peekPlatformLearningScore(providerId, platform);
        float q320 = health.peekLearningScore(providerId, platform, "320k");
        float q128 = health.peekLearningScore(providerId, platform, "128k");
        int ok320 = health.ok(providerId, platform, "320k"), fail320 = health.fail(providerId, platform, "320k");
        int ok128 = health.ok(providerId, platform, "128k"), fail128 = health.fail(providerId, platform, "128k");
        long lat320 = health.latency(providerId, platform, "320k"), lat128 = health.latency(providerId, platform, "128k");
        long cooldown = Math.max(health.cooldownRemaining(providerId, platform, "320k"), health.cooldownRemaining(providerId, platform, "128k"));
        long lastOk = Math.max(health.lastOkAt(providerId, platform, "320k"), health.lastOkAt(providerId, platform, "128k"));
        long lastFail = Math.max(health.lastFailAt(providerId, platform, "320k"), health.lastFailAt(providerId, platform, "128k"));
        String reason = health.reason(providerId, platform, "320k"); if (reason == null || reason.isEmpty()) reason = health.reason(providerId, platform, "128k");
        StringBuilder b = new StringBuilder();
        b.append("近期综合分  ").append(sourceScore(p)).append("\n\n")
                .append("320k  ·  ").append(sourceScore(q320)).append("  ·  READY ").append(ok320).append(" / fail ").append(fail320);
        if (lat320 > 0) b.append("  ·  ").append(lat320).append("ms");
        b.append("\n128k  ·  ").append(sourceScore(q128)).append("  ·  READY ").append(ok128).append(" / fail ").append(fail128);
        if (lat128 > 0) b.append("  ·  ").append(lat128).append("ms");
        b.append("\n\n最近成功  ").append(sourceRelativeTime(lastOk))
                .append("\n最近失败  ").append(sourceRelativeTime(lastFail))
                .append("\n安全冷却  ").append(cooldown > 0 ? sourceDuration(cooldown) : "无");
        if (reason != null && !reason.isEmpty()) b.append("\n最近原因  ").append(reason);
        b.append("\n\n说明：这只是 Learning V4 的观察快照；实际某一首歌的最终顺序还会结合歌曲平台身份、Exact Track affinity、基础优先级、近期 READY、延迟与 cooldown。");
        showGlassMessage(providerLabel + " × " + platformLabel, b.toString(), "返回矩阵", this::showSourceStatus, "关闭", null);
    }

    private void renderSourceLearning(LinearLayout host, SourceHealthStore health) {
        LinearLayout intro = Ui.column(this);
        intro.setPadding(Ui.dp(this, 15), Ui.dp(this, 13), Ui.dp(this, 15), Ui.dp(this, 13));
        intro.setBackground(Ui.tintedGlass(Ui.PURPLE, 18, this));
        intro.addView(Ui.text(this, "Learning V4 · 会学习，也会遗忘", 14.5f, Ui.TEXT, true), Ui.lp(-1, Ui.dp(this, 28)));
        TextView d = Ui.text(this, "Provider 路线证据约 " + SourceHealthStore.learningHalfLifeDays() + " 天衰减一半；长期没有新结果会逐步回到中性。歌曲平台身份不会因为衰减被删除。", 10.8f, Ui.TEXT_2, false);
        d.setLineSpacing(0f, 1.20f); intro.addView(d, Ui.lp(-1, -2));
        host.addView(intro, marginTop(4));

        host.addView(Ui.text(this, "当前近期路线热度", 13.5f, Ui.TEXT, true), marginTop(18));
        List<SourceUiCell> cells = new ArrayList<>();
        for (String[] provider : SOURCE_UI_PROVIDERS) for (int i = 0; i < SOURCE_UI_PLATFORMS.length; i++) {
            String platform = SOURCE_UI_PLATFORMS[i];
            float score = health.peekPlatformLearningScore(provider[0], platform);
            int samples = health.platformOk(provider[0], platform) + health.platformFail(provider[0], platform);
            if (samples > 0 || Math.abs(score) >= .05f) cells.add(new SourceUiCell(provider[0], provider[1], platform, SOURCE_UI_PLATFORM_LABELS[i], score, samples));
        }
        cells.sort((a,b) -> Float.compare(b.score, a.score));
        if (cells.isEmpty()) host.addView(compactEmpty("还没有真实播放样本，Matrix 正处在中性起点"), marginTop(8));
        else {
            int rank = 1;
            for (SourceUiCell c : cells) {
                LinearLayout row = Ui.row(this); row.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0); row.setBackground(Ui.glass(92, 15, 16, this));
                TextView r = Ui.text(this, String.format(Locale.ROOT, "%02d", rank++), 10.0f, Ui.DIM, true); r.setGravity(Gravity.CENTER);
                row.addView(r, Ui.lp(Ui.dp(this, 34), Ui.dp(this, 48)));
                LinearLayout mid = Ui.column(this);
                mid.addView(Ui.text(this, c.providerLabel + " × " + c.platformLabel, 11.5f, Ui.TEXT_2, true), Ui.lp(-1, Ui.dp(this, 27)));
                mid.addView(Ui.text(this, "样本 " + c.samples + " · 历史履历不等于当前优先级", 9.5f, Ui.DIM, false), Ui.lp(-1, Ui.dp(this, 18)));
                row.addView(mid, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f));
                TextView sc = Ui.text(this, sourceScore(c.score), 12.2f, sourceScoreColor(c.score), true); sc.setGravity(Gravity.CENTER);
                row.addView(sc, Ui.lp(Ui.dp(this, 58), Ui.dp(this, 48)));
                host.addView(row, marginTop(7));
                if (rank > 12) break;
            }
        }

        host.addView(Ui.text(this, "怎么读这个大脑", 13.5f, Ui.TEXT, true), marginTop(18));
        host.addView(sourceLearningRule("近期成功", "真实 Media3 READY 会快速增加路线信任；新的证据优先于很久以前的履历。", Ui.CYAN), marginTop(8));
        host.addView(sourceLearningRule("长期无新结果", "分数持续衰减，曾经很好或很差的路线都会慢慢恢复开放的中性状态。", Ui.PURPLE), marginTop(8));
        host.addView(sourceLearningRule("近期失败", "降低尝试优先级，但不会永久封杀 Provider；所有实际路线仍由当前 Matrix 决策。", Ui.PINK), marginTop(8));
    }

    private View sourceLearningRule(String title, String body, int accent) {
        LinearLayout card = Ui.column(this); card.setPadding(Ui.dp(this, 13), Ui.dp(this, 10), Ui.dp(this, 13), Ui.dp(this, 10)); card.setBackground(Ui.tintedGlass(accent, 15, this));
        card.addView(Ui.text(this, title, 11.5f, Ui.TEXT, true), Ui.lp(-1, Ui.dp(this, 24)));
        TextView b = Ui.text(this, body, 10.1f, Ui.TEXT_2, false); b.setLineSpacing(0f, 1.18f); card.addView(b, Ui.lp(-1, -2));
        return card;
    }

    private void renderSourceToolsTab(LinearLayout host) {
        TextView note = Ui.text(this, "工具只处理网络音源环境，不会修改歌单、收藏、离线音乐或本地导入。", 10.7f, Ui.TEXT_2, false);
        host.addView(note, marginTop(4));
        host.addView(sourceToolRow("更新社区源", "重新获取当前社区 Provider 脚本；Huibq 继续遵循自己的冻结策略。", Ui.CYAN, () -> {
            if (playback == null) { toast("播放器正在连接"); return; }
            playback.resetCommunityProviderScripts(); toast("社区音源已刷新，下次使用时会重新获取");
        }), marginTop(12));
        host.addView(sourceToolRow("重新学习音源", "把路线偏好恢复中性；READY URL、歌曲身份与安全 cooldown 都保留。", Ui.PURPLE, () -> {
            showGlassMessage("重新学习音源", "把播放/下载的路线偏好恢复到中性，再从新的真实成功与失败重新学习。\n\n不会清空 READY URL、歌曲平台身份、歌单、收藏、离线音乐；429 / IP 限制 / Provider 故障等正在生效的安全冷却也会继续保留。",
                    "重新学习", () -> { if (playback == null) { toast("播放器正在连接"); return; } playback.resetRouteLearning(); toast("音源路线已恢复中性，将从新的实播结果重新学习"); }, "取消", this::showSourceStatus);
        }), marginTop(8));
        host.addView(sourceToolRow("复制诊断信息", "复制原始 Route Matrix 状态文本，适合排查难复现的问题。", Ui.GOLD, () -> {
            if (playback == null) { toast("播放器正在连接"); return; }
            copyText(playback.sourceStatus());
        }), marginTop(8));
        TextView safety = Ui.text(this, "V67 Dashboard 不改变 Provider 启停、Matrix 排序、衰减参数、Resolver 预算或播放 fallback；这里只是把当前已有状态搬到明面上。", 10.3f, Ui.DIM, false);
        safety.setLineSpacing(0f, 1.20f); host.addView(safety, marginTop(14));
    }

    private View sourceToolRow(String title, String body, int accent, SimpleAction action) {
        LinearLayout card = Ui.column(this); card.setPadding(Ui.dp(this, 14), Ui.dp(this, 11), Ui.dp(this, 14), Ui.dp(this, 11)); card.setBackground(Ui.tintedGlass(accent, 16, this));
        card.addView(Ui.text(this, title, 12.3f, Ui.TEXT, true), Ui.lp(-1, Ui.dp(this, 26)));
        TextView b = Ui.text(this, body, 10.2f, Ui.TEXT_2, false); b.setLineSpacing(0f, 1.17f); card.addView(b, Ui.lp(-1, -2));
        card.setClickable(true); card.setFocusable(true); card.setOnClickListener(v -> { if (action != null) action.run(); }); Ui.applyRipple(card, Color.TRANSPARENT);
        return card;
    }

    // Keep the legacy entry callable from any old UI reference, but route it into the new Tools tab.
    private void showSourceTools() { sourceDashboardTab = 3; showSourceStatus(); }

    /** V88 front-end window into Personalization Engine V2.2. Read-only: it never edits taste weights. */
    private void showPersonalizationMatrix() { showEngineMatrixCenter(1); }

    private void buildPersonalizationMatrixPanel(LinearLayout panel) {
        activePersonalizationInsights = null;
        if (recommendations == null || personalizationStore == null) { panel.addView(compactEmpty("个性化引擎正在初始化"), marginTop(10)); return; }
        final List<Song> favSnapshot = new ArrayList<>(favorites);
        final List<ImportedPlaylist> playlistSnapshot = new ArrayList<>(playlists);
        final List<Song> historySnapshot = new ArrayList<>(history);
        panel.addView(Ui.text(this, "我的音乐画像", 19f, Ui.TEXT, true), marginTop(4));
        panel.addView(Ui.text(this, "Personalization Matrix · Lunaxy 此刻如何理解你的音乐偏好", 10.7f, Ui.TEXT_2, false), marginTop(2));
        LinearLayout content = Ui.column(this);
        LinearLayout tabs = personalizationDashboardTabs(content);
        LinearLayout.LayoutParams tp = Ui.lp(-1, Ui.dp(this, 46)); tp.topMargin = Ui.dp(this, 8); panel.addView(tabs, tp);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        content.addView(compactEmpty("正在整理 Session / 7天 / 30天 / 180天画像…"), marginTop(8));
        io.submit(() -> {
            RecommendationEngine.PersonalizationInsights insights;
            try { insights = recommendations.insights(favSnapshot, playlistSnapshot, historySnapshot); } catch (Exception e) { insights = null; }
            final RecommendationEngine.PersonalizationInsights ready = insights;
            main.post(() -> {
                if (panel.getParent() == null || isFinishing() || engineMatrixPage != 1) return;
                activePersonalizationInsights = ready; renderPersonalizationDashboard(content);
            });
        });
    }

    private LinearLayout personalizationDashboardTabs(LinearLayout content) {
        LinearLayout row = Ui.row(this); row.setGravity(Gravity.CENTER_VERTICAL);
        String[] labels = {"概览", "画像", "推荐", "数据"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout cell = Ui.column(this); cell.setGravity(Gravity.CENTER); cell.setTag(i);
            boolean selected = i == personalizationDashboardTab;
            TextView text = Ui.text(this, labels[i], 12.2f, selected ? Ui.TEXT : Ui.DIM, selected);
            text.setGravity(Gravity.CENTER); cell.addView(text, new LinearLayout.LayoutParams(-1, 0, 1f));
            View line = new View(this); line.setBackgroundColor(selected ? Ui.CYAN : Color.TRANSPARENT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 2)); lp.gravity = Gravity.CENTER_HORIZONTAL;
            cell.addView(line, lp); cell.setClickable(true); cell.setFocusable(true); Ui.applyRipple(cell, Color.TRANSPARENT);
            cell.setOnClickListener(v -> {
                if (personalizationDashboardTab == index) return;
                personalizationDashboardTab = index;
                for (int j = 0; j < row.getChildCount(); j++) {
                    LinearLayout c = (LinearLayout) row.getChildAt(j); boolean on = j == personalizationDashboardTab;
                    TextView t = (TextView) c.getChildAt(0); t.setTextColor(on ? Ui.TEXT : Ui.DIM); t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                    c.getChildAt(1).setBackgroundColor(on ? Ui.CYAN : Color.TRANSPARENT);
                }
                renderPersonalizationDashboard(content);
            });
            row.addView(cell, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        return row;
    }

    private void renderPersonalizationDashboard(LinearLayout host) {
        host.removeAllViews();
        RecommendationEngine.PersonalizationInsights x = activePersonalizationInsights;
        if (x == null) { host.addView(compactEmpty("正在读取本机个性化数据…"), marginTop(8)); return; }
        if (personalizationDashboardTab == 0) renderPersonalizationOverview(host, x);
        else if (personalizationDashboardTab == 1) renderPersonalizationProfiles(host, x);
        else if (personalizationDashboardTab == 2) renderPersonalizationRecommendations(host, x);
        else renderPersonalizationData(host, x);
    }

    private void renderPersonalizationOverview(LinearLayout host, RecommendationEngine.PersonalizationInsights x) {
        int heroTint = x.maturity >= 60 ? Ui.CYAN : Ui.PURPLE;
        LinearLayout hero = Ui.column(this);
        hero.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        hero.setBackground(Ui.tintedGlass(heroTint, 20, this));
        LinearLayout heroHead = Ui.row(this); heroHead.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heroCopy = Ui.column(this);
        heroCopy.addView(Ui.text(this, x.maturity >= 75 ? "画像已经比较成熟" : x.maturity >= 35 ? "Lunaxy 正在形成你的口味" : "正在认识你的音乐偏好", 16f, Ui.TEXT, true), Ui.lp(-1, Ui.dp(this, 28)));
        heroCopy.addView(Ui.text(this, "已认识 " + x.learnedTracks + " 首歌 · 长短期画像持续更新", 10.7f, Ui.TEXT_2, false), Ui.lp(-1, Ui.dp(this, 22)));
        heroHead.addView(heroCopy, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1f));
        TextView maturityValue = Ui.text(this, x.maturity + "%", 24f, heroTint, true);
        maturityValue.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        heroHead.addView(maturityValue, Ui.lp(Ui.dp(this, 84), Ui.dp(this, 52)));
        hero.addView(heroHead, Ui.lp(-1, Ui.dp(this, 52)));
        hero.addView(personalizationInsightRail(x.maturity, heroTint, false), marginTop(8));
        LinearLayout scale = Ui.row(this);
        TextView early = Ui.text(this, "认识中", 9.4f, Ui.DIM, false); early.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        TextView mature = Ui.text(this, "稳定画像", 9.4f, Ui.DIM, false); mature.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        scale.addView(early, new LinearLayout.LayoutParams(0, Ui.dp(this, 18), 1f));
        scale.addView(mature, new LinearLayout.LayoutParams(0, Ui.dp(this, 18), 1f));
        hero.addView(scale, marginTop(2));
        host.addView(hero, marginTop(6));

        host.addView(Ui.text(this, "本次 Session", 13.5f, Ui.TEXT, true), marginTop(18));
        host.addView(personalizationStatGrid(
                new String[]{"听完", "跳过", "重播", "收藏"},
                new String[]{String.valueOf(x.completes), String.valueOf(x.skips), String.valueOf(x.replays), String.valueOf(x.favoritesAdded)},
                new int[]{Ui.CYAN, Ui.PINK, Ui.PURPLE, Ui.GOLD}), marginTop(8));

        if (!x.artists.isEmpty()) {
            host.addView(Ui.text(this, "当前最强兴趣", 13.5f, Ui.TEXT, true), marginTop(18));
            TextView hint = Ui.text(this, "四段光谱从左到右依次代表 Session / 7天 / 30天 / 180天，越亮表示这一时间层越强。", 9.8f, Ui.DIM, false);
            hint.setLineSpacing(0f, 1.12f); host.addView(hint, marginTop(4));
            for (int i = 0; i < Math.min(4, x.artists.size()); i++) host.addView(personalizationArtistSummary(x.artists.get(i)), marginTop(i == 0 ? 8 : 6));
        }
    }

    private View personalizationStat(String label, String value, int tint) {
        LinearLayout cell = Ui.column(this); cell.setGravity(Gravity.CENTER);
        View accent = new View(this);
        accent.setBackground(Ui.gradient(new int[]{Ui.withAlpha(tint, 72), Ui.mix(tint, Color.WHITE, .22f)}, 4, this));
        LinearLayout.LayoutParams ap = Ui.lp(Ui.dp(this, 28), Ui.dp(this, 3)); ap.gravity = Gravity.CENTER_HORIZONTAL; ap.bottomMargin = Ui.dp(this, 3);
        cell.addView(accent, ap);
        TextView v = Ui.text(this, value, 18f, tint, true); v.setGravity(Gravity.CENTER); cell.addView(v, Ui.lp(-1, Ui.dp(this, 27)));
        TextView l = Ui.text(this, label, 10.4f, Ui.DIM, false);
        l.setGravity(Gravity.CENTER); l.setMaxLines(2); l.setEllipsize(TextUtils.TruncateAt.END);
        cell.addView(l, Ui.lp(-1, Ui.dp(this, 24)));
        return cell;
    }

    private View personalizationStatGrid(String[] labels, String[] values, int[] tints) {
        LinearLayout grid = Ui.column(this);
        int count = Math.min(labels == null ? 0 : labels.length, Math.min(values == null ? 0 : values.length, tints == null ? 0 : tints.length));
        for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
            LinearLayout row = Ui.row(this);
            for (int col = 0; col < 2; col++) {
                int index = rowIndex * 2 + col;
                LinearLayout cellHost = Ui.column(this);
                if (index < count) {
                    cellHost.setPadding(Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5));
                    cellHost.setBackground(Ui.tintedGlass(tints[index], 14, this));
                    cellHost.addView(personalizationStat(labels[index], values[index], tints[index]), Ui.lp(-1, Ui.dp(this, 58)));
                }
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, Ui.dp(this, 68), 1f);
                if (col > 0) cp.leftMargin = Ui.dp(this, 7);
                row.addView(cellHost, cp);
            }
            LinearLayout.LayoutParams rp = Ui.lp(-1, Ui.dp(this, 68));
            if (rowIndex > 0) rp.topMargin = Ui.dp(this, 7);
            grid.addView(row, rp);
        }
        return grid;
    }

    private View personalizationArtistSummary(RecommendationEngine.ArtistInsight a) {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 9), Ui.dp(this, 12), Ui.dp(this, 9));
        card.setBackground(Ui.glass(96, 15, 18, this));
        LinearLayout head = Ui.row(this); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Ui.text(this, a.name, 12.8f, Ui.TEXT, true); name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(name, new LinearLayout.LayoutParams(0, Ui.dp(this, 27), 1f));
        String trend = a.session > 10 ? "Session ↑" : a.short7 > a.medium30 + 10 ? "7天 ↑" : a.medium30 > a.long180 + 10 ? "30天 ↑" : "长期稳定";
        TextView state = Ui.text(this, trend, 10.5f, a.session > 10 ? Ui.CYAN : Ui.TEXT_2, true); state.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        head.addView(state, Ui.lp(Ui.dp(this, 90), Ui.dp(this, 27)));
        card.addView(head, Ui.lp(-1, Ui.dp(this, 27)));
        card.addView(personalizationMiniSpectrum(a), marginTop(5));
        return card;
    }

    private void renderPersonalizationProfiles(LinearLayout host, RecommendationEngine.PersonalizationInsights x) {
        TextView intro = Ui.text(this, "越靠近 Session 越代表“此刻想听什么”；180天更接近稳定长期口味。轨道长度是前台归一化兴趣指数，不暴露内部工程权重。", 10.8f, Ui.TEXT_2, false);
        intro.setLineSpacing(0f, 1.12f); host.addView(intro, marginTop(6));
        if (x.artists.isEmpty()) { host.addView(compactEmpty("继续听歌、收藏或加入歌单后，这里会逐渐形成画像"), marginTop(16)); return; }
        for (int i = 0; i < x.artists.size(); i++) {
            RecommendationEngine.ArtistInsight a = x.artists.get(i);
            LinearLayout card = Ui.column(this); card.setPadding(Ui.dp(this, 13), Ui.dp(this, 11), Ui.dp(this, 13), Ui.dp(this, 12)); card.setBackground(Ui.tintedGlass(Ui.CYAN, 17, this));
            LinearLayout head = Ui.row(this); head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(Ui.text(this, a.name, 13.2f, Ui.TEXT, true), new LinearLayout.LayoutParams(0, Ui.dp(this, 30), 1f));
            TextView overall = Ui.text(this, "综合 " + a.overall, 10.8f, Ui.CYAN, true); overall.setGravity(Gravity.CENTER_VERTICAL | Gravity.END); head.addView(overall, Ui.lp(Ui.dp(this, 82), Ui.dp(this, 30)));
            card.addView(head, Ui.lp(-1, Ui.dp(this, 30)));
            card.addView(personalSignalBar("Session", a.session, Ui.CYAN), marginTop(6));
            card.addView(personalSignalBar("7天", a.short7, Ui.GREEN), marginTop(6));
            card.addView(personalSignalBar("30天", a.medium30, Ui.PURPLE), marginTop(6));
            card.addView(personalSignalBar("180天", a.long180, Ui.GOLD), marginTop(6));
            host.addView(card, marginTop(i == 0 ? 10 : 8));
        }
    }

    private View personalSignalBar(String label, int value, int tint) {
        LinearLayout block = Ui.column(this);
        LinearLayout head = Ui.row(this); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = Ui.text(this, label, 10.4f, Ui.DIM, false); head.addView(l, new LinearLayout.LayoutParams(0, Ui.dp(this, 20), 1f));
        String formatted = (value > 0 ? "+" : "") + value;
        TextView v = Ui.text(this, formatted, 10.3f, value >= 0 ? Ui.TEXT_2 : Ui.PINK, true); v.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        head.addView(v, Ui.lp(Ui.dp(this, 52), Ui.dp(this, 20)));
        block.addView(head, Ui.lp(-1, Ui.dp(this, 20)));
        block.addView(personalizationInsightRail(value, value >= 0 ? tint : Ui.PINK, value < 0), marginTop(2));
        return block;
    }


    /** V89.1 visual language for Personalization Matrix: responsive orbital rail, not a fixed-width progress bar. */
    private View personalizationInsightRail(int rawValue, int tint, boolean negative) {
        int value = Math.max(0, Math.min(100, Math.abs(rawValue)));
        FrameLayout rail = new FrameLayout(this);
        rail.setBackground(Ui.round(Color.argb(28, 255, 255, 255), 6, this));

        View fill = new View(this);
        int soft = Color.argb(92, Color.red(tint), Color.green(tint), Color.blue(tint));
        int bright = Ui.mix(tint, Color.WHITE, .20f);
        fill.setBackground(Ui.gradient(new int[]{soft, bright}, 6, this));
        FrameLayout.LayoutParams fp = Ui.frame(Ui.dp(this, 3), Ui.dp(this, 8), (negative ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
        rail.addView(fill, fp);

        LinearLayout ticks = Ui.row(this);
        for (int i = 0; i < 4; i++) {
            FrameLayout sector = new FrameLayout(this);
            if (i > 0) {
                View tick = new View(this); tick.setBackgroundColor(Color.argb(34, 255, 255, 255));
                sector.addView(tick, Ui.frame(Ui.dp(this, 1), Ui.dp(this, 7), Gravity.START | Gravity.CENTER_VERTICAL));
            }
            ticks.addView(sector, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        rail.addView(ticks, Ui.frame(-1, -1, Gravity.FILL));

        View marker = new View(this);
        marker.setBackground(Ui.stroke(Ui.mix(tint, Color.WHITE, .18f), 6, Color.argb(118, 255, 255, 255), this));
        FrameLayout.LayoutParams mp = Ui.frame(Ui.dp(this, 10), Ui.dp(this, 10), Gravity.START | Gravity.CENTER_VERTICAL);
        rail.addView(marker, mp);
        marker.setAlpha(value <= 0 ? .38f : 1f);

        rail.post(() -> {
            if (rail.getWidth() <= 0) return;
            int width = rail.getWidth();
            int min = Ui.dp(this, 3);
            int target = Math.max(min, Math.round(width * value / 100f));
            FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) fill.getLayoutParams();
            layout.width = min; fill.setLayoutParams(layout);
            float startX = negative ? width - min - Ui.dp(this, 5) : min - Ui.dp(this, 5);
            marker.setTranslationX(Math.max(0f, Math.min(width - Ui.dp(this, 10), startX)));
            ValueAnimator animator = ValueAnimator.ofInt(min, target);
            animator.setDuration(320L);
            animator.setInterpolator(new DecelerateInterpolator(1.6f));
            animator.addUpdateListener(a -> {
                int current = (Integer) a.getAnimatedValue();
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fill.getLayoutParams();
                lp.width = current; fill.setLayoutParams(lp);
                float markerX = negative ? width - current - Ui.dp(this, 5) : current - Ui.dp(this, 5);
                marker.setTranslationX(Math.max(0f, Math.min(width - Ui.dp(this, 10), markerX)));
            });
            animator.start();
        });
        return rail;
    }

    private View personalizationMiniSpectrum(RecommendationEngine.ArtistInsight a) {
        LinearLayout strip = Ui.row(this); strip.setGravity(Gravity.CENTER_VERTICAL);
        int[] values = {a.session, a.short7, a.medium30, a.long180};
        int[] tints = {Ui.CYAN, Ui.GREEN, Ui.PURPLE, Ui.GOLD};
        for (int i = 0; i < values.length; i++) {
            int level = Math.max(0, Math.min(100, Math.abs(values[i])));
            int alpha = 34 + Math.round(level * 1.7f);
            View seg = new View(this);
            seg.setBackground(Ui.gradient(new int[]{Color.argb(Math.min(210, alpha), Color.red(tints[i]), Color.green(tints[i]), Color.blue(tints[i])), Ui.withAlpha(tints[i], Math.min(240, alpha + 26))}, 4, this));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, Ui.dp(this, 6), 1f);
            if (i > 0) sp.leftMargin = Ui.dp(this, 4);
            strip.addView(seg, sp);
        }
        return strip;
    }

    private void renderPersonalizationRecommendations(LinearLayout host, RecommendationEngine.PersonalizationInsights x) {
        host.addView(recommendationInsightCard("每日推荐", "发现优先 · 当天稳定", x.daily, Ui.PURPLE), marginTop(6));
        host.addView(recommendationInsightCard("私人电台", "Session 优先 · 每次动态重算", x.privateRadio, Ui.CYAN), marginTop(10));
        TextView note = Ui.text(this, "“没听过”会同时检查收藏、歌单、最近播放和 Personalization 数据库里的历史歌曲；有足够候选时，日推会优先保留较高陌生歌曲比例，私人电台则更保守。", 10.7f, Ui.TEXT_2, false);
        note.setLineSpacing(0f, 1.12f); host.addView(note, marginTop(14));
    }

    private View recommendationInsightCard(String title, String subtitle, RecommendationEngine.RecommendationSummary s, int tint) {
        LinearLayout card = Ui.column(this); card.setPadding(Ui.dp(this, 15), Ui.dp(this, 13), Ui.dp(this, 15), Ui.dp(this, 13)); card.setBackground(Ui.tintedGlass(tint, 17, this));
        card.addView(Ui.text(this, title, 14.2f, Ui.TEXT, true), Ui.lp(-1, Ui.dp(this, 26)));
        card.addView(Ui.text(this, subtitle, 10.5f, Ui.TEXT_2, false), Ui.lp(-1, Ui.dp(this, 22)));
        if (s == null || s.total <= 0) {
            card.addView(Ui.text(this, "还没有本轮可分析的推荐快照", 11f, Ui.DIM, false), marginTop(8)); return card;
        }
        LinearLayout balance = Ui.column(this);
        LinearLayout balanceHead = Ui.row(this);
        TextView familiar = Ui.text(this, "熟悉 " + Math.max(0, 100 - s.unheardPercent()) + "%", 10.0f, Ui.DIM, false);
        TextView discover = Ui.text(this, "新发现 " + s.unheardPercent() + "%", 10.0f, tint, true); discover.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        balanceHead.addView(familiar, new LinearLayout.LayoutParams(0, Ui.dp(this, 20), 1f));
        balanceHead.addView(discover, new LinearLayout.LayoutParams(0, Ui.dp(this, 20), 1f));
        balance.addView(balanceHead, Ui.lp(-1, Ui.dp(this, 20)));
        balance.addView(personalizationInsightRail(s.unheardPercent(), tint, false), marginTop(2));
        card.addView(balance, marginTop(10));
        card.addView(personalizationStatGrid(
                new String[]{"没听过", "ListenBrainz", "熟悉延伸", "再发现"},
                new String[]{s.unheardPercent() + "%", String.valueOf(s.listenBrainz), String.valueOf(s.familiar), String.valueOf(s.rediscovery)},
                new int[]{tint, Ui.CYAN, Ui.GREEN, Ui.GOLD}), marginTop(10));
        return card;
    }

    private void renderPersonalizationData(LinearLayout host, RecommendationEngine.PersonalizationInsights x) {
        host.addView(personalizationDataRow("已学习歌曲", x.learnedTracks + " 首", "长期 Track Stats"), marginTop(6));
        host.addView(personalizationDataRow("近期原始事件", x.recentEvents + " 条", "有界保留，用于 Session / 最近跳过"), marginTop(7));
        host.addView(personalizationDataRow("长期趋势", "7 / 30 / 180 天", "日聚合保存，不会被 progress 高频事件挤掉"), marginTop(7));
        host.addView(personalizationDataRow("外部发现", "ListenBrainz", "匿名只读协同发现；不上传 Lunaxy 听歌历史"), marginTop(7));
        host.addView(personalizationDataRow("推荐首屏", "本地优先", "上次快照 / 本地画像先显示，ListenBrainz 与目录扩展在后台补全"), marginTop(7));
        host.addView(personalizationDataRow("播放链", "完全隔离", "个性化数据不保存音频 URL / token / READY 路线"), marginTop(7));
    }

    private View personalizationDataRow(String title, String value, String subtitle) {
        LinearLayout row = Ui.column(this); row.setPadding(Ui.dp(this, 13), Ui.dp(this, 10), Ui.dp(this, 13), Ui.dp(this, 10)); row.setBackground(Ui.glass(90, 15, 18, this));
        LinearLayout top = Ui.row(this); top.addView(Ui.text(this, title, 12.4f, Ui.TEXT, true), new LinearLayout.LayoutParams(0, Ui.dp(this, 25), 1f));
        TextView v = Ui.text(this, value, 10.9f, Ui.CYAN, true); v.setGravity(Gravity.END | Gravity.CENTER_VERTICAL); top.addView(v, Ui.lp(Ui.dp(this, 132), Ui.dp(this, 25))); row.addView(top);
        TextView sub = Ui.text(this, subtitle, 10.2f, Ui.DIM, false);
        sub.setLineSpacing(0f, 1.12f); row.addView(sub, Ui.lp(-1, -2)); return row;
    }

    private List<String[]> sourceSortedProviders(SourceHealthStore health, boolean lowFirst) {
        List<String[]> list = new ArrayList<>(); for (String[] p : SOURCE_UI_PROVIDERS) list.add(p);
        list.sort((a,b) -> {
            boolean al = sourceProviderLowActivity(health, a[0]), bl = sourceProviderLowActivity(health, b[0]);
            if (al != bl) return lowFirst ? (al ? -1 : 1) : (al ? 1 : -1);
            return Float.compare(sourceProviderBestScore(health, b[0]), sourceProviderBestScore(health, a[0]));
        });
        return list;
    }

    private float sourceProviderBestScore(SourceHealthStore health, String providerId) {
        float best = -999f; boolean observed = false;
        for (String platform : SOURCE_UI_PLATFORMS) {
            int samples = health.platformOk(providerId, platform) + health.platformFail(providerId, platform);
            float score = health.peekPlatformLearningScore(providerId, platform);
            if (samples > 0 || Math.abs(score) >= .05f) { best = Math.max(best, score); observed = true; }
        }
        return observed ? best : 0f;
    }

    /** Observation-only classification. It does not silence or remove any provider in V66 routing. */
    private boolean sourceProviderLowActivity(SourceHealthStore health, String providerId) {
        int samples = 0; float bestObserved = -999f; boolean observed = false;
        for (String platform : SOURCE_UI_PLATFORMS) {
            int cellSamples = health.platformOk(providerId, platform) + health.platformFail(providerId, platform);
            samples += cellSamples;
            if (cellSamples > 0) {
                bestObserved = Math.max(bestObserved, health.peekPlatformLearningScore(providerId, platform));
                observed = true;
            }
        }
        // Fresh/neutral providers stay visible. Only sufficient and consistently negative OBSERVED evidence moves a row out of Current.
        return observed && samples >= 8 && bestObserved < -0.50f;
    }

    private String sourceProviderState(SourceHealthStore health, String providerId) {
        if (sourceProviderLowActivity(health, providerId)) return "低活跃";
        float best = sourceProviderBestScore(health, providerId);
        if (best >= 2.0f) return "强";
        if (best >= .50f) return "活跃";
        return "中性";
    }

    private static int sourceScoreColor(float score) {
        if (score >= .50f) return Ui.CYAN;
        if (score <= -.50f) return Ui.PINK;
        return Ui.TEXT_2;
    }

    private static String sourceScore(float score) {
        if (Math.abs(score) < .05f) return "0.0";
        return String.format(Locale.ROOT, "%+.1f", score);
    }

    private static String sourcePlatformLabel(String platform) {
        for (int i = 0; i < SOURCE_UI_PLATFORMS.length; i++) if (SOURCE_UI_PLATFORMS[i].equals(platform)) return SOURCE_UI_PLATFORM_LABELS[i];
        return platform == null ? "" : platform;
    }

    private static String sourceRouteLabel(String route) {
        if (route == null || route.isEmpty()) return "—";
        String[] parts = route.split("/", 2);
        return sourcePlatformLabel(parts[0]) + (parts.length > 1 ? " / " + parts[1] : "");
    }

    private static String sourceEnvironmentLabel(float bestScore, int coolingCells) {
        if (coolingCells >= 5) return "当前音源环境正在保护冷却";
        if (bestScore >= 2.0f) return "当前音源环境良好";
        if (bestScore >= .25f) return "当前音源环境正在学习";
        return "当前路线接近中性";
    }

    private static String sourceRelativeTime(long at) {
        if (at <= 0L) return "暂无";
        long d = Math.max(0L, System.currentTimeMillis() - at);
        if (d < 60_000L) return "刚刚";
        if (d < 3_600_000L) return (d / 60_000L) + "分钟前";
        if (d < 86_400_000L) return (d / 3_600_000L) + "小时前";
        return (d / 86_400_000L) + "天前";
    }

    private static String sourceDuration(long ms) {
        long sec = Math.max(1L, ms / 1000L);
        if (sec < 60) return sec + "秒";
        long min = sec / 60L; if (min < 60) return min + "分钟";
        return (min / 60L) + "小时";
    }

    private static final class SourceUiCell {
        final String providerId, providerLabel, platform, platformLabel; final float score; final int samples;
        SourceUiCell(String providerId, String providerLabel, String platform, String platformLabel, float score, int samples) {
            this.providerId = providerId; this.providerLabel = providerLabel; this.platform = platform; this.platformLabel = platformLabel; this.score = score; this.samples = samples;
        }
    }

    private void copyText(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Lunaxy diagnostics", text == null ? "" : text));
            toast("已复制诊断信息");
        } catch (Exception e) { toast("复制失败"); }
    }

    private void reloadLibrary() {
        favorites = store.favorites();
        history = store.history();
        searchHistory = store.searchHistory();
        playlists = store.playlists();
    }

    private void startRootTabPhysics(View outgoing, View incoming, int direction, float width) {
        if (outgoing == null || incoming == null || pageHost == null) return;
        final int token = ++rootTabPhysicsToken;
        final float safeWidth = Math.max(1f, width);
        final float density = Math.max(.75f, getResources().getDisplayMetrics().density);
        final float start = outgoing.getTranslationX() + direction * safeWidth;
        final float[] position = {start};
        float carriedVelocity = rootTabVelocityPxPerSec;
        if (Math.abs(carriedVelocity) < 80f * density) carriedVelocity = -direction * 920f * density;
        final float[] velocity = {Math.max(-6200f * density, Math.min(6200f * density, carriedVelocity))};
        final long[] lastFrame = {0L};

        outgoing.animate().cancel();
        incoming.animate().cancel();
        outgoing.setAlpha(1f); outgoing.setScaleX(1f); outgoing.setScaleY(1f); outgoing.setTranslationY(0f);
        incoming.setAlpha(1f); incoming.setScaleX(1f); incoming.setScaleY(1f); incoming.setTranslationY(0f);
        incoming.setTranslationX(start);
        outgoing.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        incoming.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        pageHost.addView(incoming);
        incoming.bringToFront();

        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override public void doFrame(long frameTimeNanos) {
                if (token != rootTabPhysicsToken || incoming.getParent() != pageHost) return;
                if (lastFrame[0] == 0L) lastFrame[0] = frameTimeNanos - 16_000_000L;
                float dt = Math.max(.006f, Math.min(.032f, (frameTimeNanos - lastFrame[0]) / 1_000_000_000f));
                lastFrame[0] = frameTimeNanos;

                // Near-critical page spring: enough carried momentum to read as physical travel,
                // but damped enough that a full-screen surface never visibly bounces at rest.
                final float omega = 12.6f;
                final float dampingRatio = .96f;
                float a = -omega * omega * position[0] - 2f * dampingRatio * omega * velocity[0];
                velocity[0] += a * dt;
                float maxVelocity = 6200f * density;
                velocity[0] = Math.max(-maxVelocity, Math.min(maxVelocity, velocity[0]));
                position[0] += velocity[0] * dt;

                // Keep the two surfaces locked exactly one viewport apart.
                incoming.setTranslationX(position[0]);
                outgoing.setTranslationX(position[0] - direction * safeWidth);
                rootTabVelocityPxPerSec = velocity[0];

                if (Math.abs(position[0]) <= .32f * density && Math.abs(velocity[0]) <= 9f * density) {
                    incoming.setTranslationX(0f);
                    outgoing.setTranslationX(-direction * safeWidth);
                    rootTabVelocityPxPerSec = 0f;
                    if (outgoing.getParent() == pageHost) pageHost.removeView(outgoing);
                    outgoing.setLayerType(View.LAYER_TYPE_NONE, null);
                    incoming.setLayerType(View.LAYER_TYPE_NONE, null);
                    return;
                }
                Choreographer.getInstance().postFrameCallback(this);
            }
        });
    }

    /**
     * Opaque same-tab depth transition.  The previous implementation cross-faded two complete
     * pages, which made text/cards flash as double images.  Push/pop now keeps both surfaces fully
     * opaque and preserves a reversible left/right spatial relationship.
     */
    private void startDepthPageTransition(View outgoing, View incoming, boolean push, float width) {
        if (outgoing == null || incoming == null || pageHost == null) return;
        final float safeWidth = Math.max(1f, width);
        outgoing.animate().cancel();
        incoming.animate().cancel();
        outgoing.setAlpha(1f); outgoing.setScaleX(1f); outgoing.setScaleY(1f); outgoing.setTranslationY(0f);
        incoming.setAlpha(1f); incoming.setScaleX(1f); incoming.setScaleY(1f); incoming.setTranslationY(0f);
        outgoing.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        incoming.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        if (push) {
            incoming.setTranslationX(safeWidth);
            pageHost.addView(incoming);
            incoming.bringToFront();
            outgoing.animate().translationX(-safeWidth)
                    .setDuration(330L).setInterpolator(SpringMotion.TAB_PAGE).start();
            incoming.animate().translationX(0f)
                    .setDuration(330L).setInterpolator(SpringMotion.TAB_PAGE)
                    .withEndAction(() -> {
                        if (outgoing.getParent() == pageHost) pageHost.removeView(outgoing);
                        incoming.setLayerType(View.LAYER_TYPE_NONE, null);
                    }).start();
        } else {
            incoming.setTranslationX(-safeWidth);
            pageHost.addView(incoming, 0);
            incoming.animate().translationX(0f)
                    .setDuration(330L).setInterpolator(SpringMotion.TAB_PAGE).start();
            outgoing.bringToFront();
            outgoing.animate().translationX(safeWidth)
                    .setDuration(330L).setInterpolator(SpringMotion.TAB_PAGE)
                    .withEndAction(() -> {
                        if (outgoing.getParent() == pageHost) pageHost.removeView(outgoing);
                        incoming.setLayerType(View.LAYER_TYPE_NONE, null);
                    }).start();
        }
    }


    private static float playlistStage(float value, float start, float end) {
        if (end <= start) return value >= end ? 1f : 0f;
        float t = Math.max(0f, Math.min(1f, (value - start) / (end - start)));
        return t * t * (3f - 2f * t);
    }

    /**
     * Shared-object entrance starts from a real list card, so all destination chrome must stay
     * quiet until the card is visibly travelling.  This prevents the common "tap -> detail page
     * flashes in -> hero overlay catches up" failure and makes the motion itself the response.
     */
    private void preparePlaylistDetailForSharedEntrance() {
        if (activePlaylistHeroCard != null) activePlaylistHeroCard.setAlpha(0f);
        if (activePlaylistDetailTitleRow != null) {
            activePlaylistDetailTitleRow.setAlpha(0f);
            activePlaylistDetailTitleRow.setTranslationY(-Ui.dp(this, 12));
        }
        if (activePlaylistDetailSubtitle != null) {
            activePlaylistDetailSubtitle.setAlpha(0f);
            activePlaylistDetailSubtitle.setTranslationY(-Ui.dp(this, 8));
        }
        if (activePlaylistDetailBack != null) {
            activePlaylistDetailBack.setAlpha(0f);
            activePlaylistDetailBack.setTranslationX(-Ui.dp(this, 12));
        }
        if (activePlaylistDetailSongHeader != null) {
            activePlaylistDetailSongHeader.setAlpha(0f);
            activePlaylistDetailSongHeader.setTranslationY(Ui.dp(this, 18));
        }
        if (activePlaylistDetailSongHost != null) {
            activePlaylistDetailSongHost.setAlpha(1f);
            for (int i = 0; i < activePlaylistDetailSongHost.getChildCount(); i++) {
                View child = activePlaylistDetailSongHost.getChildAt(i);
                child.setAlpha(0f);
                child.setTranslationY(Ui.dp(this, 24 + Math.min(14, i * 2)));
            }
        }
        if (activePlaylistLoader != null && activePlaylistLoader.getParent() instanceof View) {
            View loader = (View) activePlaylistLoader.getParent();
            loader.setAlpha(0f);
            loader.setTranslationY(Ui.dp(this, 18));
        }
    }

    private void resetPlaylistDetailTransitionViews() {
        View[] views = new View[]{activePlaylistDetailTitleRow, activePlaylistDetailSubtitle,
                activePlaylistDetailBack, activePlaylistDetailSongHeader};
        for (View view : views) {
            if (view == null) continue;
            view.setAlpha(1f);
            view.setTranslationX(0f);
            view.setTranslationY(0f);
        }
        if (activePlaylistDetailSongHost != null) {
            activePlaylistDetailSongHost.setAlpha(1f);
            activePlaylistDetailSongHost.setTranslationX(0f);
            activePlaylistDetailSongHost.setTranslationY(0f);
            for (int i = 0; i < activePlaylistDetailSongHost.getChildCount(); i++) {
                View child = activePlaylistDetailSongHost.getChildAt(i);
                child.setAlpha(1f);
                child.setTranslationX(0f);
                child.setTranslationY(0f);
            }
        }
        if (activePlaylistLoader != null && activePlaylistLoader.getParent() instanceof View) {
            View loader = (View) activePlaylistLoader.getParent();
            loader.setAlpha(1f);
            loader.setTranslationX(0f);
            loader.setTranslationY(0f);
        }
    }

    /**
     * One choreography value owns the entire playlist push/pop.  The card moves first, then page
     * chrome and song skeletons follow in overlapping waves.  Reverse navigation uses the exact
     * same progress backwards, so there is no separate "exit animation" to drift out of sync.
     */
    private void applyPlaylistDetailChoreography(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        float titleP = playlistStage(p, .28f, .58f);
        float subP = playlistStage(p, .34f, .64f);
        float backP = playlistStage(p, .38f, .68f);
        float headerP = playlistStage(p, .52f, .78f);

        if (activePlaylistDetailTitleRow != null) {
            activePlaylistDetailTitleRow.setAlpha(titleP);
            activePlaylistDetailTitleRow.setTranslationY(-Ui.dp(this, 12) * (1f - titleP));
        }
        if (activePlaylistDetailSubtitle != null) {
            activePlaylistDetailSubtitle.setAlpha(subP);
            activePlaylistDetailSubtitle.setTranslationY(-Ui.dp(this, 8) * (1f - subP));
        }
        if (activePlaylistDetailBack != null) {
            activePlaylistDetailBack.setAlpha(backP);
            activePlaylistDetailBack.setTranslationX(-Ui.dp(this, 12) * (1f - backP));
        }
        if (activePlaylistDetailSongHeader != null) {
            activePlaylistDetailSongHeader.setAlpha(headerP);
            activePlaylistDetailSongHeader.setTranslationY(Ui.dp(this, 18) * (1f - headerP));
        }
        if (activePlaylistDetailSongHost != null) {
            for (int i = 0; i < activePlaylistDetailSongHost.getChildCount(); i++) {
                View child = activePlaylistDetailSongHost.getChildAt(i);
                float start = .58f + Math.min(.18f, i * .035f);
                float rowP = playlistStage(p, start, Math.min(.98f, start + .22f));
                child.setAlpha(rowP);
                child.setTranslationY(Ui.dp(this, 26 + Math.min(12, i * 2)) * (1f - rowP));
            }
        }
        if (activePlaylistLoader != null && activePlaylistLoader.getParent() instanceof View) {
            float loaderP = playlistStage(p, .72f, .94f);
            View loader = (View) activePlaylistLoader.getParent();
            loader.setAlpha(loaderP);
            loader.setTranslationY(Ui.dp(this, 18) * (1f - loaderP));
        }
    }

    private void capturePlaylistDetailHeroGeometry(PlaylistHeroSnapshot snapshot) {
        if (snapshot == null || activePlaylistHeroCard == null || activePlaylistHeroThumb == null
                || activePlaylistHeroTitle == null || activePlaylistHeroMeta == null) return;
        RectF card = rectInAppRoot(activePlaylistHeroCard);
        RectF art = rectInAppRoot(activePlaylistHeroThumb);
        RectF title = rectInAppRoot(activePlaylistHeroTitle);
        RectF meta = rectInAppRoot(activePlaylistHeroMeta);
        if (card.width() < 2f || art.width() < 2f || title.width() < 2f) return;
        snapshot.detailContainer.set(card);
        snapshot.detailArtwork.set(art);
        snapshot.detailTitle.set(title);
        snapshot.detailMeta.set(meta);
        snapshot.detailTitleSizePx = activePlaylistHeroTitle.getTextSize();
        snapshot.detailMetaSizePx = activePlaylistHeroMeta.getTextSize();
        snapshot.hasDetailGeometry = true;
    }

    private Bitmap playlistSharedArtwork(PlaylistHeroSnapshot snapshot, View fallbackArtwork) {
        if (snapshot == null) return null;
        Bitmap cached = ImageLoader.peek(snapshot.artworkUrl);
        if (cached != null && !cached.isRecycled()) return cached;
        // If the visible thumbnail is backed by ImageLoader's original bitmap, reuse that bitmap
        // directly. Never rasterize the 52dp thumbnail and enlarge it into the detail hero: that was
        // the same low-resolution ownership bug that once made Mini Player -> Player briefly blur.
        if (fallbackArtwork instanceof ImageView) {
            Drawable drawable = ((ImageView) fallbackArtwork).getDrawable();
            if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                Bitmap b = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
                if (b != null && !b.isRecycled()) return b;
            }
        }
        return null;
    }

    private RectF playlistReturnRect(View actual, RectF fallback) {
        RectF measured = rectInAppRoot(actual);
        return measured.width() >= 2f && measured.height() >= 2f ? measured : new RectF(fallback);
    }

    private void startPlaylistSharedTransition(View outgoing, View incoming, boolean push) {
        PlaylistHeroSnapshot snapshot = playlistHeroSnapshot;
        if (snapshot == null || outgoing == null || incoming == null || pageHost == null || appRoot == null) {
            startDepthPageTransition(outgoing, incoming, push, Math.max(1f, pageHost == null ? 1f : pageHost.getWidth()));
            pendingPlaylistHeroPush = false;
            pendingPlaylistHeroPop = false;
            return;
        }
        if (activePlaylistSharedAnimator != null) activePlaylistSharedAnimator.cancel();
        if (activePlaylistHeroMorph != null && activePlaylistHeroMorph.getParent() instanceof ViewGroup)
            ((ViewGroup) activePlaylistHeroMorph.getParent()).removeView(activePlaylistHeroMorph);

        rootTabPhysicsToken++;
        rootTabVelocityPxPerSec = 0f;
        outgoing.animate().cancel();
        incoming.animate().cancel();
        outgoing.setAlpha(1f); incoming.setAlpha(1f);
        outgoing.setTranslationX(0f); incoming.setTranslationX(0f);
        outgoing.setTranslationY(0f); incoming.setTranslationY(0f);
        outgoing.setScaleX(1f); outgoing.setScaleY(1f);
        incoming.setScaleX(1f); incoming.setScaleY(1f);
        outgoing.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        incoming.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        activePlaylistTransitionOutgoing = outgoing;
        activePlaylistTransitionIncoming = incoming;
        activePlaylistTransitionPush = push;
        pendingPlaylistHeroPush = false;
        pendingPlaylistHeroPop = false;

        if (push) {
            pageHost.addView(incoming);
            incoming.bringToFront();
            incoming.setClipBounds(null);
            // The destination shell is already present, but its chrome is staged.  The source card
            // therefore remains the only strong object in motion during the first half of travel.
            if (!SpringMotion.isReducedMotion()) preparePlaylistDetailForSharedEntrance();
        } else {
            // Root lives below the outgoing detail.  The reverse path reuses the same shared-object
            // progress rather than revealing a second full page from an edge.
            pageHost.addView(incoming, 0);
            incoming.setAlpha(.12f);
            outgoing.bringToFront();
        }

        final int[] geometryAttempt = new int[]{0};
        final Runnable[] prepareRef = new Runnable[1];
        prepareRef[0] = () -> {
            if (activePlaylistTransitionOutgoing != outgoing || activePlaylistTransitionIncoming != incoming) return;
            final RectF listCard;
            final RectF listArtwork;
            final RectF listTitle;
            final RectF listMeta;
            final float listTitleSize;
            final float listMetaSize;
            final View listRow;
            final View listThumb;

            if (push) {
                capturePlaylistDetailHeroGeometry(snapshot);
                listCard = new RectF(snapshot.sourceContainer);
                listArtwork = new RectF(snapshot.sourceArtwork);
                listTitle = new RectF(snapshot.sourceTitle);
                listMeta = new RectF(snapshot.sourceMeta);
                listTitleSize = snapshot.sourceTitleSizePx;
                listMetaSize = snapshot.sourceMetaSizePx;
                listRow = playlistHeroSourceRow;
                listThumb = playlistHeroSourceThumb;
            } else {
                listCard = playlistReturnRect(playlistHeroReturnRow, snapshot.sourceContainer);
                listArtwork = playlistReturnRect(playlistHeroReturnThumb, snapshot.sourceArtwork);
                listTitle = playlistReturnRect(playlistHeroReturnTitle, snapshot.sourceTitle);
                listMeta = playlistReturnRect(playlistHeroReturnMeta, snapshot.sourceMeta);
                listTitleSize = playlistHeroReturnTitle == null ? snapshot.sourceTitleSizePx : playlistHeroReturnTitle.getTextSize();
                listMetaSize = playlistHeroReturnMeta == null ? snapshot.sourceMetaSizePx : playlistHeroReturnMeta.getTextSize();
                listRow = playlistHeroReturnRow;
                listThumb = playlistHeroReturnThumb;
            }

            if (!snapshot.hasDetailGeometry || listCard.width() < 2f || listArtwork.width() < 2f) {
                // A newly-built ScrollView can need more than one display turn before the hero has
                // stable app-root coordinates. Keep the real source card on screen while waiting;
                // only fall back after several pre-draw opportunities instead of silently skipping
                // the shared-element animation on a fast tap/device.
                if (geometryAttempt[0]++ < 5 && incoming.isAttachedToWindow()) {
                    incoming.postOnAnimation(prepareRef[0]);
                } else {
                    abandonPlaylistSharedTransition(push);
                }
                return;
            }

            PlaylistHeroMorphView morph = new PlaylistHeroMorphView(this);
            Bitmap art = playlistSharedArtwork(snapshot, push ? playlistHeroSourceThumb : listThumb);
            morph.configure(listCard, snapshot.detailContainer,
                    listArtwork, snapshot.detailArtwork,
                    listTitle, snapshot.detailTitle,
                    listMeta, snapshot.detailMeta,
                    listTitleSize, snapshot.detailTitleSizePx,
                    listMetaSize, snapshot.detailMetaSizePx,
                    snapshot.title, snapshot.subtitle, snapshot.accent, art);
            morph.setClickable(true);
            morph.setFocusable(false);
            // Paint the proxy at the exact source geometry before visual ownership moves away from
            // the list row. This keeps one continuous object on screen with no one-frame disappearance.
            morph.setProgress(push ? 0f : 1f);
            activePlaylistHeroMorph = morph;
            appRoot.addView(morph, Ui.frame(-1, -1, Gravity.FILL));
            morph.bringToFront();
            if (activePlaylistHeroCard != null) activePlaylistHeroCard.setAlpha(0f);
            if (listRow != null) listRow.setAlpha(0f);

            // The shared element owns the attention. Dense page content uses a non-alpha curtain so
            // old/new text never double-exposes while the card expands or contracts.
            activePlaylistSharedProgress = push ? 0f : 1f;
            applyPlaylistSharedProgress(activePlaylistSharedProgress);
            animatePlaylistSharedProgress(push ? 1f : 0f);
        };

        // Pop needs one extra frame because root scroll restoration is posted by renderTab(); this
        // lets the exact source card settle back into its saved slot before geometry is measured.
        if (push) incoming.postOnAnimation(prepareRef[0]);
        else incoming.postOnAnimation(() -> incoming.postOnAnimation(prepareRef[0]));
    }

    private void abandonPlaylistSharedTransition(boolean push) {
        View outgoing = activePlaylistTransitionOutgoing;
        View incoming = activePlaylistTransitionIncoming;
        clearPlaylistSharedOverlay(false);
        if (outgoing == null || incoming == null || pageHost == null) return;
        outgoing.setClipBounds(null);
        incoming.setClipBounds(null);
        outgoing.setAlpha(1f); incoming.setAlpha(1f);
        outgoing.setTranslationX(0f); incoming.setTranslationX(0f);
        outgoing.setLayerType(View.LAYER_TYPE_NONE, null);
        incoming.setLayerType(View.LAYER_TYPE_NONE, null);
        if (activePlaylistHeroCard != null) activePlaylistHeroCard.setAlpha(1f);
        resetPlaylistDetailTransitionViews();
        if (playlistHeroSourceRow != null) playlistHeroSourceRow.setAlpha(1f);
        if (playlistHeroReturnRow != null) playlistHeroReturnRow.setAlpha(1f);
        // Geometry can disappear after a very fast scroll/layout mutation. Fall back to the proven
        // opaque depth transition rather than snapping or cross-fading two dense pages.
        if (incoming.getParent() == pageHost) pageHost.removeView(incoming);
        if (outgoing.getParent() != pageHost) pageHost.addView(outgoing);
        startDepthPageTransition(outgoing, incoming, push, Math.max(1f, pageHost.getWidth()));
    }


    private void animatePlaylistSharedProgress(float target) {
        if (activePlaylistTransitionOutgoing == null || activePlaylistTransitionIncoming == null) return;
        if (activePlaylistSharedAnimator != null) activePlaylistSharedAnimator.cancel();
        float start = activePlaylistSharedProgress;
        float distance = Math.abs(target - start);
        if (distance < .001f) {
            applyPlaylistSharedProgress(target);
            finishPlaylistSharedProgress(target);
            return;
        }
        activePlaylistSharedAnimator = ValueAnimator.ofFloat(start, target);
        activePlaylistSharedAnimator.setDuration(Math.max(170L, Math.round(840L * distance)));
        activePlaylistSharedAnimator.setInterpolator(SpringMotion.PLAYER_OPEN);
        activePlaylistSharedAnimator.addUpdateListener(a -> applyPlaylistSharedProgress((Float) a.getAnimatedValue()));
        final float endpoint = target;
        activePlaylistSharedAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (activePlaylistSharedAnimator == animation) activePlaylistSharedAnimator = null;
                if (!cancelled) finishPlaylistSharedProgress(endpoint);
            }
        });
        activePlaylistSharedAnimator.start();
    }

    private void applyPlaylistSharedProgress(float value) {
        activePlaylistSharedProgress = Math.max(0f, Math.min(1f, value));
        float p = activePlaylistSharedProgress;
        if (activePlaylistHeroMorph != null) activePlaylistHeroMorph.setProgress(p);
        View outgoing = activePlaylistTransitionOutgoing;
        View incoming = activePlaylistTransitionIncoming;
        if (outgoing == null || incoming == null) return;

        // The source library and detail page never cross-fade as two complete screens.  Instead the
        // source recedes while individual detail elements arrive after the shared card is already
        // moving.  This is what makes the transition readable even when destination data is late.
        float rootExit = playlistStage(p, .16f, .78f);
        View root = activePlaylistTransitionPush ? outgoing : incoming;
        View detail = activePlaylistTransitionPush ? incoming : outgoing;
        root.setAlpha(1f - .88f * rootExit);
        root.setTranslationX(-Ui.dp(this, 20) * rootExit);
        detail.setAlpha(1f);
        detail.setTranslationX(0f);
        detail.setClipBounds(null);
        applyPlaylistDetailChoreography(p);
    }

    private void finishPlaylistSharedProgress(float endpoint) {
        final boolean detailVisible = endpoint >= .999f;
        View outgoing = activePlaylistTransitionOutgoing;
        View incoming = activePlaylistTransitionIncoming;
        if (pageHost == null || outgoing == null || incoming == null) {
            clearPlaylistSharedOverlay(true);
            return;
        }

        if (detailVisible) {
            incoming.setClipBounds(null);
            incoming.setTranslationX(0f);
            outgoing.setTranslationX(0f);
            if (outgoing.getParent() == pageHost) pageHost.removeView(outgoing);
            if (activePlaylistHeroCard != null) activePlaylistHeroCard.setAlpha(1f);
            if (playlistHeroSourceRow != null) playlistHeroSourceRow.setAlpha(1f);
            resetPlaylistDetailTransitionViews();
            incoming.setAlpha(1f);
            incoming.setLayerType(View.LAYER_TYPE_NONE, null);
            outgoing.setLayerType(View.LAYER_TYPE_NONE, null);
            // Preserve the destination geometry for an exact reverse path later.
            capturePlaylistDetailHeroGeometry(playlistHeroSnapshot);
            clearPlaylistSharedOverlay(true);
        } else {
            outgoing.setClipBounds(null);
            incoming.setClipBounds(null);
            incoming.setTranslationX(0f);
            outgoing.setTranslationX(0f);
            // On a reversed in-flight push, outgoing is already the library root. On a normal pop,
            // incoming is the library root. Keep whichever root is actually meant to survive.
            View root = activePlaylistTransitionPush ? outgoing : incoming;
            View detail = activePlaylistTransitionPush ? incoming : outgoing;
            if (detail.getParent() == pageHost) pageHost.removeView(detail);
            if (root.getParent() != pageHost) pageHost.addView(root);
            root.setLayerType(View.LAYER_TYPE_NONE, null);
            detail.setLayerType(View.LAYER_TYPE_NONE, null);
            if (playlistHeroSourceRow != null) playlistHeroSourceRow.setAlpha(1f);
            if (playlistHeroReturnRow != null) playlistHeroReturnRow.setAlpha(1f);
            if (activePlaylistHeroCard != null) activePlaylistHeroCard.setAlpha(1f);
            resetPlaylistDetailTransitionViews();
            root.setAlpha(1f);
            root.setTranslationX(0f);
            lastRenderedMotionTab = 2;
            lastRenderedMotionDepth = 0;
            clearPlaylistSharedOverlay(true);
            playlistHeroSnapshot = null;
            playlistHeroSourceRow = null;
            playlistHeroSourceThumb = null;
            playlistHeroReturnRow = null;
            playlistHeroReturnThumb = null;
            playlistHeroReturnTitle = null;
            playlistHeroReturnMeta = null;
            clearActivePlaylistUiRefs();
            updatePlaylistLocateButtonVisibility();
        }
    }

    private void clearPlaylistSharedOverlay(boolean nextFrame) {
        final PlaylistHeroMorphView morph = activePlaylistHeroMorph;
        activePlaylistHeroMorph = null;
        activePlaylistTransitionOutgoing = null;
        activePlaylistTransitionIncoming = null;
        activePlaylistTransitionPush = false;
        activePlaylistSharedProgress = 0f;
        if (morph == null) return;
        Runnable remove = () -> {
            if (morph.getParent() instanceof ViewGroup) ((ViewGroup) morph.getParent()).removeView(morph);
            morph.releaseArtwork();
        };
        if (nextFrame && appRoot != null) appRoot.postOnAnimation(remove); else remove.run();
    }

    private View buildCurrentPageNow() {
        if (offlinePlaylistOpen) return offlinePlaylistPage();
        if (openSmartCollection != null) return smartCollectionPage(openSmartCollection);
        if (openPlaylist != null) return playlistDetailPage(openPlaylist);
        if (searchAllResultsOpen && tab == 1) return searchAllResultsPage();
        if (tab == 0) return homePage();
        if (tab == 1) return searchPage();
        if (tab == 2) return playlistsPage();
        return favoritesPage();
    }

    private boolean shouldDeferNavigationPage(int targetTab, int targetDepth) {
        // Object/list detail pages own their own progressive row choreography. Do not wrap them in
        // one full-screen CurtainRevealFrame: a single travelling seam across the whole viewport is
        // visually mechanical and competes with the row-by-row staircase the user is following.
        if (targetDepth > 0 && (openPlaylist != null || openSmartCollection != null
                || (targetTab == 1 && searchAllResultsOpen))) return false;
        if (targetDepth > 0) return true;
        if (targetTab == 2) return playlists != null && playlists.size() > 18;
        if (targetTab == 3) return favorites != null && favorites.size() > 72;
        if (targetTab == 0) return (history != null && history.size() > 72) || (playlists != null && playlists.size() > 28);
        return false;
    }

    private View deferredNavigationPage(final int targetTab, final int targetDepth, final int generation) {
        CurtainRevealFrame shell = new CurtainRevealFrame(this);
        shell.setPlaceholder(navigationSkeletonPage(targetTab, targetDepth));
        long delay = SpringMotion.isReducedMotion() ? 48L : 360L;
        shell.postDelayed(() -> {
            if (generation != deferredPageGeneration || tab != targetTab || shell.getParent() == null) return;
            View actual = buildCurrentPageNow();
            if (generation != deferredPageGeneration || tab != targetTab || shell.getParent() == null) return;
            shell.reveal(actual);
            restoreDeferredPageContext(actual, targetTab, targetDepth);
        }, delay);
        return shell;
    }

    private View navigationSkeletonPage(int targetTab, int targetDepth) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = Ui.column(this);
        body.setPadding(Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 170));
        int accent = new int[]{Ui.PURPLE, Ui.CYAN, Ui.GREEN, Ui.PINK}[Math.max(0, Math.min(3, targetTab))];

        FluidPlaceholderView title = new FluidPlaceholderView(this, FluidPlaceholderView.TITLE, accent);
        body.addView(title, Ui.lp(-1, Ui.dp(this, 72)));
        FluidPlaceholderView hero = new FluidPlaceholderView(this, FluidPlaceholderView.CARD, accent);
        LinearLayout.LayoutParams hp = Ui.lp(-1, Ui.dp(this, targetDepth > 0 ? 126 : 108)); hp.topMargin = Ui.dp(this, 20);
        body.addView(hero, hp);
        for (int i = 0; i < 4; i++) {
            FluidPlaceholderView row = new FluidPlaceholderView(this, FluidPlaceholderView.SONG_ROW, accent);
            LinearLayout.LayoutParams rp = Ui.lp(-1, Ui.dp(this, 72)); rp.topMargin = Ui.dp(this, i == 0 ? 22 : 7);
            body.addView(row, rp);
        }
        scroll.addView(body, Ui.lp(-1, -2));
        return scroll;
    }

    private void restoreDeferredPageContext(View actual, int targetTab, int targetDepth) {
        if (actual == null || targetTab != tab) return;
        restoreRootTabContext(actual, targetTab, targetDepth);
        boolean homeRoot = targetTab == 0 && targetDepth == 0 && openSmartCollection == null
                && openPlaylist == null && !offlinePlaylistOpen;
        if (homeRoot && restoreHomePosition && actual instanceof ScrollView) {
            ScrollView homeScroll = (ScrollView) actual;
            int y = Math.max(0, homeScrollY);
            int x = Math.max(0, homeRecommendationScrollX);
            homeScroll.post(() -> {
                homeScroll.scrollTo(0, y);
                if (homeRecommendationCarousel != null) homeRecommendationCarousel.scrollTo(x, 0);
            });
            restoreHomePosition = false;
        }
        if (openPlaylist != null && restorePlaylistPosition && actual instanceof ScrollView) {
            ScrollView playlistScroll = (ScrollView) actual;
            int y = Math.max(0, playlistScrollRestoreY);
            playlistScroll.post(() -> playlistScroll.scrollTo(0, y));
            restorePlaylistPosition = false;
        }
    }

    private void renderTab() {
        hideSearchSuggestions();
        activeSearchSuggestions = null;
        stopHomeLyricQuoteTicker();
        if (openPlaylist == null && !pendingPlaylistHeroPop) clearActivePlaylistUiRefs();

        final int previousTab = lastRenderedMotionTab;
        final int previousDepth = lastRenderedMotionDepth;
        final int nextDepth = (offlinePlaylistOpen || openSmartCollection != null || openPlaylist != null
                || (searchAllResultsOpen && tab == 1)) ? 1 : 0;
        final boolean renderingHomeRoot = openSmartCollection == null && openPlaylist == null && !offlinePlaylistOpen && tab == 0;

        final int renderGeneration = ++deferredPageGeneration;
        final boolean lateralNavigation = tab != previousTab;
        final boolean depthNavigation = nextDepth != previousDepth;
        // Navigation must own the next frame.  Lateral tab travel and same-tab push/pop can both move
        // a tiny structure-matched shell immediately, then construct a heavy destination only after
        // the physical transition is underway.  Routine same-depth enrichment stays in-place and is
        // never replaced with a skeleton again.
        final boolean playlistSharedNavigation = !SpringMotion.isReducedMotion()
                && playlistHeroSnapshot != null
                && (pendingPlaylistHeroPush || pendingPlaylistHeroPop)
                && tab == 2;
        boolean deferNavigation = (lateralNavigation || depthNavigation)
                && !playlistSharedNavigation
                && shouldDeferNavigationPage(tab, nextDepth);
        View page = deferNavigation
                ? deferredNavigationPage(tab, nextDepth, renderGeneration)
                : buildCurrentPageNow();

        boolean animatePage = !suppressNextPageAnimation;
        suppressNextPageAnimation = false;
        View outgoing = pageHost.getChildCount() == 0 ? null : pageHost.getChildAt(pageHost.getChildCount() - 1);
        // Interrupted navigation keeps the most recently presented page at its current presentation
        // state. Older stale layers are discarded, so new input retargets rather than queues.
        while (pageHost.getChildCount() > 1) pageHost.removeViewAt(0);

        if (!animatePage || outgoing == null) {
            rootTabPhysicsToken++;
            rootTabVelocityPxPerSec = 0f;
            if (outgoing != null) outgoing.animate().cancel();
            pageHost.removeAllViews();
            page.setAlpha(1f); page.setTranslationX(0f); page.setTranslationY(0f); page.setScaleX(1f); page.setScaleY(1f);
            pageHost.addView(page);
        } else {
            // Root-tab changes are always lateral spaces, even when a destination restores a nested
            // detail.  Depth motion is reserved for push/pop inside the same tab.
            final boolean lateral = tab != previousTab;
            final boolean push = !lateral && nextDepth > previousDepth;
            final boolean pop = !lateral && nextDepth < previousDepth;
            final int direction = tab >= previousTab ? 1 : -1;
            final boolean reduced = SpringMotion.isReducedMotion();
            final float width = Math.max(1f, pageHost.getWidth());

            outgoing.animate().cancel();
            page.animate().cancel();
            if (lateral && !reduced) {
                // V92.9.4: root tabs are one continuous physical strip.  The page pair remains fully
                // opaque and one viewport apart, but settling is now a frame-driven near-critical
                // spring.  Rapid retargets inherit the current velocity instead of starting a fresh
                // easing curve, which gives the slide real inertia without the old double exposure.
                startRootTabPhysics(outgoing, page, direction, width);
            } else if (lateral) {
                rootTabPhysicsToken++;
                rootTabVelocityPxPerSec = 0f;
                // Reduced Motion keeps the state change but removes large spatial travel and avoids
                // translucent overlap entirely.
                pageHost.removeView(outgoing);
                page.setAlpha(.88f); page.setTranslationX(0f); page.setTranslationY(0f); page.setScaleX(1f); page.setScaleY(1f);
                pageHost.addView(page);
                page.animate().alpha(1f).setDuration(SpringMotion.fadeDuration()).setInterpolator(SpringMotion.TAB_PAGE).start();
            } else {
                rootTabPhysicsToken++;
                rootTabVelocityPxPerSec = 0f;
                if ((push || pop) && !reduced) {
                    if (playlistSharedNavigation) {
                        // V92.9.9: the playlist card itself owns navigation. Container, cover and
                        // labels keep one identity while the destination is revealed underneath it.
                        startPlaylistSharedTransition(outgoing, page, push);
                    } else {
                        // V92.9.5: no alpha overlap. Detail pages push from the right; popping performs
                        // the exact inverse path, so Recently Played / playlists / Daily Mix never flash.
                        startDepthPageTransition(outgoing, page, push, width);
                    }
                } else {
                    // Enrichment/reduced-motion renders are state replacement, not navigation.
                    // Swap atomically instead of cross-fading two dense pages.
                    if (outgoing.getParent() == pageHost) pageHost.removeView(outgoing);
                    page.setAlpha(1f); page.setTranslationX(0f); page.setTranslationY(0f);
                    page.setScaleX(1f); page.setScaleY(1f);
                    pageHost.addView(page);
                }
            }
        }

        lastRenderedMotionTab = tab;
        lastRenderedMotionDepth = nextDepth;
        updatePlaylistLocateButtonVisibility();
        restoreRootTabContext(page, tab, nextDepth);
        if (renderingHomeRoot && restoreHomePosition && page instanceof ScrollView) {
            final ScrollView homeScroll = (ScrollView) page;
            final int savedY = Math.max(0, homeScrollY);
            final int savedRecommendationX = Math.max(0, homeRecommendationScrollX);
            rootTabScrollY[0] = savedY;
            homeScroll.post(() -> {
                homeScroll.scrollTo(0, savedY);
                if (homeRecommendationCarousel != null) homeRecommendationCarousel.scrollTo(savedRecommendationX, 0);
            });
            restoreHomePosition = false;
        }
        if (openPlaylist != null && restorePlaylistPosition && page instanceof ScrollView) {
            final ScrollView playlistScroll = (ScrollView) page;
            final int savedY = Math.max(0, playlistScrollRestoreY);
            playlistScroll.post(() -> playlistScroll.scrollTo(0, savedY));
            restorePlaylistPosition = false;
        } else if (openPlaylist != null && restorePlaylistPosition && page instanceof ListView
                && openPlaylist.id.equals(playlistListRestoreId)) {
            final ListView playlistList = (ListView) page;
            final int savedPosition = Math.max(0, playlistListRestorePosition);
            final int savedTop = playlistListRestoreTop;
            playlistList.post(() -> playlistList.setSelectionFromTop(
                    Math.min(savedPosition, Math.max(0, playlistList.getCount() - 1)), savedTop));
            restorePlaylistPosition = false;
        }
    }

    private void rememberHomePosition() {
        if (tab != 0 || openSmartCollection != null || openPlaylist != null || offlinePlaylistOpen || pageHost == null || pageHost.getChildCount() == 0) return;
        View page = pageHost.getChildAt(pageHost.getChildCount() - 1);
        if (page instanceof ScrollView) homeScrollY = ((ScrollView) page).getScrollY();
        if (homeRecommendationCarousel != null) homeRecommendationScrollX = homeRecommendationCarousel.getScrollX();
    }

    private void closeSmartCollectionToHome() {
        openSmartCollection = null;
        tab = 0;
        restoreHomePosition = true;
        refreshNav();
        renderTab();
    }

    private void closePlaylistToLibrary() {
        // Back during an unfinished shared push reverses the same physical object immediately.
        // It does not wait for the forward animation to finish and does not build a second root page.
        if (activePlaylistTransitionPush
                && activePlaylistTransitionOutgoing != null && activePlaylistTransitionIncoming != null) {
            openPlaylist = null;
            offlinePlaylistOpen = false;
            pendingPlaylistHeroPush = false;
            pendingPlaylistHeroPop = false;
            tab = 2;
            refreshNav();
            animatePlaylistSharedProgress(0f);
            return;
        }
        if (openPlaylist != null && playlistHeroSnapshot != null
                && playlistHeroSnapshot.playlistId.equals(openPlaylist.id)
                && !SpringMotion.isReducedMotion()) {
            capturePlaylistDetailHeroGeometry(playlistHeroSnapshot);
            pendingPlaylistHeroPop = playlistHeroSnapshot.hasDetailGeometry;
        } else {
            pendingPlaylistHeroPop = false;
            if (SpringMotion.isReducedMotion()) playlistHeroSnapshot = null;
        }
        pendingPlaylistHeroPush = false;
        openPlaylist = null;
        offlinePlaylistOpen = false;
        tab = 2;
        refreshNav();
        renderTab();
    }

    private View pageScaffold(String title, String subtitle, View body) {
        return pageScaffold(title, subtitle, body, null, false);
    }

    private View pageScaffold(String title, String subtitle, View body, View titleAction) {
        return pageScaffold(title, subtitle, body, titleAction, false);
    }

    private View pageScaffold(String title, String subtitle, View body, View titleAction, boolean rotatingHomeLyric) {
        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 23), Ui.dp(this, 20), Ui.dp(this, 162));

        // V92.9.5: Home utilities are progressive disclosure anchored to the hero's top-right.
        // The rotating lyric keeps the full row below and never competes with floating actions.
        if (titleAction == null) {
            TextView t = Ui.text(this, title, 30.5f, Ui.TEXT, true);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            root.addView(t, Ui.lp(-1, Ui.dp(this, 48)));
        } else {
            LinearLayout titleRow = Ui.row(this);
            titleRow.setGravity(Gravity.TOP);
            TextView t = Ui.text(this, title, 30.5f, Ui.TEXT, true);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            titleRow.addView(t, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f));
            LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48));
            actionLp.leftMargin = Ui.dp(this, 8);
            titleRow.addView(titleAction, actionLp);
            titleRow.setMinimumHeight(Ui.dp(this, 48));
            root.addView(titleRow, Ui.lp(-1, -2));
        }
        if (rotatingHomeLyric || subtitle != null) {
            TextView sub = Ui.text(this, subtitle == null ? "" : subtitle, 13.5f, Ui.TEXT_2, false);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            root.addView(sub, Ui.lp(-1, Ui.dp(this, 31)));
            if (rotatingHomeLyric) startHomeLyricQuoteTicker(sub);
        }

        if (body != null) {
            LinearLayout.LayoutParams bp = Ui.lp(-1, -2);
            bp.topMargin = Ui.dp(this, 15);
            root.addView(body, bp);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(root);
        return scroll;
    }

    /** V89 unified contextual engine entry. Tap = quick insight; long press = full matrix. */
    private FrameLayout engineInsightAction(int tint, String label, Runnable quickAction, Runnable fullAction) {
        return engineInsightAction(tint, label, quickAction, fullAction, 44);
    }

    private FrameLayout engineInsightAction(int tint, String label, Runnable quickAction, Runnable fullAction, int sizeDp) {
        FrameLayout button = Ui.iconButton(this, IconView.Type.SCAN, sizeDp, tint, Color.TRANSPARENT);
        button.setContentDescription(label + "，轻点快速洞察，长按查看完整矩阵");
        button.setClickable(true);
        button.setFocusable(true);
        Ui.applyRipple(button, Color.TRANSPARENT);
        button.setOnClickListener(v -> { if (quickAction != null) quickAction.run(); });
        button.setOnLongClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            if (fullAction != null) fullAction.run();
            return true;
        });
        return button;
    }

    private View homePage() {
        LinearLayout body = Ui.column(this);

        // A music app should open with something useful, not a marketing card.
        LinearLayout quick = Ui.row(this);
        quick.setPadding(Ui.dp(this, 15), 0, Ui.dp(this, 8), 0);
        int quickAccent = playlistPlaybackAccent;
        FluidTrackHaloDrawable homeSearchHalo = new FluidTrackHaloDrawable(getResources().getDisplayMetrics().density);
        bindPlaybackHalo(homeSearchHalo, quick);
        quick.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { bindPlaybackHalo(homeSearchHalo, quick); }
            @Override public void onViewDetachedFromWindow(View v) { homeSearchHalo.stopRendering(); }
        });
        IconView qIcon = new IconView(this, IconView.Type.SEARCH, AppearanceSystem.isLight()
                ? Ui.playerControlIconColor(quickAccent) : Ui.mix(quickAccent, Color.WHITE, .28f));
        quick.addView(qIcon, Ui.lp(Ui.dp(this, 24), Ui.dp(this, 24)));
        TextView qText = Ui.text(this, "搜索歌曲、歌手", 13.2f, Ui.TEXT_2, false);
        qText.setPadding(Ui.dp(this, 10), 0, 0, 0);
        quick.addView(qText, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1f));
        FrameLayout qGo = Ui.iconButton(this, IconView.Type.SEARCH, 38, Color.rgb(15, 20, 24), quickAccent);
        qGo.setBackground(Ui.primaryFill(quickAccent, 19, this));
        quick.addView(qGo, Ui.lp(Ui.dp(this, 38), Ui.dp(this, 38)));
        quick.setClickable(true);
        Ui.applyRipple(quick, Color.argb(30, 255, 255, 255));
        View.OnClickListener openSearch = v -> switchRootTab(1, navItems[1]);
        quick.setOnClickListener(openSearch);
        qGo.setOnClickListener(openSearch);
        onboardingSearchTarget = quick;
        body.addView(quick, Ui.lp(-1, Ui.dp(this, 54)));

        // V90: remove the permanent Smart Source card. Engine diagnostics live behind the title action.
        List<Song> recent = history.subList(0, Math.min(10, history.size()));
        View recentHeader = history.isEmpty()
                ? sectionHeader("最近播放", "")
                : sectionHeaderAction("最近播放", "全部播放", this::openRecentHistory);
        onboardingRecentTarget = recentHeader;
        body.addView(recentHeader, marginTop(18));
        if (history.isEmpty()) {
            body.addView(compactEmpty("播放过的歌曲会出现在这里"), marginTop(7));
        } else {
            body.addView(recentCarousel(recent), marginTop(8));
        }

        View recommendationHeader = recommendationSectionHeader();
        onboardingRecommendationTarget = recommendationHeader;
        body.addView(recommendationHeader, marginTop(10));
        body.addView(recommendationCarousel(), marginTop(7));

        if (!playlists.isEmpty()) {
            body.addView(sectionHeader("最近导入", "歌单"), marginTop(24));
            for (int i = 0; i < Math.min(3, playlists.size()); i++) body.addView(playlistCard(playlists.get(i)), marginTop(8));
        }

        // V92.9.5: one quiet top-right hub owns secondary Home utilities. It grows vertically
        // from the touched source, so microphone and engine diagnostics do not crowd Search.
        FluidToolDock homeTools = new FluidToolDock(this, FluidToolDock.Direction.DOWN,
                IconView.Type.GRID, Ui.CYAN, "首页工具，点击展开语音与引擎矩阵");
        FrameLayout voiceTool = homeTools.addAction(IconView.Type.MIC,
                VoiceAssistantContract.enabled(this) ? Ui.CYAN : Ui.PURPLE,
                VoiceAssistantContract.enabled(this) ? "Lunaxy Voice，立即聆听" : "Lunaxy Voice 设置",
                () -> { if (VoiceAssistantContract.enabled(this)) startVoiceAssistant(true); else showVoiceAssistantSettings(); });
        voiceTool.setOnLongClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            showVoiceAssistantSettings();
            return true;
        });
        FrameLayout engineTool = homeTools.addAction(IconView.Type.SCAN, Ui.CYAN, "引擎矩阵",
                () -> showEngineMatrixCenter(0));
        onboardingSourceTarget = homeTools.hubView();
        View page = pageScaffold(greetingTitle(), "", body, homeTools, true);
        onboardingHomeScroll = page instanceof ScrollView ? (ScrollView) page : null;
        return page;
    }

    private void maybeStartOnboarding() {
        if (isFinishing() || onboardingStarted || onboardingOverlay != null) return;
        SharedPreferences prefs = getSharedPreferences("lunaxy_onboarding", MODE_PRIVATE);
        if (prefs.getBoolean("first_run_guide_v1_done", false)) return;
        onboardingStarted = true;
        if (tab != 0 || openPlaylist != null || openSmartCollection != null) {
            tab = 0;
            openPlaylist = null;
            openSmartCollection = null;
            refreshNav();
            renderTab();
        }
        onboardingStep = 0;
        main.postDelayed(() -> showOnboardingStep(0), 180L);
    }

    private void showOnboardingStep(int step) {
        if (isFinishing()) return;
        onboardingStep = Math.max(0, Math.min(4, step));
        View target;
        String title;
        String body;
        switch (onboardingStep) {
            case 0:
                target = onboardingSearchTarget;
                title = "先从搜索开始";
                body = "输入歌名或歌手即可搜索。搜索结果点一下只会临时插入并播放这首歌，不会打乱你原来的播放列表。";
                break;
            case 1:
                target = onboardingSourceTarget;
                title = "多平台会自动选路";
                body = "QQ、酷我、酷狗、网易会自动匹配同一首歌。路线经验会随时间衰减：最近成功更重要，久远成功/失败会慢慢回到中性。点首页右上角工具键，再点引擎图标，可以查看播放链、个性化与最近一次搜索矩阵。";
                break;
            case 2:
                target = onboardingRecentTarget;
                title = "最近播放随时回到现场";
                body = "首页展示最近 10 首，点“全部播放”会进入完整的最近播放歌单，最多保留 50 首。";
                break;
            case 3:
                target = onboardingRecommendationTarget;
                title = "推荐会越来越贴近你";
                body = "每日推荐、私人电台和天气电台会结合你的收藏、歌单、播放习惯与时间天气生成。";
                break;
            default:
                target = navBar;
                title = "四个入口，其他都交给音乐";
                body = "首页、搜索、歌单、收藏都固定在底部。星空实验室、桌面歌词、下载中心、本地音乐等新功能会在第一次用到时给出轻量提示。";
                break;
        }
        if (target == null) {
            main.postDelayed(() -> showOnboardingStep(onboardingStep), 160L);
            return;
        }
        scrollGuideTargetIntoView(target, () -> attachOrUpdateOnboarding(target, title, body));
    }

    private void scrollGuideTargetIntoView(View target, Runnable after) {
        if (onboardingOverlay != null) onboardingOverlay.setVisibility(View.INVISIBLE);
        if (target != navBar && onboardingHomeScroll != null && isDescendantOf(target, onboardingHomeScroll)) {
            android.graphics.Rect rect = new android.graphics.Rect();
            target.getDrawingRect(rect);
            onboardingHomeScroll.offsetDescendantRectToMyCoords(target, rect);
            int desiredY = Math.max(0, rect.centerY() - Math.max(Ui.dp(this, 170), onboardingHomeScroll.getHeight() / 3));
            onboardingHomeScroll.smoothScrollTo(0, desiredY);
            main.postDelayed(after, 290L);
        } else {
            main.postDelayed(after, 40L);
        }
    }

    private boolean isDescendantOf(View child, ViewGroup ancestor) {
        if (child == null || ancestor == null) return false;
        View current = child;
        while (current != null) {
            if (current == ancestor) return true;
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private void attachOrUpdateOnboarding(View target, String title, String body) {
        if (target == null || appRoot == null || isFinishing()) return;
        if (onboardingOverlay == null) {
            onboardingOverlay = new OnboardingOverlay(this);
            appRoot.addView(onboardingOverlay, Ui.frame(-1, -1, Gravity.FILL));
        }
        final boolean last = onboardingStep == 4;
        onboardingOverlay.setStep(target, title, body, onboardingStep + 1, 5, last,
                new OnboardingOverlay.Callback() {
                    @Override public void onNext() {
                        if (last) finishOnboarding();
                        else showOnboardingStep(onboardingStep + 1);
                    }
                    @Override public void onSkip() { finishOnboarding(); }
                });
        onboardingOverlay.setVisibility(View.VISIBLE);
        onboardingOverlay.bringToFront();
        onboardingOverlay.post(onboardingOverlay::refreshTarget);
    }

    private void finishOnboarding() {
        getSharedPreferences("lunaxy_onboarding", MODE_PRIVATE).edit()
                .putBoolean("first_run_guide_v1_done", true).apply();
        if (onboardingOverlay != null) {
            ViewGroup parent = (ViewGroup) onboardingOverlay.getParent();
            if (parent != null) parent.removeView(onboardingOverlay);
            onboardingOverlay = null;
        }
        onboardingStarted = false;
    }

    // V4.4 Context Coach ------------------------------------------------------
    // Separate from the five-step first-run tour: a hidden-feature tip appears only when the
    // relevant scene actually exists. "知道了" persists completion; "稍后再说" suppresses it only
    // for this process so the user can encounter it naturally on a future launch.
    private boolean contextCoachDone(String id) {
        if (id == null || id.isEmpty()) return true;
        return getSharedPreferences(COACH_PREFS, MODE_PRIVATE).getBoolean(id, false);
    }

    private void markContextCoachDone(String id) {
        if (id == null || id.isEmpty()) return;
        getSharedPreferences(COACH_PREFS, MODE_PRIVATE).edit().putBoolean(id, true).apply();
        deferredContextCoachThisSession.remove(id);
    }

    private boolean canShowContextCoach(String id, View target) {
        if (isFinishing() || appRoot == null || target == null || id == null || id.isEmpty()) return false;
        if (contextCoachDone(id) || deferredContextCoachThisSession.contains(id)) return false;
        if (onboardingStarted || (onboardingOverlay != null && onboardingOverlay.getVisibility() == View.VISIBLE)) return false;
        // Fresh installs finish the primary tour before contextual tips begin.
        if (!getSharedPreferences("lunaxy_onboarding", MODE_PRIVATE).getBoolean("first_run_guide_v1_done", false)) return false;
        return contextCoachOverlay == null;
    }

    private void maybeShowContextCoach(String id, View target, String title, String body) {
        if (!canShowContextCoach(id, target)) return;
        if (!target.isAttachedToWindow() || target.getWidth() <= 0 || target.getHeight() <= 0 || !target.isShown()) {
            target.postDelayed(() -> {
                if (canShowContextCoach(id, target) && target.isAttachedToWindow() && target.isShown())
                    showContextCoachNow(id, target, title, body);
            }, 120L);
            return;
        }
        showContextCoachNow(id, target, title, body);
    }

    private void showContextCoachNow(String id, View target, String title, String body) {
        if (!canShowContextCoach(id, target)) return;
        activeContextCoachId = id;
        contextCoachOverlay = new ContextCoachOverlay(this);
        appRoot.addView(contextCoachOverlay, Ui.frame(-1, -1, Gravity.FILL));
        contextCoachOverlay.setTip(target, title, body, new ContextCoachOverlay.Callback() {
            @Override public void onLater() {
                deferredContextCoachThisSession.add(id);
                dismissContextCoach();
            }
            @Override public void onGotIt() {
                markContextCoachDone(id);
                dismissContextCoach();
            }
        });
        contextCoachOverlay.bringToFront();
        contextCoachOverlay.post(contextCoachOverlay::refreshTarget);
    }

    private void dismissContextCoach() {
        if (contextCoachOverlay != null) {
            try {
                ViewGroup parent = (ViewGroup) contextCoachOverlay.getParent();
                if (parent != null) parent.removeView(contextCoachOverlay);
            } catch (Exception ignored) { }
            contextCoachOverlay = null;
        }
        activeContextCoachId = "";
    }

    private void deferActiveContextCoach() {
        if (!activeContextCoachId.isEmpty()) deferredContextCoachThisSession.add(activeContextCoachId);
        dismissContextCoach();
    }

    private void startHomeLyricQuoteTicker(TextView target) {
        homeLyricQuoteView = target;
        homeLyricQuoteGeneration++;
        final int generation = homeLyricQuoteGeneration;
        homeLyricQuoteIndex = 0;
        homeLyricQuotes.clear();
        if (target == null) return;
        target.setText("正在从你的音乐里找一句歌词…");
        collectCachedHomeLyricQuotes(homeLyricQuotes, 28);
        java.util.Collections.shuffle(homeLyricQuotes);
        if (!homeLyricQuotes.isEmpty()) target.setText("“" + homeLyricQuotes.get(0) + "”");

        homeLyricQuoteTicker = new Runnable() {
            @Override public void run() {
                if (generation != homeLyricQuoteGeneration || homeLyricQuoteView != target || target.getParent() == null) return;
                if (homeLyricQuotes.size() > 1) {
                    homeLyricQuoteIndex = (homeLyricQuoteIndex + 1) % homeLyricQuotes.size();
                    String quote = homeLyricQuotes.get(homeLyricQuoteIndex);
                    target.animate().cancel();
                    target.animate().alpha(.18f).setDuration(150).withEndAction(() -> {
                        if (generation != homeLyricQuoteGeneration || homeLyricQuoteView != target) return;
                        target.setText("“" + quote + "”");
                        target.animate().alpha(1f).setDuration(220).start();
                    }).start();
                }
                main.postDelayed(this, 5000L);
            }
        };
        main.postDelayed(homeLyricQuoteTicker, 5000L);
        prefetchHomeLyricQuotes(generation, target);
    }

    private void stopHomeLyricQuoteTicker() {
        homeLyricQuoteGeneration++;
        if (homeLyricQuoteTicker != null) main.removeCallbacks(homeLyricQuoteTicker);
        homeLyricQuoteTicker = null;
        homeLyricQuoteView = null;
    }

    private void collectCachedHomeLyricQuotes(List<String> out, int max) {
        if (out == null || lyricCache == null || max <= 0) return;
        List<Song> candidates = new ArrayList<>(homeQuoteSongCandidates().values());
        java.util.Collections.shuffle(candidates);
        int scan = Math.min(160, candidates.size());
        for (int i = 0; i < scan; i++) {
            if (out.size() >= max) break;
            String cached = lyricCache.get(candidates.get(i));
            appendQuoteLines(cached, candidates.get(i), out, max);
        }
    }

    private LinkedHashMap<String, Song> homeQuoteSongCandidates() {
        LinkedHashMap<String, Song> out = new LinkedHashMap<>();
        for (Song song : favorites) if (song != null) out.put(song.key(), song);
        for (ImportedPlaylist playlist : playlists) {
            if (playlist == null || playlist.songs == null) continue;
            for (Song song : playlist.songs) if (song != null) out.put(song.key(), song);
        }
        return out;
    }

    private void prefetchHomeLyricQuotes(int generation, TextView target) {
        if (homeLyricQuotes.size() >= 10 || lyricCache == null) return;
        List<Song> candidates = new ArrayList<>(homeQuoteSongCandidates().values());
        java.util.Collections.shuffle(candidates);
        List<Song> fetch = new ArrayList<>();
        for (Song song : candidates) {
            if (fetch.size() >= 6) break;
            if (song == null || !lyricCache.get(song).trim().isEmpty()) continue;
            if (!homeQuotePrefetchAttempted.add(song.key())) continue;
            fetch.add(song);
        }
        if (fetch.isEmpty()) return;
        io.submit(() -> {
            List<String> found = new ArrayList<>();
            for (Song song : fetch) {
                try {
                    String lrc = "";
                    com.xingyu.music.model.SourceVariant wy = song.variant("wy");
                    com.xingyu.music.model.SourceVariant tx = song.variant("tx");
                    if (wy != null) lrc = netease.lyricData(wy.sourceId).lrc;
                    else if (tx != null) lrc = qqLyrics.lyric(tx.sourceId);
                    if (lrc != null && !lrc.trim().isEmpty()) {
                        lyricCache.put(song, lrc);
                        appendQuoteLines(lrc, song, found, 12);
                    }
                } catch (Exception ignored) { }
                if (found.size() >= 12) break;
            }
            if (found.isEmpty()) return;
            main.post(() -> {
                if (generation != homeLyricQuoteGeneration || homeLyricQuoteView != target) return;
                LinkedHashSet<String> merged = new LinkedHashSet<>(homeLyricQuotes);
                merged.addAll(found);
                homeLyricQuotes.clear();
                homeLyricQuotes.addAll(merged);
                java.util.Collections.shuffle(homeLyricQuotes);
                if (!homeLyricQuotes.isEmpty() && target.getText().toString().startsWith("正在")) {
                    homeLyricQuoteIndex = 0;
                    target.setText("“" + homeLyricQuotes.get(0) + "”");
                }
            });
        });
    }

    /**
     * Builds the home quote pool from lyric lines that look like the song's chorus/highlight rather
     * than blindly sampling every timestamped row. LRC/YRC does not carry an official "chorus" tag,
     * so the safest local heuristic is: repeated lines first (typical chorus), include their immediate
     * neighbours, otherwise prefer the middle 30%-82% of the song. Metadata/title/artist rows are
     * aggressively rejected before they can enter the ticker.
     */
    private static void appendQuoteLines(String raw, Song song, List<String> out, int max) {
        if (raw == null || raw.trim().isEmpty() || out == null || out.size() >= max) return;
        List<LyricLine> parsed = LyricParser.parse(raw);
        if (parsed.isEmpty()) return;

        List<LyricLine> valid = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (LyricLine line : parsed) {
            String value = line == null || line.text == null ? "" : line.text.trim();
            if (!isGoodHomeQuote(value, song)) continue;
            valid.add(line);
            String key = normalizeQuote(value);
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        if (valid.isEmpty()) return;

        LinkedHashSet<Integer> preferredIndexes = new LinkedHashSet<>();
        for (int i = 0; i < valid.size(); i++) {
            String key = normalizeQuote(valid.get(i).text);
            if (counts.getOrDefault(key, 0) >= 2) {
                for (int d = -2; d <= 2; d++) {
                    int idx = i + d;
                    if (idx >= 0 && idx < valid.size() &&
                            Math.abs(valid.get(idx).timeMs - valid.get(i).timeMs) <= 14000L) {
                        preferredIndexes.add(idx);
                    }
                }
            }
        }

        // If the lyric has no repeated chorus, use the musical middle instead of intro/credits/outro.
        if (preferredIndexes.isEmpty()) {
            long end = Math.max(1L, valid.get(valid.size() - 1).timeMs + 4500L);
            for (int i = 0; i < valid.size(); i++) {
                float ratio = valid.get(i).timeMs / (float) end;
                if (ratio >= .30f && ratio <= .82f) preferredIndexes.add(i);
            }
        }
        if (preferredIndexes.isEmpty()) for (int i = 0; i < valid.size(); i++) preferredIndexes.add(i);

        // Prefer genuinely repeated hook lines, then the surrounding chorus lines.
        List<Integer> ordered = new ArrayList<>(preferredIndexes);
        ordered.sort((a, b) -> {
            int ca = counts.getOrDefault(normalizeQuote(valid.get(a).text), 0);
            int cb = counts.getOrDefault(normalizeQuote(valid.get(b).text), 0);
            if (ca != cb) return Integer.compare(cb, ca);
            return Long.compare(valid.get(a).timeMs, valid.get(b).timeMs);
        });
        for (Integer index : ordered) {
            if (out.size() >= max) break;
            String value = valid.get(index).text.trim();
            if (!out.contains(value)) out.add(value);
        }
    }

    private static String normalizeQuote(String value) {
        if (value == null) return "";
        String lower = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return lower.replaceAll("[\\p{P}\\p{S}\\s]+", "");
    }

    private static boolean isGoodHomeQuote(String value, Song song) {
        if (value == null) return false;
        String clean = value.trim();
        if (clean.length() < 5 || clean.length() > 42) return false;
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.contains("http://") || lower.contains("https://") || lower.contains("www.") || lower.contains("@")) return false;
        if (lower.contains("qq音乐") || lower.contains("网易云") || lower.contains("纯音乐") || lower.contains("instrumental")) return false;
        if (lower.matches("^(作词|作曲|编曲|制作人?|制作|监制|混音|录音|母带|和声|吉他|贝斯|鼓|发行|出品|词|曲|歌手|歌曲|演唱|lyrics?|lyricist|composer|arranger|producer|written by|music by|vocals?|artist|title)\\s*[:：].*$")) return false;
        if (lower.matches("^[\\p{L}\\p{N} ._'·&/-]{1,18}\\s*[:：]\\s*[^，。！？!?]{1,30}$")) return false;
        if (clean.startsWith("[") || clean.startsWith("【") || clean.startsWith("{") || clean.endsWith("制作")) return false;

        String normalized = normalizeQuote(clean);
        if (normalized.isEmpty()) return false;
        if (song != null) {
            String title = normalizeQuote(song.title);
            String artist = normalizeQuote(song.artist);
            // Metadata providers sometimes timestamp title/artist as if they were lyric lines.
            if (!title.isEmpty() && (normalized.equals(title) || (normalized.length() <= title.length() + 3 && title.contains(normalized)))) return false;
            if (!artist.isEmpty() && (normalized.equals(artist) || (normalized.length() <= artist.length() + 3 && artist.contains(normalized)))) return false;
            if (!title.isEmpty() && !artist.isEmpty() && normalized.equals(title + artist)) return false;
        }
        int meaningful = 0;
        for (int i = 0; i < clean.length(); i++) if (Character.isLetterOrDigit(clean.charAt(i))) meaningful++;
        return meaningful >= 4;
    }

    private View libraryActionCard(IconView.Type type, String title, int tint) {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 15), Ui.dp(this, 14), Ui.dp(this, 13));
        applyRecommendationCardMaterial(card, tint, true);
        card.setClickable(true); card.setFocusable(true);
        Ui.applyRipple(card, Color.argb(34, 255, 255, 255));
        FrameLayout icon = Ui.iconButton(this, type, 38, tint, Color.argb(28, Color.red(tint), Color.green(tint), Color.blue(tint)));
        card.addView(icon, Ui.lp(Ui.dp(this, 38), Ui.dp(this, 38)));
        TextView name = Ui.text(this, title, 15.5f, Ui.TEXT, true);
        name.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams np = Ui.lp(-1, Ui.dp(this, 32)); np.topMargin = Ui.dp(this, 10);
        card.addView(name, np);
        return card;
    }


    private int recommendationCardStyle() {
        return Math.max(RecommendationGlassDrawable.STYLE_ORIGINAL,
                Math.min(RecommendationGlassDrawable.STYLE_DEEP_CRYSTAL,
                        getSharedPreferences(HOME_CARD_STYLE_PREFS, MODE_PRIVATE)
                                .getInt(HOME_CARD_STYLE_KEY, RecommendationGlassDrawable.STYLE_ORIGINAL)));
    }

    private static String recommendationStyleName(int style) {
        switch (style) {
            case RecommendationGlassDrawable.STYLE_AURORA: return "柔光极光";
            case RecommendationGlassDrawable.STYLE_PRISM: return "棱镜边框";
            case RecommendationGlassDrawable.STYLE_MOON_FROST: return "月雾磨砂";
            case RecommendationGlassDrawable.STYLE_DEEP_CRYSTAL: return "深空晶体";
            default: return "原版";
        }
    }

    private void applyRecommendationCardMaterial(View card, int tint, boolean animated) {
        int style = recommendationCardStyle();
        if (style == RecommendationGlassDrawable.STYLE_ORIGINAL) {
            // Moonlight original is a white/pastel content card rather than dark glass translated
            // onto a white canvas. Dark/OLED keep the long-stable original material.
            card.setBackground(AppearanceSystem.isLight()
                    ? Ui.tintedGlass(tint, 20, this)
                    : Ui.glass(164, 20, 22, this));
            return;
        }
        card.setBackground(new RecommendationGlassDrawable(
                getResources().getDisplayMetrics().density, style, tint, animated));
    }

    private View recommendationSectionHeader() {
        LinearLayout row = Ui.row(this);
        row.setClipChildren(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, "为你推荐", 16.5f, Ui.TEXT, true);
        row.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f));

        // Progressive disclosure: one compact adjustment hub expands left into two secondary
        // actions. Each child still opens its existing second-level surface, keeping Home quiet.
        FluidToolDock dock = new FluidToolDock(this, FluidToolDock.Direction.LEFT,
                IconView.Type.TUNE, Ui.TEXT_2, "推荐设置，点击展开卡片样式与主题");
        dock.addAction(IconView.Type.LAYERS, Ui.CYAN, "卡片样式", this::showRecommendationStyleDrawer);
        dock.addAction(IconView.Type.PALETTE, Ui.PURPLE, "主题与动效", this::showAppearanceSheet);
        row.addView(dock, Ui.lp(Ui.dp(this, 48), Ui.dp(this, 48)));
        return row;
    }

    private void showRecommendationStyleDrawer() {
        int selected = recommendationCardStyle();
        LinearLayout sheet = Ui.column(this);
        sheet.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 16));
        sheet.setBackground(Ui.transientGlass(25, this));
        sheet.setElevation(Ui.dp(this, 10));

        FrameLayout handleHost = new FrameLayout(this);
        View handle = new View(this);
        handle.setBackground(Ui.round(Color.argb(105, 220, 220, 232), 2, this));
        handleHost.addView(handle, Ui.frame(Ui.dp(this, 36), Ui.dp(this, 4), Gravity.CENTER));
        sheet.addView(handleHost, Ui.lp(-1, Ui.dp(this, 14)));

        TextView title = Ui.text(this, "卡片样式", 17.2f, Ui.TEXT, true);
        sheet.addView(title, Ui.lp(-1, Ui.dp(this, 30)));

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        LinearLayout choices = Ui.row(this);
        choices.setGravity(Gravity.TOP);
        int[] previewTints = new int[]{Ui.CYAN, Ui.GREEN, Ui.PURPLE, Ui.BLUE, Ui.CYAN};
        for (int i = 0; i < RecommendationGlassDrawable.STYLE_COUNT; i++) {
            final int styleIndex = i;
            LinearLayout option = Ui.column(this);
            option.setGravity(Gravity.CENTER_HORIZONTAL);
            option.setPadding(Ui.dp(this, 4), Ui.dp(this, 5), Ui.dp(this, 4), Ui.dp(this, 5));
            // Selection is carried by a real theme-aware tonal surface. Using low-alpha white
            // strokes here made Moonlight choices disappear into the sheet background.
            option.setBackground(i == selected
                    ? Ui.tintedGlass(previewTints[i], 16, this)
                    : Ui.contentSurface(16, this));
            option.setClickable(true); option.setFocusable(true);
            Ui.applyRipple(option, Color.TRANSPARENT);

            FrameLayout preview = new FrameLayout(this);
            if (i == RecommendationGlassDrawable.STYLE_ORIGINAL) {
                preview.setBackground(AppearanceSystem.isLight()
                        ? Ui.tintedGlass(previewTints[i], 13, this)
                        : Ui.glass(164, 13, 22, this));
            } else {
                preview.setBackground(new RecommendationGlassDrawable(
                        getResources().getDisplayMetrics().density, i, previewTints[i], false));
            }
            option.addView(preview, Ui.lp(Ui.dp(this, 54), Ui.dp(this, 38)));

            TextView label = Ui.text(this, recommendationStyleName(i), 9.4f, i == selected ? Ui.TEXT : Ui.TEXT_2, i == selected);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            LinearLayout.LayoutParams lp = Ui.lp(Ui.dp(this, 66), Ui.dp(this, 25));
            lp.topMargin = Ui.dp(this, 5);
            option.addView(label, lp);
            option.setOnClickListener(v -> {
                getSharedPreferences(HOME_CARD_STYLE_PREFS, MODE_PRIVATE).edit()
                        .putInt(HOME_CARD_STYLE_KEY, styleIndex).apply();
                rememberHomePosition();
                restoreHomePosition = true;
                suppressNextPageAnimation = true;
                dismissModalNow();
                renderTab();
            });

            LinearLayout.LayoutParams op = Ui.lp(Ui.dp(this, 72), Ui.dp(this, 78));
            if (i > 0) op.leftMargin = Ui.dp(this, 6);
            choices.addView(option, op);
        }
        scroller.addView(choices);
        LinearLayout.LayoutParams cp = Ui.lp(-1, Ui.dp(this, 82));
        cp.topMargin = Ui.dp(this, 8);
        sheet.addView(scroller, cp);
        presentModal(sheet);
    }

    /** V92.9 global appearance sheet: theme, material accessibility, and motion accessibility. */
    private void showAppearanceSheet() {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 16));
        card.setBackground(Ui.transientGlass(27, this));
        card.setElevation(Ui.dp(this, 12));

        LinearLayout header = Ui.row(this);
        LinearLayout titles = Ui.column(this);
        TextView title = Ui.text(this, "外观与动效", 18.8f, Ui.TEXT, true);
        TextView subtitle = Ui.text(this, "Lunaxy Visual System · 全局生效", 10.7f, Ui.TEXT_2, false);
        titles.addView(title, Ui.lp(-1, Ui.dp(this, 29)));
        titles.addView(subtitle, Ui.lp(-1, Ui.dp(this, 22)));
        header.addView(titles, new LinearLayout.LayoutParams(0, Ui.dp(this, 54), 1f));
        TextView done = Ui.text(this, "完成", 10.8f, Color.rgb(12, 16, 22), true);
        done.setGravity(Gravity.CENTER);
        done.setBackground(Ui.primaryFill(Ui.PURPLE, 14, this));
        done.setClickable(true); Ui.applyRipple(done, Color.TRANSPARENT);
        done.setOnClickListener(v -> hideModal());
        header.addView(done, Ui.lp(Ui.dp(this, 58), Ui.dp(this, 34)));
        card.addView(header, Ui.lp(-1, Ui.dp(this, 56)));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = Ui.column(this);
        body.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        scroll.addView(body);
        card.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        final Runnable[] redraw = new Runnable[1];
        redraw[0] = () -> {
            card.setBackground(Ui.transientGlass(27, this));
            title.setTextColor(Ui.TEXT);
            subtitle.setTextColor(Ui.TEXT_2);
            done.setBackground(Ui.primaryFill(Ui.PURPLE, 14, this));
            body.removeAllViews();
            body.addView(appearanceHero(), marginTop(2));
            body.addView(starStudioSection("主题", "四套外观共享同一信息结构；跟随系统会在深色与月白之间自动选择。"), marginTop(14));

            LinearLayout row1 = Ui.row(this);
            row1.addView(appearanceModeChoice("跟随系统", AppearanceSystem.effectiveMode() == AppearanceSystem.MODE_MOONLIGHT ? "当前：月白" : "当前：深空",
                    AppearanceSystem.MODE_SYSTEM, Ui.CYAN, redraw[0]), new LinearLayout.LayoutParams(0, Ui.dp(this, 66), 1f));
            LinearLayout.LayoutParams r12 = new LinearLayout.LayoutParams(0, Ui.dp(this, 66), 1f); r12.leftMargin = Ui.dp(this, 8);
            row1.addView(appearanceModeChoice("深空", "Lunaxy 默认", AppearanceSystem.MODE_DEEP, Ui.PURPLE, redraw[0]), r12);
            body.addView(row1, marginTop(8));

            LinearLayout row2 = Ui.row(this);
            row2.addView(appearanceModeChoice("OLED", "纯黑 · 更克制", AppearanceSystem.MODE_OLED, Ui.GREEN, redraw[0]), new LinearLayout.LayoutParams(0, Ui.dp(this, 66), 1f));
            LinearLayout.LayoutParams r22 = new LinearLayout.LayoutParams(0, Ui.dp(this, 66), 1f); r22.leftMargin = Ui.dp(this, 8);
            row2.addView(appearanceModeChoice("月白", "浅色 · 清透纸白", AppearanceSystem.MODE_MOONLIGHT, Ui.GOLD, redraw[0]), r22);
            body.addView(row2, marginTop(8));

            body.addView(starStudioSection("可访问性", "减少动态效果会保留状态变化但缩短空间运动；减少透明度会把玻璃层降级为更实的色调表面。"), marginTop(18));
            body.addView(appearanceToggle("减少动态效果", "大型页面位移、主题扩散与持续星空运动会自动降级",
                    AppearanceSystem.reduceMotion(), Ui.CYAN, v -> {
                        AppearanceSystem.setReduceMotion(this, !AppearanceSystem.reduceMotion());
                        if (stars != null) stars.invalidate();
                        if (playerStars != null) playerStars.invalidate();
                        if (playerStars2D != null) playerStars2D.invalidate();
                        if (nowVinyl != null) nowVinyl.invalidate();
                        if (nowVinylStack != null) nowVinylStack.invalidate();
                        redraw[0].run();
                    }), marginTop(8));
            body.addView(appearanceToggle("减少透明度", "Navigation、Mini Player、Sheet 使用更实的表面并保持层级",
                    AppearanceSystem.reduceTransparency(), Ui.PINK, v -> {
                        AppearanceSystem.setReduceTransparency(this, !AppearanceSystem.reduceTransparency());
                        rebuildHiddenPlayerForAppearance();
                        applyAppearanceToShell();
                        rememberRootTabContext();
                        suppressNextPageAnimation = true;
                        renderTab();
                        refreshNav();
                        redraw[0].run();
                    }), marginTop(8));

            body.addView(starInfoCard("材质层级", "内容层保持稳定阅读；Navigation / Mini Player 属于功能悬浮层；Sheet 属于临时交互层。玻璃不再作为所有卡片的默认装饰。", Ui.PURPLE), marginTop(14));
        };
        redraw[0].run();
        presentFractionModal(card, .76f);
    }

    private View appearanceHero() {
        int mode = AppearanceSystem.selectedMode();
        int accent = mode == AppearanceSystem.MODE_MOONLIGHT ? Ui.GOLD
                : mode == AppearanceSystem.MODE_OLED ? Ui.GREEN : mode == AppearanceSystem.MODE_DEEP ? Ui.PURPLE : Ui.CYAN;
        return starStudioHero("当前 · " + AppearanceSystem.modeName(mode),
                (AppearanceSystem.reduceMotion() ? "减少动态 · " : "流体动态 · ")
                        + (AppearanceSystem.reduceTransparency() ? "实色材质" : "分层玻璃"), accent);
    }

    private View appearanceModeChoice(String title, String subtitle, int mode, int accent, Runnable redraw) {
        boolean selected = AppearanceSystem.selectedMode() == mode;
        LinearLayout c = Ui.column(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        c.setBackground(selected ? Ui.tintedGlass(accent, 17, this) : Ui.contentSurface(17, this));
        c.setClickable(true); c.setFocusable(true); Ui.applyRipple(c, Color.TRANSPARENT);
        TextView t = Ui.text(this, (selected ? "✓  " : "") + title, 12f, selected ? Ui.TEXT : Ui.TEXT_2, true);
        TextView sub = Ui.text(this, subtitle, 9.8f, Ui.DIM, false);
        c.addView(t, Ui.lp(-1, Ui.dp(this, 31)));
        c.addView(sub, Ui.lp(-1, Ui.dp(this, 23)));
        c.setOnClickListener(v -> applyAppearanceMode(v, mode, redraw));
        return c;
    }

    private View appearanceToggle(String title, String subtitle, boolean selected, int accent, View.OnClickListener action) {
        LinearLayout c = Ui.row(this);
        c.setPadding(Ui.dp(this, 13), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        c.setBackground(selected ? Ui.tintedGlass(accent, 18, this) : Ui.contentSurface(18, this));
        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.text(this, title, 12.4f, Ui.TEXT, true), new LinearLayout.LayoutParams(-1, 0, 1f));
        texts.addView(Ui.text(this, subtitle, 9.8f, Ui.DIM, false), new LinearLayout.LayoutParams(-1, 0, 1f));
        c.addView(texts, new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f));
        TextView state = Ui.text(this, selected ? "已开启" : "关闭", 10.2f, selected ? accent : Ui.DIM, true);
        state.setGravity(Gravity.CENTER);
        c.addView(state, Ui.lp(Ui.dp(this, 52), Ui.dp(this, 40)));
        c.setClickable(true); c.setFocusable(true); Ui.applyRipple(c, Color.TRANSPARENT); c.setOnClickListener(action);
        return c;
    }

    private void applyAppearanceMode(View source, int mode, Runnable redraw) {
        if (AppearanceSystem.selectedMode() == mode) return;
        Bitmap oldFrame = null;
        float sourceX = appRoot == null ? 0f : appRoot.getWidth() * .5f;
        float sourceY = appRoot == null ? 0f : appRoot.getHeight() * .5f;
        if (!AppearanceSystem.reduceMotion() && appRoot != null && appRoot.getWidth() > 0 && appRoot.getHeight() > 0) {
            try {
                oldFrame = Bitmap.createBitmap(appRoot.getWidth(), appRoot.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas snapshotCanvas = new Canvas(oldFrame);
                appRoot.draw(snapshotCanvas);
                if (source != null) {
                    int[] root = new int[2], loc = new int[2];
                    appRoot.getLocationOnScreen(root); source.getLocationOnScreen(loc);
                    sourceX = loc[0] - root[0] + source.getWidth() * .5f;
                    sourceY = loc[1] - root[1] + source.getHeight() * .5f;
                }
            } catch (Throwable ignored) {
                if (oldFrame != null && !oldFrame.isRecycled()) oldFrame.recycle();
                oldFrame = null;
            }
        }

        AppearanceSystem.setMode(this, mode);
        rebuildHiddenPlayerForAppearance();
        applyAppearanceToShell();
        rememberRootTabContext();
        suppressNextPageAnimation = true;
        renderTab();
        refreshNav();
        if (redraw != null) redraw.run();

        if (oldFrame == null || appRoot == null || SpringMotion.isReducedMotion()) {
            if (appRoot != null) {
                appRoot.animate().cancel();
                appRoot.setAlpha(.84f);
                appRoot.animate().alpha(1f).setDuration(SpringMotion.fadeDuration()).start();
            }
            return;
        }

        final Bitmap frame = oldFrame;
        ThemeRevealOverlay reveal = new ThemeRevealOverlay(this, frame, sourceX, sourceY);
        appRoot.addView(reveal, Ui.frame(-1, -1, Gravity.FILL));
        float r1 = (float) Math.hypot(sourceX, sourceY);
        float r2 = (float) Math.hypot(appRoot.getWidth() - sourceX, sourceY);
        float r3 = (float) Math.hypot(sourceX, appRoot.getHeight() - sourceY);
        float r4 = (float) Math.hypot(appRoot.getWidth() - sourceX, appRoot.getHeight() - sourceY);
        final float maxRadius = Math.max(Math.max(r1, r2), Math.max(r3, r4)) + Ui.dp(this, 12);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(SpringMotion.themeRevealDuration());
        animator.setInterpolator(SpringMotion.PLAYER_OPEN);
        animator.addUpdateListener(a -> reveal.setRadius(maxRadius * (Float) a.getAnimatedValue()));
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (reveal.getParent() == appRoot) appRoot.removeView(reveal);
                if (!frame.isRecycled()) frame.recycle();
            }
            @Override public void onAnimationCancel(android.animation.Animator animation) {
                if (reveal.getParent() == appRoot) appRoot.removeView(reveal);
                if (!frame.isRecycled()) frame.recycle();
            }
        });
        animator.start();
    }

    /** Lightweight peek/parallax for recommendation cards; transforms only, never relayouts while scrolling. */
    private void installRecommendationParallax(HorizontalScrollView scroll, LinearLayout row) {
        if (scroll == null || row == null) return;
        final android.view.ViewTreeObserver.OnScrollChangedListener listener = () -> {
            float viewportCenter = scroll.getScrollX() + scroll.getWidth() * .5f;
            float span = Math.max(Ui.dp(this, 180), scroll.getWidth() * .68f);
            for (int i = 0; i < row.getChildCount(); i++) {
                View child = row.getChildAt(i);
                if (!(child instanceof LinearLayout) || child.getWidth() <= 0) continue;
                float center = child.getLeft() + child.getWidth() * .5f;
                float n = Math.max(-1f, Math.min(1f, (center - viewportCenter) / span));
                float depth = Math.abs(n);
                child.setScaleX(1f - .010f * depth);
                child.setScaleY(1f - .010f * depth);
                child.setTranslationY(Ui.dp(this, .8f) * depth);
                LinearLayout card = (LinearLayout) child;
                if (card.getChildCount() > 0) card.getChildAt(0).setTranslationX(-n * Ui.dp(this, 6.5f));
                if (card.getChildCount() > 1) card.getChildAt(1).setTranslationX(-n * Ui.dp(this, 2.2f));
            }
        };
        scroll.getViewTreeObserver().addOnScrollChangedListener(listener);
        scroll.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { scroll.post(listener::onScrollChanged); }
            @Override public void onViewDetachedFromWindow(View v) {
                if (scroll.getViewTreeObserver().isAlive()) scroll.getViewTreeObserver().removeOnScrollChangedListener(listener);
            }
        });
        scroll.post(listener::onScrollChanged);
    }

    private View recommendationCarousel() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        homeRecommendationCarousel = scroll;
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);

        View daily = libraryActionCard(IconView.Type.MUSIC, "每日推荐", Ui.PURPLE);
        daily.setOnClickListener(v -> openDailyRecommendations());
        daily.setOnLongClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            showPersonalizationMatrix();
            return true;
        });
        row.addView(daily, Ui.lp(Ui.dp(this, 154), Ui.dp(this, 114)));

        Space gap1 = new Space(this); row.addView(gap1, Ui.lp(Ui.dp(this, 10), 1));
        View radio = libraryActionCard(IconView.Type.SHUFFLE, "私人电台", Ui.CYAN);
        radio.setOnClickListener(v -> openPrivateRadio());
        radio.setOnLongClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            showPersonalizationMatrix();
            return true;
        });
        row.addView(radio, Ui.lp(Ui.dp(this, 154), Ui.dp(this, 114)));

        Space gap2 = new Space(this); row.addView(gap2, Ui.lp(Ui.dp(this, 10), 1));
        View weather = libraryActionCard(IconView.Type.WEATHER, "天气电台", Ui.GOLD);
        weather.setOnClickListener(v -> openWeatherRadio());
        row.addView(weather, Ui.lp(Ui.dp(this, 154), Ui.dp(this, 114)));

        scroll.addView(row);
        installRecommendationParallax(scroll, row);
        return scroll;
    }

    private void openRecentHistory() {
        rememberHomePosition();
        resetSmartCollectionReveal("recent");
        List<Song> songs = new ArrayList<>(history);
        if (songs.isEmpty()) {
            toast("还没有最近播放");
            return;
        }
        openSmartCollection = new SmartCollection("最近播放", "最近听过 · " + songs.size() + " 首",
                "recent", Ui.CYAN, songs);
        renderTab();
    }

    private void openDailyRecommendations() {
        rememberHomePosition();
        resetSmartCollectionReveal("daily");
        if (recommendations == null || !recommendations.hasTaste(favorites, playlists, history)) {
            toast("先收藏、导入歌单或听几首歌，推荐会更懂你");
            return;
        }
        final int token = ++recommendationOpenToken;
        final List<Song> favSnapshot = new ArrayList<>(favorites);
        final List<ImportedPlaylist> playlistSnapshot = new ArrayList<>(playlists);
        final List<Song> historySnapshot = new ArrayList<>(history);
        final List<Song> cached = recommendations.cachedDaily(20);
        final boolean fresh = recommendations.hasFreshDailySnapshot();

        if (!cached.isEmpty()) {
            String subtitle = fresh
                    ? recommendationSubtitle("daily", cached, favSnapshot, playlistSnapshot, historySnapshot)
                    : "上次推荐先出 · " + cached.size() + " 首 · 正在更新今日推荐";
            openRecommendationSnapshot(token, "daily", "每日推荐", Ui.PURPLE, cached, subtitle, false);
            if (fresh) return;
        } else {
            openRecommendationSnapshot(token, "daily", "每日推荐", Ui.PURPLE, new ArrayList<>(),
                    "正在读取本地画像 · 内容会原位生成", false);
        }

        io.submit(() -> {
            if (cached.isEmpty()) {
                List<Song> preview = recommendations.dailyLocalPreview(favSnapshot, playlistSnapshot, historySnapshot, 20);
                main.post(() -> {
                    if (token != recommendationOpenToken) return;
                    showBusy(false);
                    String subtitle = "本地口味先出 · " + preview.size() + " 首 · 正在补充新发现";
                    openRecommendationSnapshot(token, "daily", "每日推荐", Ui.PURPLE, preview, subtitle, false);
                });
            }
            List<Song> songs = recommendations.daily(favSnapshot, playlistSnapshot, historySnapshot, 20);
            main.post(() -> {
                if (token != recommendationOpenToken) return;
                showBusy(false);
                if (songs == null || songs.isEmpty()) {
                    if (openSmartCollection == null || !"daily".equals(openSmartCollection.kind)) toast("今天暂时没有生成推荐");
                    return;
                }
                refreshRecommendationSnapshot(token, "daily", "每日推荐", Ui.PURPLE, songs,
                        recommendationSubtitle("daily", songs, favSnapshot, playlistSnapshot, historySnapshot));
            });
        });
    }

    private void openPrivateRadio() {
        rememberHomePosition();
        resetSmartCollectionReveal("private");
        if (recommendations == null || !recommendations.hasTaste(favorites, playlists, history)) {
            toast("先收藏、导入歌单或听几首歌，再开启私人电台");
            return;
        }
        final int token = ++recommendationOpenToken;
        final List<Song> favSnapshot = new ArrayList<>(favorites);
        final List<ImportedPlaylist> playlistSnapshot = new ArrayList<>(playlists);
        final List<Song> historySnapshot = new ArrayList<>(history);
        final List<Song> cached = recommendations.cachedPrivate(30);

        if (!cached.isEmpty()) {
            openRecommendationSnapshot(token, "private", "私人电台", Ui.CYAN, cached,
                    "最近结果先出 · " + cached.size() + " 首 · 正在按本次 Session 更新", false);
        } else {
            openRecommendationSnapshot(token, "private", "私人电台", Ui.CYAN, new ArrayList<>(),
                    "正在读取当前 Session · 内容会原位生成", false);
        }

        io.submit(() -> {
            if (cached.isEmpty()) {
                List<Song> preview = recommendations.privateLocalPreview(favSnapshot, playlistSnapshot, historySnapshot, 30);
                main.post(() -> {
                    if (token != recommendationOpenToken) return;
                    showBusy(false);
                    openRecommendationSnapshot(token, "private", "私人电台", Ui.CYAN, preview,
                            "本地 Session 先出 · " + preview.size() + " 首 · 正在补充协同发现", false);
                });
            }
            List<Song> songs = recommendations.privateRadio(favSnapshot, playlistSnapshot, historySnapshot, 30);
            main.post(() -> {
                if (token != recommendationOpenToken) return;
                showBusy(false);
                if (songs == null || songs.isEmpty()) {
                    if (openSmartCollection == null || !"private".equals(openSmartCollection.kind)) toast("私人电台还没有足够的歌曲");
                    return;
                }
                refreshRecommendationSnapshot(token, "private", "私人电台", Ui.CYAN, songs,
                        recommendationSubtitle("private", songs, favSnapshot, playlistSnapshot, historySnapshot));
            });
        });
    }

    private String recommendationSubtitle(String kind, List<Song> songs, List<Song> favSnapshot,
                                          List<ImportedPlaylist> playlistSnapshot, List<Song> historySnapshot) {
        RecommendationEngine.RecommendationSummary summary = recommendations.summaryFor(kind, songs, favSnapshot, playlistSnapshot, historySnapshot);
        String discovery = summary.total > 0 ? (" · " + summary.unheardPercent() + "% 新发现") : "";
        if ("private".equals(kind)) return "实时口味与协同发现 · " + songs.size() + " 首" + discovery;
        return "今天为你挑选 · " + songs.size() + " 首" + discovery;
    }

    /** First paint may use a stale/materialized snapshot; it must never wait for external discovery. */
    private void openRecommendationSnapshot(int token, String kind, String title, int accent, List<Song> songs,
                                            String subtitle, boolean preserveScroll) {
        if (token != recommendationOpenToken || tab != 0 || openPlaylist != null || offlinePlaylistOpen) return;
        if (openSmartCollection != null && !kind.equals(openSmartCollection.kind)) return;
        int restoreY = preserveScroll ? currentSmartCollectionScrollY() : 0;
        openSmartCollection = new SmartCollection(title, subtitle, kind, accent, songs);
        suppressNextPageAnimation = preserveScroll;
        renderTab();
        if (preserveScroll) restoreCurrentSmartCollectionScroll(restoreY);
    }

    /** Rich remote discovery only enriches a page the user is still viewing. */
    private void refreshRecommendationSnapshot(int token, String kind, String title, int accent, List<Song> songs, String subtitle) {
        if (token != recommendationOpenToken || openSmartCollection == null || !kind.equals(openSmartCollection.kind)) return;
        int restoreY = currentSmartCollectionScrollY();
        openSmartCollection = new SmartCollection(title, subtitle, kind, accent, songs);
        suppressNextPageAnimation = true;
        renderTab();
        restoreCurrentSmartCollectionScroll(restoreY);
    }

    private int currentSmartCollectionScrollY() {
        if (pageHost == null || pageHost.getChildCount() == 0) return 0;
        View page = pageHost.getChildAt(0);
        return page instanceof ScrollView ? ((ScrollView) page).getScrollY() : 0;
    }

    private void restoreCurrentSmartCollectionScroll(int y) {
        if (pageHost == null || pageHost.getChildCount() == 0) return;
        View page = pageHost.getChildAt(0);
        if (page instanceof ScrollView) page.post(() -> ((ScrollView) page).scrollTo(0, Math.max(0, y)));
    }

    private void openWeatherRadio() {
        rememberHomePosition();
        resetSmartCollectionReveal("weather");
        if (recommendations == null || !recommendations.hasTaste(favorites, playlists, history)) {
            toast("先收藏、导入歌单或听几首歌，天气电台会更适合你");
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            pendingWeatherRadioOpen = true;
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_WEATHER_LOCATION);
            return;
        }
        resolveLocationAndOpenWeatherRadio();
    }

    private void resolveLocationAndOpenWeatherRadio() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) { toast("暂时无法读取当前位置"); return; }
        try {
            Location best = bestLastLocation(lm);
            if (best != null && System.currentTimeMillis() - best.getTime() < 3L * 60L * 60L * 1000L) {
                fetchWeatherAndOpen(best);
                return;
            }
            boolean networkEnabled = false;
            boolean gpsEnabled = false;
            try { networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) { }
            try { gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER); } catch (Exception ignored) { }
            if (!networkEnabled && !gpsEnabled) { toast("请先打开系统定位，再使用天气电台"); return; }
            showBusy(true);
            if (Build.VERSION.SDK_INT >= 30) {
                String provider = networkEnabled ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
                lm.getCurrentLocation(provider, null, getMainExecutor(), location -> {
                    showBusy(false);
                    if (location == null) toast("没有获取到当前位置，请打开定位后重试");
                    else fetchWeatherAndOpen(location);
                });
            } else {
                String provider = networkEnabled ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
                lm.requestSingleUpdate(provider, new LocationListener() {
                    @Override public void onLocationChanged(Location location) {
                        showBusy(false);
                        if (location == null) toast("没有获取到当前位置，请打开定位后重试");
                        else fetchWeatherAndOpen(location);
                    }
                    @Override public void onProviderDisabled(String provider) { showBusy(false); }
                    @Override public void onProviderEnabled(String provider) { }
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
                }, null);
            }
        } catch (SecurityException e) {
            showBusy(false);
            toast("需要定位权限才能生成天气电台");
        } catch (Exception e) {
            showBusy(false);
            toast("当前位置读取失败");
        }
    }

    private Location bestLastLocation(LocationManager lm) {
        Location best = null;
        String[] providers = {LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER};
        for (String provider : providers) {
            try {
                Location loc = lm.getLastKnownLocation(provider);
                if (loc == null) continue;
                if (best == null || loc.getTime() > best.getTime()
                        || (loc.getTime() == best.getTime() && loc.getAccuracy() < best.getAccuracy())) best = loc;
            } catch (Exception ignored) { }
        }
        return best;
    }

    private void fetchWeatherAndOpen(Location location) {
        if (location == null || weatherMood == null) { toast("天气电台暂时不可用"); return; }
        final List<Song> favSnapshot = new ArrayList<>(favorites);
        final List<ImportedPlaylist> playlistSnapshot = new ArrayList<>(playlists);
        final List<Song> historySnapshot = new ArrayList<>(history);
        showBusy(true);
        io.submit(() -> {
            try {
                WeatherMoodProvider.WeatherSnapshot weather = weatherMood.fetch(location.getLatitude(), location.getLongitude());
                List<Song> songs = recommendations.weatherRadio(favSnapshot, playlistSnapshot, historySnapshot, weather, 24);
                main.post(() -> {
                    showBusy(false);
                    if (songs == null || songs.isEmpty()) { toast("天气电台暂时没有生成歌曲"); return; }
                    openSmartCollection = new SmartCollection("天气电台", weather.compactLabel() + " · " + songs.size() + " 首",
                            "weather", Ui.GOLD, songs);
                    renderTab();
                });
            } catch (Exception e) {
                main.post(() -> { showBusy(false); toast("天气获取失败，稍后再试"); });
            }
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults != null && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQ_WEATHER_LOCATION) {
            if (pendingWeatherRadioOpen) {
                pendingWeatherRadioOpen = false;
                if (granted) resolveLocationAndOpenWeatherRadio();
                else toast("未授予定位权限，天气电台不会读取你的位置");
            }
            return;
        }
        if (requestCode == REQ_DESKTOP_LYRIC_NOTIFICATIONS) {
            int mode = pendingDesktopLyricsNotificationMode;
            pendingDesktopLyricsNotificationMode = 0;
            if (mode == 3) {
                if (granted) {
                    if (playback != null) playback.refreshSystemSurface();
                    toast("系统通知已允许 · 媒体控制兼容性已增强");
                } else toast("通知仍未允许 · 可在系统通知设置中手动开启");
                main.postDelayed(this::showDesktopLyricSettings, 180L);
                return;
            }
            if (Settings.canDrawOverlays(this)) {
                DesktopLyricService.start(this);
                if (!granted && Build.VERSION.SDK_INT >= 33)
                    toast("桌面歌词已开启 · 通知未授权时部分厂商可能不显示系统媒体卡");
                else if (mode == 1) toast("桌面歌词已开启");
                if (mode == 2) main.postDelayed(this::showDesktopLyricSettings, 180L);
            }
            return;
        }
        if (requestCode == REQ_VOICE_AUDIO) {
            boolean wanted = pendingVoiceEnable;
            pendingVoiceEnable = false;
            if (wanted && granted) {
                VoiceAssistantContract.prefs(this).edit().putBoolean(VoiceAssistantContract.KEY_ENABLED, true).apply();
                startVoiceAssistant(false);
                toast("Lunaxy Voice 已开启 · " + VoiceAssistantContract.wakeLabel(this));
                main.postDelayed(this::showVoiceAssistantSettings, 220L);
            } else if (wanted) toast("未授予麦克风权限 · 语音唤醒不会启动");
            return;
        }
        if (requestCode == REQ_LOCAL_AUDIO) {
            boolean wanted = pendingLocalMusicScan; pendingLocalMusicScan = false;
            if (wanted && granted) scanLocalMusic();
            else if (wanted) toast("未授予音频访问权限 · 无法扫描本地音乐");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT_PLAYLIST) {
            ImportedPlaylist playlist = pendingPlaylistExport; pendingPlaylistExport = null;
            if (playlist != null) writePlaylistBundle(uri, playlist);
        } else if (requestCode == REQ_IMPORT_LUNAXY_PLAYLIST) {
            readPlaylistBundle(uri);
        }
    }

    private String greetingTitle() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 6) return "夜深了，听点喜欢的";
        if (hour < 12) return "早上好，今天听什么？";
        if (hour < 18) return "下午好，找首歌吧";
        return "晚上好，把时间交给音乐";
    }

    private View recentCarousel(List<Song> songs) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);
        row.setClipChildren(false);
        for (int i = 0; i < songs.size(); i++) {
            Song song = songs.get(i);
            LinearLayout card = Ui.column(this);
            card.setPadding(0, 0, 0, Ui.dp(this, 4));
            card.setClipChildren(false);
            card.setTag("lunaxy_recent_card");
            card.setClickable(true);
            card.setFocusable(true);
            Ui.applyRipple(card, Color.TRANSPARENT);
            card.setOnClickListener(v -> playPickedSong(song));

            FrameLayout artwork = new FrameLayout(this);
            artwork.setClipChildren(false);
            CoverAmbientDrawable ambient = new CoverAmbientDrawable(getResources().getDisplayMetrics().density);
            artwork.setBackground(ambient);

            ImageView cover = new ImageView(this);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(Ui.round(Color.rgb(22, 22, 28), 16, this));
            cover.setClipToOutline(true);
            FrameLayout.LayoutParams coverLp = Ui.frame(Ui.dp(this, 118), Ui.dp(this, 118), Gravity.CENTER);
            artwork.addView(cover, coverLp);
            card.addView(artwork, Ui.lp(Ui.dp(this, 124), Ui.dp(this, 124)));
            ImageLoader.load(song.coverUrl, cover, (bitmap, accentColor) -> ambient.setAccent(recentCoverAmbientAccent(bitmap, accentColor)));

            TextView title = Ui.text(this, song.title, 13, Ui.TEXT, true);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleP = Ui.lp(Ui.dp(this, 124), Ui.dp(this, 26));
            titleP.topMargin = Ui.dp(this, 8);
            card.addView(title, titleP);
            TextView artist = Ui.text(this, song.artist, 11, Ui.DIM, false);
            artist.setSingleLine(true);
            artist.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(artist, Ui.lp(Ui.dp(this, 124), Ui.dp(this, 24)));

            LinearLayout.LayoutParams cp = Ui.lp(Ui.dp(this, 124), Ui.dp(this, 182));
            if (i > 0) cp.leftMargin = Ui.dp(this, 12);
            row.addView(card, cp);
        }
        scroll.addView(row);
        installRecentCoverPhysics(scroll, row);
        return scroll;
    }

    private int recentCoverAmbientAccent(Bitmap bitmap, int fallbackAccent) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) return fallbackAccent;
        int stepX = Math.max(1, bitmap.getWidth() / 12);
        int stepY = Math.max(1, bitmap.getHeight() / 12);
        float saturation = 0f, value = 0f;
        int count = 0;
        float[] hsv = new float[3];
        for (int y = 0; y < bitmap.getHeight(); y += stepY) {
            for (int x = 0; x < bitmap.getWidth(); x += stepX) {
                Color.colorToHSV(bitmap.getPixel(x, y), hsv);
                saturation += hsv[1];
                value += hsv[2];
                count++;
            }
        }
        if (count <= 0) return fallbackAccent;
        saturation /= count;
        value /= count;
        if (saturation < .13f) {
            // Near-monochrome artwork: bright covers get warm grey; dark covers get quiet blue-grey.
            return value > .62f ? Color.rgb(184, 178, 172) : Color.rgb(111, 130, 149);
        }
        return Ui.mix(fallbackAccent, Color.rgb(150, 158, 170), .18f);
    }

    /** Very light cover-wall depth: center remains 1.0, side covers ease toward 0.965 with mild fade. */
    private void installRecentCoverPhysics(HorizontalScrollView scroll, LinearLayout row) {
        if (scroll == null || row == null) return;
        final android.view.ViewTreeObserver.OnScrollChangedListener listener = () -> {
            float viewportCenter = scroll.getScrollX() + scroll.getWidth() * .5f;
            float span = Math.max(Ui.dp(this, 170), scroll.getWidth() * .53f);
            for (int i = 0; i < row.getChildCount(); i++) {
                View card = row.getChildAt(i);
                if (!(card instanceof ViewGroup) || !"lunaxy_recent_card".equals(card.getTag()) || card.getWidth() <= 0) continue;
                ViewGroup group = (ViewGroup) card;
                if (group.getChildCount() == 0) continue;
                View artwork = group.getChildAt(0);
                float center = card.getLeft() + card.getWidth() * .5f;
                float depth = Math.min(1f, Math.abs(center - viewportCenter) / span);
                float scale = 1f - .035f * depth;
                artwork.setScaleX(scale);
                artwork.setScaleY(scale);
                artwork.setAlpha(1f - .10f * depth);
            }
        };
        scroll.getViewTreeObserver().addOnScrollChangedListener(listener);
        scroll.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { scroll.post(listener::onScrollChanged); }
            @Override public void onViewDetachedFromWindow(View v) {
                if (scroll.getViewTreeObserver().isAlive()) scroll.getViewTreeObserver().removeOnScrollChangedListener(listener);
            }
        });
        scroll.post(listener::onScrollChanged);
    }

    private View searchPage() {
        LinearLayout body = Ui.column(this);

        // Search field + independent action button. Suggestions are rendered in a compact
        // card directly under the field (not full screen), matching the requested layout.
        LinearLayout searchRow = Ui.row(this);

        FrameLayout field = new FrameLayout(this);
        field.setBackground(Ui.contentSurface(26, this));

        EditText input = new EditText(this);
        input.setHint("歌名、歌手或一句歌词…");
        input.setHintTextColor(Color.rgb(126, 126, 145));
        input.setTextColor(Ui.TEXT);
        input.setTextSize(15.5f);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 18), 0);
        input.setTypeface(Typeface.DEFAULT);
        if (!lastQuery.isEmpty()) { input.setText(lastQuery); input.setSelection(input.length()); }
        field.addView(input, Ui.frame(-1, -1, Gravity.FILL));
        searchRow.addView(field, new LinearLayout.LayoutParams(0, Ui.dp(this, 60), 1f));

        Space gap = new Space(this);
        searchRow.addView(gap, Ui.lp(Ui.dp(this, 10), 1));

        FrameLayout go = Ui.iconButton(this, IconView.Type.SEARCH, 60, Color.rgb(13, 18, 22), Ui.CYAN);
        go.setBackground(Ui.primaryFill(Ui.CYAN, 30, this));
        searchRow.addView(go, Ui.lp(Ui.dp(this, 60), Ui.dp(this, 60)));
        body.addView(searchRow, Ui.lp(-1, Ui.dp(this, 60)));

        LinearLayout suggestionHost = Ui.column(this);
        activeSearchSuggestions = suggestionHost;
        suggestionHost.setVisibility(View.GONE);
        LinearLayout.LayoutParams sgp = Ui.lp(-1, 0);
        sgp.topMargin = Ui.dp(this, 6);
        sgp.rightMargin = Ui.dp(this, 70); // same visual width as the text field above
        body.addView(suggestionHost, sgp);

        LinearLayout resultsHolder = Ui.column(this);
        LinearLayout.LayoutParams rh = marginTop(9);
        body.addView(resultsHolder, rh);

        Runnable doSearch = () -> {
            hideSearchSuggestions();
            performSearch(input.getText().toString(), resultsHolder);
        };
        go.setOnClickListener(v -> doSearch.run());
        input.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH) { doSearch.run(); return true; }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence text, int st, int before, int count) {
                scheduleSearchSuggestions(text == null ? "" : text.toString(), input, suggestionHost, resultsHolder);
            }
            @Override public void afterTextChanged(Editable e) { }
        });
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && input.length() > 0) scheduleSearchSuggestions(input.getText().toString(), input, suggestionHost, resultsHolder);
            else if (!hasFocus) main.postDelayed(this::hideSearchSuggestions, 160);
        });

        final String pending = pendingSearchQuery == null ? "" : pendingSearchQuery.trim();
        if (!pending.isEmpty()) {
            pendingSearchQuery = "";
            if (!pending.contentEquals(input.getText())) {
                input.setText(pending);
                input.setSelection(input.length());
            }
        }

        View historyStrip = searchHistoryStrip(input, resultsHolder);
        if (historyStrip != null) {
            LinearLayout.LayoutParams hp = marginTop(12);
            body.addView(historyStrip, Math.max(0, body.getChildCount() - 1), hp);
        }

        if (!pending.isEmpty()) {
            // Artist-link navigation should land directly in a real search state without flashing
            // the previous query's rows or forcing keyboard focus.
            doSearch.run();
        } else if (!searchResults.isEmpty()) renderSearchResults(resultsHolder);
        else {
            // V92.9.6: Search is already self-explanatory from the field itself. Keep recent
            // searches and recent playback contiguous instead of inserting a large decorative
            // empty-state island between them. This keeps useful music above the fold.
            if (!history.isEmpty()) {
                resultsHolder.addView(songList(history.subList(0, Math.min(6, history.size())), history), marginTop(1));
            } else {
                resultsHolder.addView(compactEmpty("最近播放会出现在这里"), marginTop(10));
            }
        }
        FrameLayout engine = engineInsightAction(Ui.CYAN, "搜索引擎洞察",
                this::showSearchQuickInsight, this::showSearchPerformanceMatrix);
        return pageScaffold("发现音乐", null, body, engine);
    }

    private void scheduleSearchSuggestions(String raw, EditText input, LinearLayout host, LinearLayout resultsHolder) {
        final String query = raw == null ? "" : raw.trim();
        cancelPlaybackErrorAutoSkip();
        if (pendingSuggestionRunnable != null) main.removeCallbacks(pendingSuggestionRunnable);
        if (activeSuggestionJob != null && !activeSuggestionJob.isDone()) activeSuggestionJob.cancel(true);
        final int token = ++suggestionRequestToken;
        if (query.isEmpty()) {
            hideSearchSuggestions();
            return;
        }

        // First paint is purely local. Recent searches appear without waiting on any provider.
        List<String> localLabels = buildSearchSuggestionLabels(query, new ArrayList<>(), new ArrayList<>());
        if (!localLabels.isEmpty()) renderSearchSuggestions(localLabels, query, input, host, resultsHolder);

        pendingSuggestionRunnable = () -> {
            activeSuggestionJob = io.submit(() -> {
                List<Song> wy = new ArrayList<>();
                List<Song> tx = new ArrayList<>();
                try {
                    if (!Thread.currentThread().isInterrupted()) wy = netease.search(query, 10);
                    if (!Thread.currentThread().isInterrupted()) tx = qq.search(query, 10);
                } catch (Exception ignored) { }
                if (Thread.currentThread().isInterrupted()) return;
                final List<String> labels = buildSearchSuggestionLabels(query, wy, tx);
                main.post(() -> {
                    if (token != suggestionRequestToken || host != activeSearchSuggestions || isFinishing()) return;
                    renderSearchSuggestions(labels, query, input, host, resultsHolder);
                });
            });
        };
        // Slightly longer than a keystroke burst, but local history is already visible immediately.
        main.postDelayed(pendingSuggestionRunnable, 360L);
    }

    private List<String> buildSearchSuggestionLabels(String query, List<Song> wy, List<Song> tx) {
        String needle = normalizeSuggestion(query);
        if (needle.isEmpty()) return new ArrayList<>();
        LinkedHashSet<String> starts = new LinkedHashSet<>();
        LinkedHashSet<String> contains = new LinkedHashSet<>();
        if (searchHistory != null) {
            for (String h : searchHistory) addSuggestionCandidate(h, needle, starts, contains);
        }
        List<Song> all = new ArrayList<>();
        if (wy != null) all.addAll(wy);
        if (tx != null) all.addAll(tx);
        // Artist suggestions first: typing “林” should naturally surface 林俊杰/林忆莲 etc.
        for (Song song : all) {
            if (song == null) continue;
            String[] artists = song.artist.split("[/、&，,\u00B7·]+");
            for (String artist : artists) addSuggestionCandidate(artist, needle, starts, contains);
        }
        for (Song song : all) if (song != null) addSuggestionCandidate(song.title, needle, starts, contains);
        ArrayList<String> out = new ArrayList<>(12);
        for (String x : starts) { if (out.size() >= 12) break; out.add(x); }
        for (String x : contains) { if (out.size() >= 12) break; if (!out.contains(x)) out.add(x); }
        return out;
    }

    private void addSuggestionCandidate(String raw, String needle, LinkedHashSet<String> starts, LinkedHashSet<String> contains) {
        if (raw == null) return;
        String value = raw.trim();
        if (value.isEmpty() || value.length() > 32) return;
        String normalized = normalizeSuggestion(value);
        if (normalized.isEmpty() || !normalized.contains(needle)) return;
        if (normalized.startsWith(needle)) starts.add(value); else contains.add(value);
    }

    private String normalizeSuggestion(String text) {
        return (text == null ? "" : text.toLowerCase(Locale.ROOT))
                .replaceAll("[\\s\\p{Punct}，。！？：；、“”‘’《》【】（）()]+", "");
    }

    private void renderSearchSuggestions(List<String> labels, String query, EditText input, LinearLayout host, LinearLayout resultsHolder) {
        host.removeAllViews();
        if (labels == null || labels.isEmpty() || query == null || query.trim().isEmpty()) {
            host.setVisibility(View.GONE);
            ViewGroup.LayoutParams p = host.getLayoutParams();
            if (p != null) { p.height = 0; host.setLayoutParams(p); }
            return;
        }
        LinearLayout rows = Ui.column(this);
        rows.setPadding(Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5));
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            LinearLayout row = Ui.row(this);
            row.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
            row.setClickable(true); row.setFocusable(true);
            row.setBackground(i == 0 ? Ui.round(Color.argb(18, 123, 211, 255), 13, this) : Ui.round(Color.TRANSPARENT, 13, this));
            Ui.applyRipple(row, Color.TRANSPARENT);
            IconView icon = new IconView(this, IconView.Type.SEARCH, i == 0 ? Ui.CYAN : Ui.DIM);
            row.addView(icon, Ui.lp(Ui.dp(this, 24), Ui.dp(this, 24)));
            TextView text = Ui.text(this, label, 13.2f, Ui.TEXT_2, false);
            text.setSingleLine(true); text.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, Ui.dp(this, 43), 1f);
            tp.leftMargin = Ui.dp(this, 9);
            row.addView(text, tp);
            row.setOnClickListener(v -> {
                input.setText(label); input.setSelection(label.length());
                hideSearchSuggestions();
                performSearch(label, resultsHolder);
            });
            rows.addView(row, Ui.lp(-1, Ui.dp(this, 43)));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        scroll.addView(rows);
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.glass(238, 18, 26, this));
        card.setElevation(Ui.dp(this, 12));
        card.addView(scroll, Ui.frame(-1, -1, Gravity.FILL));
        int max = Math.max(Ui.dp(this, 132), getResources().getDisplayMetrics().heightPixels / 3);
        int wanted = Math.min(max, Ui.dp(this, 10 + labels.size() * 43));
        host.addView(card, Ui.lp(-1, wanted));
        host.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams p = host.getLayoutParams();
        if (p != null) { p.height = wanted; host.setLayoutParams(p); }
        host.setAlpha(0f); host.setTranslationY(-Ui.dp(this, 6));
        host.animate().alpha(1f).translationY(0f).setDuration(150).start();
    }

    private void hideSearchSuggestions() {
        suggestionRequestToken++;
        if (pendingSuggestionRunnable != null) { main.removeCallbacks(pendingSuggestionRunnable); pendingSuggestionRunnable = null; }
        if (activeSearchSuggestions == null) return;
        LinearLayout host = activeSearchSuggestions;
        host.animate().cancel();
        host.setVisibility(View.GONE);
        host.removeAllViews();
        ViewGroup.LayoutParams p = host.getLayoutParams();
        if (p != null) { p.height = 0; host.setLayoutParams(p); }
    }

    private View searchHistoryStrip(EditText input, LinearLayout resultsHolder) {
        if (searchHistory == null || searchHistory.isEmpty()) return null;
        LinearLayout wrap = Ui.column(this);
        LinearLayout head = Ui.row(this);
        TextView title = Ui.text(this, "最近搜索", 11.8f, Ui.TEXT_2, true);
        head.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 28), 1f));
        TextView clear = Ui.text(this, "清空", 10.8f, Ui.DIM, false);
        clear.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        clear.setClickable(true); clear.setFocusable(true); Ui.applyRipple(clear, Color.TRANSPARENT);
        clear.setOnClickListener(v -> { searchHistory = store.clearSearchHistory(); renderTab(); });
        head.addView(clear, Ui.lp(Ui.dp(this, 54), Ui.dp(this, 28)));
        wrap.addView(head, Ui.lp(-1, Ui.dp(this, 28)));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(this);
        int limit = Math.min(12, searchHistory.size());
        for (int i = 0; i < limit; i++) {
            String q = searchHistory.get(i);
            TextView chip = Ui.text(this, q, 11.2f, Ui.TEXT_2, false);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true); chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setPadding(Ui.dp(this, 13), 0, Ui.dp(this, 13), 0);
            chip.setBackground(Ui.glass(92, 15, 18, this));
            chip.setClickable(true); chip.setFocusable(true); Ui.applyRipple(chip, Color.TRANSPARENT);
            chip.setOnClickListener(v -> {
                input.setText(q); input.setSelection(q.length());
                performSearch(q, resultsHolder);
            });
            LinearLayout.LayoutParams cp = Ui.lp(-2, Ui.dp(this, 34));
            if (i > 0) cp.leftMargin = Ui.dp(this, 8);
            row.addView(chip, cp);
        }
        scroll.addView(row);
        LinearLayout.LayoutParams sp = Ui.lp(-1, Ui.dp(this, 38)); sp.topMargin = Ui.dp(this, 4);
        wrap.addView(scroll, sp);

        // Beta V8: give recent history its own visual section before the primary result tabs.
        View divider = new View(this);
        divider.setBackgroundColor(AppearanceSystem.isLight()
                ? Color.argb(24, 70, 78, 96) : Color.argb(24, 255, 255, 255));
        LinearLayout.LayoutParams dp = Ui.lp(-1, Ui.dp(this, 1));
        dp.topMargin = Ui.dp(this, 14);
        wrap.addView(divider, dp);
        return wrap;
    }

    private View searchIntro() {
        LinearLayout empty = Ui.column(this);
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        empty.setPadding(Ui.dp(this, 10), Ui.dp(this, 64), Ui.dp(this, 10), Ui.dp(this, 30));

        TextView dots = Ui.text(this, "•  •  •", 18, Ui.PURPLE, true);
        dots.setGravity(Gravity.CENTER);
        dots.setLetterSpacing(.08f);
        empty.addView(dots, Ui.lp(-1, Ui.dp(this, 36)));

        TextView title = Ui.text(this, "在星空里找到想听的歌", 17.5f, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        empty.addView(title, Ui.lp(-1, Ui.dp(this, 36)));

        TextView sub = Ui.text(this, "输入歌名、歌手或记得的一句歌词", 12.2f, Ui.TEXT_2, false);
        sub.setGravity(Gravity.CENTER);
        sub.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        sub.setMaxLines(2);
        empty.addView(sub, Ui.lp(-1, Ui.dp(this, 48)));
        return empty;
    }

    private static final class SearchOutcome {
        final List<Song> songs; final String error; final int total;
        SearchOutcome(List<Song> songs, String error, int total) {
            this.songs = songs;
            this.error = error == null ? "" : error;
            this.total = Math.max(0, total);
        }
    }

    private void performSearch(String q, LinearLayout holder) {
        final String keyword = q == null ? "" : q.trim();
        if (keyword.isEmpty()) { toast("输入歌曲、歌手或一句歌词"); return; }
        final int requestToken = ++searchRequestToken;
        cancelActiveSearchNetworkJobs();
        if (activeSuggestionJob != null && !activeSuggestionJob.isDone()) activeSuggestionJob.cancel(true);
        if (pendingSearchMergeRunnable != null) main.removeCallbacks(pendingSearchMergeRunnable);
        if (pendingSearchEnrichmentRunnable != null) main.removeCallbacks(pendingSearchEnrichmentRunnable);
        if (pendingLyricSearchRunnable != null) main.removeCallbacks(pendingLyricSearchRunnable);
        if (pendingLyricUiRefreshRunnable != null) main.removeCallbacks(pendingLyricUiRefreshRunnable);
        if (pendingLyricUiDrainRunnable != null) main.removeCallbacks(pendingLyricUiDrainRunnable);
        pendingSearchMergeRunnable = null;
        pendingLyricSearchRunnable = null;
        pendingLyricUiRefreshRunnable = null;
        pendingLyricUiDrainRunnable = null;
        pendingSearchEnrichmentRunnable = null;
        activeLyricSectionUi = null;
        lyricFirstUiPaintDelivered = false;
        lyricFirstVerifiedPaintDelivered = false;
        lyricUiVisibleLimit = 0;
        searchSnapshotVersion = 0;
        searchFirstPaintDelivered = false;
        searchFirstMergeInFlight = false;
        if (!keyword.equals(lastQuery)) {
            searchAllSavedPosition = 0;
            searchAllSavedTop = 0;
            rootTabSearchAllResultsOpen[1] = false;
            rootTabDetailScrollY[1] = 0;
        }
        lastQuery = keyword;
        searchSourceFilter = "";
        searchAllResultsOpen = false;
        searchAutoLoadNotBeforeMs = 0L;
        lyricSearchResults = new ArrayList<>();
        searchMetadataExpanded = true;
        searchLyricsExpanded = true;
        searchResultMode = 0;
        searchIntentMode = inferSearchIntent(keyword);
        lyricSearchStarted = false;
        lyricSearchPending = false;
        lyricVerificationPending = false;
        lyricIndexJobs = 0;
        lyricVerificationJobs = 0;
        lyricCatalogFallbackStarted = false;
        searchWyFinished = false;
        searchTxFinished = false;
        searchMetadataPending = true;
        searchResults = new ArrayList<>();
        lastWySearchSongs = new ArrayList<>(); lastTxSearchSongs = new ArrayList<>();
        lastKwSearchSongs = new ArrayList<>(); lastKgSearchSongs = new ArrayList<>();
        searchMoreLoading = false;
        searchWyHasMore = false; searchTxHasMore = false; searchKwHasMore = false; searchKgHasMore = false;
        searchWyNextOffset = SEARCH_PAGE_SIZE; searchTxNextPage = 2; searchKwNextPage = 2; searchKgNextPage = 2;
        lastWyCount = lastTxCount = lastKwCount = lastKgCount = 0;
        lastWyError = lastTxError = lastKwError = lastKgError = "";
        searchHistory = store.addSearchHistory(keyword);
        searchStartedAtMs = SystemClock.elapsedRealtime();
        searchCachePaintMs = 0L; searchFirstNetworkMs = 0L; searchStableMs = 0L;
        searchCacheHit = false; currentSearchLatencyMs.clear();
        lastSearchContentSignature = "";

        // Local layer first: a cached canonical snapshot is rendered synchronously from in-memory
        // SharedPreferences, then every provider is refreshed in the background. Cached metadata
        // never contains a playback URL and can therefore safely survive provider URL expiry.
        SearchCacheStore.Entry cached = searchCache == null ? null : searchCache.get(keyword);
        if (cached != null && cached.songs != null && !cached.songs.isEmpty()) {
            searchCacheHit = true;
            searchResults = new ArrayList<>(cached.songs);
            seedCatalogRowsFromCanonicalCache(cached.songs);
            lastWyCount = cached.totals.getOrDefault("wy", lastWySearchSongs.size());
            lastTxCount = cached.totals.getOrDefault("tx", lastTxSearchSongs.size());
            lastKwCount = cached.totals.getOrDefault("kw", lastKwSearchSongs.size());
            lastKgCount = cached.totals.getOrDefault("kg", lastKgSearchSongs.size());
            searchFirstPaintDelivered = true;
            searchCachePaintMs = Math.max(1L, SystemClock.elapsedRealtime() - searchStartedAtMs);
        }

        showBusy(false);
        renderSearchResults(holder);

        // Lyric work is no longer on the critical path for ordinary metadata searches. Sentence-like
        // lyric intent starts immediately; mixed queries wait and only start if metadata is weak.
        if (searchIntentMode == SEARCH_INTENT_LYRIC) {
            startLyricSearchOnDemand(keyword, requestToken, holder);
        } else if (searchIntentMode == SEARCH_INTENT_MIXED) {
            pendingLyricSearchRunnable = () -> {
                if (requestToken != searchRequestToken || isFinishing() || lyricSearchStarted) return;
                if (visibleSearchSongs().size() < 6 || !hasStrongMetadataMatch(keyword, visibleSearchSongs()))
                    startLyricSearchOnDemand(keyword, requestToken, holder);
            };
            main.postDelayed(pendingLyricSearchRunnable, 900L);
        }

        final java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(4);
        List<String> order = searchPerformance == null ? java.util.Arrays.asList("wy", "tx", "kg", "kw") : searchPerformance.orderedSources();
        ArrayList<String> fast = new ArrayList<>(), enrichment = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) { if (i < 2) fast.add(order.get(i)); else enrichment.add(order.get(i)); }
        launchCatalogWave(fast, requestToken, keyword, holder, remaining);
        pendingSearchEnrichmentRunnable = () -> launchCatalogWave(enrichment, requestToken, keyword, holder, remaining);
        main.postDelayed(pendingSearchEnrichmentRunnable, 420L);
    }

    private void seedCatalogRowsFromCanonicalCache(List<Song> cached) {
        if (cached == null) return;
        for (Song song : cached) {
            if (song == null) continue;
            if (song.variant("wy") != null) lastWySearchSongs.add(song.preferSource("wy"));
            if (song.variant("tx") != null) lastTxSearchSongs.add(song.preferSource("tx"));
            if (song.variant("kw") != null) lastKwSearchSongs.add(song.preferSource("kw"));
            if (song.variant("kg") != null) lastKgSearchSongs.add(song.preferSource("kg"));
        }
    }

    private void launchCatalogWave(List<String> sources, int requestToken, String keyword, LinearLayout holder,
                                   java.util.concurrent.atomic.AtomicInteger remaining) {
        if (sources == null || sources.isEmpty() || requestToken != searchRequestToken) return;
        for (String source : sources) {
            if ("wy".equals(source)) submitCatalogSearch("wy", requestToken, keyword, holder, remaining, () -> {
                try { List<Song> songs = netease.search(keyword, SEARCH_PAGE_SIZE); return new SearchOutcome(songs, "", Math.max(songs.size(), netease.lastSearchTotal())); }
                catch (Exception e) { return new SearchOutcome(new ArrayList<>(), safeMessage(e), 0); }
            });
            else if ("tx".equals(source)) submitCatalogSearch("tx", requestToken, keyword, holder, remaining, () -> {
                try { List<Song> songs = qq.search(keyword, SEARCH_PAGE_SIZE); return new SearchOutcome(songs, "", Math.max(songs.size(), qq.lastSearchTotal())); }
                catch (Exception e) { return new SearchOutcome(new ArrayList<>(), safeMessage(e), 0); }
            });
            else if ("kw".equals(source)) submitCatalogSearch("kw", requestToken, keyword, holder, remaining, () -> {
                try { List<Song> songs = kuwo.search(keyword, SEARCH_PAGE_SIZE); return new SearchOutcome(songs, "", Math.max(songs.size(), kuwo.lastSearchTotal())); }
                catch (Exception e) { return new SearchOutcome(new ArrayList<>(), safeMessage(e), 0); }
            });
            else if ("kg".equals(source)) submitCatalogSearch("kg", requestToken, keyword, holder, remaining, () -> {
                try { List<Song> songs = kugou.search(keyword, SEARCH_PAGE_SIZE); return new SearchOutcome(songs, "", Math.max(songs.size(), kugou.lastSearchTotal())); }
                catch (Exception e) { return new SearchOutcome(new ArrayList<>(), safeMessage(e), 0); }
            });
        }
    }

    private void cancelActiveSearchNetworkJobs() {
        synchronized (activeSearchNetworkJobs) {
            for (Future<?> f : activeSearchNetworkJobs) if (f != null && !f.isDone()) f.cancel(true);
            activeSearchNetworkJobs.clear();
        }
    }

    private void startLyricSearchOnDemand(String keyword, int requestToken, LinearLayout holder) {
        if (lyricSearchStarted || requestToken != searchRequestToken || keyword == null || keyword.trim().isEmpty()) return;
        lyricSearchStarted = true;
        lyricSearchPending = true;
        lyricIndexJobs = 2;
        lyricVerificationPending = false;
        lyricCatalogFallbackStarted = false;
        if (pendingLyricSearchRunnable != null) main.removeCallbacks(pendingLyricSearchRunnable);
        scheduleLyricSearch(keyword, requestToken, holder);
        renderSearchResults(holder);
    }

    private void scheduleLyricSearch(String keyword, int requestToken, LinearLayout holder) {
        if (requestToken != searchRequestToken || isFinishing() || !lyricSearchStarted) return;
        // V87: once intent/on-demand logic admits lyric discovery, start it immediately on its
        // isolated executors. The expensive decision is made before this method, not with another
        // arbitrary timer inside the lyric pipeline.
        submitNeteaseLyricIndex(keyword, requestToken, holder);
        submitQQLyricIndex(keyword, requestToken, holder);
    }

    private void submitNeteaseLyricIndex(String keyword, int requestToken, LinearLayout holder) {
        try {
            lyricIndexExecutor.submit(() -> {
                List<NeteaseLyricSearchApi.Hit> hits;
                try {
                    if (requestToken != searchRequestToken || Thread.currentThread().isInterrupted()) hits = new ArrayList<>();
                    else hits = neteaseLyricSearch.search(keyword, LYRIC_INDEX_LIMIT);
                } catch (Exception ignored) { hits = new ArrayList<>(); }
                final List<LyricSearchResult> batch = mergeLyricHits(keyword, hits, new ArrayList<>(), LYRIC_INDEX_LIMIT);
                main.post(() -> onLyricIndexFinished(keyword, requestToken, holder, batch));
            });
        } catch (RejectedExecutionException ignored) {
            main.post(() -> onLyricIndexFinished(keyword, requestToken, holder, new ArrayList<>()));
        }
    }

    private void submitQQLyricIndex(String keyword, int requestToken, LinearLayout holder) {
        try {
            lyricIndexExecutor.submit(() -> {
                List<QQMusicLyricSearchApi.Hit> hits;
                try {
                    if (requestToken != searchRequestToken || Thread.currentThread().isInterrupted()) hits = new ArrayList<>();
                    else hits = qqLyricSearch.search(keyword, LYRIC_INDEX_LIMIT);
                } catch (Exception ignored) { hits = new ArrayList<>(); }
                final List<LyricSearchResult> batch = mergeLyricHits(keyword, new ArrayList<>(), hits, LYRIC_INDEX_LIMIT);
                main.post(() -> onLyricIndexFinished(keyword, requestToken, holder, batch));
            });
        } catch (RejectedExecutionException ignored) {
            main.post(() -> onLyricIndexFinished(keyword, requestToken, holder, new ArrayList<>()));
        }
    }

    private void onLyricIndexFinished(String keyword, int requestToken, LinearLayout holder,
                                      List<LyricSearchResult> batch) {
        if (requestToken != searchRequestToken || isFinishing()) return;
        lyricIndexJobs = Math.max(0, lyricIndexJobs - 1);
        boolean firstVisibleBatch = false;
        if (batch != null && !batch.isEmpty()) {
            firstVisibleBatch = lyricSearchResults.isEmpty();
            lyricSearchResults = mergeLyricResultLists(keyword, lyricSearchResults, batch, LYRIC_INDEX_LIMIT);
            // Index snippets already carry remote song identities. Avoid the expensive full remote-catalog
            // enrichment pass on the main thread for every returning source; the final catalog settle does
            // one deferred enrichment pass instead.
            scheduleLyricVerification(keyword, requestToken, holder, batch);
        }
        // If the dedicated lyric indexes already produced usable excerpts, do not flood the LRC
        // verifier with a second batch of generic catalog candidates. The catalog path is a fallback,
        // not a competitor to the fast V9-style index path.
        if (lyricIndexJobs == 0 && !lyricSearchResults.isEmpty()) lyricCatalogFallbackStarted = true;
        else maybeStartRemoteCatalogLyricFallback(keyword, requestToken, holder);
        refreshLyricDiscoveryPending();
        if (firstVisibleBatch && !lyricFirstUiPaintDelivered) {
            lyricFirstUiPaintDelivered = true;
            lyricUiVisibleLimit = Math.min(LYRIC_UI_BATCH_SIZE, visibleLyricSearchResults().size());
            requestLyricUiRefresh(holder, true);
        } else {
            requestLyricUiRefresh(holder, false);
        }
    }

    private void refreshLyricDiscoveryPending() {
        // "No lyric hit" is only legal after BOTH dedicated remote indexes have actually finished
        // and the normal WY/TX remote-search fallback has been considered. A timer must never turn
        // a still-queued/still-running search into a false negative.
        lyricSearchPending = lyricIndexJobs > 0 || !lyricCatalogFallbackStarted;
    }

    private void scheduleLyricVerification(String keyword, int requestToken, LinearLayout holder,
                                           List<LyricSearchResult> seed) {
        if (seed == null || seed.isEmpty() || requestToken != searchRequestToken || isFinishing()) return;
        final List<LyricSearchResult> snapshot = new ArrayList<>(seed);
        final int verifyCount = Math.min(LYRIC_VERIFY_LIMIT, snapshot.size());
        if (verifyCount <= 0) return;

        lyricVerificationJobs++;
        lyricVerificationPending = true;
        final java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(verifyCount);

        // V82/V14: each verification job goes directly to the lyric verifier pool. There is no
        // shared-io coordinator and no batch-wide timeout that can hide an already completed hit.
        for (int i = 0; i < verifyCount; i++) {
            final LyricSearchResult hit = snapshot.get(i);
            try {
                lyricSearchExecutor.submit(() -> {
                    LyricSearchResult result;
                    try { result = verifyLyricHit(keyword, hit); }
                    catch (Exception ignored) { result = hit; }
                    final LyricSearchResult verified = result;
                    main.post(() -> {
                        if (requestToken != searchRequestToken || isFinishing()) return;
                        if (verified != null && verified.song != null && verified.snippet != null
                                && !verified.snippet.trim().isEmpty()) {
                            lyricSearchResults = mergeLyricResultLists(keyword, lyricSearchResults,
                                    java.util.Collections.singletonList(verified), LYRIC_INDEX_LIMIT);
                            // First exact result paints immediately. Later verifier callbacks are coalesced
                            // into short UI batches so twelve parallel LRC jobs cannot rebuild the page twelve times.
                            boolean immediate = verified.verified && !lyricFirstVerifiedPaintDelivered;
                            if (immediate) lyricFirstVerifiedPaintDelivered = true;
                            requestLyricUiRefresh(holder, immediate);
                        }
                        if (remaining.decrementAndGet() == 0) finishLyricVerificationBatch(keyword, requestToken, holder);
                    });
                });
            } catch (RejectedExecutionException ignored) {
                if (remaining.decrementAndGet() == 0) finishLyricVerificationBatch(keyword, requestToken, holder);
            }
        }
    }

    private void finishLyricVerificationBatch(String keyword, int requestToken, LinearLayout holder) {
        if (requestToken != searchRequestToken || isFinishing()) return;
        lyricVerificationJobs = Math.max(0, lyricVerificationJobs - 1);
        lyricVerificationPending = lyricVerificationJobs > 0;
        if (!lyricVerificationPending && !lyricSearchPending) {
            ArrayList<LyricSearchResult> cleaned = new ArrayList<>();
            for (LyricSearchResult hit : lyricSearchResults) {
                if (hit != null && hit.song != null && hit.snippet != null && !hit.snippet.trim().isEmpty()) cleaned.add(hit);
            }
            lyricSearchResults = cleaned;
        }
        if (searchResultMode == 2 && lyricSearchResults.isEmpty() && !lyricSearchPending && !lyricVerificationPending) {
            renderSearchResults(holder);
        } else {
            requestLyricUiRefresh(holder, false);
        }
    }

    private LyricSearchResult verifyLyricHit(String keyword, LyricSearchResult hit) {
        if (hit == null || hit.song == null) return hit;
        String needle = normalizeSuggestion(keyword);
        if (needle.length() < 3) return hit;
        Song song = hit.song;
        String cached = lyricCache == null ? "" : lyricCache.get(song);
        String context = matchingLyricContext(cached, needle);
        if (!context.isEmpty()) return new LyricSearchResult(song, context, true, hit.evidenceMask | 1);

        com.xingyu.music.model.SourceVariant wy = song.variant("wy");
        if (wy != null && !wy.sourceId.isEmpty()) {
            try {
                String lrc = netease.lyricData(wy.sourceId).lrc;
                if (lyricCache != null && lrc != null && !lrc.trim().isEmpty()) lyricCache.put(song, lrc);
                context = matchingLyricContext(lrc, needle);
                if (!context.isEmpty()) return new LyricSearchResult(song, context, true, hit.evidenceMask | 2);
            } catch (Exception ignored) { }
        }
        com.xingyu.music.model.SourceVariant tx = song.variant("tx");
        if (tx != null && !tx.sourceId.isEmpty()) {
            try {
                String lrc = qqLyrics.lyric(tx.sourceId);
                if (lyricCache != null && lrc != null && !lrc.trim().isEmpty()) lyricCache.put(song, lrc);
                context = matchingLyricContext(lrc, needle);
                if (!context.isEmpty()) return new LyricSearchResult(song, context, true, hit.evidenceMask | 4);
            } catch (Exception ignored) { }
        }
        return hit;
    }

    private void submitCatalogSearch(String source, int requestToken, String keyword, LinearLayout holder,
                                     java.util.concurrent.atomic.AtomicInteger remaining,
                                     java.util.concurrent.Callable<SearchOutcome> call) {
        final long started = SystemClock.elapsedRealtime();
        Future<?> future = io.submit(() -> {
            SearchOutcome outcome;
            try {
                if (Thread.currentThread().isInterrupted() || requestToken != searchRequestToken) return;
                outcome = call.call();
            } catch (Exception e) { outcome = new SearchOutcome(new ArrayList<>(), safeMessage(e), 0); }
            final SearchOutcome result = outcome;
            final long latency = Math.max(1L, SystemClock.elapsedRealtime() - started);
            if (searchPerformance != null) searchPerformance.record(source, latency,
                    result.error == null || result.error.isEmpty(), result.songs == null ? 0 : result.songs.size());
            main.post(() -> {
                if (requestToken != searchRequestToken || isFinishing()) return;
                currentSearchLatencyMs.put(source, latency);
                if (searchFirstNetworkMs <= 0L && result.songs != null && !result.songs.isEmpty())
                    searchFirstNetworkMs = Math.max(1L, SystemClock.elapsedRealtime() - searchStartedAtMs);
                applyCatalogOutcome(source, result);
                if ("wy".equals(source)) searchWyFinished = true;
                if ("tx".equals(source)) searchTxFinished = true;
                maybeStartRemoteCatalogLyricFallback(keyword, requestToken, holder);
                boolean finished = remaining.decrementAndGet() <= 0;
                scheduleSearchMerge(keyword, requestToken, holder, finished);
            });
        });
        synchronized (activeSearchNetworkJobs) { activeSearchNetworkJobs.add(future); }
    }

    private void scheduleSearchMerge(String keyword, int requestToken, LinearLayout holder, boolean finished) {
        final int snapshotVersion = ++searchSnapshotVersion;
        final Map<String,List<Song>> catalogs = snapshotSearchCatalogs();
        // Once the user has a usable first paint, keep those rows physically stable. Capture that
        // head when the coalesced merge actually launches, not when it is scheduled: a second
        // provider can return while the first merge is still in flight, and an early empty snapshot
        // would otherwise re-rank/flash the rows that just became visible.
        Runnable launch = () -> {
            List<Song> frozenHead = searchFirstPaintDelivered ? new ArrayList<>(searchResults) : new ArrayList<>();
            launchSearchMerge(keyword, requestToken, holder, finished, snapshotVersion, catalogs, frozenHead, false);
        };
        if (!searchFirstPaintDelivered && !searchFirstMergeInFlight) {
            searchFirstMergeInFlight = true;
            launchSearchMerge(keyword, requestToken, holder, finished, snapshotVersion, catalogs, new ArrayList<>(), true);
            return;
        }
        if (pendingSearchMergeRunnable != null) main.removeCallbacks(pendingSearchMergeRunnable);
        pendingSearchMergeRunnable = launch;
        main.postDelayed(launch, SEARCH_MERGE_COALESCE_MS);
    }

    private void launchSearchMerge(String keyword, int requestToken, LinearLayout holder, boolean finished,
                                   int snapshotVersion, Map<String,List<Song>> catalogs, List<Song> frozenHead, boolean firstLaunch) {
        searchMergeExecutor.submit(() -> {
            final List<Song> merged = frozenHead != null && !frozenHead.isEmpty()
                    ? appendSearchPageStable(keyword, frozenHead, catalogs)
                    : mergeSearchSnapshot(keyword, catalogs);
            main.post(() -> {
                if (firstLaunch) searchFirstMergeInFlight = false;
                if (requestToken != searchRequestToken || isFinishing()) return;
                boolean stale = snapshotVersion != searchSnapshotVersion;
                // A stale first-catalog snapshot is still valuable as the first usable paint. Once
                // anything is visible, only the newest snapshot may replace it.
                if (stale && searchFirstPaintDelivered) return;
                searchResults = merged;
                if (!merged.isEmpty()) searchFirstPaintDelivered = true;
                boolean latestFinished = finished && !stale;
                if (latestFinished) {
                    searchMetadataPending = false;
                    searchStableMs = Math.max(1L, SystemClock.elapsedRealtime() - searchStartedAtMs);
                    persistSearchVariantsAsync(merged);
                    if (searchCache != null && !merged.isEmpty()) {
                        LinkedHashMap<String,Integer> totals = new LinkedHashMap<>();
                        totals.put("wy", lastWyCount); totals.put("tx", lastTxCount); totals.put("kw", lastKwCount); totals.put("kg", lastKgCount);
                        final List<Song> cacheSongs = new ArrayList<>(merged);
                        final String cacheQuery = keyword;
                        io.submit(() -> searchCache.put(cacheQuery, cacheSongs, totals));
                    }
                }
                renderSearchResults(holder);
                if (latestFinished && !lyricSearchResults.isEmpty())
                    scheduleLyricIdentityEnrichment(keyword, requestToken, holder);
            });
        });
    }

    private Map<String,List<Song>> snapshotSearchCatalogs() {
        Map<String,List<Song>> catalogs = new LinkedHashMap<>();
        catalogs.put("wy", new ArrayList<>(lastWySearchSongs));
        catalogs.put("tx", new ArrayList<>(lastTxSearchSongs));
        catalogs.put("kw", new ArrayList<>(lastKwSearchSongs));
        catalogs.put("kg", new ArrayList<>(lastKgSearchSongs));
        return catalogs;
    }

    private List<Song> mergeSearchSnapshot(String keyword, Map<String,List<Song>> catalogs) {
        int count = 0;
        if (catalogs != null) for (List<Song> songs : catalogs.values()) if (songs != null) count += songs.size();
        return SearchRanker.mergeAndRank(keyword, catalogs, Math.max(50, count));
    }

    private void persistSearchVariantsAsync(List<Song> songs) {
        if (trackVariantStore == null || songs == null || songs.isEmpty()) return;
        final List<Song> copy = new ArrayList<>(songs);
        try {
            variantPrefetch.execute(() -> {
                for (Song song : copy) {
                    if (Thread.currentThread().isInterrupted()) return;
                    trackVariantStore.remember(song);
                }
            });
        } catch (RejectedExecutionException ignored) { }
    }

    private void applyCatalogOutcome(String source, SearchOutcome result) {
        List<Song> songs = new ArrayList<>(result == null ? new ArrayList<>() : result.songs);
        int total = result == null ? 0 : result.total;
        String error = result == null ? "unknown" : result.error;
        if ("wy".equals(source)) {
            lastWySearchSongs = songs; lastWyCount = total; lastWyError = error;
            searchWyNextOffset = songs.size(); searchWyHasMore = providerHasMore(songs.size(), total, SEARCH_PAGE_SIZE);
        } else if ("tx".equals(source)) {
            lastTxSearchSongs = songs; lastTxCount = total; lastTxError = error;
            searchTxNextPage = 2; searchTxHasMore = providerHasMore(songs.size(), total, SEARCH_PAGE_SIZE);
        } else if ("kw".equals(source)) {
            lastKwSearchSongs = songs; lastKwCount = total; lastKwError = error;
            searchKwNextPage = 2; searchKwHasMore = providerHasMore(songs.size(), total, SEARCH_PAGE_SIZE);
        } else if ("kg".equals(source)) {
            lastKgSearchSongs = songs; lastKgCount = total; lastKgError = error;
            searchKgNextPage = 2; searchKgHasMore = providerHasMore(songs.size(), total, SEARCH_PAGE_SIZE);
        }
    }

    private boolean shouldSearchLyrics(String keyword, int normalResultCount) {
        // V87 Search Engine V2: lyric discovery is admitted by intent or explicit Lyrics-tab demand,
        // never merely because a metadata query happens to contain three characters.
        return lyricSearchStarted || inferSearchIntent(keyword) == SEARCH_INTENT_LYRIC;
    }

    private int inferSearchIntent(String text) {
        String raw = text == null ? "" : text.trim();
        String normalized = normalizeSuggestion(raw);
        int length = normalized.length();
        if (length < 3) return SEARCH_INTENT_METADATA;
        boolean lyricPunctuation = raw.contains("，") || raw.contains(",") || raw.contains("。")
                || raw.contains("？") || raw.contains("?") || raw.contains("！") || raw.contains("!");
        if (lyricPunctuation && length >= 5) return SEARCH_INTENT_LYRIC;
        if (length >= 11) return SEARCH_INTENT_LYRIC;
        if (containsSentenceCueInternal(raw) && length >= 6) return SEARCH_INTENT_LYRIC;
        // Space-separated short tokens are usually "artist title", while a longer free-form query
        // is more likely a lyric sentence copied from memory.
        if (raw.contains(" ")) {
            String[] tokens = raw.split("\\s+");
            boolean compact = tokens.length <= 3;
            for (String token : tokens) if (normalizeSuggestion(token).length() > 8) compact = false;
            return compact ? SEARCH_INTENT_MIXED : SEARCH_INTENT_LYRIC;
        }
        return length >= 6 ? SEARCH_INTENT_MIXED : SEARCH_INTENT_METADATA;
    }

    private boolean containsSentenceCue(String text) {
        return inferSearchIntent(text) == SEARCH_INTENT_LYRIC;
    }

    private boolean containsSentenceCueInternal(String text) {
        if (text == null) return false;
        String q = text.trim();
        String[] cues = {"我", "你", "他", "她", "我们", "如果", "因为", "怎么", "为何", "曾经", "还是", "已经", "没有", "不能", "想起", "记得", "爱你", "离开", "等待", "世界", "以后", "曾", "当我", "只要"};
        int hits = 0;
        for (String cue : cues) if (q.contains(cue) && ++hits >= 2) return true;
        return false;
    }

    private String matchingLyricLine(String lrc, String normalizedNeedle) {
        return matchingLyricContext(lrc, normalizedNeedle);
    }

    private String matchingLyricContext(String lrc, String normalizedNeedle) {
        if (lrc == null || lrc.trim().isEmpty() || normalizedNeedle == null || normalizedNeedle.isEmpty()) return "";
        ArrayList<String> lines = new ArrayList<>();
        for (String raw : lrc.split("\\r?\\n")) {
            String line = cleanLyricSearchLine(raw);
            if (line.length() >= 2) lines.add(line);
        }
        for (int i = 0; i < lines.size(); i++) {
            // Join neighboring lines before matching so a remembered phrase that crosses an LRC
            // line break can still be verified exactly after punctuation/space normalization.
            int to = Math.min(lines.size(), i + 3);
            StringBuilder joined = new StringBuilder();
            for (int j = i; j < to; j++) {
                if (joined.length() > 0) joined.append(" · ");
                joined.append(lines.get(j));
            }
            if (!normalizeSuggestion(joined.toString()).contains(normalizedNeedle)) continue;
            int from = Math.max(0, i - 1);
            int end = Math.min(lines.size(), i + 3);
            StringBuilder context = new StringBuilder();
            for (int j = from; j < end; j++) {
                if (context.length() > 0) context.append(" · ");
                context.append(lines.get(j));
            }
            String value = context.toString().trim();
            return value.length() <= 118 ? value : value.substring(0, 118) + "…";
        }
        return "";
    }

    private String cleanLyricSearchLine(String raw) {
        if (raw == null) return "";
        String line = raw.replaceAll("\\[[^\\]]*\\]", " ")
                .replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        if (line.matches("(?i)^(作词|作曲|编曲|制作人?|制作|监制|混音|录音|母带|和声|吉他|贝斯|鼓|发行|出品|词|曲|歌手|歌曲|演唱|lyrics?|lyricist|composer|arranger|producer)\\s*[:：].*$")) return "";
        return line;
    }

    private void maybeStartRemoteCatalogLyricFallback(String keyword, int requestToken, LinearLayout holder) {
        if (requestToken != searchRequestToken || isFinishing() || lyricCatalogFallbackStarted) return;
        if (!shouldSearchLyrics(keyword, 0) || lyricIndexJobs > 0 || !searchWyFinished || !searchTxFinished) return;
        // This is a second REMOTE path, not a local fallback. It only activates when both dedicated
        // lyric indexes finished without a usable excerpt, so generic catalog LRC checks cannot
        // crowd the fast lyric-index verification queue.
        if (!lyricSearchResults.isEmpty()) {
            lyricCatalogFallbackStarted = true;
            refreshLyricDiscoveryPending();
            requestLyricUiRefresh(holder, false);
            return;
        }
        List<LyricSearchResult> fallback = buildRemoteCatalogLyricCandidates(keyword, lastWySearchSongs, lastTxSearchSongs, LYRIC_CATALOG_FALLBACK_LIMIT);
        lyricCatalogFallbackStarted = true;
        refreshLyricDiscoveryPending();
        if (!fallback.isEmpty()) scheduleLyricVerification(keyword, requestToken, holder, fallback);
        // Important when the fallback has zero candidates: discovery has still completed, so the UI
        // may now truthfully transition out of pending once both dedicated indexes are also done.
        if (searchResultMode == 2 && fallback.isEmpty() && lyricSearchResults.isEmpty() && !lyricVerificationPending)
            renderSearchResults(holder);
        else requestLyricUiRefresh(holder, false);
    }

    private List<LyricSearchResult> buildRemoteCatalogLyricCandidates(String keyword, List<Song> wySongs, List<Song> txSongs, int max) {
        ArrayList<LyricSearchResult> out = new ArrayList<>();
        int wi = 0, ti = 0;
        while (out.size() < max && ((wySongs != null && wi < wySongs.size()) || (txSongs != null && ti < txSongs.size()))) {
            if (wySongs != null && wi < wySongs.size() && out.size() < max) {
                Song song = wySongs.get(wi++);
                if (song != null) mergeLyricCandidate(keyword, out, new LyricSearchResult(song, "", false, 2));
            }
            if (txSongs != null && ti < txSongs.size() && out.size() < max) {
                Song song = txSongs.get(ti++);
                if (song != null) mergeLyricCandidate(keyword, out, new LyricSearchResult(song, "", false, 4));
            }
        }
        return out;
    }

    private List<LyricSearchResult> mergeLyricResultLists(String keyword, List<LyricSearchResult> base,
                                                           List<LyricSearchResult> extra, int max) {
        ArrayList<LyricSearchResult> out = new ArrayList<>();
        if (base != null) for (LyricSearchResult hit : base) mergeLyricCandidate(keyword, out, hit);
        if (extra != null) for (LyricSearchResult hit : extra) mergeLyricCandidate(keyword, out, hit);
        out.sort((a, b) -> Integer.compare(lyricMatchScore(keyword, b), lyricMatchScore(keyword, a)));
        return out.size() <= max ? out : new ArrayList<>(out.subList(0, max));
    }

    private List<LyricSearchResult> mergeLyricHits(String keyword,
                                                    List<NeteaseLyricSearchApi.Hit> wyRemote,
                                                    List<QQMusicLyricSearchApi.Hit> txRemote, int max) {
        ArrayList<LyricSearchResult> out = new ArrayList<>();
        if (wyRemote != null) for (NeteaseLyricSearchApi.Hit hit : wyRemote) {
            if (hit == null || hit.song == null) continue;
            Song song = enrichWithKnownVariant(hit.song);
            mergeLyricCandidate(keyword, out, new LyricSearchResult(song, hit.snippet, false, 2));
        }
        if (txRemote != null) for (QQMusicLyricSearchApi.Hit hit : txRemote) {
            if (hit == null || hit.song == null) continue;
            Song song = enrichWithKnownVariant(hit.song);
            mergeLyricCandidate(keyword, out, new LyricSearchResult(song, hit.snippet, false, 4));
        }
        out.sort((a, b) -> Integer.compare(lyricMatchScore(keyword, b), lyricMatchScore(keyword, a)));
        return out.size() <= max ? out : new ArrayList<>(out.subList(0, max));
    }

    private void mergeLyricCandidate(String keyword, List<LyricSearchResult> out, LyricSearchResult incoming) {
        if (out == null || incoming == null || incoming.song == null) return;
        for (int i = 0; i < out.size(); i++) {
            LyricSearchResult old = out.get(i);
            if (old == null || old.song == null || !sameLyricCandidate(old.song, incoming.song)) continue;
            Song mergedSong = old.song.withVariant(incoming.song);
            String snippet = betterLyricSnippet(keyword, old.snippet, incoming.snippet);
            out.set(i, new LyricSearchResult(mergedSong, snippet,
                    old.verified || incoming.verified, old.evidenceMask | incoming.evidenceMask));
            return;
        }
        out.add(incoming);
    }

    private boolean sameLyricCandidate(Song a, Song b) {
        if (a == null || b == null) return false;
        if (ExactTrackMatcher.confidence(a, b) >= 0.98d) return true;
        if (!normalizeSuggestion(a.title).equals(normalizeSuggestion(b.title))) return false;
        if (!normalizeSuggestion(a.artist).equals(normalizeSuggestion(b.artist))) return false;
        if (a.durationMs <= 0L || b.durationMs <= 0L) return true;
        return Math.abs(a.durationMs - b.durationMs) <= 6500L;
    }

    private String betterLyricSnippet(String keyword, String a, String b) {
        String left = a == null ? "" : a.trim();
        String right = b == null ? "" : b.trim();
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        String needle = normalizeSuggestion(keyword);
        boolean le = !needle.isEmpty() && normalizeSuggestion(left).contains(needle);
        boolean re = !needle.isEmpty() && normalizeSuggestion(right).contains(needle);
        if (re && !le) return right;
        if (le && !re) return left;
        return right.length() > left.length() ? right : left;
    }

    private int lyricMatchScore(String keyword, LyricSearchResult hit) {
        if (hit == null || hit.song == null) return Integer.MIN_VALUE / 4;
        String needle = normalizeSuggestion(keyword);
        String snippet = normalizeSuggestion(hit.snippet);
        int score = SearchRanker.recordingQualityBias(hit.song);
        if (hit.verified) score += 18000;
        if ((hit.evidenceMask & 2) != 0 && (hit.evidenceMask & 4) != 0) score += 900;
        if (!needle.isEmpty() && !snippet.isEmpty()) {
            if (snippet.contains(needle)) score += 10000;
            else {
                int matched = lyricCharacterCoverage(needle, snippet);
                score += (int) Math.round(3600.0 * matched / Math.max(1, needle.length()));
            }
        }
        if (needle.length() >= 3 && metadataContainsQuery(hit.song, keyword) && !snippet.contains(needle)) score -= 1200;
        return score;
    }

    private int lyricCharacterCoverage(String needle, String haystack) {
        if (needle == null || haystack == null || needle.isEmpty() || haystack.isEmpty()) return 0;
        int matched = 0;
        int from = 0;
        for (int i = 0; i < needle.length(); i++) {
            int at = haystack.indexOf(needle.charAt(i), from);
            if (at < 0) continue;
            matched++;
            from = at + 1;
        }
        return matched;
    }

    private List<LyricSearchResult> enrichAndRerankLyricResults(String keyword, List<LyricSearchResult> input) {
        ArrayList<LyricSearchResult> out = new ArrayList<>();
        if (input != null) {
            for (LyricSearchResult hit : input) {
                if (hit == null || hit.song == null) continue;
                out.add(new LyricSearchResult(enrichWithKnownVariant(hit.song), hit.snippet, hit.verified, hit.evidenceMask));
            }
        }
        out.sort((a, b) -> Integer.compare(lyricMatchScore(keyword, b), lyricMatchScore(keyword, a)));
        return out;
    }

    private Song enrichWithKnownVariant(Song incoming) {
        if (incoming == null) return null;
        LinkedHashMap<String, Song> known = new LinkedHashMap<>();
        // Lyric discovery is deliberately remote-only. Do not walk favorites/playlists/history here:
        // Stable may contain years of local data while Beta does not. Only merge identities already
        // returned by this same remote search request.
        for (Song song : searchResults) if (song != null) known.put(song.key(), song);
        for (Song song : lastWySearchSongs) if (song != null) known.put(song.key(), song);
        for (Song song : lastTxSearchSongs) if (song != null) known.put(song.key(), song);
        for (Song song : lastKwSearchSongs) if (song != null) known.put(song.key(), song);
        for (Song song : lastKgSearchSongs) if (song != null) known.put(song.key(), song);
        for (Song song : known.values()) {
            // A lyric hit must never bridge variants on title/artist alone. V3 uses the same
            // conservative recording gate as multi-catalog playback.
            if (ExactTrackMatcher.confidence(song, incoming) >= 0.98d) return song.withVariant(incoming);
        }
        return incoming;
    }

    private boolean metadataContainsQuery(Song song, String keyword) {
        String needle = normalizeSuggestion(keyword);
        if (song == null || needle.isEmpty()) return false;
        return normalizeSuggestion(song.title).contains(needle) || normalizeSuggestion(song.artist).contains(needle);
    }

    private List<Song> visibleSearchSongs() {
        if (searchSourceFilter == null || searchSourceFilter.isEmpty()) return new ArrayList<>(searchResults);
        // Filter the already-canonicalized rows rather than falling back to raw provider rows,
        // so a KW-filtered result still carries its exact TX/KG/WY sibling identities into playback.
        ArrayList<Song> out = new ArrayList<>();
        for (Song song : searchResults) if (song != null && song.variant(searchSourceFilter) != null) out.add(song);
        return out;
    }

    private List<LyricSearchResult> visibleLyricSearchResults() {
        ArrayList<LyricSearchResult> out = new ArrayList<>();
        for (LyricSearchResult hit : lyricSearchResults) {
            if (hit == null || hit.song == null) continue;
            if (searchSourceFilter != null && !searchSourceFilter.isEmpty() && hit.song.variant(searchSourceFilter) == null) continue;
            out.add(hit);
        }
        return out;
    }

    private String searchContentSignature(List<Song> songs, List<LyricSearchResult> lyrics) {
        StringBuilder b = new StringBuilder(256);
        b.append(searchResultMode).append('|').append(searchSourceFilter).append('|')
                .append(searchMetadataPending).append('|').append(lyricSearchStarted).append('|')
                .append(lyricSearchPending).append('|').append(lyricVerificationPending).append('|')
                .append(searchMetadataExpanded).append('|').append(searchLyricsExpanded);
        int songLimit = Math.min(SEARCH_SUMMARY_COUNT, songs == null ? 0 : songs.size());
        b.append("|s-visible:").append(songLimit);
        for (int i = 0; i < songLimit; i++) {
            Song song = songs.get(i); if (song == null) continue;
            // Source variants can arrive after first paint without changing the visible row. Keep
            // the row identity stable and let the source/filter strip reflect background progress.
            b.append('|').append(song.key());
        }
        int lyricLimit = Math.min(8, lyrics == null ? 0 : lyrics.size());
        b.append("|l:").append(lyrics == null ? 0 : lyrics.size());
        for (int i = 0; i < lyricLimit; i++) {
            LyricSearchResult lyric = lyrics.get(i); if (lyric == null || lyric.song == null) continue;
            b.append('|').append(lyric.song.key()).append(lyric.verified ? 'v' : 'c');
        }
        return b.toString();
    }

    private void renderSearchResults(LinearLayout holder) {
        if (holder == null) return;
        boolean newShell = holder != activeSearchResultsHolder || activeSearchContentHost == null
                || activeSearchContentHost.getParent() == null || activeSearchTabs == null;
        activeSearchResultsHolder = holder;
        activeLyricSectionUi = null;
        if (newShell) {
            holder.removeAllViews();
            activeSearchTabs = searchResultModeTabs(holder);
            holder.addView(activeSearchTabs, Ui.lp(-1, Ui.dp(this, 48)));

            HorizontalScrollView sourceScroll = new HorizontalScrollView(this);
            sourceScroll.setHorizontalScrollBarEnabled(false);
            activeSearchSourceHost = Ui.row(this);
            sourceScroll.addView(activeSearchSourceHost);
            holder.addView(sourceScroll, marginTop(8));

            activeSearchContentHost = Ui.column(this);
            holder.addView(activeSearchContentHost, marginTop(0));
        }
        refreshSearchModeTabsVisual();
        refreshSearchSourceFilters(holder);
        LinearLayout target = activeSearchContentHost;

        List<Song> visibleSongs = visibleSearchSongs();
        List<LyricSearchResult> visibleLyrics = visibleLyricSearchResults();
        String contentSignature = searchContentSignature(visibleSongs, visibleLyrics);
        if (!newShell && contentSignature.equals(lastSearchContentSignature)) return;
        lastSearchContentSignature = contentSignature;
        target.removeAllViews();
        boolean lyricBusy = lyricSearchStarted && (lyricSearchPending || lyricVerificationPending);
        boolean metadataBusy = searchMetadataPending;
        boolean lyricAvailableOrRunning = lyricSearchStarted || !visibleLyrics.isEmpty();
        boolean modeHasNothing = searchResultMode == 1 ? visibleSongs.isEmpty()
                : searchResultMode == 2 ? visibleLyrics.isEmpty()
                : (visibleSongs.isEmpty() && (!lyricAvailableOrRunning || visibleLyrics.isEmpty()));
        boolean modeBusy = searchResultMode == 1 ? metadataBusy
                : searchResultMode == 2 ? lyricBusy
                : (metadataBusy || lyricBusy);
        if (modeHasNothing && !modeBusy) {
            LinearLayout empty = Ui.column(this);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(Ui.dp(this, 10), Ui.dp(this, 42), Ui.dp(this, 10), Ui.dp(this, 30));
            String sourceLabel = searchSourceFilter == null || searchSourceFilter.isEmpty() ? "" : Song.providerLabel(searchSourceFilter);
            String titleText = searchResultMode == 2 ? "还没有开始歌词检索" : (sourceLabel.isEmpty() ? "暂时没搜到" : sourceLabel + "暂时没搜到");
            if (searchResultMode == 2 && lyricSearchStarted) titleText = "没有找到歌词命中";
            TextView title = Ui.text(this, titleText, 18, Ui.TEXT, true);
            title.setGravity(Gravity.CENTER); empty.addView(title, Ui.lp(-1, Ui.dp(this, 34)));
            String subText = searchResultMode == 2
                    ? (lyricSearchStarted ? "试试更完整的一句歌词，命中率会更高。" : "点一下开始按歌词内容搜索，不影响歌曲搜索速度。")
                    : "换个关键词，或输入你记得的一句歌词。";
            TextView sub = Ui.text(this, subText, 12.5f, Ui.TEXT_2, false);
            sub.setGravity(Gravity.CENTER); empty.addView(sub, Ui.lp(-1, Ui.dp(this, 38)));
            if (searchResultMode == 2 && !lyricSearchStarted && lastQuery != null && !lastQuery.trim().isEmpty()) {
                TextView start = outlineAction("搜索歌词");
                LinearLayout.LayoutParams sp = Ui.lp(Ui.dp(this, 144), Ui.dp(this, 40)); sp.topMargin = Ui.dp(this, 10);
                empty.addView(start, sp);
                start.setOnClickListener(v -> startLyricSearchOnDemand(lastQuery, searchRequestToken, holder));
            } else if (hasAnyCatalogError() && searchResultMode != 2) {
                TextView detail = outlineAction("查看搜索矩阵");
                LinearLayout.LayoutParams dp = Ui.lp(Ui.dp(this, 154), Ui.dp(this, 40)); dp.topMargin = Ui.dp(this, 10);
                empty.addView(detail, dp); detail.setOnClickListener(v -> showSearchPerformanceMatrix());
            }
            target.addView(empty); return;
        }

        List<Song> summarySongs = visibleSongs.size() > SEARCH_SUMMARY_COUNT
                ? new ArrayList<>(visibleSongs.subList(0, SEARCH_SUMMARY_COUNT)) : visibleSongs;
        if (searchResultMode == 1) {
            addMetadataSearchSection(target, summarySongs, true);
            return;
        }
        if (searchResultMode == 2) {
            addLyricSearchSection(target, visibleLyrics, true);
            return;
        }

        boolean lyricFirst = lyricSearchStarted && shouldPrioritizeLyricSection(lastQuery, visibleSongs);
        if (lyricFirst) {
            addLyricSearchSection(target, visibleLyrics, true);
            addMetadataSearchSection(target, summarySongs, true);
        } else {
            addMetadataSearchSection(target, summarySongs, true);
            // Ordinary artist/title queries do not pay for or display an empty lyric subsystem. A
            // lyric section appears only after intent/on-demand actually starts discovery.
            if (lyricAvailableOrRunning) addLyricSearchSection(target, visibleLyrics, true);
        }
    }

    private void refreshSearchSourceFilters(LinearLayout holder) {
        if (activeSearchSourceHost == null) return;
        LinearLayout health = activeSearchSourceHost;
        health.removeAllViews();
        health.addView(allSourceChip(holder), Ui.lp(-2, Ui.dp(this, 34)));
        addChipGap(health);
        health.addView(sourceChip("网易", "wy", lastWyCount, lastWyError, Ui.PURPLE, holder), Ui.lp(-2, Ui.dp(this, 34)));
        addChipGap(health);
        health.addView(sourceChip("QQ", "tx", lastTxCount, lastTxError, Ui.CYAN, holder), Ui.lp(-2, Ui.dp(this, 34)));
        addChipGap(health);
        health.addView(sourceChip("酷我", "kw", lastKwCount, lastKwError, Ui.GREEN, holder), Ui.lp(-2, Ui.dp(this, 34)));
        addChipGap(health);
        health.addView(sourceChip("酷狗", "kg", lastKgCount, lastKgError, Color.rgb(232, 178, 75), holder), Ui.lp(-2, Ui.dp(this, 34)));
    }

    private void refreshSearchModeTabsVisual() {
        if (activeSearchTabViews == null) return;
        for (int i = 0; i < activeSearchTabViews.length; i++) {
            TextView tabView = activeSearchTabViews[i]; if (tabView == null) continue;
            boolean selected = i == searchResultMode;
            tabView.setTextColor(selected ? Ui.CYAN : Ui.TEXT_2);
            tabView.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tabView.setAlpha(selected ? 1f : .82f);
        }
        if (activeSearchTabs != null && activeSearchTabs.getTag() instanceof View)
            activeSearchTabs.post(() -> positionSearchTabIndicator(activeSearchTabs, (View) activeSearchTabs.getTag(), searchResultMode, false));
    }

    private FrameLayout searchResultModeTabs(LinearLayout holder) {
        // Beta V8: this is a true full-width primary tab bar, not another row of pills.
        // The provider pills below remain untouched and therefore read clearly as secondary filters.
        FrameLayout tabs = new FrameLayout(this);
        tabs.setClipChildren(false);
        tabs.setClipToPadding(false);

        LinearLayout labels = Ui.row(this);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        final TextView[] tabViews = new TextView[3];
        String[] names = new String[]{"综合", "单曲", "歌词"};
        for (int mode = 0; mode < names.length; mode++) {
            final int targetMode = mode;
            boolean selected = searchResultMode == mode;
            TextView tab = Ui.text(this, names[mode], 13.6f, selected ? Ui.CYAN : Ui.TEXT_2, selected);
            tab.setGravity(Gravity.CENTER);
            tab.setClickable(true); tab.setFocusable(true);
            tab.setBackgroundColor(Color.TRANSPARENT);
            tab.setAlpha(selected ? 1f : .82f);
            Ui.applyRipple(tab, Color.TRANSPARENT);
            tabViews[mode] = tab;
            labels.addView(tab, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f));
            tab.setOnClickListener(v -> animateSearchResultModeSwitch(tabs, tabViews, holder, targetMode));
        }
        tabs.addView(labels, Ui.frame(-1, Ui.dp(this, 46), Gravity.TOP));
        activeSearchTabViews = tabViews;

        View baseline = new View(this);
        baseline.setBackgroundColor(Color.argb(24, 255, 255, 255));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, Ui.dp(this, 1), Gravity.BOTTOM);
        tabs.addView(baseline, bp);

        View indicator = new View(this);
        indicator.setBackground(Ui.round(Ui.CYAN, 2f, this));
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 3), Gravity.START | Gravity.BOTTOM);
        tabs.addView(indicator, ip);
        tabs.setTag(indicator);

        // renderSearchResults() rebuilds this tab bar after a mode switch. Position the
        // new indicator before its first draw so it never flashes once at translationX=0.
        tabs.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                android.view.ViewTreeObserver observer = tabs.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnPreDrawListener(this);
                positionSearchTabIndicator(tabs, indicator, searchResultMode, false);
                return true;
            }
        });
        return tabs;
    }

    private void animateSearchResultModeSwitch(FrameLayout tabs, TextView[] tabViews, LinearLayout holder, int targetMode) {
        if (targetMode < 0 || targetMode > 2 || targetMode == searchResultMode || tabs == null) return;
        if (targetMode == 2 && !lyricSearchStarted && lastQuery != null && !lastQuery.trim().isEmpty())
            startLyricSearchOnDemand(lastQuery, searchRequestToken, holder);
        View indicator = tabs.getTag() instanceof View ? (View) tabs.getTag() : null;
        int fromMode = searchResultMode;
        int token = ++searchModeAnimationToken;

        if (indicator != null) positionSearchTabIndicator(tabs, indicator, targetMode, true);
        TextView from = tabViews != null && fromMode < tabViews.length ? tabViews[fromMode] : null;
        TextView to = tabViews != null && targetMode < tabViews.length ? tabViews[targetMode] : null;
        if (from != null && to != null) {
            to.animate().cancel(); from.animate().cancel();
            to.setScaleX(.97f); to.setScaleY(.97f);
            to.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(210).setInterpolator(new DecelerateInterpolator(1.7f)).start();
            from.animate().scaleX(.985f).scaleY(.985f).alpha(.78f).setDuration(170).setInterpolator(new DecelerateInterpolator()).start();
            ValueAnimator tint = ValueAnimator.ofFloat(0f, 1f);
            tint.setDuration(210);
            tint.setInterpolator(new DecelerateInterpolator(1.6f));
            tint.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                from.setTextColor(Ui.mix(Ui.CYAN, Ui.TEXT_2, t));
                to.setTextColor(Ui.mix(Ui.TEXT_2, Ui.CYAN, t));
            });
            tint.start();
        }

        final int direction = targetMode > fromMode ? 1 : -1;
        main.postDelayed(() -> {
            if (token != searchModeAnimationToken || isFinishing()) return;
            searchResultMode = targetMode;
            renderSearchResults(holder);
            animateSearchResultContent(holder, direction);
        }, 255L);
    }

    private void positionSearchTabIndicator(FrameLayout tabs, View indicator, int mode, boolean animate) {
        int width = tabs.getWidth();
        if (width <= 0 || indicator == null) return;
        float segment = width / 3f;
        float targetX = segment * mode + (segment - indicator.getLayoutParams().width) / 2f;
        if (indicator.getTag() instanceof ValueAnimator) ((ValueAnimator) indicator.getTag()).cancel();
        indicator.animate().cancel();
        if (!animate) {
            indicator.setTranslationX(targetX);
            indicator.setScaleX(1f);
            indicator.setTag(null);
            return;
        }

        // One continuous motion: glide to the next third while the underline subtly stretches,
        // then settles back to its compact width at the destination.
        final float startX = indicator.getTranslationX();
        ValueAnimator motion = ValueAnimator.ofFloat(0f, 1f);
        motion.setDuration(245L);
        motion.setInterpolator(new DecelerateInterpolator(1.45f));
        motion.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            indicator.setTranslationX(startX + (targetX - startX) * t);
            indicator.setScaleX(1f + .24f * (float) Math.sin(Math.PI * t));
        });
        indicator.setTag(motion);
        motion.start();
    }

    private void animateSearchResultContent(LinearLayout holder, int direction) {
        // Keep the tab bar and provider filters visually anchored; only the result body glides in.
        if (holder == null) return;
        for (int i = 2; i < holder.getChildCount(); i++) {
            View child = holder.getChildAt(i);
            child.animate().cancel();
            child.setAlpha(.58f);
            child.setTranslationX(direction * Ui.dp(this, 10));
            child.animate().alpha(1f).translationX(0f).setDuration(185L)
                    .setInterpolator(new DecelerateInterpolator(1.5f)).start();
        }
    }

    private boolean shouldPrioritizeLyricSection(String keyword, List<Song> songs) {
        if (searchIntentMode != SEARCH_INTENT_LYRIC) return false;
        return !hasStrongMetadataMatch(keyword, songs);
    }

    private boolean hasStrongMetadataMatch(String keyword, List<Song> songs) {
        String raw = keyword == null ? "" : keyword.trim();
        String needle = normalizeSuggestion(raw);
        if (needle.isEmpty() || songs == null) return false;
        for (Song song : songs) {
            if (song == null) continue;
            if (needle.equals(normalizeSuggestion(song.title)) || needle.equals(normalizeSuggestion(song.artist))) return true;
        }
        if (!raw.contains(" ")) return false;
        String[] tokens = raw.split("\\s+");
        for (Song song : songs) {
            if (song == null) continue;
            String metadata = normalizeSuggestion(song.title + " " + song.artist);
            boolean all = true;
            for (String token : tokens) {
                String t = normalizeSuggestion(token);
                if (!t.isEmpty() && !metadata.contains(t)) { all = false; break; }
            }
            if (all) return true;
        }
        return false;
    }

    private void addChipGap(LinearLayout row) { Space gap = new Space(this); row.addView(gap, Ui.lp(Ui.dp(this, 8), 1)); }

    private boolean hasAnyCatalogError() {
        return !lastWyError.isEmpty() || !lastTxError.isEmpty() || !lastKwError.isEmpty() || !lastKgError.isEmpty();
    }

    private List<Song> searchSongsFor(String source) {
        if ("wy".equals(source)) return lastWySearchSongs;
        if ("tx".equals(source)) return lastTxSearchSongs;
        if ("kw".equals(source)) return lastKwSearchSongs;
        if ("kg".equals(source)) return lastKgSearchSongs;
        return new ArrayList<>();
    }

    private void addMetadataSearchSection(LinearLayout holder, List<Song> songs, boolean allowEmpty) {
        List<Song> safeSongs = songs == null ? new ArrayList<>() : songs;
        if (safeSongs.isEmpty() && !allowEmpty) return;
        String suffix = (searchSourceFilter == null || searchSourceFilter.isEmpty()) ? "" : " · " + Song.providerLabel(searchSourceFilter);
        int discovered = visibleSearchSongs().size();
        String status = searchMetadataPending
                ? (safeSongs.isEmpty() ? "搜索中…" : "已发现 " + discovered + " 首 · 后台补全中…")
                : discovered + " 首" + suffix;
        LinearLayout content = Ui.column(this);
        if (searchMetadataPending && safeSongs.isEmpty()) {
            content.addView(skeletonSongRows(4, Ui.CYAN), Ui.lp(-1, -2));
        } else if (safeSongs.isEmpty()) {
            TextView empty = Ui.text(this, "没有更合适的歌名 / 歌手结果", 11.2f, Ui.DIM, false);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            content.addView(empty, Ui.lp(-1, Ui.dp(this, 40)));
        } else {
            content.addView(songList(safeSongs, safeSongs), Ui.lp(-1, -2));
        }
        // Keep the compact streaming page authoritative until all four first pages settle. Opening
        // the virtualized "全部" page mid-stream would otherwise freeze a partial snapshot while
        // later catalogs are still enriching the canonical rows behind it.
        boolean hasAllPage = !searchMetadataPending && !safeSongs.isEmpty()
                && (searchHasMoreForCurrentFilter() || visibleSearchSongs().size() > safeSongs.size());
        String allAction = hasAllPage ? "全部 ›" : "";
        LinearLayout header = searchSectionHeader("歌曲 / 歌手", status,
                searchMetadataExpanded, expanded -> {
                    searchMetadataExpanded = expanded;
                    setSearchSectionExpanded(content, expanded);
                }, allAction, v -> openAllSearchResults());
        LinearLayout.LayoutParams hp = marginTop(14);
        holder.addView(header, hp);
        holder.addView(content, marginTop(4));
        setSearchSectionExpanded(content, searchMetadataExpanded);
    }

    private boolean providerHasMore(int loaded, int total, int pageSize) {
        // Some search endpoints report only the current page size as "total". A full page therefore
        // remains probe-able even when total == loaded; one empty/partial next page will close it.
        if (total > loaded) return true;
        return loaded >= Math.max(1, pageSize);
    }

    private boolean searchHasMoreForCurrentFilter() {
        if (searchMoreLoading) return true;
        if ("wy".equals(searchSourceFilter)) return searchWyHasMore;
        if ("tx".equals(searchSourceFilter)) return searchTxHasMore;
        if ("kw".equals(searchSourceFilter)) return searchKwHasMore;
        if ("kg".equals(searchSourceFilter)) return searchKgHasMore;
        return searchWyHasMore || searchTxHasMore || searchKwHasMore || searchKgHasMore;
    }

    private void openAllSearchResults() {
        if (lastQuery == null || lastQuery.trim().isEmpty() || visibleSearchSongs().isEmpty()) return;
        hideSearchSuggestions();
        searchAllResultsOpen = true;
        suppressNextPageAnimation = false;
        renderTab();
    }

    /**
     * V45 commercial-style full search page. The comprehensive search page stays compact; this
     * dedicated page owns the virtualized list and silently requests another provider page when the
     * user approaches the bottom. Pagination never resolves audio URLs and never touches playback.
     */
    private View searchAllResultsPage() {
        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), 0);

        LinearLayout top = Ui.row(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout back = Ui.iconButton(this, IconView.Type.BACK, 42, Ui.TEXT_2, Color.TRANSPARENT);
        back.setContentDescription("返回搜索综合页");
        back.setOnClickListener(v -> {
            searchAllResultsOpen = false;
            activeSearchAllList = null;
            searchAllSavedPosition = 0;
            searchAllSavedTop = 0;
            rootTabSearchAllResultsOpen[1] = false;
            suppressNextPageAnimation = true;
            renderTab();
        });
        top.addView(back, Ui.lp(Ui.dp(this, 42), Ui.dp(this, 42)));

        LinearLayout titles = Ui.column(this);
        titles.setPadding(Ui.dp(this, 10), 0, 0, 0);
        TextView title = Ui.text(this, "歌曲 / 歌手", 21f, Ui.TEXT, true);
        title.setSingleLine(true);
        TextView subtitle = Ui.text(this, "“" + lastQuery + "” · 原唱与正式版本优先", 10.8f, Ui.TEXT_2, false);
        subtitle.setSingleLine(true); subtitle.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(title, new LinearLayout.LayoutParams(-1, 0, 1f));
        titles.addView(subtitle, new LinearLayout.LayoutParams(-1, 0, 1f));
        top.addView(titles, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f));
        root.addView(top, Ui.lp(-1, Ui.dp(this, 48)));

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filters = Ui.row(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.addView(searchAllAllSourceChip(), Ui.lp(-2, Ui.dp(this, 34)));
        addChipGap(filters);
        filters.addView(searchAllSourceChip("网易", "wy", lastWyCount, lastWyError), Ui.lp(-2, Ui.dp(this, 34)));
        addChipGap(filters);
        filters.addView(searchAllSourceChip("QQ", "tx", lastTxCount, lastTxError), Ui.lp(-2, Ui.dp(this, 34)));
        addChipGap(filters);
        filters.addView(searchAllSourceChip("酷我", "kw", lastKwCount, lastKwError), Ui.lp(-2, Ui.dp(this, 34)));
        addChipGap(filters);
        filters.addView(searchAllSourceChip("酷狗", "kg", lastKgCount, lastKgError), Ui.lp(-2, Ui.dp(this, 34)));
        filterScroll.addView(filters);
        LinearLayout.LayoutParams fp = Ui.lp(-1, Ui.dp(this, 34)); fp.topMargin = Ui.dp(this, 12);
        root.addView(filterScroll, fp);

        TextView status = Ui.text(this, searchAllStatusText(), 10.6f, Ui.DIM, false);
        status.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sp = Ui.lp(-1, Ui.dp(this, 32)); sp.topMargin = Ui.dp(this, 4);
        root.addView(status, sp);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(android.R.color.transparent);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setVerticalScrollBarEnabled(false);
        // Avoid the legacy bitmap scrolling cache: recycled rows + async artwork already provide
        // bounded work, while a cached translucent list can consume memory and show stale frames.
        list.setScrollingCacheEnabled(false);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, Ui.dp(this, 158));
        list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        TextView footer = Ui.text(this, "", 10.4f, Ui.DIM, false);
        footer.setGravity(Gravity.CENTER);
        footer.setVisibility(View.GONE);
        footer.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 18));
        list.addFooterView(footer, null, false);

        SearchAllAdapter adapter = new SearchAllAdapter(visibleSearchSongs());
        list.setAdapter(adapter);
        activeSearchAllList = list;
        if (searchAllSavedPosition > 0 || searchAllSavedTop != 0) {
            final int savedPosition = searchAllSavedPosition;
            final int savedTop = searchAllSavedTop;
            list.post(() -> {
                if (activeSearchAllList == list && searchAllResultsOpen)
                    list.setSelectionFromTop(Math.min(savedPosition, Math.max(0, adapter.getCount() - 1)), savedTop);
            });
        }
        list.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(android.widget.AbsListView view, int scrollState) { }
            @Override public void onScroll(android.widget.AbsListView view, int firstVisibleItem,
                                           int visibleItemCount, int totalItemCount) {
                if (!searchAllResultsOpen || searchMoreLoading || visibleItemCount <= 0) return;
                if (SystemClock.uptimeMillis() < searchAutoLoadNotBeforeMs) return;
                int dataCount = adapter.getCount();
                if (dataCount <= 0 || !searchHasMoreForCurrentFilter()) return;
                int lastVisible = firstVisibleItem + visibleItemCount - 1;
                if (lastVisible >= Math.max(0, dataCount - 7)) {
                    loadMoreSearchAllResults(list, adapter, footer, status);
                }
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = Ui.dp(this, 2);
        root.addView(list, lp);
        return root;
    }

    private TextView searchAllAllSourceChip() {
        boolean selected = searchSourceFilter == null || searchSourceFilter.isEmpty();
        TextView chip = Ui.pill(this, selected ? "✓  全部" : "全部", Ui.TEXT_2);
        chip.setClickable(true); chip.setFocusable(true); chip.setAlpha(selected ? 1f : .84f);
        if (selected) chip.setBackground(Ui.tintedGlass(Ui.CYAN, 18, this));
        Ui.applyRipple(chip, Color.TRANSPARENT);
        chip.setOnClickListener(v -> { searchSourceFilter = ""; suppressNextPageAnimation = true; renderTab(); });
        return chip;
    }

    private TextView searchAllSourceChip(String name, String source, int count, String error) {
        boolean ok = count > 0;
        boolean selected = source.equals(searchSourceFilter);
        int accent = "wy".equals(source) ? Ui.PURPLE : ("tx".equals(source) ? Ui.CYAN : ("kw".equals(source) ? Ui.GREEN : Color.rgb(232, 178, 75)));
        int tint = ok ? accent : (error == null || error.isEmpty() ? Ui.DIM : Ui.RED);
        String label = (selected ? "✓  " : "") + (ok ? name + "  " + count
                : (error == null || error.isEmpty() ? name + "  0" : name + "  暂不可用"));
        TextView chip = Ui.pill(this, label, tint);
        chip.setClickable(true); chip.setFocusable(true);
        chip.setAlpha(selected ? 1f : .84f);
        if (selected) chip.setBackground(Ui.tintedGlass(tint, 22, this));
        Ui.applyRipple(chip, Color.TRANSPARENT);
        chip.setOnClickListener(v -> {
            searchSourceFilter = source.equals(searchSourceFilter) ? "" : source;
            suppressNextPageAnimation = true;
            renderTab();
        });
        if (error != null && !error.isEmpty()) chip.setOnLongClickListener(v -> { showSearchDebug(); return true; });
        return chip;
    }

    private String searchAllStatusText() {
        int loaded;
        if (searchSourceFilter != null && !searchSourceFilter.isEmpty()) loaded = searchSongsFor(searchSourceFilter).size();
        else loaded = visibleSearchSongs().size();
        if (searchMoreLoading) return "已加载 " + loaded + " 首 · 正在获取更多";
        if (searchHasMoreForCurrentFilter()) return "已加载 " + loaded + " 首 · 继续下滑会自动加载";
        return "已加载 " + loaded + " 首 · 已到全部结果";
    }

    private void loadMoreSearchAllResults(ListView list, SearchAllAdapter adapter, TextView footer, TextView status) {
        if (list == null || adapter == null || searchMoreLoading || lastQuery == null || lastQuery.trim().isEmpty()) return;
        final boolean all = searchSourceFilter == null || searchSourceFilter.isEmpty();
        final boolean loadWy = (all || "wy".equals(searchSourceFilter)) && searchWyHasMore;
        final boolean loadTx = (all || "tx".equals(searchSourceFilter)) && searchTxHasMore;
        final boolean loadKw = (all || "kw".equals(searchSourceFilter)) && searchKwHasMore;
        final boolean loadKg = (all || "kg".equals(searchSourceFilter)) && searchKgHasMore;
        if (!loadWy && !loadTx && !loadKw && !loadKg) return;

        final int token = searchRequestToken;
        final int wyOffset = searchWyNextOffset, txPage = searchTxNextPage, kwPage = searchKwNextPage, kgPage = searchKgNextPage;
        final int firstVisible = list.getFirstVisiblePosition();
        final Song anchorSong = firstVisible >= 0 && firstVisible < adapter.getCount() ? (Song) adapter.getItem(firstVisible) : null;
        final String anchorKey = anchorSong == null ? "" : anchorSong.key();
        final View firstChild = list.getChildCount() > 0 ? list.getChildAt(0) : null;
        final int anchorTop = firstChild == null ? 0 : firstChild.getTop();

        searchMoreLoading = true;
        if (footer != null) { footer.setText("正在加载更多…"); footer.setVisibility(View.VISIBLE); }
        if (status != null) status.setText(searchAllStatusText());

        final Future<SearchPaginationApi.Page> wyFuture = loadWy ? io.submit(() -> searchPagination.netease(lastQuery, wyOffset, SEARCH_PAGE_SIZE)) : null;
        final Future<SearchPaginationApi.Page> txFuture = loadTx ? io.submit(() -> searchPagination.qq(lastQuery, txPage, SEARCH_PAGE_SIZE)) : null;
        final Future<SearchPaginationApi.Page> kwFuture = loadKw ? io.submit(() -> searchPagination.kuwo(lastQuery, kwPage, SEARCH_PAGE_SIZE)) : null;
        final Future<SearchPaginationApi.Page> kgFuture = loadKg ? io.submit(() -> searchPagination.kugou(lastQuery, kgPage, SEARCH_PAGE_SIZE)) : null;

        io.submit(() -> {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(16);
            SearchPaginationApi.Page wy = awaitSearchPage(wyFuture, lastWyCount, deadline);
            SearchPaginationApi.Page tx = awaitSearchPage(txFuture, lastTxCount, deadline);
            SearchPaginationApi.Page kw = awaitSearchPage(kwFuture, lastKwCount, deadline);
            SearchPaginationApi.Page kg = awaitSearchPage(kgFuture, lastKgCount, deadline);
            main.post(() -> {
                if (token != searchRequestToken || isFinishing()) return;
                boolean hadError = false;
                if (loadWy && wy != null) hadError |= applyPaginationPage("wy", wy);
                if (loadTx && tx != null) hadError |= applyPaginationPage("tx", tx);
                if (loadKw && kw != null) hadError |= applyPaginationPage("kw", kw);
                if (loadKg && kg != null) hadError |= applyPaginationPage("kg", kg);

                final boolean pageHadError = hadError;
                final Map<String,List<Song>> pageCatalogs = new LinkedHashMap<>();
                pageCatalogs.put("wy", wy == null ? new ArrayList<>() : new ArrayList<>(wy.songs));
                pageCatalogs.put("tx", tx == null ? new ArrayList<>() : new ArrayList<>(tx.songs));
                pageCatalogs.put("kw", kw == null ? new ArrayList<>() : new ArrayList<>(kw.songs));
                pageCatalogs.put("kg", kg == null ? new ArrayList<>() : new ArrayList<>(kg.songs));
                final List<Song> frozenHead = new ArrayList<>(searchResults);
                searchAutoLoadNotBeforeMs = SystemClock.uptimeMillis() + (hadError ? 2600L : 260L);
                searchMergeExecutor.submit(() -> {
                    // V60 stable pagination: preserve the already-ranked first result set. New pages
                    // are ranked only inside their own batch, then appended/variant-merged without
                    // moving rows the user is currently reading. This removes the visible full-list
                    // reshuffle and expensive all-results re-rank on every near-bottom prefetch.
                    final List<Song> merged = appendSearchPageStable(lastQuery, frozenHead, pageCatalogs);
                    main.post(() -> {
                        if (token != searchRequestToken || isFinishing()) return;
                        searchMoreLoading = false;
                        searchResults = merged;
                        persistSearchVariantsAsync(merged);
                        if (searchAllResultsOpen && list.isAttachedToWindow()) {
                            adapter.replace(visibleSearchSongs());
                            if (!anchorKey.isEmpty()) {
                                int anchorIndex = adapter.indexOfKey(anchorKey);
                                if (anchorIndex >= 0 && Math.abs(anchorIndex - firstVisible) > 1)
                                    list.post(() -> list.setSelectionFromTop(anchorIndex, anchorTop));
                            }
                            if (status != null) status.setText(searchAllStatusText());
                            if (footer != null) {
                                if (pageHadError) {
                                    footer.setText("部分平台加载失败 · 其他平台结果不受影响"); footer.setVisibility(View.VISIBLE);
                                    footer.postDelayed(() -> { if (footer.isAttachedToWindow() && !searchMoreLoading) footer.setVisibility(View.GONE); }, 1800L);
                                } else footer.setVisibility(View.GONE);
                            }
                        }
                    });
                });
            });
        });
    }

    private List<Song> appendSearchPageStable(String keyword, List<Song> frozenHead, Map<String,List<Song>> pageCatalogs) {
        ArrayList<Song> out = new ArrayList<>();
        if (frozenHead != null) out.addAll(frozenHead);
        int pageCount = 0;
        if (pageCatalogs != null) for (List<Song> songs : pageCatalogs.values()) if (songs != null) pageCount += songs.size();
        if (pageCount <= 0) return out;

        List<Song> rankedPage = SearchRanker.mergeAndRank(keyword, pageCatalogs, Math.max(1, pageCount));
        LinkedHashMap<String,Integer> exactKeyIndex = new LinkedHashMap<>();
        for (int i = 0; i < out.size(); i++) {
            Song song = out.get(i);
            if (song != null) exactKeyIndex.put(song.key(), i);
        }
        for (Song incoming : rankedPage) {
            if (incoming == null) continue;
            Integer direct = exactKeyIndex.get(incoming.key());
            int existing = direct == null ? findStableSearchExactIndex(out, incoming) : direct;
            if (existing >= 0) {
                Song merged = out.get(existing).withVariant(incoming);
                out.set(existing, merged);
                exactKeyIndex.put(merged.key(), existing);
            } else {
                exactKeyIndex.put(incoming.key(), out.size());
                out.add(incoming);
            }
        }
        return out;
    }

    private int findStableSearchExactIndex(List<Song> existing, Song incoming) {
        if (existing == null || incoming == null) return -1;
        String title = normalizeSuggestion(incoming.title);
        String artist = normalizeSuggestion(incoming.artist);
        for (int i = 0; i < existing.size(); i++) {
            Song current = existing.get(i);
            if (current == null) continue;
            if (!title.equals(normalizeSuggestion(current.title)) || !artist.equals(normalizeSuggestion(current.artist))) continue;
            if (ExactTrackMatcher.confidence(current, incoming) >= 0.98d) return i;
        }
        return -1;
    }

    private SearchPaginationApi.Page awaitSearchPage(Future<SearchPaginationApi.Page> future, int total, long deadline) {
        if (future == null) return null;
        try { return future.get(Math.max(1L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); }
        catch (Exception e) { future.cancel(true); return new SearchPaginationApi.Page(new ArrayList<>(), total, "请求超时或网络不可用"); }
    }

    private boolean applyPaginationPage(String source, SearchPaginationApi.Page page) {
        List<Song> target = searchSongsFor(source);
        int before = target.size(); appendUniqueProviderSongs(target, page.songs, source); int added = target.size() - before;
        boolean error = !page.error.isEmpty();
        if ("wy".equals(source)) {
            if (page.total > 0) lastWyCount = Math.max(lastWyCount, page.total); lastWyError = error ? page.error : (added > 0 ? "" : lastWyError);
            if (!error) searchWyNextOffset += Math.max(1, page.songs.size());
            searchWyHasMore = error || (!page.songs.isEmpty() && providerHasMore(searchWyNextOffset, lastWyCount, SEARCH_PAGE_SIZE));
            if (!error && added == 0 && page.songs.size() < SEARCH_PAGE_SIZE) searchWyHasMore = false;
        } else if ("tx".equals(source)) {
            if (page.total > 0) lastTxCount = Math.max(lastTxCount, page.total); lastTxError = error ? page.error : (added > 0 ? "" : lastTxError);
            if (!error) searchTxNextPage++; int through = Math.max(0, (searchTxNextPage - 1) * SEARCH_PAGE_SIZE);
            searchTxHasMore = error || (!page.songs.isEmpty() && providerHasMore(through, lastTxCount, SEARCH_PAGE_SIZE));
            if (!error && added == 0 && page.songs.size() < SEARCH_PAGE_SIZE) searchTxHasMore = false;
        } else if ("kw".equals(source)) {
            if (page.total > 0) lastKwCount = Math.max(lastKwCount, page.total); lastKwError = error ? page.error : (added > 0 ? "" : lastKwError);
            if (!error) searchKwNextPage++; int through = Math.max(0, (searchKwNextPage - 1) * SEARCH_PAGE_SIZE);
            searchKwHasMore = error || (!page.songs.isEmpty() && providerHasMore(through, lastKwCount, SEARCH_PAGE_SIZE));
            if (!error && added == 0 && page.songs.size() < SEARCH_PAGE_SIZE) searchKwHasMore = false;
        } else if ("kg".equals(source)) {
            if (page.total > 0) lastKgCount = Math.max(lastKgCount, page.total); lastKgError = error ? page.error : (added > 0 ? "" : lastKgError);
            if (!error) searchKgNextPage++; int through = Math.max(0, (searchKgNextPage - 1) * SEARCH_PAGE_SIZE);
            searchKgHasMore = error || (!page.songs.isEmpty() && providerHasMore(through, lastKgCount, SEARCH_PAGE_SIZE));
            if (!error && added == 0 && page.songs.size() < SEARCH_PAGE_SIZE) searchKgHasMore = false;
        }
        return error;
    }

    private static final class SearchAllHolder {
        CurtainRevealFrame frame;
        LinearLayout wrapper;
        LinearLayout row;
        ImageView cover;
        TextView title;
        TextView artist;
        FrameLayout favorite;
        IconView favoriteIcon;
        FrameLayout next;
        FrameLayout more;
    }

    private final class SearchAllAdapter extends BaseAdapter {
        private final List<Song> items = new ArrayList<>();
        private final LinkedHashSet<String> revealedKeys = new LinkedHashSet<>();
        SearchAllAdapter(List<Song> source) { replace(source); }
        void replace(List<Song> source) {
            items.clear();
            if (source != null) items.addAll(source);
            notifyDataSetChanged();
        }
        int indexOfKey(String key) {
            if (key == null || key.isEmpty()) return -1;
            for (int i = 0; i < items.size(); i++) if (key.equals(items.get(i).key())) return i;
            return -1;
        }
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            SearchAllHolder holder;
            CurtainRevealFrame frame;
            if (convertView == null) {
                frame = new CurtainRevealFrame(MainActivity.this);
                holder = new SearchAllHolder();
                holder.frame = frame;
                holder.wrapper = Ui.column(MainActivity.this);
                holder.row = Ui.row(MainActivity.this);
                holder.row.setGravity(Gravity.CENTER_VERTICAL);
                holder.row.setPadding(Ui.dp(MainActivity.this, 9), Ui.dp(MainActivity.this, 7), Ui.dp(MainActivity.this, 7), Ui.dp(MainActivity.this, 7));
                holder.row.setBackground(Ui.glass(126, 17, 18, MainActivity.this));
                holder.row.setClickable(true); holder.row.setFocusable(true);
                Ui.applyRipple(holder.row, Color.argb(32, 255, 255, 255));

                holder.cover = new ImageView(MainActivity.this);
                holder.cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                holder.cover.setBackground(Ui.round(Color.rgb(22, 22, 28), 12, MainActivity.this));
                holder.cover.setClipToOutline(true);
                holder.row.addView(holder.cover, Ui.lp(Ui.dp(MainActivity.this, 52), Ui.dp(MainActivity.this, 52)));

                LinearLayout texts = Ui.column(MainActivity.this);
                texts.setPadding(Ui.dp(MainActivity.this, 11), Ui.dp(MainActivity.this, 2), Ui.dp(MainActivity.this, 6), Ui.dp(MainActivity.this, 2));
                holder.title = Ui.text(MainActivity.this, "", 13.7f, Ui.TEXT, true);
                holder.title.setSingleLine(true); holder.title.setEllipsize(TextUtils.TruncateAt.END);
                holder.artist = Ui.text(MainActivity.this, "", 10.8f, Ui.TEXT_2, false);
                holder.artist.setSingleLine(true); holder.artist.setEllipsize(TextUtils.TruncateAt.END);
                texts.addView(holder.title, new LinearLayout.LayoutParams(-1, 0, 1f));
                texts.addView(holder.artist, new LinearLayout.LayoutParams(-1, 0, 1f));
                holder.row.addView(texts, new LinearLayout.LayoutParams(0, Ui.dp(MainActivity.this, 52), 1f));

                holder.favorite = Ui.iconButton(MainActivity.this, IconView.Type.HEART, 36, Ui.DIM, Color.TRANSPARENT);
                holder.favoriteIcon = (IconView) holder.favorite.getChildAt(0);
                holder.row.addView(holder.favorite, Ui.lp(Ui.dp(MainActivity.this, 32), Ui.dp(MainActivity.this, 32)));

                holder.next = Ui.iconButton(MainActivity.this, IconView.Type.PLUS, 32, Ui.TEXT_2, Color.TRANSPARENT);
                holder.next.setContentDescription("添加到下一首");
                holder.row.addView(holder.next, Ui.lp(Ui.dp(MainActivity.this, 30), Ui.dp(MainActivity.this, 30)));

                holder.more = Ui.iconButton(MainActivity.this, IconView.Type.MORE, 30, Ui.DIM, Color.TRANSPARENT);
                holder.row.addView(holder.more, Ui.lp(Ui.dp(MainActivity.this, 29), Ui.dp(MainActivity.this, 29)));
                holder.wrapper.addView(holder.row, Ui.lp(-1, Ui.dp(MainActivity.this, 68)));
                holder.wrapper.addView(new Space(MainActivity.this), Ui.lp(-1, Ui.dp(MainActivity.this, 7)));
                frame.setTag(holder);
            } else {
                frame = (CurtainRevealFrame) convertView;
                holder = (SearchAllHolder) frame.getTag();
            }

            Song song = items.get(position);
            holder.title.setText(song.title);
            holder.artist.setText(subtitleFor(song));
            String coverKey = song.coverUrl == null ? "" : song.coverUrl;
            Object oldCoverKey = holder.cover.getTag();
            if (!coverKey.equals(oldCoverKey == null ? "" : String.valueOf(oldCoverKey))) {
                holder.cover.setImageDrawable(null);
                holder.cover.setTag(coverKey);
                if (!coverKey.trim().isEmpty()) ImageLoader.load(coverKey, holder.cover, null);
            }

            holder.favoriteIcon.setIconColor(isFavorite(song) ? Ui.PINK : Ui.DIM);

            SearchAllHolder bound = holder;
            holder.favorite.setOnClickListener(v -> {
                favorites = store.toggleFavorite(song);
                bound.favoriteIcon.setIconColor(isFavorite(song) ? Ui.PINK : Ui.DIM);
            });
            holder.next.setOnClickListener(v -> {
                ensurePlaybackServiceStarted();
                if (playback != null) playback.enqueueNext(song);
                else pendingNextSongs.add(song);
                toast("已添加到下一首");
            });
            holder.more.setOnClickListener(v -> showSongActions(song));
            holder.row.setOnClickListener(v -> playPickedSong(song));

            String revealKey = song.key() + "#" + position;
            if (SpringMotion.isReducedMotion() || revealedKeys.contains(revealKey)) {
                frame.showImmediately(holder.wrapper);
            } else {
                revealedKeys.add(revealKey);
                int revealAccent = progressiveLoadingAccent(Ui.CYAN);
                frame.setAccent(revealAccent);
                FluidPlaceholderView placeholder = new FluidPlaceholderView(MainActivity.this, FluidPlaceholderView.SONG_ROW, revealAccent);
                long step = position < 10 ? position * 58L : 0L;
                placeholder.setShimmerStartDelay(step);
                frame.setPlaceholder(placeholder);
                frame.reveal(holder.wrapper, position < 10 ? 42L + step : 0L);
            }
            return frame;
        }
    }

    /**
     * V74 catalog presentation: song rows and Now Playing stay source-free so music metadata
     * and playback controls own the visual hierarchy. Catalog identities remain available in
     * Song Info and the search page's explicit platform filters.
     */
    private List<String> catalogSourceCodes(Song song) {
        ArrayList<String> out = new ArrayList<>();
        if (song == null) return out;
        String[] preferredOrder = {"wy", "tx", "kw", "kg"};
        for (String code : preferredOrder) {
            if (song.variant(code) != null) out.add(code);
        }
        for (com.xingyu.music.model.SourceVariant variant : song.variants()) {
            if (variant == null || variant.source == null || variant.source.trim().isEmpty()) continue;
            String code = variant.source.trim();
            if (!out.contains(code)) out.add(code);
        }
        if (out.isEmpty() && song.source != null && !song.source.trim().isEmpty()) out.add(song.source.trim());
        return out;
    }

    private String catalogSourcesText(Song song) {
        List<String> codes = catalogSourceCodes(song);
        if (codes.isEmpty()) return "未知来源";
        StringBuilder text = new StringBuilder();
        for (String code : codes) {
            String label = Song.providerLabel(code);
            if (label == null || label.trim().isEmpty()) continue;
            if (text.length() > 0) text.append(" · ");
            text.append(label);
        }
        return text.length() == 0 ? "未知来源" : text.toString();
    }

    /**
     * V3.1 UI-only fix: multi-catalog labels can grow from one platform to four or more.
     * Keep the pill content-complete without giving it an unbounded width that would crush
     * the title/actions on narrow phones. 1-2 catalog labels stay compact; longer labels
     * wrap naturally at the middle-dot spaces into at most two centered lines.
     */
    private void configureAdaptiveSourceBadge(TextView badge, int maxWidthDp) {
        if (badge == null) return;
        badge.setSingleLine(false);
        badge.setMaxLines(2);
        badge.setEllipsize(null);
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(Ui.dp(this, 46));
        badge.setMaxWidth(Ui.dp(this, maxWidthDp));
        badge.setMinHeight(Ui.dp(this, 27));
        badge.setPadding(Ui.dp(this, 8), Ui.dp(this, 3), Ui.dp(this, 8), Ui.dp(this, 3));
        badge.setIncludeFontPadding(false);
        badge.setLineSpacing(0f, 0.94f);
    }

    private void appendUniqueProviderSongs(List<Song> target, List<Song> incoming, String source) {
        if (target == null || incoming == null || incoming.isEmpty()) return;
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Song song : target) {
            if (song == null) continue;
            com.xingyu.music.model.SourceVariant v = song.variant(source);
            seen.add(v == null || v.sourceId.isEmpty() ? song.key() : source + ":" + v.sourceId);
        }
        for (Song song : incoming) {
            if (song == null) continue;
            com.xingyu.music.model.SourceVariant v = song.variant(source);
            String key = v == null || v.sourceId.isEmpty() ? song.key() : source + ":" + v.sourceId;
            if (seen.add(key)) target.add(song);
        }
    }

    private ScrollView ancestorScrollView(View view) {
        if (view == null) return null;
        android.view.ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent instanceof ScrollView) return (ScrollView) parent;
            parent = parent.getParent();
        }
        return null;
    }

    private void addLyricSearchSection(LinearLayout holder, List<LyricSearchResult> hits, boolean allowEmpty) {
        List<LyricSearchResult> safeHits = hits == null ? new ArrayList<>() : hits;
        if (safeHits.isEmpty() && !lyricSearchPending && !lyricVerificationPending && !allowEmpty) return;

        LinearLayout root = Ui.column(this);
        LinearLayout header = Ui.row(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, "歌词匹配", 13.3f, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, Ui.lp(-2, Ui.dp(this, 36)));
        TextView meta = Ui.text(this, "", 10.7f, Ui.DIM, false);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mp = Ui.lp(-2, Ui.dp(this, 36)); mp.leftMargin = Ui.dp(this, 8);
        header.addView(meta, mp);
        header.addView(new Space(this), new LinearLayout.LayoutParams(0, Ui.dp(this, 36), 1f));
        TextView arrow = Ui.text(this, "▾", 18f, Ui.TEXT_2, true);
        arrow.setGravity(Gravity.CENTER);
        arrow.setRotation(searchLyricsExpanded ? 0f : -90f);
        arrow.setContentDescription(searchLyricsExpanded ? "收起歌词匹配" : "展开歌词匹配");
        header.addView(arrow, Ui.lp(Ui.dp(this, 36), Ui.dp(this, 36)));
        header.setClickable(true); header.setFocusable(true); Ui.applyRipple(header, Color.TRANSPARENT);

        LinearLayout content = Ui.column(this);
        LyricSectionUi ui = new LyricSectionUi(root, content, meta, arrow);
        header.setOnClickListener(v -> {
            searchLyricsExpanded = !searchLyricsExpanded;
            arrow.animate().rotation(searchLyricsExpanded ? 0f : -90f).setDuration(150).start();
            arrow.setContentDescription(searchLyricsExpanded ? "收起歌词匹配" : "展开歌词匹配");
            setSearchSectionExpanded(content, searchLyricsExpanded);
        });
        root.addView(header, marginTop(18));
        root.addView(content, marginTop(4));
        holder.addView(root, Ui.lp(-1, -2));
        if (holder == activeSearchResultsHolder) activeLyricSectionUi = ui;
        bindLyricSectionUi(ui, safeHits);
        if (holder == activeSearchResultsHolder) ensureLyricUiDrain(holder);
    }

    private void requestLyricUiRefresh(LinearLayout holder, boolean immediate) {
        if (holder == null || holder != activeSearchResultsHolder || activeLyricSectionUi == null) return;
        if (pendingLyricUiRefreshRunnable != null) main.removeCallbacks(pendingLyricUiRefreshRunnable);
        final int token = searchRequestToken;
        Runnable refresh = () -> {
            pendingLyricUiRefreshRunnable = null;
            if (token != searchRequestToken || isFinishing() || holder != activeSearchResultsHolder || activeLyricSectionUi == null) return;
            bindLyricSectionUi(activeLyricSectionUi, visibleLyricSearchResults());
        };
        pendingLyricUiRefreshRunnable = refresh;
        if (immediate) refresh.run();
        else main.postDelayed(refresh, LYRIC_UI_COALESCE_MS);
        ensureLyricUiDrain(holder);
    }

    private void ensureLyricUiDrain(LinearLayout holder) {
        if (holder == null || holder != activeSearchResultsHolder || activeLyricSectionUi == null) return;
        int total = visibleLyricSearchResults().size();
        if (total <= 0) return;
        if (lyricUiVisibleLimit <= 0) lyricUiVisibleLimit = Math.min(LYRIC_UI_BATCH_SIZE, total);
        if (lyricUiVisibleLimit >= total || pendingLyricUiDrainRunnable != null) return;
        final int token = searchRequestToken;
        pendingLyricUiDrainRunnable = () -> {
            pendingLyricUiDrainRunnable = null;
            if (token != searchRequestToken || isFinishing() || holder != activeSearchResultsHolder || activeLyricSectionUi == null) return;
            int latestTotal = visibleLyricSearchResults().size();
            lyricUiVisibleLimit = Math.min(latestTotal, Math.max(lyricUiVisibleLimit, 0) + LYRIC_UI_BATCH_SIZE);
            bindLyricSectionUi(activeLyricSectionUi, visibleLyricSearchResults());
            ensureLyricUiDrain(holder);
        };
        main.postDelayed(pendingLyricUiDrainRunnable, LYRIC_UI_DRAIN_MS);
    }

    private void bindLyricSectionUi(LyricSectionUi ui, List<LyricSearchResult> hits) {
        if (ui == null) return;
        List<LyricSearchResult> safeHits = hits == null ? new ArrayList<>() : hits;
        List<LyricSearchResult> rowHits = safeHits;
        if (lyricUiVisibleLimit > 0 && lyricUiVisibleLimit < safeHits.size())
            rowHits = new ArrayList<>(safeHits.subList(0, lyricUiVisibleLimit));
        int verifiedCount = 0;
        for (LyricSearchResult hit : safeHits) if (hit != null && hit.verified) verifiedCount++;
        String suffix = (searchSourceFilter == null || searchSourceFilter.isEmpty()) ? "" : " · " + Song.providerLabel(searchSourceFilter);
        if (lyricSearchPending) ui.meta.setText("网易 + QQ 检索中…");
        else if (lyricVerificationPending) ui.meta.setText("已发现 " + safeHits.size() + " 首 · 正在核对原句…");
        else if (verifiedCount > 0) ui.meta.setText(safeHits.size() + " 首 · 已核对 " + verifiedCount + suffix);
        else ui.meta.setText(safeHits.size() + " 首" + suffix);
        ui.arrow.setRotation(searchLyricsExpanded ? 0f : -90f);
        setSearchSectionExpanded(ui.content, searchLyricsExpanded);

        if (safeHits.isEmpty()) {
            ui.rows.clear();
            ui.showingRows = false;
            ui.content.removeAllViews();
            String text;
            if (lyricSearchPending) text = searchIntentMode == SEARCH_INTENT_LYRIC
                    ? "这更像一句歌词，正在优先反查歌曲…"
                    : "正在从网易 / QQ 歌词索引中寻找匹配…";
            else text = normalizeSuggestion(lastQuery).length() < 3
                    ? "输入至少 3 个字可搜索歌词"
                    : (lyricVerificationPending ? "正在核对真实歌词…" : "没有找到足够精准的歌词结果");
            TextView state = Ui.text(this, text, 11.2f, lyricSearchPending ? Ui.TEXT_2 : Ui.DIM, false);
            state.setGravity(Gravity.CENTER_VERTICAL);
            ui.content.addView(state, Ui.lp(-1, Ui.dp(this, lyricSearchPending ? 42 : 40)));
            return;
        }

        if (!ui.showingRows) {
            ui.content.removeAllViews();
            ui.rows.clear();
            ui.showingRows = true;
        }
        LinkedHashSet<String> wanted = new LinkedHashSet<>();
        int targetIndex = 0;
        for (LyricSearchResult hit : rowHits) {
            if (hit == null || hit.song == null) continue;
            String key = lyricUiKey(hit.song);
            if (!wanted.add(key)) continue;
            LyricRowUi row = ui.rows.get(key);
            boolean created = row == null;
            if (created) {
                row = createLyricRowUi();
                ui.rows.put(key, row);
            }
            bindLyricRowUi(row, hit);
            LinearLayout.LayoutParams cp = Ui.lp(-1, Ui.dp(this, 94));
            cp.topMargin = targetIndex > 0 ? Ui.dp(this, 8) : 0;
            if (row.card.getParent() != ui.content) {
                ui.content.addView(row.card, Math.min(targetIndex, ui.content.getChildCount()), cp);
            } else {
                int current = ui.content.indexOfChild(row.card);
                if (current != targetIndex) {
                    ui.content.removeView(row.card);
                    ui.content.addView(row.card, Math.min(targetIndex, ui.content.getChildCount()), cp);
                } else row.card.setLayoutParams(cp);
            }
            if (created) {
                row.card.setAlpha(0f);
                row.card.setTranslationY(Ui.dp(this, 7));
                row.card.animate().alpha(1f).translationY(0f).setDuration(155L)
                        .setInterpolator(new DecelerateInterpolator(1.5f)).start();
            }
            targetIndex++;
        }
        ArrayList<String> stale = new ArrayList<>();
        for (String key : ui.rows.keySet()) if (!wanted.contains(key)) stale.add(key);
        for (String key : stale) {
            LyricRowUi row = ui.rows.remove(key);
            if (row != null && row.card.getParent() == ui.content) ui.content.removeView(row.card);
        }
    }

    private String lyricUiKey(Song song) {
        if (song == null) return "";
        long durationBucket = song.durationMs > 0L ? Math.round(song.durationMs / 3000.0) : 0L;
        return normalizeSuggestion(song.title) + "|" + normalizeSuggestion(song.artist) + "|" + durationBucket;
    }

    private LyricRowUi createLyricRowUi() {
        LinearLayout card = Ui.row(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(this, 9), Ui.dp(this, 8), Ui.dp(this, 7), Ui.dp(this, 8));
        card.setBackground(Ui.glass(132, 17, 18, this));
        card.setClickable(true); card.setFocusable(true);
        Ui.applyRipple(card, Color.argb(30, 255, 255, 255));

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(Ui.round(Color.rgb(22, 22, 28), 12, this));
        cover.setClipToOutline(true);
        card.addView(cover, Ui.lp(Ui.dp(this, 54), Ui.dp(this, 54)));

        LinearLayout texts = Ui.column(this);
        texts.setPadding(Ui.dp(this, 11), 0, Ui.dp(this, 5), 0);
        TextView title = Ui.text(this, "", 13.6f, Ui.TEXT, true);
        title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title, Ui.lp(-1, Ui.dp(this, 22)));
        TextView artist = Ui.text(this, "", 10.6f, Ui.DIM, false);
        artist.setSingleLine(true); artist.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(artist, Ui.lp(-1, Ui.dp(this, 18)));
        TextView snippet = Ui.text(this, "", 11.15f, Ui.TEXT_2, false);
        snippet.setMaxLines(2); snippet.setEllipsize(TextUtils.TruncateAt.END);
        snippet.setLineSpacing(0f, 0.96f);
        texts.addView(snippet, Ui.lp(-1, Ui.dp(this, 38)));
        card.addView(texts, new LinearLayout.LayoutParams(0, Ui.dp(this, 78), 1f));

        FrameLayout more = Ui.iconButton(this, IconView.Type.MORE, 32, Ui.DIM, Color.TRANSPARENT);
        more.setContentDescription("歌曲操作");
        card.addView(more, Ui.lp(Ui.dp(this, 32), Ui.dp(this, 32)));
        return new LyricRowUi(card, cover, title, artist, snippet, more);
    }

    private void bindLyricRowUi(LyricRowUi row, LyricSearchResult hit) {
        Song song = hit.song;
        row.title.setText(song.title);
        row.artist.setText(song.artist);
        row.snippet.setText(highlightLyricSnippet(hit.snippet, lastQuery));
        String coverKey = song.coverUrl == null ? "" : song.coverUrl.trim();
        if (!coverKey.equals(row.coverKey)) {
            row.coverKey = coverKey;
            row.cover.setImageDrawable(null);
            if (!coverKey.isEmpty()) ImageLoader.load(coverKey, row.cover, null);
        }
        row.more.setOnClickListener(v -> showSongActions(song));
        row.card.setOnClickListener(v -> playPickedSong(song));
        row.card.setOnLongClickListener(v -> { showSongActions(song); return true; });
    }

    private void scheduleLyricIdentityEnrichment(String keyword, int requestToken, LinearLayout holder) {
        final List<LyricSearchResult> input = new ArrayList<>(lyricSearchResults);
        final ArrayList<Song> known = new ArrayList<>();
        known.addAll(searchResults); known.addAll(lastWySearchSongs); known.addAll(lastTxSearchSongs);
        known.addAll(lastKwSearchSongs); known.addAll(lastKgSearchSongs);
        try {
            searchMergeExecutor.submit(() -> {
                ArrayList<LyricSearchResult> enriched = new ArrayList<>();
                for (LyricSearchResult hit : input) {
                    if (hit == null || hit.song == null) continue;
                    Song song = hit.song;
                    for (Song candidate : known) {
                        if (candidate != null && ExactTrackMatcher.confidence(candidate, song) >= 0.98d) {
                            song = candidate.withVariant(song); break;
                        }
                    }
                    enriched.add(new LyricSearchResult(song, hit.snippet, hit.verified, hit.evidenceMask));
                }
                enriched.sort((a, b) -> Integer.compare(lyricMatchScore(keyword, b), lyricMatchScore(keyword, a)));
                main.post(() -> {
                    if (requestToken != searchRequestToken || isFinishing()) return;
                    lyricSearchResults = mergeLyricResultLists(keyword, lyricSearchResults, enriched, LYRIC_INDEX_LIMIT);
                    requestLyricUiRefresh(holder, false);
                });
            });
        } catch (RejectedExecutionException ignored) { }
    }

    private interface SearchSectionToggle { void onToggle(boolean expanded); }

    private LinearLayout searchSectionHeader(String title, String meta, boolean expanded, SearchSectionToggle toggle) {
        return searchSectionHeader(title, meta, expanded, toggle, "", null);
    }

    private LinearLayout searchSectionHeader(String title, String meta, boolean expanded, SearchSectionToggle toggle,
                                             String actionLabel, View.OnClickListener action) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.text(this, title, 13.3f, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(t, Ui.lp(-2, Ui.dp(this, 36)));
        TextView m = Ui.text(this, meta == null ? "" : meta, 10.7f, Ui.DIM, false);
        m.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mp = Ui.lp(-2, Ui.dp(this, 36));
        mp.leftMargin = Ui.dp(this, 8);
        row.addView(m, mp);
        Space fill = new Space(this);
        row.addView(fill, new LinearLayout.LayoutParams(0, Ui.dp(this, 36), 1f));
        if (actionLabel != null && !actionLabel.trim().isEmpty()) {
            TextView actionView = Ui.text(this, actionLabel, 11.2f, Ui.CYAN, true);
            actionView.setGravity(Gravity.CENTER);
            actionView.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
            actionView.setClickable(true); actionView.setFocusable(true);
            Ui.applyRipple(actionView, Color.TRANSPARENT);
            actionView.setOnClickListener(v -> { if (action != null) action.onClick(v); });
            row.addView(actionView, Ui.lp(-2, Ui.dp(this, 36)));
        }
        TextView arrow = Ui.text(this, "▾", 18f, Ui.TEXT_2, true);
        arrow.setGravity(Gravity.CENTER);
        arrow.setRotation(expanded ? 0f : -90f);
        arrow.setContentDescription(expanded ? "收起" + title : "展开" + title);
        row.addView(arrow, Ui.lp(Ui.dp(this, 36), Ui.dp(this, 36)));
        row.setClickable(true); row.setFocusable(true); Ui.applyRipple(row, Color.TRANSPARENT);
        row.setOnClickListener(v -> {
            boolean next = arrow.getRotation() < -45f;
            arrow.animate().rotation(next ? 0f : -90f).setDuration(150).start();
            arrow.setContentDescription(next ? "收起" + title : "展开" + title);
            if (toggle != null) toggle.onToggle(next);
        });
        return row;
    }

    private void setSearchSectionExpanded(View content, boolean expanded) {
        if (content == null) return;
        content.setVisibility(expanded ? View.VISIBLE : View.GONE);
        content.setAlpha(expanded ? 1f : 0f);
    }

    private View lyricSearchResultList(List<LyricSearchResult> hits) {
        LinearLayout box = Ui.column(this);
        for (int i = 0; i < hits.size(); i++) {
            LyricSearchResult hit = hits.get(i);
            if (hit == null || hit.song == null) continue;
            Song song = hit.song;

            LinearLayout card = Ui.row(this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(Ui.dp(this, 9), Ui.dp(this, 8), Ui.dp(this, 7), Ui.dp(this, 8));
            card.setBackground(Ui.glass(132, 17, 18, this));
            card.setClickable(true); card.setFocusable(true);
            Ui.applyRipple(card, Color.argb(30, 255, 255, 255));

            ImageView cover = new ImageView(this);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(Ui.round(Color.rgb(22, 22, 28), 12, this));
            cover.setClipToOutline(true);
            card.addView(cover, Ui.lp(Ui.dp(this, 54), Ui.dp(this, 54)));
            if (song.coverUrl != null && !song.coverUrl.trim().isEmpty()) ImageLoader.load(song.coverUrl, cover, null);

            LinearLayout texts = Ui.column(this);
            texts.setPadding(Ui.dp(this, 11), 0, Ui.dp(this, 5), 0);
            TextView title = Ui.text(this, song.title, 13.6f, Ui.TEXT, true);
            title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title, Ui.lp(-1, Ui.dp(this, 22)));
            TextView artist = Ui.text(this, song.artist, 10.6f, Ui.DIM, false);
            artist.setSingleLine(true); artist.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(artist, Ui.lp(-1, Ui.dp(this, 18)));
            TextView snippet = Ui.text(this, "", 11.15f, Ui.TEXT_2, false);
            snippet.setMaxLines(2); snippet.setEllipsize(TextUtils.TruncateAt.END);
            snippet.setLineSpacing(0f, 0.96f);
            snippet.setText(highlightLyricSnippet(hit.snippet, lastQuery));
            texts.addView(snippet, Ui.lp(-1, Ui.dp(this, 38)));
            card.addView(texts, new LinearLayout.LayoutParams(0, Ui.dp(this, 78), 1f));

            FrameLayout more = Ui.iconButton(this, IconView.Type.MORE, 32, Ui.DIM, Color.TRANSPARENT);
            more.setContentDescription("歌曲操作");
            more.setOnClickListener(v -> showSongActions(song));
            card.addView(more, Ui.lp(Ui.dp(this, 32), Ui.dp(this, 32)));

            card.setOnClickListener(v -> playPickedSong(song));
            card.setOnLongClickListener(v -> { showSongActions(song); return true; });
            LinearLayout.LayoutParams cp = Ui.lp(-1, Ui.dp(this, 94));
            if (i > 0) cp.topMargin = Ui.dp(this, 8);
            box.addView(card, cp);
        }
        return box;
    }

    private CharSequence highlightLyricSnippet(String snippet, String query) {
        String text = snippet == null ? "" : snippet.trim();
        String needle = normalizeSuggestion(query);
        if (text.isEmpty() || needle.isEmpty()) return text;
        StringBuilder normalized = new StringBuilder();
        ArrayList<Integer> map = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            String piece = normalizeSuggestion(String.valueOf(text.charAt(i)));
            for (int j = 0; j < piece.length(); j++) {
                normalized.append(piece.charAt(j));
                map.add(i);
            }
        }
        int at = normalized.indexOf(needle);
        if (at < 0 || map.isEmpty()) return text;
        int endIndex = at + needle.length() - 1;
        if (at >= map.size() || endIndex >= map.size()) return text;
        int start = map.get(at);
        int end = Math.min(text.length(), map.get(endIndex) + 1);
        if (start < 0 || end <= start) return text;
        SpannableString styled = new SpannableString(text);
        styled.setSpan(new ForegroundColorSpan(Ui.GREEN), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return styled;
    }

    private TextView allSourceChip(LinearLayout holder) {
        boolean selected = searchSourceFilter == null || searchSourceFilter.isEmpty();
        TextView chip = Ui.pill(this, selected ? "✓  全部" : "全部", Ui.TEXT_2);
        chip.setClickable(true); chip.setFocusable(true); chip.setAlpha(selected ? 1f : .82f);
        if (selected) chip.setBackground(Ui.tintedGlass(Ui.CYAN, 18, this));
        Ui.applyRipple(chip, Color.TRANSPARENT);
        chip.setOnClickListener(v -> { searchSourceFilter = ""; renderSearchResults(holder); });
        return chip;
    }

    private TextView sourceChip(String name, String source, int count, String error, int color, LinearLayout holder) {
        boolean ok = count > 0;
        boolean selected = source.equals(searchSourceFilter);
        String label = (selected ? "✓  " : "") + (ok ? name + "  " + count : (error.isEmpty() ? name + "  0" : name + "  暂不可用"));
        int tint = ok ? color : (error.isEmpty() ? Ui.DIM : Ui.RED);
        TextView chip = Ui.pill(this, label, tint);
        chip.setClickable(true); chip.setFocusable(true);
        chip.setAlpha(selected ? 1f : .82f);
        if (selected) chip.setBackground(Ui.tintedGlass(tint, 22, this));
        Ui.applyRipple(chip, Color.TRANSPARENT);
        chip.setOnClickListener(v -> {
            searchSourceFilter = source.equals(searchSourceFilter) ? "" : source;
            renderSearchResults(holder);
        });
        if (!error.isEmpty()) chip.setOnLongClickListener(v -> { showSearchDebug(); return true; });
        return chip;
    }

    private void showSearchDebug() {
        StringBuilder b = new StringBuilder();
        appendCatalogDebug(b, "网易云", lastWyCount, lastWyError);
        appendCatalogDebug(b, "QQ", lastTxCount, lastTxError);
        appendCatalogDebug(b, "酷我", lastKwCount, lastKwError);
        appendCatalogDebug(b, "酷狗", lastKgCount, lastKgError);
        showGlassMessage("搜索网络详情", b.toString(), "知道了", null, "搜索矩阵", this::showSearchPerformanceMatrix);
    }

    /** V89 lightweight search insight: enough to understand the run without turning search into a dashboard. */
    private void showSearchQuickInsight() {
        LinearLayout card = modalCard("引擎洞察", "Search Engine V2 · 本次搜索怎么完成");

        String first = searchCacheHit ? ("缓存首屏 " + searchCachePaintMs + "ms")
                : (searchFirstNetworkMs > 0 ? "网络首屏 " + searchFirstNetworkMs + "ms" : "等待第一批结果");
        LinearLayout hero = Ui.column(this);
        hero.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        hero.setBackground(Ui.tintedGlass(searchCacheHit ? Ui.CYAN : Ui.PURPLE, 18, this));
        hero.addView(Ui.text(this, first, 15.2f, Ui.TEXT, true), Ui.lp(-1, Ui.dp(this, 28)));
        String intent = searchIntentMode == SEARCH_INTENT_LYRIC ? "歌词" : searchIntentMode == SEARCH_INTENT_MIXED ? "混合" : "歌曲 / 歌手";
        hero.addView(Ui.text(this, "意图 " + intent + (searchStableMs > 0 ? " · 首轮稳定 " + searchStableMs + "ms" : " · 后台仍在补全"),
                10.7f, Ui.TEXT_2, false), Ui.lp(-1, Ui.dp(this, 24)));
        card.addView(hero, marginTop(10));

        String[] ids = {"wy", "tx", "kg", "kw"};
        String[] names = {"网易", "QQ", "酷狗", "酷我"};
        int participants = 0;
        long fastestMs = Long.MAX_VALUE;
        String fastest = "—";
        for (int i = 0; i < ids.length; i++) {
            long ms = currentSearchLatencyMs.getOrDefault(ids[i], 0L);
            if (ms > 0) {
                participants++;
                if (ms < fastestMs) { fastestMs = ms; fastest = names[i]; }
            }
        }
        int merged = searchResults == null ? 0 : searchResults.size();
        card.addView(personalizationStatGrid(
                new String[]{"参与来源", "当前最快", "合并结果", "歌词链"},
                new String[]{String.valueOf(participants), fastest, String.valueOf(merged), lyricSearchStarted ? "已启动" : "未占用"},
                new int[]{Ui.CYAN, Ui.GREEN, Ui.PURPLE, lyricSearchStarted ? Ui.GOLD : Ui.TEXT_2}), marginTop(12));

        TextView note = Ui.text(this, searchCacheHit
                ? "这次先命中了本地搜索快照，网络结果只在后台做新鲜度补全；临时播放 URL 不进入搜索缓存。"
                : "搜索优先让最快的来源先形成可用首屏，其余来源后台补齐；普通歌手/歌名搜索不会让歌词验证阻塞首屏。",
                10.6f, Ui.TEXT_2, false);
        note.setLineSpacing(0f, 1.18f);
        card.addView(note, marginTop(12));

        LinearLayout actions = Ui.row(this);
        TextView close = modalButton("关闭", false);
        close.setOnClickListener(v -> hideModal());
        actions.addView(close, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f));
        Space gap = new Space(this); actions.addView(gap, Ui.lp(Ui.dp(this, 8), 1));
        TextView full = modalButton("完整搜索矩阵", true);
        full.setOnClickListener(v -> { dismissModalNow(); showSearchPerformanceMatrix(); });
        actions.addView(full, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1.35f));
        card.addView(actions, marginTop(14));

        TextView gesture = Ui.text(this, "提示：长按右上角引擎键可直接进入完整搜索矩阵。", 9.7f, Ui.DIM, false);
        gesture.setGravity(Gravity.CENTER);
        card.addView(gesture, marginTop(8));
        presentModal(card);
    }

    private void showSearchPerformanceMatrix() { showEngineMatrixCenter(2); }

    private void buildSearchMatrixPanel(LinearLayout panel) {
        panel.addView(Ui.text(this, "搜索矩阵", 19f, Ui.TEXT, true), marginTop(4));
        panel.addView(Ui.text(this, "Search Engine V2 · 最近一次搜索调度与来源健康", 10.7f, Ui.TEXT_2, false), marginTop(2));
        LinearLayout content = Ui.column(this);
        LinearLayout hero = Ui.column(this);
        hero.setPadding(Ui.dp(this, 14), Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 13));
        hero.setBackground(Ui.tintedGlass(searchCacheHit ? Ui.CYAN : Ui.PURPLE, 18, this));
        String first = searchCacheHit ? ("缓存首屏 " + searchCachePaintMs + "ms") : (searchFirstNetworkMs > 0 ? "网络首屏 " + searchFirstNetworkMs + "ms" : "暂无最近搜索性能数据");
        hero.addView(Ui.text(this, first, 15.2f, Ui.TEXT, true), Ui.lp(-1, Ui.dp(this, 28)));
        String stable = searchStableMs > 0 ? (" · 完整首轮 " + searchStableMs + "ms") : " · 等待下一次搜索";
        String intent = searchIntentMode == SEARCH_INTENT_LYRIC ? "歌词" : searchIntentMode == SEARCH_INTENT_MIXED ? "混合" : "歌曲 / 歌手";
        hero.addView(Ui.text(this, "意图 " + intent + stable + (lyricSearchStarted ? " · 歌词链已启动" : " · 歌词链未占用"), 10.8f, Ui.TEXT_2, false), Ui.lp(-1, Ui.dp(this, 26)));
        content.addView(hero, marginTop(10));
        String[] ids = {"wy", "tx", "kg", "kw"}; String[] names = {"网易", "QQ", "酷狗", "酷我"};
        for (int i = 0; i < ids.length; i++) {
            SearchPerformanceStore.Stats stat = searchPerformance == null ? null : searchPerformance.stats(ids[i]);
            long current = currentSearchLatencyMs.getOrDefault(ids[i], 0L);
            String value = current > 0 ? current + "ms 本次" : "等待 / 未启动";
            String learned = stat == null ? "尚无历史" : ("近期 " + stat.compact() + " · 样本 " + stat.samples);
            LinearLayout row = Ui.row(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
            row.setBackground(Ui.glass(48, 14, 16, this));
            TextView name = Ui.text(this, names[i], 12.8f, Ui.TEXT, true); row.addView(name, Ui.lp(Ui.dp(this, 64), Ui.dp(this, 54)));
            LinearLayout text = Ui.column(this); text.addView(Ui.text(this, value, 11.4f, Ui.TEXT_2, true), Ui.lp(-1, Ui.dp(this, 25))); text.addView(Ui.text(this, learned, 10.1f, Ui.DIM, false), Ui.lp(-1, Ui.dp(this, 23)));
            row.addView(text, new LinearLayout.LayoutParams(0, Ui.dp(this, 54), 1f)); content.addView(row, marginTop(i == 0 ? 10 : 6));
        }
        TextView note = Ui.text(this, "第一波优先近期最快的 2 个来源，约 420ms 后补其余来源；缓存只保存歌曲元数据，不保存临时播放 URL。", 10.6f, Ui.TEXT_2, false);
        note.setLineSpacing(0f, 1.14f); content.addView(note, marginTop(12));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false); scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        // V90: the old thin close strip is replaced by a full-width, touch-friendly bottom action.
        TextView close = modalButton("关闭矩阵", true); close.setTextSize(12.5f); close.setOnClickListener(v -> hideModal());
        LinearLayout.LayoutParams cp = Ui.lp(-1, Ui.dp(this, 52)); cp.topMargin = Ui.dp(this, 10); panel.addView(close, cp);
    }

    private static void appendCatalogDebug(StringBuilder b, String name, int count, String error) {
        if (b.length() > 0) b.append("\n\n");
        b.append(name).append("：").append(count).append(" 条");
        if (error != null && !error.isEmpty()) b.append("\n").append(error);
    }

    private View playlistsPage() {
        LinearLayout body = Ui.column(this);

        LinearLayout actions = Ui.row(this);
        View create = smallLibraryAction(IconView.Type.PLUS, "新建歌单", "自己整理", Ui.PURPLE);
        create.setOnClickListener(v -> showCreatePlaylistDialog());
        actions.addView(create, new LinearLayout.LayoutParams(0, Ui.dp(this, 82), 1f));
        Space gap = new Space(this); actions.addView(gap, Ui.lp(Ui.dp(this, 10), 1));
        View imported = smallLibraryAction(IconView.Type.PLAYLIST, "导入歌单", "四平台 / Lunaxy 文件", Ui.CYAN);
        imported.setOnClickListener(v -> showImportDialog());
        actions.addView(imported, new LinearLayout.LayoutParams(0, Ui.dp(this, 82), 1f));
        body.addView(actions, Ui.lp(-1, Ui.dp(this, 82)));

        LinearLayout manageRow = Ui.row(this);
        View merge = smallLibraryAction(IconView.Type.PLUS, "合并歌单", "任选两个 · 自动去重", Ui.GREEN);
        merge.setOnClickListener(v -> showMergePlaylists());
        manageRow.addView(merge, new LinearLayout.LayoutParams(0, Ui.dp(this, 76), 1f));
        Space mg = new Space(this); manageRow.addView(mg, Ui.lp(Ui.dp(this, 10), 1));
        View manage = smallLibraryAction(IconView.Type.DELETE, "批量管理", "多选删除歌单", Ui.ORANGE);
        manage.setOnClickListener(v -> showPlaylistManager());
        manageRow.addView(manage, new LinearLayout.LayoutParams(0, Ui.dp(this, 76), 1f));
        LinearLayout.LayoutParams mrp = Ui.lp(-1, Ui.dp(this, 76)); mrp.topMargin = Ui.dp(this, 10);
        body.addView(manageRow, mrp);

        List<ImportedPlaylist> mine = new ArrayList<>();
        List<ImportedPlaylist> importedLists = new ArrayList<>();
        for (ImportedPlaylist p : playlists) {
            if (p == null) continue;
            if ("local".equals(p.source)) mine.add(p); else importedLists.add(p);
        }
        body.addView(playlistCategorySection("mine", "我的歌单", mine.size() + " 个",
                mine), marginTop(24));
        body.addView(playlistCategorySection("imported", "导入歌单", importedLists.size() + " 个",
                importedLists), marginTop(8));
        body.addView(offlineLibrarySection(), marginTop(8));
        return pageScaffold("歌单", "本地创建 · 平台导入 · 离线音乐", body);
    }

    private View offlineLibrarySection() {
        LinearLayout section = Ui.column(this);
        LinearLayout header = Ui.row(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(Ui.dp(this, 2), 0, Ui.dp(this, 4), 0);
        header.addView(Ui.text(this, "离线", 17.5f, Ui.TEXT, true), Ui.lp(-2, Ui.dp(this, 40)));
        List<OfflineStore.Record> records = offlineStore == null ? new ArrayList<>() : offlineStore.all();
        int localCount=0,downloadCount=0; for(OfflineStore.Record r:records){if(r!=null&&OfflineStore.ORIGIN_LOCAL.equals(r.origin))localCount++;else downloadCount++;}
        TextView count = Ui.text(this, records.size() + " 首 · 下载 " + downloadCount + " · 本地 " + localCount, 10.6f, Ui.DIM, false);
        LinearLayout.LayoutParams clp = Ui.lp(-2, Ui.dp(this, 40)); clp.leftMargin = Ui.dp(this, 10); header.addView(count, clp);
        header.addView(new Space(this), new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1f));
        FrameLayout tasks = Ui.iconButton(this, IconView.Type.DOWNLOAD, 34, Ui.CYAN, Color.argb(18,92,205,255));
        tasks.setContentDescription("下载中心"); tasks.setOnClickListener(v -> showDownloadCenter());
        header.addView(tasks, Ui.lp(Ui.dp(this, 36), Ui.dp(this, 36)));
        TextView arrow = Ui.text(this, "›", 22f, Ui.CYAN, true); arrow.setGravity(Gravity.CENTER); header.addView(arrow, Ui.lp(Ui.dp(this, 38), Ui.dp(this, 38)));
        header.setClickable(true); Ui.applyRipple(header, Color.TRANSPARENT); header.setOnClickListener(v -> { offlinePlaylistOpen = true; openPlaylist = null; offlineLibraryFilter="all"; suppressNextPageAnimation = true; renderTab(); });
        section.addView(header, Ui.lp(-1, Ui.dp(this, 40)));

        LinearLayout card = Ui.row(this); card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0); card.setBackground(Ui.tintedGlass(Ui.CYAN, 19, this));
        FrameLayout iconShell = Ui.iconButton(this, IconView.Type.MUSIC, 42, Ui.CYAN, Color.argb(20, 92, 205, 255)); card.addView(iconShell, Ui.lp(Ui.dp(this, 46), Ui.dp(this, 46)));
        LinearLayout texts = Ui.column(this); texts.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), 0, Ui.dp(this, 7));
        texts.addView(Ui.text(this, "离线音乐", 14.5f, Ui.TEXT, true), new LinearLayout.LayoutParams(-1, 0, 1f));
        texts.addView(Ui.text(this, records.isEmpty() ? "下载歌曲和本地导入都会出现在这里" : "统一离线库 · 播放时优先本地文件", 10.4f, Ui.TEXT_2, false), new LinearLayout.LayoutParams(-1, 0, 1f));
        card.addView(texts, new LinearLayout.LayoutParams(0, Ui.dp(this, 62), 1f));
        card.setClickable(true); Ui.applyRipple(card, Color.TRANSPARENT); card.setOnClickListener(v -> { offlinePlaylistOpen = true; openPlaylist = null; offlineLibraryFilter="all"; suppressNextPageAnimation = true; renderTab(); });
        LinearLayout.LayoutParams cp = Ui.lp(-1, Ui.dp(this, 62)); cp.topMargin = Ui.dp(this, 6); section.addView(card, cp);
        return section;
    }

    private View offlinePlaylistPage() {
        LinearLayout body = Ui.column(this);
        LinearLayout back = Ui.row(this); back.setGravity(Gravity.CENTER_VERTICAL);
        back.addView(new IconView(this, IconView.Type.BACK, Ui.CYAN), Ui.lp(Ui.dp(this, 24), Ui.dp(this, 24)));
        TextView bt = Ui.text(this, "返回", 13, Ui.CYAN, true);
        back.addView(bt, new LinearLayout.LayoutParams(0, Ui.dp(this, 34), 1f));
        back.setClickable(true);
        back.setOnClickListener(v -> closePlaylistToLibrary());
        body.addView(back, Ui.lp(-1, Ui.dp(this, 38)));

        List<OfflineStore.Record> allRecords = offlineStore == null ? new ArrayList<>() : offlineStore.all();
        List<OfflineStore.Record> records = offlineStore == null ? new ArrayList<>() : offlineStore.allByOrigin(offlineLibraryFilter);
        lastOfflineRecordCount = allRecords.size();
        int localCount = 0, downloadCount = 0;
        for (OfflineStore.Record r : allRecords) {
            if (r != null && OfflineStore.ORIGIN_LOCAL.equals(r.origin)) localCount++; else downloadCount++;
        }

        // V5: music first. Utilities live in the title bar; content statistics stay quiet.
        TextView stats = Ui.text(this, allRecords.size() + " 首  ·  "
                + formatStorage(offlineStore == null ? 0L : offlineStore.totalBytes()), 11.4f, Ui.TEXT_2, false);
        body.addView(stats, marginTop(7));

        LinearLayout filterRow = Ui.row(this);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        String[] keys = new String[]{"all", OfflineStore.ORIGIN_DOWNLOAD, OfflineStore.ORIGIN_LOCAL};
        String[] labels = new String[]{"全部 " + allRecords.size(), "下载 " + downloadCount, "本地 " + localCount};
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            boolean selected = key.equals(offlineLibraryFilter);
            LinearLayout tabItem = Ui.column(this);
            tabItem.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView label = Ui.text(this, labels[i], 11.4f, selected ? Ui.CYAN : Ui.TEXT_2, selected);
            label.setGravity(Gravity.CENTER);
            tabItem.addView(label, new LinearLayout.LayoutParams(-1, Ui.dp(this, 30)));
            View indicator = new View(this);
            indicator.setBackground(Ui.round(selected ? Ui.CYAN : Color.TRANSPARENT, 2, this));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 2));
            tabItem.addView(indicator, ip);
            tabItem.setClickable(true);
            Ui.applyRipple(tabItem, Color.TRANSPARENT);
            tabItem.setOnClickListener(v -> {
                offlineLibraryFilter = key;
                suppressNextPageAnimation = true;
                renderTab();
            });
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1f);
            if (i > 0) tp.leftMargin = Ui.dp(this, 8);
            filterRow.addView(tabItem, tp);
        }
        LinearLayout.LayoutParams frp = Ui.lp(-1, Ui.dp(this, 40));
        frp.topMargin = Ui.dp(this, 8);
        body.addView(filterRow, frp);

        if (records.isEmpty()) body.addView(compactEmpty("当前分类还没有歌曲"), marginTop(12));
        else {
            int i = 0;
            for (OfflineStore.Record r : records) body.addView(offlineSongRow(r, records), marginTop(i++ == 0 ? 12 : 8));
        }

        // Three compact ghost actions use otherwise-empty title-bar space instead of another dashboard card.
        LinearLayout titleActions = Ui.row(this);
        titleActions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        FrameLayout scan = Ui.iconButton(this, IconView.Type.SCAN, 48, Ui.GREEN, Color.TRANSPARENT);
        scan.setContentDescription("扫描本地音乐");
        scan.setOnClickListener(v -> requestLocalMusicScan());
        FrameLayout tasks = Ui.iconButton(this, IconView.Type.DOWNLOAD, 48, Ui.CYAN, Color.TRANSPARENT);
        tasks.setContentDescription("下载任务");
        tasks.setOnClickListener(v -> showDownloadCenter());
        FrameLayout systemSound = Ui.iconButton(this, IconView.Type.BELL, 48, Ui.DIM, Color.TRANSPARENT);
        systemSound.setContentDescription("系统声");
        systemSound.setOnClickListener(v -> showSystemSoundStatusDialog());
        titleActions.addView(scan, Ui.lp(Ui.dp(this, 48), Ui.dp(this, 48)));
        LinearLayout.LayoutParams ta2 = Ui.lp(Ui.dp(this, 48), Ui.dp(this, 48)); ta2.leftMargin = Ui.dp(this, 4); titleActions.addView(tasks, ta2);
        LinearLayout.LayoutParams ta3 = Ui.lp(Ui.dp(this, 48), Ui.dp(this, 48)); ta3.leftMargin = Ui.dp(this, 4); titleActions.addView(systemSound, ta3);

        systemSound.postDelayed(() -> maybeShowContextCoach(COACH_OFFLINE_SYSTEM, systemSound,
                "查看手机当前系统声音", "这里可以确认来电铃声、通知音和闹钟音。"), 180L);
        scan.postDelayed(() -> maybeShowContextCoach(COACH_LOCAL_SCAN, scan,
                "扫描本地音乐", "先试听和筛选，再决定哪些音频加入离线音乐。"), 240L);
        tasks.postDelayed(() -> maybeShowContextCoach(COACH_DOWNLOAD_CENTER, tasks,
                "下载任务", "整张歌单和批量下载都可以在这里查看进度、暂停或继续。"), 310L);

        return pageScaffold("离线", "下载与本地导入的统一音乐库", body, titleActions);
    }

    private View offlineSongRow(OfflineStore.Record record, List<OfflineStore.Record> allRecords) {
        Song song = record.song; LinearLayout row = Ui.row(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 5), 0); row.setBackground(Ui.glass(135, 18, 20, this));
        ImageView cover = new ImageView(this); cover.setScaleType(ImageView.ScaleType.CENTER_CROP); cover.setBackground(Ui.round(Color.rgb(24,24,31), 11, this)); cover.setClipToOutline(true); ImageLoader.load(song.coverUrl, cover, null); row.addView(cover, Ui.lp(Ui.dp(this, 48), Ui.dp(this, 48)));
        LinearLayout text = Ui.column(this); text.setPadding(Ui.dp(this, 11), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 7)); TextView title = Ui.text(this, song.title, 14.2f, Ui.TEXT, true); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END); text.addView(title, new LinearLayout.LayoutParams(-1, 0, 1f));
        String origin=OfflineStore.ORIGIN_LOCAL.equals(record.origin)?"本地导入":record.quality;
        TextView sub = Ui.text(this, song.artist + " · " + origin + " · " + formatStorage(record.file().length()), 10.2f, Ui.TEXT_2, false); sub.setSingleLine(true); sub.setEllipsize(TextUtils.TruncateAt.END); text.addView(sub, new LinearLayout.LayoutParams(-1, 0, 1f)); row.addView(text, new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1f));

        // Offline rows mirror the playlist action language: bell / + / more.
        FrameLayout ring = Ui.iconButton(this, IconView.Type.BELL, 32, Ui.CYAN, Color.TRANSPARENT); ring.setContentDescription("铃声");
        ring.setOnClickListener(v -> {
            if (!contextCoachDone(COACH_OFFLINE_RINGTONE) && !deferredContextCoachThisSession.contains(COACH_OFFLINE_RINGTONE) && contextCoachOverlay == null) {
                maybeShowContextCoach(COACH_OFFLINE_RINGTONE, ring,"把离线歌曲做成系统声音","点铃铛可以截取片段，并设置为来电铃声、通知音或闹钟音。" ); return;
            }
            showRingtoneOptions(record);
        }); row.addView(ring, Ui.lp(Ui.dp(this, 34), Ui.dp(this, 34)));

        FrameLayout plus = Ui.iconButton(this, IconView.Type.PLUS, 32, Ui.GREEN, Color.TRANSPARENT); plus.setContentDescription("添加到下一首");
        plus.setOnClickListener(v->{if(playback==null){toast("播放器还在连接");return;}playback.enqueueNext(song);toast("已添加到下一首");}); row.addView(plus,Ui.lp(Ui.dp(this,34),Ui.dp(this,34)));
        FrameLayout more = Ui.iconButton(this, IconView.Type.MORE, 31, Ui.DIM, Color.TRANSPARENT); more.setOnClickListener(v -> showSongActions(song)); row.addView(more, Ui.lp(Ui.dp(this, 32), Ui.dp(this, 32)));
        row.setClickable(true); Ui.applyRipple(row, Color.TRANSPARENT); row.setOnClickListener(v -> { List<Song> songs = new ArrayList<>(); int start = 0; for (int i=0;i<allRecords.size();i++){songs.add(allRecords.get(i).song); if (song.key().equals(allRecords.get(i).song.key())) start=i;} playQueue(songs,start); });
        return row;
    }


    private boolean hasLocalAudioPermission() {
        if (Build.VERSION.SDK_INT >= 33) return checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocalMusicScan() {
        if (hasLocalAudioPermission()) { scanLocalMusic(); return; }
        pendingLocalMusicScan = true;
        String permission = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        requestPermissions(new String[]{permission}, REQ_LOCAL_AUDIO);
    }

    private void scanLocalMusic() {
        showBusy(true);
        io.submit(() -> {
            List<LocalMusicScanner.Item> items = LocalMusicScanner.scan(this);
            main.post(() -> {
                showBusy(false);
                if (items.isEmpty()) { toast("没有扫描到可访问的本地音频"); return; }
                showLocalMusicReview(items);
            });
        });
    }

    private void showLocalMusicReview(List<LocalMusicScanner.Item> allItems) {
        if (allItems == null || allItems.isEmpty()) { toast("没有可导入的本地音频"); return; }
        stopLocalMusicPreview(true);
        final ArrayList<LocalMusicScanner.Item> visible = new ArrayList<>();
        final LinkedHashMap<String, LocalMusicScanner.Item> selected = new LinkedHashMap<>();
        final int[] filter = new int[]{0}; // 0 recommended, 1 all, 2 recording, 3 short
        LinearLayout card = modalCard("扫描本地音乐", "");

        TextView summary = Ui.text(this, "扫描到 " + allItems.size() + " 个音频 · 可以先试听，再决定是否导入", 10.8f, Ui.TEXT_2, false);
        card.addView(summary, Ui.lp(-1, Ui.dp(this, 28)));

        LinearLayout tabs = Ui.row(this);
        String[] tabNames = new String[]{"推荐音乐", "全部音频", "可能是录音", "短音频"};
        TextView[] tabViews = new TextView[tabNames.length];
        for (int i = 0; i < tabNames.length; i++) {
            final int index = i;
            TextView t = modalButton(tabNames[i], false);
            t.setTextSize(10.6f);
            tabViews[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1f);
            if (i > 0) lp.leftMargin = Ui.dp(this, 5);
            tabs.addView(t, lp);
            t.setOnClickListener(v -> filter[0] = index);
        }
        tabViews[0].setBackground(Ui.tintedGlass(Ui.CYAN, 15, this));
        LinearLayout.LayoutParams tabsLp = Ui.lp(-1, Ui.dp(this, 40));
        tabsLp.topMargin = Ui.dp(this, 6);
        card.addView(tabs, tabsLp);

        LinearLayout tools = Ui.row(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        TextView counter = Ui.text(this, "已选 0 首", 11.2f, Ui.TEXT_2, true);
        tools.addView(counter, new LinearLayout.LayoutParams(0, Ui.dp(this, 38), 1f));
        TextView all = outlineAction("全选当前");
        all.setTextColor(Ui.CYAN);
        tools.addView(all, Ui.lp(Ui.dp(this, 88), Ui.dp(this, 38)));
        LinearLayout.LayoutParams toolsLp = Ui.lp(-1, Ui.dp(this, 38));
        toolsLp.topMargin = Ui.dp(this, 8);
        card.addView(tools, toolsLp);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setVerticalScrollBarEnabled(false);
        final BaseAdapter[] adapterRef = new BaseAdapter[1];
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return visible.size(); }
            @Override public Object getItem(int position) { return visible.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                LocalMusicScanner.Item item = visible.get(position);
                LinearLayout row = Ui.row(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 5), Ui.dp(MainActivity.this, 6), Ui.dp(MainActivity.this, 5));
                row.setBackground(Ui.glass(82, 14, 17, MainActivity.this));

                ImageView cover = new ImageView(MainActivity.this);
                cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cover.setClipToOutline(true);
                cover.setBackground(Ui.round(Color.rgb(24,24,31), 9, MainActivity.this));
                if (!item.artworkUri.isEmpty()) ImageLoader.load(item.artworkUri, cover, null);
                row.addView(cover, Ui.lp(Ui.dp(MainActivity.this, 42), Ui.dp(MainActivity.this, 42)));

                LinearLayout texts = Ui.column(MainActivity.this);
                texts.setPadding(Ui.dp(MainActivity.this, 9), 0, Ui.dp(MainActivity.this, 4), 0);
                TextView title = Ui.text(MainActivity.this, item.title, 12.6f, Ui.TEXT, true);
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.END);
                String meta = item.artist + " · " + Ui.time(item.durationMs) + " · " + item.folder;
                TextView sub = Ui.text(MainActivity.this, meta, 9.8f, Ui.DIM, false);
                sub.setSingleLine(true);
                sub.setEllipsize(TextUtils.TruncateAt.END);
                texts.addView(title, new LinearLayout.LayoutParams(-1, 0, 1f));
                texts.addView(sub, new LinearLayout.LayoutParams(-1, 0, 1f));
                row.addView(texts, new LinearLayout.LayoutParams(0, Ui.dp(MainActivity.this, 42), 1f));

                boolean previewing = item.stableKey().equals(localMusicPreviewKey) && localMusicPreviewPlayer != null;
                FrameLayout preview = Ui.iconButton(MainActivity.this, previewing ? IconView.Type.PAUSE : IconView.Type.PLAY,
                        31, previewing ? Ui.CYAN : Ui.TEXT_2, Color.argb(16,255,255,255));
                preview.setContentDescription(previewing ? "停止试听" : "试听");
                preview.setOnClickListener(v -> toggleLocalMusicPreview(item, () -> {
                    if (adapterRef[0] != null) adapterRef[0].notifyDataSetChanged();
                }));
                row.addView(preview, Ui.lp(Ui.dp(MainActivity.this, 34), Ui.dp(MainActivity.this, 34)));
                if (position == 0) preview.postDelayed(() -> maybeShowContextCoach(COACH_LOCAL_PREVIEW, preview,
                        "不确定是什么？先试听", "试听只播放这条本地音频，不会加入正式播放队列，也不会修改手机里的原文件。"), 260L);

                CheckBox check = new CheckBox(MainActivity.this);
                check.setButtonTintList(new ColorStateList(
                        new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                        new int[]{Ui.CYAN, Ui.DIM}));
                check.setChecked(selected.containsKey(item.stableKey()));
                row.addView(check, Ui.lp(Ui.dp(MainActivity.this, 40), Ui.dp(MainActivity.this, 40)));

                SimpleAction update = () -> {
                    counter.setText("已选 " + selected.size() + " 首");
                    all.setText(allVisibleSelected(visible, selected) ? "取消当前" : "全选当前");
                };
                SimpleAction toggle = () -> {
                    if (selected.containsKey(item.stableKey())) selected.remove(item.stableKey());
                    else selected.put(item.stableKey(), item);
                    check.setChecked(selected.containsKey(item.stableKey()));
                    update.run();
                };
                row.setClickable(true);
                Ui.applyRipple(row, Color.TRANSPARENT);
                row.setOnClickListener(v -> toggle.run());
                check.setOnClickListener(v -> {
                    if (check.isChecked()) selected.put(item.stableKey(), item); else selected.remove(item.stableKey());
                    update.run();
                });
                return row;
            }
        };
        adapterRef[0] = adapter;
        list.setAdapter(adapter);

        Runnable refresh = () -> {
            rebuildLocalScanList(allItems, visible, filter[0]);
            adapter.notifyDataSetChanged();
            counter.setText("已选 " + selected.size() + " 首");
            all.setText(allVisibleSelected(visible, selected) ? "取消当前" : "全选当前");
        };
        for (int i = 0; i < tabViews.length; i++) {
            final int index = i;
            tabViews[i].setOnClickListener(v -> {
                filter[0] = index;
                for (int k = 0; k < tabViews.length; k++) {
                    tabViews[k].setBackground(k == filter[0] ? Ui.tintedGlass(Ui.CYAN,15,this) : Ui.glass(70,15,20,this));
                }
                refresh.run();
            });
        }
        refresh.run();
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        listLp.topMargin = Ui.dp(this, 7);
        card.addView(list, listLp);

        all.setOnClickListener(v -> {
            boolean clear = allVisibleSelected(visible, selected);
            for (LocalMusicScanner.Item item : visible) {
                if (clear) selected.remove(item.stableKey()); else selected.put(item.stableKey(), item);
            }
            adapter.notifyDataSetChanged();
            counter.setText("已选 " + selected.size() + " 首");
            all.setText(allVisibleSelected(visible, selected) ? "取消当前" : "全选当前");
        });

        TextView importButton = modalButton("导入到离线音乐", true);
        importButton.setOnClickListener(v -> {
            if (selected.isEmpty()) { toast("先选择要导入的音乐"); return; }
            ArrayList<LocalMusicScanner.Item> chosen = new ArrayList<>(selected.values());
            dismissModalNow();
            importLocalMusic(chosen);
        });
        LinearLayout.LayoutParams ip = Ui.lp(-1, Ui.dp(this, 48));
        ip.topMargin = Ui.dp(this, 9);
        card.addView(importButton, ip);
        presentFractionModal(card, .86f);
    }

    private void toggleLocalMusicPreview(LocalMusicScanner.Item item, Runnable refresh) {
        if (item == null || item.uri == null) return;
        String key = item.stableKey();
        if (key.equals(localMusicPreviewKey) && localMusicPreviewPlayer != null) {
            stopLocalMusicPreview(true);
            if (refresh != null) refresh.run();
            return;
        }

        stopLocalMusicPreview(false);
        if (!localMusicPreviewResumePlayback && playback != null) {
            try {
                PlaybackSnapshot snapshot = playback.snapshot();
                if (snapshot != null && snapshot.playing && snapshot.song != null) {
                    localMusicPreviewResumeSongKey = snapshot.song.key();
                    playback.pause();
                    localMusicPreviewResumePlayback = true;
                }
            } catch (Exception ignored) { }
        }

        localMusicPreviewKey = key;
        localMusicPreviewUiRefresh = refresh;
        try {
            android.media.MediaPlayer player = new android.media.MediaPlayer();
            player.setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(this, item.uri);
            player.setOnPreparedListener(mp -> {
                try { mp.start(); } catch (Exception ignored) { }
                if (localMusicPreviewUiRefresh != null) localMusicPreviewUiRefresh.run();
            });
            player.setOnCompletionListener(mp -> stopLocalMusicPreview(true));
            player.setOnErrorListener((mp, what, extra) -> {
                toast("这条音频暂时无法试听");
                stopLocalMusicPreview(true);
                return true;
            });
            localMusicPreviewPlayer = player;
            player.prepareAsync();
            if (refresh != null) refresh.run();
        } catch (Exception e) {
            stopLocalMusicPreview(true);
            toast("这条音频暂时无法试听");
        }
    }

    private void stopLocalMusicPreview(boolean resumePlayback) {
        Runnable refresh = localMusicPreviewUiRefresh;
        localMusicPreviewUiRefresh = null;
        if (localMusicPreviewPlayer != null) {
            try { localMusicPreviewPlayer.stop(); } catch (Exception ignored) { }
            try { localMusicPreviewPlayer.reset(); } catch (Exception ignored) { }
            try { localMusicPreviewPlayer.release(); } catch (Exception ignored) { }
            localMusicPreviewPlayer = null;
        }
        localMusicPreviewKey = "";
        if (refresh != null) {
            try { refresh.run(); } catch (Exception ignored) { }
        }
        if (resumePlayback && localMusicPreviewResumePlayback) {
            boolean shouldResume = false;
            if (playback != null && !localMusicPreviewResumeSongKey.isEmpty()) {
                try {
                    PlaybackSnapshot now = playback.snapshot();
                    shouldResume = now != null && now.song != null
                            && localMusicPreviewResumeSongKey.equals(now.song.key()) && !now.playing;
                } catch (Exception ignored) { }
            }
            localMusicPreviewResumePlayback = false;
            localMusicPreviewResumeSongKey = "";
            if (shouldResume && playback != null) {
                try { playback.resume(); } catch (Exception ignored) { }
            }
        } else if (!resumePlayback && !localMusicPreviewResumePlayback) {
            localMusicPreviewResumeSongKey = "";
        }
    }

    private void rebuildLocalScanList(List<LocalMusicScanner.Item> all, List<LocalMusicScanner.Item> visible, int filter) {
        visible.clear();
        if(all==null)return;
        for(LocalMusicScanner.Item item:all){
            if(item==null)continue;
            if(filter==0&&!item.recommended)continue;
            if(filter==2&&!item.recordingLike)continue;
            if(filter==3&&!item.shortAudio)continue;
            visible.add(item);
        }
    }

    private boolean allVisibleSelected(List<LocalMusicScanner.Item> visible, Map<String,LocalMusicScanner.Item> selected){
        if(visible==null||visible.isEmpty())return false;
        for(LocalMusicScanner.Item item:visible)if(item!=null&&!selected.containsKey(item.stableKey()))return false;
        return true;
    }

    private void importLocalMusic(List<LocalMusicScanner.Item> chosen) {
        if(chosen==null||chosen.isEmpty())return;
        showBusy(true); final int total=chosen.size();
        io.submit(()->{
            int imported=0,skipped=0,failed=0;
            File dir=new File(getFilesDir(),"local_music"); if(!dir.exists())dir.mkdirs();
            for(LocalMusicScanner.Item item:chosen){
                if(item==null||item.uri==null){failed++;continue;}
                Song song=new Song("local",item.stableKey(),item.title,item.artist,item.album,item.artworkUri,item.durationMs);
                if(offlineStore!=null&&offlineStore.contains(song)){skipped++;continue;}
                String ext=localExtension(item.displayName,item.mime);
                File out=new File(dir,safeLocalFileName(item.title)+"_"+Integer.toHexString(item.stableKey().hashCode())+ext);
                try(InputStream in=getContentResolver().openInputStream(item.uri);OutputStream os=new java.io.FileOutputStream(out)){
                    if(in==null)throw new IllegalStateException("open failed");byte[] buf=new byte[65536];int n;while((n=in.read(buf))>0)os.write(buf,0,n);
                    os.flush(); if(out.length()<=0L)throw new IllegalStateException("empty");
                    offlineStore.putLocalCopy(song,out,item.mime);imported++;
                }catch(Exception e){failed++;try{out.delete();}catch(Exception ignored){}}
            }
            int ok=imported,skip=skipped,bad=failed;
            main.post(()->{showBusy(false);offlineLibraryFilter="local";if(offlinePlaylistOpen){suppressNextPageAnimation=true;renderTab();}toast("本地音乐导入完成 · "+ok+" 首"+(skip>0?" · 已存在 "+skip:"")+(bad>0?" · 失败 "+bad:""));});
        });
    }

    private String safeLocalFileName(String value){String v=value==null?"music":value.trim();if(v.isEmpty())v="music";v=v.replaceAll("[\\\\/:*?\"<>|\\r\\n]+","_");return v.length()>48?v.substring(0,48):v;}
    private String localExtension(String displayName,String mime){String n=displayName==null?"":displayName;int dot=n.lastIndexOf('.');if(dot>=0&&dot<n.length()-1&&n.length()-dot<=7)return n.substring(dot).toLowerCase(Locale.ROOT);if(mime!=null&&mime.contains("flac"))return ".flac";if(mime!=null&&mime.contains("wav"))return ".wav";if(mime!=null&&mime.contains("mp4"))return ".m4a";return ".mp3";}

    private void showSystemSoundStatusDialog() {
        LinearLayout card = modalCard("当前系统声音", "查看 Android 当前默认声音；这里只读取系统状态，不改变播放或下载设置");
        String ring = RingtoneHelper.currentToneTitle(this, android.media.RingtoneManager.TYPE_RINGTONE);
        String notification = RingtoneHelper.currentToneTitle(this, android.media.RingtoneManager.TYPE_NOTIFICATION);
        String alarm = RingtoneHelper.currentToneTitle(this, android.media.RingtoneManager.TYPE_ALARM);
        LinearLayout statusBox = Ui.column(this); statusBox.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12)); statusBox.setBackground(Ui.glass(100, 16, 18, this));
        TextView status = Ui.text(this, "来电铃声  " + ring + "\n通知音      " + notification + "\n闹钟音      " + alarm, 11.6f, Ui.TEXT_2, false);
        status.setLineSpacing(Ui.dp(this, 3), 1.12f); statusBox.addView(status, Ui.lp(-1, -2));
        LinearLayout.LayoutParams slp = Ui.lp(-1, -2); slp.topMargin = Ui.dp(this, 12); card.addView(statusBox, slp);
        LinearLayout actions = Ui.row(this); actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView preview = modalButton("试听", false); preview.setOnClickListener(v -> showSystemTonePreviewOptions());
        actions.addView(preview, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1f));
        Space gap = new Space(this); actions.addView(gap, Ui.lp(Ui.dp(this, 8), 1));
        TextView settings = modalButton("打开系统声音设置", true); settings.setOnClickListener(v -> { dismissModalNow(); openSystemSoundSettings(); });
        actions.addView(settings, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1.35f));
        LinearLayout.LayoutParams ap = Ui.lp(-1, Ui.dp(this, 42)); ap.topMargin = Ui.dp(this, 12); card.addView(actions, ap);
        presentModal(card);
    }

    private void showSystemTonePreviewOptions() {
        showGlassOptions("试听当前系统声音", "最多试听 8 秒，可随时关闭", new String[]{"试听来电铃声", "试听通知音", "试听闹钟音"}, which -> {
            previewSystemTone(RingtoneHelper.typeFromChoice(which));
        });
    }

    private void previewSystemTone(int type) {
        stopRingtonePreview();
        Uri uri = RingtoneHelper.currentToneUri(this, type);
        if (uri == null) { toast(RingtoneHelper.typeLabel(type) + "当前为静音 / 未设置"); return; }
        try {
            if (playback != null) playback.pause();
            systemTonePreview = android.media.RingtoneManager.getRingtone(this, uri);
            if (systemTonePreview == null) { toast("系统无法试听这个声音"); return; }
            systemTonePreview.play();
            ringtonePreviewStop = this::stopRingtonePreview;
            main.postDelayed(ringtonePreviewStop, 8000L);
            toast("正在试听" + RingtoneHelper.typeLabel(type) + " · 最多 8 秒");
        } catch (Exception e) { stopRingtonePreview(); toast("试听失败：" + compactError(e)); }
    }

    private void openSystemSoundSettings() {
        try { startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS)); }
        catch (Exception e) { toast("无法打开系统声音设置"); }
    }

    private void showRingtoneOptions(OfflineStore.Record record) {
        if (record == null || !record.exists()) { toast("离线文件不存在"); return; }
        showGlassOptions("设为系统声音", record.song.title + " · 可先截取片段并试听", new String[]{"设为来电铃声", "设为通知音", "设为闹钟音"}, which -> {
            showRingtoneClipEditor(record, RingtoneHelper.typeFromChoice(which));
        });
    }

    private void showRingtoneClipEditor(OfflineStore.Record record, int type) {
        if (record == null || !record.exists()) { toast("离线文件不存在"); return; }
        stopRingtonePreview();
        long measured = AudioClipper.durationMs(record.file());
        final long totalMs = measured > 0L ? measured : Math.max(1000L, record.song.durationMs);
        final long[] startMs = {0L};
        final long[] endMs = {Math.min(totalMs, 30_000L)};
        if (endMs[0] < Math.min(5000L, totalMs)) endMs[0] = totalMs;

        String rawLyric = lyricCache == null ? "" : lyricCache.get(record.song);
        List<LyricLine> clipLyrics = LyricParser.parse(rawLyric);

        LinearLayout card = modalCard("截取" + RingtoneHelper.typeLabel(type), record.song.title + " · " + record.song.artist + " · 长按右侧标签拖动开始/结束线");
        TextView range = Ui.text(this, "片段 " + clockMs(startMs[0]) + " → " + clockMs(endMs[0]) + " · " + clockMs(endMs[0] - startMs[0]), 12f, Ui.CYAN, true);
        LinearLayout.LayoutParams rlp = Ui.lp(-1, Ui.dp(this, 30)); rlp.topMargin = Ui.dp(this, 10); card.addView(range, rlp);

        RingtoneLyricSelectorView selector = new RingtoneLyricSelectorView(this, clipLyrics, totalMs, startMs[0], endMs[0]);
        selector.setListener((s, e) -> {
            startMs[0] = s; endMs[0] = e;
            range.setText("片段 " + clockMs(s) + " → " + clockMs(e) + " · " + clockMs(Math.max(0L, e - s)));
        });
        LinearLayout.LayoutParams selectorLp = Ui.lp(-1, Ui.dp(this, 360)); selectorLp.topMargin = Ui.dp(this, 8); card.addView(selector, selectorLp);

        TextView hint = Ui.text(this,
                selector.hasRealLyrics()
                        ? "歌词上下滚动 · 长按“开始 / 结束”标签后上下拖动分割线 · 选区内歌词会高亮"
                        : "这首歌暂时没有本地缓存歌词，因此显示时间轴；不会为了截铃声额外请求在线音源。播放并缓存歌词后再进入即可按歌词截取。",
                10.2f, Ui.DIM, false);
        hint.setLineSpacing(0f, 1.12f);
        LinearLayout.LayoutParams hlp = Ui.lp(-1, -2); hlp.topMargin = Ui.dp(this, 8); card.addView(hint, hlp);

        LinearLayout tools = Ui.row(this); tools.setGravity(Gravity.CENTER_VERTICAL);
        TextView preview = modalButton("试听片段", false); preview.setOnClickListener(v -> previewOfflineSegment(record, startMs[0], endMs[0]));
        tools.addView(preview, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1f));
        Space gap = new Space(this); tools.addView(gap, Ui.lp(Ui.dp(this, 8), 1));
        TextView full = modalButton("使用整首", false); full.setOnClickListener(v -> selector.setSelection(0L, totalMs));
        tools.addView(full, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1f));
        LinearLayout.LayoutParams tlp = Ui.lp(-1, Ui.dp(this, 42)); tlp.topMargin = Ui.dp(this, 10); card.addView(tools, tlp);

        if (!AudioClipper.canClip(record)) {
            TextView warning = Ui.text(this, "当前格式暂不支持片段裁剪；仍可选择“使用整首”设置系统声音。", 10.2f, Ui.DIM, false);
            LinearLayout.LayoutParams wlp = Ui.lp(-1, -2); wlp.topMargin = Ui.dp(this, 8); card.addView(warning, wlp);
        }

        LinearLayout actions = Ui.row(this); actions.setGravity(Gravity.END);
        TextView cancel = modalButton("取消", false); cancel.setOnClickListener(v -> { stopRingtonePreview(); hideModal(); });
        actions.addView(cancel, Ui.lp(Ui.dp(this, 94), Ui.dp(this, 46))); Space g = new Space(this); actions.addView(g, Ui.lp(Ui.dp(this, 8), 1));
        TextView ok = modalButton("确认并设置", true); ok.setOnClickListener(v -> {
            long s = startMs[0], e = endMs[0];
            boolean fullSong = s <= 350L && e >= totalMs - 350L;
            if (!fullSong && !AudioClipper.canClip(record)) { toast("当前格式暂不支持片段裁剪，请选择“使用整首”"); return; }
            if (!fullSong && e - s < Math.min(5000L, totalMs)) { toast("铃声片段至少需要 5 秒"); return; }
            stopRingtonePreview(); dismissModalNow(); requestSystemTone(record, type, s, e);
        });
        actions.addView(ok, Ui.lp(Ui.dp(this, 126), Ui.dp(this, 46)));
        LinearLayout.LayoutParams alp = Ui.lp(-1, Ui.dp(this, 46)); alp.topMargin = Ui.dp(this, 12); card.addView(actions, alp);
        // V4.3: the lyric viewport keeps its own vertical gesture ownership, while the whole
        // editor can also scroll outside the lyric area so small/short screens can always reach
        // the Cancel / Confirm buttons below it.
        ScrollView editorScroll = new ScrollView(this);
        editorScroll.setFillViewport(true);
        editorScroll.setVerticalScrollBarEnabled(false);
        editorScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        editorScroll.setClipToPadding(false);
        editorScroll.addView(card, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        presentFractionModal(editorScroll, .82f);
        selector.postDelayed(() -> maybeShowContextCoach(COACH_RINGTONE_CLIP, selector,
                "按歌词截出真正想要的片段",
                "歌词可以上下滚动。长按右侧“开始 / 结束”标签后上下拖动横线；跨过相邻歌词的中点才会切到下一句，松手会平滑吸附。选好后先试听，再确认设置。"), 230L);
    }

    private void previewOfflineSegment(OfflineStore.Record record, long startMs, long endMs) {
        if (record == null || !record.exists()) return;
        stopRingtonePreview();
        final int token = ++ringtonePreviewToken;
        if (playback != null) playback.pause();
        io.submit(() -> {
            android.media.MediaPlayer mp = null;
            try {
                mp = new android.media.MediaPlayer();
                mp.setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build());
                mp.setDataSource(record.file().getAbsolutePath()); mp.prepare();
                final android.media.MediaPlayer ready = mp;
                main.post(() -> {
                    if (token != ringtonePreviewToken) { try { ready.release(); } catch (Exception ignored) { } return; }
                    try {
                        ringtoneClipPreview = ready; ready.seekTo((int)Math.min(Integer.MAX_VALUE, Math.max(0L, startMs))); ready.start();
                        long length = Math.max(1000L, Math.min(30_000L, endMs - startMs));
                        ringtonePreviewStop = this::stopRingtonePreview; main.postDelayed(ringtonePreviewStop, length);
                        toast("试听 " + clockMs(startMs) + " → " + clockMs(endMs));
                    } catch (Exception e) { stopRingtonePreview(); toast("试听失败：" + compactError(e)); }
                });
            } catch (Exception e) {
                if (mp != null) try { mp.release(); } catch (Exception ignored) { }
                main.post(() -> toast("试听失败：" + compactError(e)));
            }
        });
    }

    private void stopRingtonePreview() {
        ringtonePreviewToken++;
        if (ringtonePreviewStop != null) main.removeCallbacks(ringtonePreviewStop);
        ringtonePreviewStop = null;
        if (ringtoneClipPreview != null) { try { ringtoneClipPreview.stop(); } catch (Exception ignored) { } try { ringtoneClipPreview.release(); } catch (Exception ignored) { } ringtoneClipPreview = null; }
        if (systemTonePreview != null) { try { systemTonePreview.stop(); } catch (Exception ignored) { } systemTonePreview = null; }
    }

    private void requestSystemTone(OfflineStore.Record record, int type, long startMs, long endMs) {
        if (!RingtoneHelper.canWriteSystemSettings(this)) {
            pendingRingtoneSongKey = record.song.key(); pendingRingtoneType = type; pendingRingtoneStartMs = startMs; pendingRingtoneEndMs = endMs;
            try { startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()))); toast("请允许 Lunaxy 修改系统设置，返回后会继续设置" ); }
            catch (Exception e) { pendingRingtoneSongKey = ""; pendingRingtoneStartMs = pendingRingtoneEndMs = 0L; toast("无法打开系统授权页面"); }
            return;
        }
        applySystemTone(record, type, startMs, endMs);
    }

    private void applySystemTone(OfflineStore.Record record, int type, long startMs, long endMs) {
        toast("正在生成并写入系统" + RingtoneHelper.typeLabel(type) + "…");
        io.submit(() -> {
            AudioClipper.ClipResult clip = null;
            try {
                long total = AudioClipper.durationMs(record.file()); if (total <= 0L) total = record.song.durationMs;
                long effectiveEnd = endMs <= 0L ? total : endMs;
                clip = AudioClipper.clip(this, record, Math.max(0L, startMs), effectiveEnd);
                RingtoneHelper.SetResult result = RingtoneHelper.exportAndSetVerified(this, record, type, clip.file, clip.mime, clip.startMs, clip.endMs);
                String detail = result.title.isEmpty() ? RingtoneHelper.typeLabel(type) : result.title;
                main.post(() -> {
                    toast("✓ 已设为" + RingtoneHelper.typeLabel(type) + " · 系统读回验证成功");
                    if (offlinePlaylistOpen) { suppressNextPageAnimation = true; renderTab(); }
                    showGlassMessage("设置成功", detail + "\n片段 " + clockMs(startMs) + " → " + clockMs(effectiveEnd) + "\n系统已读回确认新的" + RingtoneHelper.typeLabel(type) + "。", "知道了", null, "打开系统声音设置", this::openSystemSoundSettings);
                });
            } catch (Exception e) { main.post(() -> toast("设置失败：" + compactError(e))); }
            finally { if (clip != null) clip.cleanup(); }
        });
    }

    private String clockMs(long ms) {
        long total = Math.max(0L, ms) / 1000L;
        return String.format(Locale.ROOT, "%02d:%02d", total / 60L, total % 60L);
    }

    private void enableDesktopLyricsDirectly() {
        if (!Settings.canDrawOverlays(this)) {
            pendingDesktopLyricsDirectEnable = true;
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                pendingDesktopLyricsDirectEnable = false;
                toast("无法打开悬浮窗权限页面");
            }
            return;
        }
        enableDesktopLyricsAfterOverlay(true);
    }

    private void enableDesktopLyricsAfterOverlay(boolean direct) {
        if (maybeRequestDesktopLyricNotificationPermission(direct ? 1 : 2)) return;
        DesktopLyricService.start(this);
        if (direct) toast(DesktopLyricService.enabled(this) ? "桌面歌词已开启" : "正在开启桌面歌词");
        else main.postDelayed(this::showDesktopLyricSettings, 180L);
    }

    private boolean maybeRequestDesktopLyricNotificationPermission(int mode) {
        if (Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return false;
        SharedPreferences p = getSharedPreferences("xingyu_ui_settings", MODE_PRIVATE);
        if (p.getBoolean("desktop_notification_prompted_v1", false)) return false;
        p.edit().putBoolean("desktop_notification_prompted_v1", true).apply();
        pendingDesktopLyricsNotificationMode = mode;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_DESKTOP_LYRIC_NOTIFICATIONS);
        return true;
    }

    private void requestDesktopLyricNotificationCompatibility() {
        if (Build.VERSION.SDK_INT < 33) { toast("当前 Android 版本无需单独授予通知权限"); return; }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            if (playback != null) playback.refreshSystemSurface();
            toast("系统通知已允许");
            return;
        }
        SharedPreferences p = getSharedPreferences("xingyu_ui_settings", MODE_PRIVATE);
        if (!p.getBoolean("desktop_notification_prompted_v1", false)) {
            p.edit().putBoolean("desktop_notification_prompted_v1", true).apply();
            pendingDesktopLyricsNotificationMode = 3;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_DESKTOP_LYRIC_NOTIFICATIONS);
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        } catch (Exception e) { toast("请在系统设置中允许 Lunaxy 通知"); }
    }

    private void showDesktopLyricSettings() {
        if (!Settings.canDrawOverlays(this)) {
            showGlassMessage("桌面歌词", "开启前需要允许显示在其他应用上层。", "去授权", () -> {
                pendingDesktopLyricsEnable = true;
                try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
                catch (Exception e) { pendingDesktopLyricsEnable = false; toast("无法打开悬浮窗权限页面"); }
            }, "取消", null);
            return;
        }

        boolean enabled = DesktopLyricService.enabled(this);
        boolean locked = DesktopLyricService.locked(this);
        boolean doubleLine = DesktopLyricService.doubleLine(this);
        boolean coverColor = DesktopLyricService.useCoverColor(this);
        LinearLayout card = modalCard("桌面歌词", "");

        LinearLayout hero = Ui.row(this); hero.setGravity(Gravity.CENTER_VERTICAL); hero.setPadding(Ui.dp(this,14),Ui.dp(this,10),Ui.dp(this,14),Ui.dp(this,10)); hero.setBackground(Ui.tintedGlass(nowAccent,18,this));
        hero.addView(Ui.text(this, enabled ? (locked ? "已开启 · 已锁定" : "已开启") : "未开启", 14.8f, Ui.TEXT, true), new LinearLayout.LayoutParams(0,Ui.dp(this,40),1f));
        TextView dot=Ui.text(this,enabled?"●":"○",16f,enabled?Ui.mix(nowAccent,Color.WHITE,.2f):Ui.DIM,true);dot.setGravity(Gravity.CENTER);hero.addView(dot,Ui.lp(Ui.dp(this,32),Ui.dp(this,40)));
        LinearLayout.LayoutParams hp=Ui.lp(-1,Ui.dp(this,60));hp.topMargin=Ui.dp(this,10);card.addView(hero,hp);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            TextView compat = modalButton("系统媒体卡兼容 · 允许通知", false);
            compat.setTextSize(11.2f);
            compat.setTextColor(Ui.CYAN);
            compat.setOnClickListener(v -> { dismissModalNow(); requestDesktopLyricNotificationCompatibility(); });
            LinearLayout.LayoutParams nlp=Ui.lp(-1,Ui.dp(this,42));nlp.topMargin=Ui.dp(this,8);card.addView(compat,nlp);
        }

        TextView toggle=modalButton(enabled?"关闭桌面歌词":"开启桌面歌词",!enabled);if(enabled)toggle.setTextColor(Ui.PINK);
        toggle.setOnClickListener(v->{
            if (DesktopLyricService.enabled(this)) {
                DesktopLyricService.stop(this);
                dismissModalNow();
                main.postDelayed(this::showDesktopLyricSettings,140L);
            } else {
                dismissModalNow();
                enableDesktopLyricsAfterOverlay(false);
            }
        });
        LinearLayout.LayoutParams tp=Ui.lp(-1,Ui.dp(this,48));tp.topMargin=Ui.dp(this,9);card.addView(toggle,tp);

        LinearLayout lines=Ui.row(this);TextView single=modalButton("单行",false),dual=modalButton("双行",false);
        single.setBackground(doubleLine?Ui.glass(76,15,20,this):Ui.tintedGlass(nowAccent,15,this));dual.setBackground(doubleLine?Ui.tintedGlass(nowAccent,15,this):Ui.glass(76,15,20,this));
        single.setOnClickListener(v->{DesktopLyricService.setDoubleLine(this,false);dismissModalNow();main.postDelayed(this::showDesktopLyricSettings,100L);});dual.setOnClickListener(v->{DesktopLyricService.setDoubleLine(this,true);dismissModalNow();main.postDelayed(this::showDesktopLyricSettings,100L);});
        lines.addView(single,new LinearLayout.LayoutParams(0,Ui.dp(this,43),1f));LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(0,Ui.dp(this,43),1f);dlp.leftMargin=Ui.dp(this,8);lines.addView(dual,dlp);LinearLayout.LayoutParams lp=Ui.lp(-1,Ui.dp(this,43));lp.topMargin=Ui.dp(this,8);card.addView(lines,lp);

        LinearLayout colors=Ui.row(this);TextView cover=modalButton("跟随封面",false),custom=modalButton("自定义渐变",false);
        cover.setBackground(coverColor?Ui.tintedGlass(nowAccent,15,this):Ui.glass(76,15,20,this));custom.setBackground(!coverColor?Ui.tintedGlass(nowAccent,15,this):Ui.glass(76,15,20,this));
        cover.setOnClickListener(v->{DesktopLyricService.setUseCoverColor(this,true);dismissModalNow();main.postDelayed(this::showDesktopLyricSettings,100L);});custom.setOnClickListener(v->{DesktopLyricService.setUseCoverColor(this,false);dismissModalNow();main.postDelayed(this::showLyricStylePicker,100L);});
        colors.addView(cover,new LinearLayout.LayoutParams(0,Ui.dp(this,43),1f));LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(0,Ui.dp(this,43),1f);clp.leftMargin=Ui.dp(this,8);colors.addView(custom,clp);LinearLayout.LayoutParams cp=Ui.lp(-1,Ui.dp(this,43));cp.topMargin=Ui.dp(this,8);card.addView(colors,cp);

        LinearLayout tools=Ui.row(this);TextView lock=modalButton(locked?"解锁":"锁定",false),reset=modalButton("重置位置",false);lock.setEnabled(enabled);reset.setEnabled(enabled);lock.setAlpha(enabled?1f:.4f);reset.setAlpha(enabled?1f:.4f);
        lock.setOnClickListener(v->{if(enabled){DesktopLyricService.setLocked(this,!locked);dismissModalNow();main.postDelayed(this::showDesktopLyricSettings,100L);}});reset.setOnClickListener(v->{if(enabled){DesktopLyricService.resetPosition(this);toast("已重置到顶部");}});
        tools.addView(lock,new LinearLayout.LayoutParams(0,Ui.dp(this,43),1f));LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(0,Ui.dp(this,43),1f);rlp.leftMargin=Ui.dp(this,8);tools.addView(reset,rlp);LinearLayout.LayoutParams trp=Ui.lp(-1,Ui.dp(this,43));trp.topMargin=Ui.dp(this,8);card.addView(tools,trp);
        toggle.postDelayed(() -> {
            if (!contextCoachDone(COACH_DESKTOP_LYRIC)) {
                maybeShowContextCoach(COACH_DESKTOP_LYRIC, toggle,
                        "桌面歌词", "默认双行并跟随封面颜色。锁定后歌词会触摸穿透，播放控制可以直接在系统媒体卡片中完成。");
            } else if (enabled) {
                maybeShowContextCoach(COACH_DESKTOP_LYRIC_CONTROLS, lock,
                        "轻点歌词，快速调整", "未锁定时轻点桌面歌词，可以调整字号、颜色并直接锁定或关闭；锁定后请从系统媒体卡片解锁。");
            }
        }, 220L);
        presentModal(card);
    }

    private void showDownloadCenter() {
        downloadCenterOpen = true;
        final int generation = ++downloadCenterGeneration;
        LinearLayout card = modalCard("下载中心", "大歌单时间预算换路 · 播放解析时下载自动让路");

        LinearLayout hero = Ui.column(this);
        hero.setPadding(Ui.dp(this,15),Ui.dp(this,13),Ui.dp(this,15),Ui.dp(this,13));
        hero.setBackground(Ui.tintedGlass(Ui.CYAN,20,this));
        TextView overall = Ui.text(this,"读取下载任务…",18f,Ui.TEXT,true);
        hero.addView(overall,Ui.lp(-1,Ui.dp(this,30)));
        TextView progressText = Ui.text(this,"",10.8f,Ui.TEXT_2,false);
        hero.addView(progressText,Ui.lp(-1,Ui.dp(this,24)));
        FrameLayout track = new FrameLayout(this); track.setBackground(Ui.round(Color.argb(46,255,255,255),4,this));
        View fill = new View(this); fill.setBackground(Ui.round(Ui.CYAN,4,this)); fill.setPivotX(0f); fill.setScaleX(0f);
        track.addView(fill,Ui.frame(-1,-1,Gravity.FILL));
        LinearLayout.LayoutParams trp=Ui.lp(-1,Ui.dp(this,5));trp.topMargin=Ui.dp(this,5);hero.addView(track,trp);
        TextView current = Ui.text(this,"",10.6f,Ui.DIM,false); current.setSingleLine(true); current.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams crp=Ui.lp(-1,Ui.dp(this,26));crp.topMargin=Ui.dp(this,5);hero.addView(current,crp);
        LinearLayout.LayoutParams hp=Ui.lp(-1,-2);hp.topMargin=Ui.dp(this,12);card.addView(hero,hp);

        LinearLayout globalActions=Ui.row(this);
        TextView pauseAll=modalButton("暂停全部",false); pauseAll.setTextColor(Ui.CYAN);
        TextView close=modalButton("完成",false);
        globalActions.addView(pauseAll,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1f));
        LinearLayout.LayoutParams cap=new LinearLayout.LayoutParams(0,Ui.dp(this,42),1f);cap.leftMargin=Ui.dp(this,8);globalActions.addView(close,cap);
        LinearLayout.LayoutParams gap=Ui.lp(-1,Ui.dp(this,42));gap.topMargin=Ui.dp(this,9);card.addView(globalActions,gap);
        close.setOnClickListener(v->dismissModalNow());

        TextView hint=Ui.text(this,"每首首轮最多 8 路 · 难歌整单后再补 8 路 · 左滑可删除记录",10.2f,Ui.DIM,false);
        LinearLayout.LayoutParams hip=Ui.lp(-1,Ui.dp(this,25));hip.topMargin=Ui.dp(this,7);card.addView(hint,hip);

        LinearLayout planHost=Ui.column(this);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.addView(planHost);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1f);sp.topMargin=Ui.dp(this,5);card.addView(scroll,sp);

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            if (!downloadCenterOpen || generation != downloadCenterGeneration || isFinishing()) return;
            io.submit(() -> {
                List<DownloadPlanStore.Summary> summaries = OfflineDownloadService.planSummaries(this);
                boolean paused = OfflineDownloadService.downloadsPaused(this);
                main.post(() -> {
                    if (!downloadCenterOpen || generation != downloadCenterGeneration || isFinishing()) return;
                    int total=0,downloaded=0,already=0,waiting=0,failed=0,pending=0,active=0;
                    String currentSong="",currentState="";int currentProgress=0;
                    for(DownloadPlanStore.Summary x:summaries){
                        boolean fullyDone=x.exhausted&&x.waiting==0&&x.failed==0&&x.pending==0&&x.active==0;
                        if(!x.cancelled&&!fullyDone){
                            total+=x.total;downloaded+=x.downloaded;already+=x.alreadyOffline;waiting+=x.waiting;failed+=x.failed;pending+=x.pending;active+=x.active;
                        }
                        if(!x.cancelled&&!x.currentSong.isEmpty()){currentSong=x.currentSong;currentState=x.currentState;currentProgress=x.currentProgress;}
                    }
                    int offlineTotal=downloaded+already;
                    int pct=total<=0?0:Math.max(0,Math.min(100,Math.round(offlineTotal*100f/total)));
                    overall.setText(total<=0?"当前没有进行中的任务":offlineTotal+" / "+total+" · "+pct+"%");
                    progressText.setText(total<=0?"已完成与已取消任务保留在下方历史列表":"新下载 "+downloaded+" · 已存在 "+already+" · 处理中 "+active+" · 等待 "+(waiting+pending)+" · 失败 "+failed);
                    fill.setScaleX(pct/100f);
                    current.setText(currentSong.isEmpty() ? (paused?"全部任务已暂停":"后台单线程推进 · 旧 READY 失效会自动换路") : "正在处理 · "+currentSong+(currentState.isEmpty()?"":" · "+currentState)+(currentProgress>0?" · "+currentProgress+"%":""));
                    pauseAll.setText(paused?"继续全部":"暂停全部");
                    pauseAll.setOnClickListener(v->{if(OfflineDownloadService.downloadsPaused(this))OfflineDownloadService.resumeAll(this);else OfflineDownloadService.pauseAll(this);});
                    hero.postDelayed(() -> maybeShowContextCoach(COACH_DOWNLOAD_MATRIX, hero,
                            "下载现在也会自己换路", "旧 READY 地址遇到 403/404 会自动降级主动搜索。播放稳定时每首一轮最多约 14 条真实路线 / 20 秒；整张任务跑完后难歌再跑第二轮新路线。切歌、解析或 BUFFERING 时会立即让路，不会无限请求。"), 180L);
                    if (waiting > 0) hero.postDelayed(() -> maybeShowContextCoach(COACH_DOWNLOAD_WAITING, hero,
                            "等待线路不是卡住", "本轮预算用完会先跳过继续下一首；整张任务结束后自动补一轮，仍失败才停下，之后可手动重试。播放解析或 BUFFERING 时下载会立即让路。"), 420L);

                    // Never rebuild the list underneath an active pointer. V63 refreshed the entire
                    // planHost every 900ms; if that landed during a swipe, the dragged View was destroyed
                    // and recreated at translationX=0, which looked exactly like an involuntary snap-back.
                    if(!downloadPlanGestureActive){
                        planHost.removeAllViews();
                        if(summaries.isEmpty()) planHost.addView(compactEmpty("还没有下载任务 · 从歌单或批量管理发起下载"));
                        else {
                            boolean coached=false,hasLive=false,hasHistory=false;
                            for(DownloadPlanStore.Summary x:summaries)if(!x.historyOnly())hasLive=true;else hasHistory=true;
                            if(hasLive){TextView liveTitle=Ui.text(this,"进行中",11.2f,Ui.TEXT_2,true);planHost.addView(liveTitle,Ui.lp(-1,Ui.dp(this,28)));}
                            for(DownloadPlanStore.Summary x:summaries){
                                if(x.historyOnly())continue;View plan=downloadPlanCard(x);planHost.addView(plan,marginTop(planHost.getChildCount()==0?0:7));
                                if(!coached){coached=true;plan.postDelayed(()->maybeShowContextCoach(COACH_DOWNLOAD_DETAIL,plan,
                                        "下载任务可以展开", "点开任务能看到每一首的实时状态和换路阶段；失败项不会卡住整单。向左拖过删除阈值会震动确认，松手只删除任务记录，不删除已经下载好的音乐。"),220L);}
                            }
                            if(hasHistory){TextView historyTitle=Ui.text(this,"历史任务 · 左滑删除",10.8f,Ui.DIM,true);LinearLayout.LayoutParams hlp=Ui.lp(-1,Ui.dp(this,30));hlp.topMargin=Ui.dp(this,8);planHost.addView(historyTitle,hlp);}
                            for(DownloadPlanStore.Summary x:summaries){if(!x.historyOnly())continue;View plan=downloadPlanCard(x);planHost.addView(plan,marginTop(6));}
                        }
                    }
                    main.postDelayed(refresh[0],900L);
                });
            });
        };
        presentFractionModal(card,.79f);
        refresh[0].run();
    }

    private View downloadPlanCard(DownloadPlanStore.Summary s) {
        FrameLayout shell=new FrameLayout(this);

        // V64 Lunaxy Ghost Swipe: no second glass/delete card lives behind the task. The only
        // revealed affordance is a line icon floating directly over the starfield. The task card
        // itself remains the single visual surface throughout the gesture.
        IconView deleteGhost=new IconView(this,IconView.Type.DELETE,Color.argb(150,255,126,178));
        deleteGhost.setAlpha(0f);deleteGhost.setScaleX(.72f);deleteGhost.setScaleY(.72f);
        FrameLayout.LayoutParams ghostLp=Ui.frame(Ui.dp(this,54),Ui.dp(this,54),Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        ghostLp.rightMargin=Ui.dp(this,8);shell.addView(deleteGhost,ghostLp);

        LinearLayout box=Ui.column(this);box.setPadding(Ui.dp(this,13),Ui.dp(this,11),Ui.dp(this,13),Ui.dp(this,11));box.setBackground(Ui.glass(102,17,20,this));
        LinearLayout head=Ui.row(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=Ui.text(this,s.name,13.2f,Ui.TEXT,true);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);head.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,28),1f));
        TextView state=Ui.pill(this,s.status,s.cancelled?Ui.DIM:(s.paused?Ui.ORANGE:(s.status.startsWith("已完成")?Ui.GREEN:Ui.CYAN)));state.setGravity(Gravity.CENTER);head.addView(state,Ui.lp(Ui.dp(this,92),Ui.dp(this,28)));box.addView(head,Ui.lp(-1,Ui.dp(this,28)));
        int offline=s.downloaded+s.alreadyOffline;
        TextView detail=Ui.text(this,offline+" / "+s.total+" · "+s.percent()+"%  ·  处理中 "+s.active+"  ·  等待 "+(s.waiting+s.pending)+"  ·  失败 "+s.failed,10.5f,Ui.TEXT_2,false);detail.setSingleLine(true);detail.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams dp=Ui.lp(-1,Ui.dp(this,28));dp.topMargin=Ui.dp(this,3);box.addView(detail,dp);
        if(!s.currentSong.isEmpty()){
            TextView cur=Ui.text(this,s.currentSong+(s.currentState.isEmpty()?"":" · "+s.currentState),10.2f,Ui.DIM,false);cur.setSingleLine(true);cur.setEllipsize(TextUtils.TruncateAt.END);box.addView(cur,Ui.lp(-1,Ui.dp(this,25)));
        }
        boolean completed = s.exhausted && s.waiting == 0 && s.failed == 0 && s.pending == 0 && s.active == 0 && offline >= s.total;
        if(!s.cancelled && !completed){
            LinearLayout actions=Ui.row(this);
            TextView toggle=modalButton(s.paused||s.exhausted?"继续":"暂停",false);toggle.setTextColor(Ui.CYAN);
            toggle.setOnClickListener(v->{if(s.paused||s.exhausted)OfflineDownloadService.resumePlan(this,s.id);else OfflineDownloadService.pausePlan(this,s.id);});
            TextView cancel=modalButton("取消任务",false);cancel.setTextColor(Ui.PINK);cancel.setOnClickListener(v->OfflineDownloadService.cancelPlan(this,s.id));
            actions.addView(toggle,new LinearLayout.LayoutParams(0,Ui.dp(this,38),1f));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,Ui.dp(this,38),1f);cp.leftMargin=Ui.dp(this,7);actions.addView(cancel,cp);
            LinearLayout.LayoutParams ap=Ui.lp(-1,Ui.dp(this,38));ap.topMargin=Ui.dp(this,7);box.addView(actions,ap);
        }
        box.setClickable(true);box.setFocusable(true);Ui.applyRipple(box,Color.TRANSPARENT);
        box.setOnClickListener(v->showDownloadPlanDetail(s.id));
        shell.addView(box,Ui.frame(-1,-2,Gravity.FILL));

        final float[] down={0f,0f};
        final boolean[] horizontal={false};
        final boolean[] armed={false};
        final boolean[] moved={false};
        final int touchSlop=android.view.ViewConfiguration.get(this).getScaledTouchSlop();
        final float trigger=Ui.dp(this,74);
        final float resistanceRoom=Ui.dp(this,50);

        box.setOnTouchListener((v,e)->{
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                down[0]=e.getRawX();down[1]=e.getRawY();horizontal[0]=false;armed[0]=false;moved[0]=false;
                downloadPlanGestureActive=true;
                box.animate().cancel();deleteGhost.animate().cancel();
                return true;
            }
            if(action==MotionEvent.ACTION_MOVE){
                float dx=e.getRawX()-down[0],dy=e.getRawY()-down[1];
                if(!horizontal[0]){
                    if(Math.abs(dx)>touchSlop&&Math.abs(dx)>Math.abs(dy)*1.12f&&dx<0f){
                        horizontal[0]=true;moved[0]=true;
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }else if(Math.abs(dy)>touchSlop&&Math.abs(dy)>Math.abs(dx)){
                        // This was a vertical scroll intention. Give control straight back to the
                        // parent ScrollView; ACTION_CANCEL will clear the temporary guard.
                        downloadPlanGestureActive=false;
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        return false;
                    }
                }
                if(horizontal[0]){
                    float raw=Math.max(0f,-dx);
                    float drag=raw<=trigger?raw:trigger+Math.min(resistanceRoom,(raw-trigger)*.32f);
                    box.setTranslationX(-drag);
                    float progress=Math.max(0f,Math.min(1f,raw/trigger));
                    deleteGhost.setAlpha(.12f+.88f*progress);
                    float scale=.72f+.28f*progress;deleteGhost.setScaleX(scale);deleteGhost.setScaleY(scale);
                    boolean nextArmed=raw>=trigger;
                    if(nextArmed!=armed[0]){
                        armed[0]=nextArmed;
                        deleteGhost.setIconColor(nextArmed?Ui.PINK:Color.argb(170,255,126,178));
                        if(nextArmed){
                            int feedback=Build.VERSION.SDK_INT>=30?android.view.HapticFeedbackConstants.CONFIRM:android.view.HapticFeedbackConstants.LONG_PRESS;
                            v.performHapticFeedback(feedback);
                            deleteGhost.animate().scaleX(1.08f).scaleY(1.08f).setDuration(90).withEndAction(()->deleteGhost.animate().scaleX(1f).scaleY(1f).setDuration(90).start()).start();
                        }
                    }
                    return true;
                }
                return true;
            }
            if(action==MotionEvent.ACTION_UP){
                v.getParent().requestDisallowInterceptTouchEvent(false);
                if(horizontal[0]){
                    if(armed[0]){
                        // Crossing the physical threshold + haptic is the confirmation. Releasing
                        // executes immediately: no second glass layer and no confirmation modal.
                        float exit=-Math.max(getResources().getDisplayMetrics().widthPixels,box.getWidth()+Ui.dp(this,80));
                        deleteGhost.setIconColor(Ui.PINK);deleteGhost.setAlpha(1f);
                        box.animate().translationX(exit).alpha(.12f).setDuration(210)
                                .setInterpolator(new android.view.animation.AccelerateInterpolator(1.35f))
                                .withEndAction(()->{
                                    OfflineDownloadService.deletePlan(this,s.id);
                                    downloadPlanGestureActive=false;
                                    toast("已删除下载记录 · 离线音乐保留");
                                }).start();
                    }else{
                        box.animate().translationX(0f).setDuration(270)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(.72f))
                                .withEndAction(()->{deleteGhost.setAlpha(0f);deleteGhost.setScaleX(.72f);deleteGhost.setScaleY(.72f);downloadPlanGestureActive=false;}).start();
                    }
                    return true;
                }
                downloadPlanGestureActive=false;
                if(!moved[0])v.performClick();
                return true;
            }
            if(action==MotionEvent.ACTION_CANCEL){
                v.getParent().requestDisallowInterceptTouchEvent(false);
                // Cancellation can come from the parent scroll or lifecycle; it must NEVER be
                // interpreted as a delete release. V63 treated CANCEL like UP, causing snap-back.
                box.animate().translationX(0f).setDuration(220)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(.60f))
                        .withEndAction(()->{deleteGhost.setAlpha(0f);deleteGhost.setScaleX(.72f);deleteGhost.setScaleY(.72f);downloadPlanGestureActive=false;}).start();
                return true;
            }
            return true;
        });
        return shell;
    }

    private void showDownloadPlanDetail(String planId) {
        if(planId==null||planId.isEmpty())return;
        downloadCenterOpen=true;
        final int generation=++downloadCenterGeneration;
        final String[] filter={"all"};
        final LinearLayout[] hostRef=new LinearLayout[1];
        LinearLayout card=modalCard("任务详情","");
        LinearLayout hero=Ui.column(this);hero.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12));hero.setBackground(Ui.tintedGlass(Ui.CYAN,18,this));
        TextView title=Ui.text(this,"读取任务…",16.8f,Ui.TEXT,true);hero.addView(title,Ui.lp(-1,Ui.dp(this,28)));
        TextView stats=Ui.text(this,"",10.8f,Ui.TEXT_2,false);hero.addView(stats,Ui.lp(-1,Ui.dp(this,24)));
        FrameLayout progressTrack=new FrameLayout(this);progressTrack.setBackground(Ui.round(Color.argb(44,255,255,255),4,this));
        View progressFill=new View(this);progressFill.setBackground(Ui.round(Ui.CYAN,4,this));progressFill.setPivotX(0f);progressFill.setScaleX(0f);progressTrack.addView(progressFill,Ui.frame(-1,-1,Gravity.FILL));
        LinearLayout.LayoutParams pp=Ui.lp(-1,Ui.dp(this,5));pp.topMargin=Ui.dp(this,4);hero.addView(progressTrack,pp);
        LinearLayout.LayoutParams hp=Ui.lp(-1,-2);hp.topMargin=Ui.dp(this,10);card.addView(hero,hp);

        LinearLayout actions=Ui.row(this);
        TextView back=modalButton("返回下载中心",false);TextView toggle=modalButton("暂停",false);toggle.setTextColor(Ui.CYAN);
        actions.addView(back,new LinearLayout.LayoutParams(0,Ui.dp(this,40),1f));LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(0,Ui.dp(this,40),1f);tlp.leftMargin=Ui.dp(this,8);actions.addView(toggle,tlp);
        LinearLayout.LayoutParams ap=Ui.lp(-1,Ui.dp(this,40));ap.topMargin=Ui.dp(this,8);card.addView(actions,ap);back.setOnClickListener(v->showDownloadCenter());

        LinearLayout tabs=Ui.row(this);tabs.setGravity(Gravity.CENTER_VERTICAL);
        String[] keys={"all",DownloadPlanStore.CAT_DONE,DownloadPlanStore.CAT_ACTIVE,DownloadPlanStore.CAT_WAITING,DownloadPlanStore.CAT_FAILED};
        String[] labels={"全部","已完成","下载中","等待","失败"};
        TextView[] tabText=new TextView[keys.length];View[] tabLine=new View[keys.length];
        for(int i=0;i<keys.length;i++){
            FrameLayout slot=new FrameLayout(this);TextView tv=Ui.text(this,labels[i],10.8f,i==0?Ui.CYAN:Ui.TEXT_2,i==0);tv.setGravity(Gravity.CENTER);slot.addView(tv,Ui.frame(-1,-1,Gravity.FILL));
            View line=new View(this);line.setBackground(Ui.round(Ui.CYAN,2,this));line.setVisibility(i==0?View.VISIBLE:View.INVISIBLE);FrameLayout.LayoutParams ll=Ui.frame(Ui.dp(this,24),Ui.dp(this,2),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);ll.bottomMargin=Ui.dp(this,2);slot.addView(line,ll);
            final int idx=i;slot.setOnClickListener(v->{filter[0]=keys[idx];for(int j=0;j<tabText.length;j++){tabText[j].setTextColor(j==idx?Ui.CYAN:Ui.TEXT_2);tabText[j].setTypeface(Typeface.DEFAULT,j==idx?Typeface.BOLD:Typeface.NORMAL);tabLine[j].setVisibility(j==idx?View.VISIBLE:View.INVISIBLE);}renderDownloadDetailList(planId,filter[0],hostRef[0]);});
            tabText[i]=tv;tabLine[i]=line;tabs.addView(slot,new LinearLayout.LayoutParams(0,Ui.dp(this,38),1f));
        }
        LinearLayout.LayoutParams tabp=Ui.lp(-1,Ui.dp(this,38));tabp.topMargin=Ui.dp(this,8);card.addView(tabs,tabp);

        LinearLayout detailHost=Ui.column(this);
        // Holder used by tab click listeners after detailHost is constructed.
        hostRef[0]=detailHost;
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.addView(detailHost);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1f);sp.topMargin=Ui.dp(this,5);card.addView(scroll,sp);

        TextView retry=modalButton("重试失败项",false);retry.setTextColor(Ui.ORANGE);retry.setVisibility(View.GONE);LinearLayout.LayoutParams rp=Ui.lp(-1,Ui.dp(this,40));rp.topMargin=Ui.dp(this,7);card.addView(retry,rp);

        Runnable[] refresh=new Runnable[1];
        refresh[0]=()->{
            if(!downloadCenterOpen||generation!=downloadCenterGeneration||isFinishing())return;
            io.submit(()->{
                List<DownloadPlanStore.Summary> ss=OfflineDownloadService.planSummaries(this);DownloadPlanStore.Summary found=null;for(DownloadPlanStore.Summary x:ss)if(planId.equals(x.id)){found=x;break;}
                DownloadPlanStore.Summary summary=found;
                List<DownloadPlanStore.Item> items=OfflineDownloadService.planDetails(this,planId);
                main.post(()->{
                    if(!downloadCenterOpen||generation!=downloadCenterGeneration||isFinishing())return;
                    if(summary==null){toast("任务已经删除");showDownloadCenter();return;}
                    int done=0,active=0,waiting=0,failed=0,pending=0;for(DownloadPlanStore.Item it:items){if(DownloadPlanStore.CAT_DONE.equals(it.category))done++;else if(DownloadPlanStore.CAT_ACTIVE.equals(it.category))active++;else if(DownloadPlanStore.CAT_FAILED.equals(it.category))failed++;else if(DownloadPlanStore.CAT_WAITING.equals(it.category))waiting++;else if(DownloadPlanStore.CAT_PENDING.equals(it.category))pending++;}
                    title.setText(summary.name);stats.setText(done+" / "+summary.total+" · "+summary.percent()+"%   ·   下载中 "+active+"   ·   等待 "+(waiting+pending)+"   ·   失败 "+failed);progressFill.setScaleX(summary.percent()/100f);
                    toggle.setText(summary.cancelled?"已取消":(summary.paused||summary.exhausted?"继续":"暂停"));toggle.setEnabled(!summary.cancelled);toggle.setAlpha(summary.cancelled?.42f:1f);toggle.setOnClickListener(v->{if(summary.cancelled)return;if(summary.paused||summary.exhausted)OfflineDownloadService.resumePlan(this,planId);else OfflineDownloadService.pausePlan(this,planId);});
                    retry.setVisibility(!summary.cancelled&&(failed>0||waiting>0)?View.VISIBLE:View.GONE);retry.setText(failed>0?"重试失败项 · "+failed+" 首":"重试等待项 · "+waiting+" 首");retry.setOnClickListener(v->{OfflineDownloadService.retryFailed(this,planId);toast("已重新加入待下载队列");});
                    renderDownloadDetailList(items,filter[0],detailHost);
                    main.postDelayed(refresh[0],800L);
                });
            });
        };
        presentFractionModal(card,.82f);refresh[0].run();
    }

    private void renderDownloadDetailList(String planId,String filter,LinearLayout host){
        if(host==null)return;io.submit(()->{List<DownloadPlanStore.Item> items=OfflineDownloadService.planDetails(this,planId);main.post(()->renderDownloadDetailList(items,filter,host));});
    }

    private void renderDownloadDetailList(List<DownloadPlanStore.Item> items,String filter,LinearLayout host){
        if(host==null)return;host.removeAllViews();int shown=0;
        for(DownloadPlanStore.Item item:items){
            boolean match="all".equals(filter)||filter.equals(item.category)||(DownloadPlanStore.CAT_WAITING.equals(filter)&&DownloadPlanStore.CAT_PENDING.equals(item.category));
            if(!match)continue;host.addView(downloadDetailRow(item),marginTop(shown++==0?0:6));
        }
        if(shown==0)host.addView(compactEmpty("这个状态下暂时没有歌曲"));
    }

    private View downloadDetailRow(DownloadPlanStore.Item item){
        LinearLayout row=Ui.row(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(Ui.dp(this,9),Ui.dp(this,7),Ui.dp(this,10),Ui.dp(this,7));row.setBackground(Ui.glass(72,15,16,this));
        ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setClipToOutline(true);cover.setBackground(Ui.round(Color.rgb(24,24,31),10,this));ImageLoader.load(item.song.coverUrl,cover,null);row.addView(cover,Ui.lp(Ui.dp(this,46),Ui.dp(this,46)));
        LinearLayout meta=Ui.column(this);TextView t=Ui.text(this,item.song.title,12.3f,Ui.TEXT,true);t.setSingleLine(true);t.setEllipsize(TextUtils.TruncateAt.END);meta.addView(t,Ui.lp(-1,Ui.dp(this,25)));TextView a=Ui.text(this,item.song.artist,10.2f,Ui.DIM,false);a.setSingleLine(true);a.setEllipsize(TextUtils.TruncateAt.END);meta.addView(a,Ui.lp(-1,Ui.dp(this,21)));LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1f);mp.leftMargin=Ui.dp(this,9);row.addView(meta,mp);
        int color=DownloadPlanStore.CAT_DONE.equals(item.category)?Ui.GREEN:(DownloadPlanStore.CAT_FAILED.equals(item.category)?Ui.PINK:(DownloadPlanStore.CAT_ACTIVE.equals(item.category)?Ui.CYAN:Ui.TEXT_2));
        String state=item.state;if(item.current&&item.progress>0&&!state.contains("%"))state=state+" · "+item.progress+"%";TextView st=Ui.text(this,state,9.8f,color,DownloadPlanStore.CAT_ACTIVE.equals(item.category));st.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);st.setMaxLines(2);st.setEllipsize(TextUtils.TruncateAt.END);row.addView(st,Ui.lp(Ui.dp(this,126),Ui.dp(this,46)));
        return row;
    }

    private void enqueueDownload(Song song) {
        if (song == null) return;
        if (offlineStore != null && offlineStore.contains(song)) { toast("这首歌已经在离线歌单"); return; }
        OfflineDownloadService.enqueue(this, song); toast("已创建下载任务 · 可在离线页查看进度");
    }

    private void removeDownload(Song song) {
        if (song == null || offlineStore == null) return;
        OfflineStore.Record before=offlineStore.get(song); boolean local=before!=null&&OfflineStore.ORIGIN_LOCAL.equals(before.origin);
        offlineStore.remove(song); toast(local?"已从离线库移除 · 原文件保留":"已删除离线文件");
        if (offlinePlaylistOpen) { suppressNextPageAnimation = true; renderTab(); }
    }

    private static String formatStorage(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) return String.format(Locale.ROOT, "%.2f GB", bytes / (1024d*1024d*1024d));
        if (bytes >= 1024L * 1024L) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024d*1024d));
        if (bytes >= 1024L) return String.format(Locale.ROOT, "%.0f KB", bytes / 1024d);
        return bytes + " B";
    }

    private static String compactError(Throwable e) { String s = e == null || e.getMessage() == null ? "unknown" : e.getMessage().replace('\n',' ').trim(); return s.length() > 90 ? s.substring(0,90) + "…" : s; }

    private View playlistCategorySection(String key, String title, String subtitle,
                                         List<ImportedPlaylist> entries) {
        LinearLayout section = Ui.column(this);
        LinearLayout header = Ui.row(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 2), 0, Ui.dp(this, 4), 0);
        TextView titleView = Ui.text(this, title, 17.5f, Ui.TEXT, true);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(titleView, Ui.lp(-2, Ui.dp(this, 40)));
        TextView countView = Ui.text(this, subtitle, 10.8f, Ui.DIM, false);
        countView.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams countLp = Ui.lp(-2, Ui.dp(this, 40));
        countLp.leftMargin = Ui.dp(this, 10);
        header.addView(countView, countLp);
        Space headerFill = new Space(this);
        header.addView(headerFill, new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1f));
        TextView arrow = Ui.text(this, "▾", 20f, Ui.TEXT_2, true);
        arrow.setGravity(Gravity.CENTER);
        header.addView(arrow, Ui.lp(Ui.dp(this, 38), Ui.dp(this, 38)));
        header.setClickable(true); header.setFocusable(true); Ui.applyRipple(header, Color.TRANSPARENT);
        section.addView(header, Ui.lp(-1, Ui.dp(this, 40)));

        LinearLayout content = Ui.column(this);
        if (entries == null || entries.isEmpty()) {
            content.addView(compactEmpty("这里暂时还是空的"), marginTop(6));
        } else {
            int i = 0;
            for (ImportedPlaylist p : entries) content.addView(playlistCard(p), marginTop(i++ == 0 ? 6 : 8));
        }
        section.addView(content, Ui.lp(-1, -2));

        SharedPreferences prefs = getSharedPreferences("xingyu_ui_settings", MODE_PRIVATE);
        String prefKey = "playlist_section_open_" + key;
        boolean expanded = prefs.getBoolean(prefKey, true);
        if (!expanded) {
            content.setVisibility(View.GONE);
            content.setAlpha(0f);
            arrow.setRotation(-90f);
        }
        header.setOnClickListener(v -> {
            boolean opening = content.getVisibility() != View.VISIBLE;
            prefs.edit().putBoolean(prefKey, opening).apply();
            arrow.animate().rotation(opening ? 0f : -90f).setDuration(170).start();
            if (opening) {
                content.setVisibility(View.VISIBLE);
                content.setAlpha(0f);
                content.setTranslationY(-Ui.dp(this, 8));
                content.animate().alpha(1f).translationY(0f).setDuration(180).start();
            } else {
                content.animate().alpha(0f).translationY(-Ui.dp(this, 8)).setDuration(150)
                        .withEndAction(() -> { content.setVisibility(View.GONE); content.setTranslationY(0f); }).start();
            }
        });
        return section;
    }

    private View smallLibraryAction(IconView.Type type, String title, String sub, int tint) {
        LinearLayout card = Ui.row(this);
        card.setPadding(Ui.dp(this, 13), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        card.setBackground(Ui.glass(142, 18, 18, this));
        card.setClickable(true); card.setFocusable(true); Ui.applyRipple(card, Color.argb(34,255,255,255));
        FrameLayout icon = Ui.iconButton(this, type, 38, tint, Color.argb(24,Color.red(tint),Color.green(tint),Color.blue(tint)));
        card.addView(icon, Ui.lp(Ui.dp(this, 38), Ui.dp(this, 38)));
        LinearLayout texts = Ui.column(this); texts.setPadding(Ui.dp(this, 10), 0, 0, 0);
        texts.addView(Ui.text(this,title,13.5f,Ui.TEXT,true),new LinearLayout.LayoutParams(-1,0,1f));
        texts.addView(Ui.text(this,sub,10.5f,Ui.DIM,false),new LinearLayout.LayoutParams(-1,0,1f));
        card.addView(texts,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1f));
        return card;
    }

    private void showCreatePlaylistDialog() {
        showGlassInput("新建歌单", "给歌单起个名字", false, "创建", name -> {
            playlists = store.createPlaylist(name);
            renderTab();
            toast("歌单已创建");
        });
    }

    private void showImportDialog() {
        showGlassOptions("导入歌单", "选择导入方式", new String[]{"音乐平台分享链接", "Lunaxy 歌单文件"}, which -> {
            if (which == 0) showPlatformImportDialog();
            else openLunaxyPlaylistFile();
        });
    }

    private void showPlatformImportDialog() {
        showGlassInput("导入平台歌单", "粘贴网易云 / QQ / 酷我 / 酷狗的完整分享链接", true, "开始导入", this::importPlaylist, input -> {
            if (!contextCoachDone(COACH_IMPORT_PLAYLIST) && !deferredContextCoachThisSession.contains(COACH_IMPORT_PLAYLIST) && contextCoachOverlay == null) {
                input.clearFocus();
                try {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
                } catch (Exception ignored) { }
            }
            maybeShowContextCoach(COACH_IMPORT_PLAYLIST, input,
                    "复制分享链接就能导入",
                    "支持网易云、QQ、酷我和酷狗完整分享链接。Lunaxy 会先锁定平台身份，再只交给对应解析器；链接残缺时会直接提示重复制，不会跨平台猜测。");
        });
    }

    private void showPlaylistManager() {
        if (playlists.isEmpty()) { toast("还没有可管理的歌单"); return; }
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        LinearLayout card = modalCard("批量管理", "勾选歌单后一次删除；删除只影响本机保存，不会修改音乐平台原歌单");

        LinearLayout rows = Ui.column(this);
        TextView[] count = new TextView[1];
        for (ImportedPlaylist p : playlists) {
            LinearLayout row = Ui.row(this);
            row.setPadding(Ui.dp(this, 8), Ui.dp(this, 7), Ui.dp(this, 8), Ui.dp(this, 7));
            row.setBackground(Ui.glass(88,15,16,this));
            row.addView(playlistThumb(p, 42), Ui.lp(Ui.dp(this,42), Ui.dp(this,42)));
            LinearLayout texts = Ui.column(this); texts.setPadding(Ui.dp(this,10),0,0,0);
            TextView title = Ui.text(this,p.name,12.8f,Ui.TEXT,true); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title,new LinearLayout.LayoutParams(-1,0,1f));
            texts.addView(Ui.text(this,p.songs.size()+" 首",10.3f,Ui.DIM,false),new LinearLayout.LayoutParams(-1,0,1f));
            row.addView(texts,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1f));
            CheckBox check = new CheckBox(this);
            check.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Ui.ORANGE,Ui.DIM}));
            row.addView(check,Ui.lp(Ui.dp(this,42),Ui.dp(this,42)));
            Runnable toggle = () -> {
                if(selected.contains(p.id)) selected.remove(p.id); else selected.add(p.id);
                check.setChecked(selected.contains(p.id));
                if(count[0]!=null) count[0].setText("已选 "+selected.size()+" 个");
            };
            row.setClickable(true); Ui.applyRipple(row,Color.TRANSPARENT); row.setOnClickListener(v->toggle.run());
            check.setOnClickListener(v->{ if(check.isChecked()) selected.add(p.id); else selected.remove(p.id); if(count[0]!=null) count[0].setText("已选 "+selected.size()+" 个"); });
            rows.addView(row,marginTop(rows.getChildCount()==0?0:6));
        }
        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false); scroll.addView(rows);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,0,1f); sp.topMargin=Ui.dp(this,10); card.addView(scroll,sp);
        LinearLayout footer = Ui.row(this);
        count[0]=Ui.text(this,"已选 0 个",11.2f,Ui.TEXT_2,true);
        footer.addView(count[0],new LinearLayout.LayoutParams(0,Ui.dp(this,46),1f));
        TextView delete=modalButton("删除所选",true); delete.setBackground(Ui.primaryFill(Ui.ORANGE,18,this));
        footer.addView(delete,Ui.lp(Ui.dp(this,118),Ui.dp(this,46)));
        LinearLayout.LayoutParams fp=Ui.lp(-1,Ui.dp(this,46)); fp.topMargin=Ui.dp(this,10); card.addView(footer,fp);
        delete.setOnClickListener(v->{
            if(selected.isEmpty()){ toast("先选择要删除的歌单"); return; }
            ArrayList<String> ids=new ArrayList<>(selected);
            dismissModalNow();
            showGlassMessage("确认删除", "将从本机删除已选的 "+ids.size()+" 个歌单。此操作不会删除音乐平台上的原歌单。", "删除", ()->{
                playlists=store.removePlaylists(ids); openPlaylist=null; renderTab(); toast("已删除 "+ids.size()+" 个歌单");
            }, "取消", null);
        });
        presentFractionModal(card,.64f);
    }

    private void showMergePlaylists() {
        if (playlists.size() < 2) { toast("至少需要两个歌单才能合并"); return; }
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        LinearLayout card = modalCard("合并歌单", "选择任意两个歌单。重复歌曲会自动合并平台身份；320k 优先，无法区分时优先保留 QQ 身份。原歌单会保留。");
        EditText name = new EditText(this);
        name.setHint("合并后的歌单名称（可选）"); name.setHintTextColor(Ui.DIM); name.setTextColor(Ui.TEXT); name.setTextSize(13f);
        name.setTypeface(Typeface.DEFAULT); name.setSingleLine(true); name.setBackground(Ui.glass(156,16,22,this));
        name.setPadding(Ui.dp(this,13),0,Ui.dp(this,13),0);
        LinearLayout.LayoutParams np=Ui.lp(-1,Ui.dp(this,48)); np.topMargin=Ui.dp(this,12); card.addView(name,np);

        LinearLayout rows=Ui.column(this);
        TextView[] counter=new TextView[1];
        for(ImportedPlaylist p:playlists){
            LinearLayout row=Ui.row(this); row.setPadding(Ui.dp(this,8),Ui.dp(this,7),Ui.dp(this,8),Ui.dp(this,7));
            row.setBackground(Ui.glass(88,15,16,this));
            row.addView(playlistThumb(p,42),Ui.lp(Ui.dp(this,42),Ui.dp(this,42)));
            TextView title=Ui.text(this,p.name,12.6f,Ui.TEXT_2,true); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,Ui.dp(this,42),1f); tp.leftMargin=Ui.dp(this,10); row.addView(title,tp);
            CheckBox check=new CheckBox(this); check.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Ui.GREEN,Ui.DIM})); row.addView(check,Ui.lp(Ui.dp(this,42),Ui.dp(this,42)));
            Runnable sync=()->{
                boolean has=selected.contains(p.id);
                if(has){ selected.remove(p.id); check.setChecked(false); }
                else if(selected.size()<2){ selected.add(p.id); check.setChecked(true); }
                else { check.setChecked(false); toast("一次选择两个歌单即可"); }
                if(counter[0]!=null) counter[0].setText("已选 "+selected.size()+" / 2");
            };
            row.setClickable(true); Ui.applyRipple(row,Color.TRANSPARENT); row.setOnClickListener(v->sync.run());
            check.setOnClickListener(v->{
                if(check.isChecked()&&!selected.contains(p.id)){ if(selected.size()<2) selected.add(p.id); else { check.setChecked(false); toast("一次选择两个歌单即可"); } }
                else if(!check.isChecked()) selected.remove(p.id);
                if(counter[0]!=null) counter[0].setText("已选 "+selected.size()+" / 2");
            });
            rows.addView(row,marginTop(rows.getChildCount()==0?0:6));
        }
        ScrollView scroll=new ScrollView(this); scroll.setVerticalScrollBarEnabled(false); scroll.addView(rows);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1f); sp.topMargin=Ui.dp(this,10); card.addView(scroll,sp);
        LinearLayout footer=Ui.row(this); counter[0]=Ui.text(this,"已选 0 / 2",11.2f,Ui.TEXT_2,true); footer.addView(counter[0],new LinearLayout.LayoutParams(0,Ui.dp(this,46),1f));
        TextView merge=modalButton("合并并去重",true); merge.setBackground(Ui.primaryFill(Ui.GREEN,18,this)); footer.addView(merge,Ui.lp(Ui.dp(this,124),Ui.dp(this,46)));
        LinearLayout.LayoutParams fp=Ui.lp(-1,Ui.dp(this,46)); fp.topMargin=Ui.dp(this,10); card.addView(footer,fp);
        merge.setOnClickListener(v->{
            if(selected.size()!=2){ toast("请选择两个歌单"); return; }
            ArrayList<String> ids=new ArrayList<>(selected);
            String mergedName=name.getText().toString().trim();
            playlists=store.mergePlaylists(ids.get(0),ids.get(1),mergedName);
            dismissModalNow(); renderTab();
            ImportedPlaylist merged=playlists.isEmpty()?null:playlists.get(0);
            toast(merged==null?"合并完成":"已合并为《"+merged.name+"》 · "+merged.songs.size()+" 首");
        });
        presentFractionModal(card,.70f);
    }

    private void importPlaylist(String input) {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) { toast("请粘贴完整的歌单分享链接"); return; }

        // V68 platform-first import guard. Platform identity is determined before any parser runs.
        // We never probe another platform when the chosen platform parser fails: a malformed KG/KW/
        // QQ/WY link must fail as itself rather than being "rescued" into somebody else's playlist.
        String platform = playlistImportPlatform(text);
        if (platform.isEmpty()) {
            toast(text.matches(".*\\d{5,}.*")
                    ? "仅凭歌单 ID 无法可靠判断平台，请重新复制完整分享链接"
                    : "无法确认歌单平台，请重新复制完整的网易云 / QQ / 酷我 / 酷狗分享链接");
            return;
        }
        if ("ambiguous".equals(platform)) {
            toast("检测到多个音乐平台链接，请只保留一个完整歌单分享链接后重试");
            return;
        }

        showBusy(true);
        io.submit(() -> {
            ImportedPlaylist p = null;
            Exception last = null;
            try {
                if ("kw".equals(platform)) p = kuwoPlaylistImport.playlistFromInput(text);
                else if ("kg".equals(platform)) p = kugouPlaylistImport.playlistFromInput(text);
                else if ("tx".equals(platform)) p = qq.playlistFromInput(text);
                else if ("wy".equals(platform)) {
                    String id = neteasePlaylistId(text);
                    if (id == null) throw new Exception("无法识别网易云歌单 ID，请重新复制完整分享链接");
                    p = netease.playlist(id);
                }
                if (p != null && !platform.equals(p.source)) {
                    throw new Exception("平台身份校验失败：识别为" + playlistPlatformLabel(platform)
                            + "，但解析结果不是该平台；已拒绝导入以避免歌单张冠李戴");
                }
            } catch (Exception e) { last = e; p = null; }
            ImportedPlaylist result = p;
            Exception error = last;
            main.post(() -> {
                showBusy(false);
                if (result != null) {
                    playlists = store.savePlaylist(result);
                    // Metadata enrichment remains a slow side-car and never becomes a playback route.
                    prefetchExactVariants(result.songs);
                    toast("已导入《" + result.name + "》 · " + result.songs.size() + " 首");
                    tab = 2;
                    refreshNav();
                    renderTab();
                } else {
                    String reason = error == null ? "链接不完整或当前格式暂无法解析" : safeMessage(error);
                    toast(playlistPlatformLabel(platform) + "导入失败：" + reason + "。请重新复制该平台的完整分享链接");
                }
            });
        });
    }

    private String playlistImportPlatform(String raw) {
        String lower = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        boolean wy = lower.contains("music.163.com") || lower.contains("163cn.tv");
        boolean tx = lower.contains("y.qq.com") || lower.contains("qqmusic.qq.com");
        boolean kw = lower.contains("kuwo.cn");
        boolean kg = lower.contains("kugou.com");
        int count = (wy ? 1 : 0) + (tx ? 1 : 0) + (kw ? 1 : 0) + (kg ? 1 : 0);
        if (count > 1) return "ambiguous";
        if (wy) return "wy";
        if (tx) return "tx";
        if (kw) return "kw";
        if (kg) return "kg";
        return "";
    }

    private String playlistPlatformLabel(String platform) {
        if ("wy".equals(platform)) return "网易云";
        if ("tx".equals(platform)) return "QQ音乐";
        if ("kw".equals(platform)) return "酷我";
        if ("kg".equals(platform)) return "酷狗";
        return "歌单";
    }

    private void prefetchExactVariants(List<Song> songs) {
        if (songs == null || songs.isEmpty() || backgroundVariantEnricher == null) return;
        int index = 0;
        for (Song song : songs) {
            if (song == null) continue;
            long delayMs = Math.min(10L * 60L * 1000L, 900L * index++);
            try {
                variantPrefetch.schedule(() -> {
                    try { backgroundVariantEnricher.enrichIfNeeded(song, null); } catch (Exception ignored) { }
                }, delayMs, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ignored) {
                // Activity may already be destroying while an import result is being delivered.
            }
        }
    }

    private String neteasePlaylistId(String text) {
        if (text.matches("\\d{5,}")) return text;
        Matcher m = Pattern.compile("(?:id=|playlist/)(\\d{5,})").matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private View favoritesPage() {
        LinearLayout body = Ui.column(this);
        if (favorites.isEmpty()) {
            body.addView(compactEmpty("在搜索结果或播放页点爱心，就会保存在这里"));
        } else {
            body.addView(collectionPlayHero("收藏歌曲", favorites, Ui.PINK, Ui.ORANGE));

            LinearLayout tools = Ui.row(this);
            TextView manage = outlineAction("批量管理");
            manage.setTextColor(Ui.CYAN);
            manage.setOnClickListener(v -> showSongBatchManager("收藏歌曲", favorites, null));
            tools.addView(manage, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1f));
            LinearLayout.LayoutParams tlp = Ui.lp(-1, Ui.dp(this, 42)); tlp.topMargin = Ui.dp(this, 10);
            body.addView(tools, tlp);

            body.addView(sortableSongsHeader("歌曲", favorites.size(), () -> showManualSongOrder("收藏歌曲", favorites, null)), marginTop(18));
            body.addView(songList(favorites, favorites), marginTop(6));
        }
        return pageScaffold("收藏", favorites.isEmpty() ? "还没有收藏歌曲" : favorites.size() + " 首保存在本机", body);
    }

    private View collectionPlayHero(String title, List<Song> songs, int firstAccent, int secondAccent) {
        LinearLayout hero = Ui.row(this);
        hero.setPadding(Ui.dp(this, 14), Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 13));
        hero.setBackground(Ui.tintedGlass(firstAccent, 22, this));
        if (!songs.isEmpty()) {
            ImageView cover = new ImageView(this);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setClipToOutline(true);
            cover.setBackground(Ui.round(Color.rgb(25, 25, 32), 14, this));
            ImageLoader.load(songs.get(0).coverUrl, cover, null);
            hero.addView(cover, Ui.lp(Ui.dp(this, 58), Ui.dp(this, 58)));
        }
        LinearLayout texts = Ui.column(this);
        texts.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 10), 0);
        texts.addView(Ui.text(this, title, 15f, Ui.TEXT, true), new LinearLayout.LayoutParams(-1, 0, 1f));
        texts.addView(Ui.text(this, songs.size() + " 首 · 保存在本机", 10.8f, Ui.TEXT_2, false), new LinearLayout.LayoutParams(-1, 0, 1f));
        hero.addView(texts, new LinearLayout.LayoutParams(0, Ui.dp(this, 58), 1f));
        TextView play = Ui.text(this, "从第一首播放", 11.2f, Color.rgb(12, 11, 18), true);
        play.setGravity(Gravity.CENTER);
        play.setBackground(Ui.primaryFill(firstAccent, 16, this));
        play.setClickable(true); Ui.applyRipple(play, Color.argb(40, 0, 0, 0));
        play.setOnClickListener(v -> playQueue(songs, 0));
        hero.addView(play, Ui.lp(Ui.dp(this, 112), Ui.dp(this, 40)));
        return hero;
    }

    /** Stable layout skeleton: same row geometry as content, no spinner-to-page jump. */
    private View skeletonSongRows(int count, int accent) {
        LinearLayout box = Ui.column(this);
        int rows = Math.max(2, Math.min(7, count));
        int shimmerAccent = progressiveLoadingAccent(accent);
        for (int i = 0; i < rows; i++) {
            FluidPlaceholderView row = new FluidPlaceholderView(this, FluidPlaceholderView.SONG_ROW, shimmerAccent);
            row.setShimmerStartDelay(i * 62L);
            LinearLayout.LayoutParams rp = Ui.lp(-1, Ui.dp(this, 72));
            if (i > 0) rp.topMargin = Ui.dp(this, 7);
            box.addView(row, rp);
        }
        return box;
    }

    private View smartCollectionPage(SmartCollection collection) {
        LinearLayout body = Ui.column(this);
        LinearLayout back = Ui.row(this);
        back.setGravity(Gravity.CENTER_VERTICAL);
        IconView bi = new IconView(this, IconView.Type.BACK, collection.accent);
        back.addView(bi, Ui.lp(Ui.dp(this, 24), Ui.dp(this, 24)));
        TextView bt = Ui.text(this, "返回", 13, collection.accent, true);
        back.addView(bt, new LinearLayout.LayoutParams(0, Ui.dp(this, 34), 1f));
        back.setClickable(true);
        back.setOnClickListener(v -> closeSmartCollectionToHome());
        body.addView(back, Ui.lp(-1, Ui.dp(this, 38)));

        LinearLayout hero = Ui.column(this);
        hero.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        hero.setBackground(Ui.tintedGlass(collection.accent, 23, this));

        LinearLayout top = Ui.row(this);
        if (!collection.songs.isEmpty()) {
            ImageView cover = new ImageView(this);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setClipToOutline(true);
            cover.setBackground(Ui.round(Color.rgb(22, 22, 28), 18, this));
            ImageLoader.load(collection.songs.get(0).coverUrl, cover, null);
            top.addView(cover, Ui.lp(Ui.dp(this, 78), Ui.dp(this, 78)));
        } else {
            FrameLayout icon = Ui.iconButton(this, "weather".equals(collection.kind) ? IconView.Type.WEATHER : IconView.Type.MUSIC,
                    78, collection.accent, Color.argb(24, Color.red(collection.accent), Color.green(collection.accent), Color.blue(collection.accent)));
            top.addView(icon, Ui.lp(Ui.dp(this, 78), Ui.dp(this, 78)));
        }
        LinearLayout texts = Ui.column(this);
        texts.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), 0, Ui.dp(this, 2));
        TextView name = Ui.text(this, collection.title, 20, Ui.TEXT, true);
        name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(name, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView sub = Ui.text(this, collection.subtitle, 11.5f, Ui.TEXT_2, false);
        sub.setSingleLine(true); sub.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(sub, new LinearLayout.LayoutParams(-1, 0, 1f));
        top.addView(texts, new LinearLayout.LayoutParams(0, Ui.dp(this, 78), 1f));
        hero.addView(top, Ui.lp(-1, Ui.dp(this, 78)));

        TextView play = filledAction("从第一首播放", collection.accent, Color.rgb(12, 12, 18));
        play.setEnabled(!collection.songs.isEmpty());
        play.setAlpha(collection.songs.isEmpty() ? .42f : 1f);
        play.setOnClickListener(v -> { if (!collection.songs.isEmpty()) playQueue(collection.songs, 0); });
        LinearLayout.LayoutParams pp = Ui.lp(-1, Ui.dp(this, 44)); pp.topMargin = Ui.dp(this, 12);
        hero.addView(play, pp);
        body.addView(hero, Ui.lp(-1, -2));

        boolean collectionLoading = collection.songs.isEmpty() && collection.subtitle != null && collection.subtitle.contains("正在");
        body.addView(sectionHeader("歌曲", collectionLoading ? "准备中" : collection.songs.size() + " 首"), marginTop(22));
        if (collectionLoading) {
            body.addView(skeletonSongRows(5, collection.accent), marginTop(6));
        } else if (collection.songs.isEmpty()) {
            body.addView(compactEmpty("暂时没有生成歌曲"), marginTop(6));
        } else {
            body.addView(smartCollectionSongList(collection), marginTop(6));
        }
        String pageSub;
        if ("weather".equals(collection.kind)) pageSub = "天气 × 时间 × 你的口味";
        else if ("recent".equals(collection.kind)) pageSub = "最近播放 · 最多保留 50 首";
        else pageSub = "根据你的本地音乐偏好生成";
        View engine = null;
        if ("daily".equals(collection.kind) || "private".equals(collection.kind)) {
            engine = engineInsightAction(collection.accent, collection.title + "引擎洞察",
                    () -> showRecommendationComposition(collection), this::showPersonalizationMatrix);
        }
        return pageScaffold(collection.title, pageSub, body, engine);
    }

    /** V89 quick contextual insight. The full dashboard stays one deliberate level deeper. */
    private void showRecommendationComposition(SmartCollection collection) {
        if (collection == null || recommendations == null) return;
        RecommendationEngine.RecommendationSummary summary = recommendations.summaryFor(collection.kind, collection.songs, favorites, playlists, history);
        LinearLayout card = modalCard("引擎洞察", collection.title + " · 为什么是这些歌");
        if (summary.total > 0) {
            card.addView(personalizationStatGrid(
                    new String[]{"新发现", "ListenBrainz", "熟悉延伸", "再发现"},
                    new String[]{summary.unheardPercent() + "%", String.valueOf(summary.listenBrainz), String.valueOf(summary.familiar), String.valueOf(summary.rediscovery)},
                    new int[]{collection.accent, Ui.CYAN, Ui.GREEN, Ui.GOLD}), marginTop(10));
        }

        String posture;
        if ("private".equals(collection.kind)) posture = "私人电台会继续读取当前 Session 的听完、跳过和收藏反馈，后续队列会随你此刻的选择改变。";
        else posture = "每日推荐偏向发现：在长期口味和近期兴趣之间保留一部分陌生候选，同时避免让同一歌手霸屏。";
        TextView state = Ui.text(this, posture, 10.8f, Ui.TEXT_2, false);
        state.setLineSpacing(0f, 1.18f);
        card.addView(state, marginTop(12));

        int shown = 0;
        for (Song song : collection.songs) {
            if (song == null || shown >= 1) break;
            RecommendationEngine.RecommendationReason reason = recommendations.reasonFor(collection.kind, song);
            LinearLayout r = Ui.column(this);
            r.setPadding(Ui.dp(this, 12), Ui.dp(this, 9), Ui.dp(this, 12), Ui.dp(this, 9));
            r.setBackground(Ui.glass(74, 14, 16, this));
            TextView n = Ui.text(this, song.title + " · " + song.artist, 11.4f, Ui.TEXT, true);
            n.setSingleLine(true); n.setEllipsize(TextUtils.TruncateAt.END);
            r.addView(n, Ui.lp(-1, Ui.dp(this, 23)));
            r.addView(Ui.text(this, reason.why, 10.0f, reason.unheard ? Ui.CYAN : Ui.TEXT_2, false), Ui.lp(-1, Ui.dp(this, 22)));
            card.addView(r, marginTop(6));
            shown++;
        }

        LinearLayout actions = Ui.row(this);
        TextView close = modalButton("关闭", false);
        close.setOnClickListener(v -> hideModal());
        actions.addView(close, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f));
        Space gap = new Space(this); actions.addView(gap, Ui.lp(Ui.dp(this, 8), 1));
        TextView full = modalButton("完整音乐画像", true);
        full.setOnClickListener(v -> { dismissModalNow(); showPersonalizationMatrix(); });
        actions.addView(full, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1.35f));
        card.addView(actions, marginTop(14));

        TextView gesture = Ui.text(this, "提示：长按页面右上角的引擎键可直接打开完整矩阵。", 9.7f, Ui.DIM, false);
        gesture.setGravity(Gravity.CENTER);
        card.addView(gesture, marginTop(8));
        presentModal(card);
    }

    private void showRecommendationReason(Song song) {
        if (song == null || recommendations == null || openSmartCollection == null) return;
        RecommendationEngine.RecommendationReason reason = recommendations.reasonFor(openSmartCollection.kind, song);
        String body = reason.why + (reason.unheard
                ? "\n\n这是 Lunaxy 历史里未听过的候选。"
                : "\n\n这是熟悉内容或重新发现候选。")
                + "\n\n推荐只决定‘想给你哪首歌’；真正播放仍由当前 SourceCoordinator 实时找可用线路。";
        showGlassMessage("为什么推荐《" + song.title + "》", body, "知道了", null, "个性化矩阵", this::showPersonalizationMatrix);
    }

    private void resetSmartCollectionReveal(String scope) {
        smartCollectionRevealScope = scope == null ? "" : scope;
        smartCollectionRevealKeys.clear();
    }

    private View smartCollectionSongList(SmartCollection collection) {
        LinearLayout box = Ui.column(this);
        if (collection == null || collection.songs == null) return box;
        if (!collection.kind.equals(smartCollectionRevealScope)) resetSmartCollectionReveal(collection.kind);
        int stagedIndex = 0;
        for (int i = 0; i < collection.songs.size(); i++) {
            final int queueIndex = i;
            Song song = collection.songs.get(i);
            View row = songRow(song, collection.songs, i);
            // Recommendation collections behave like real playlists: tapping a row installs the
            // whole generated list as the queue and begins from that selected song. Child action
            // buttons keep their own favorite / next / more listeners.
            row.setOnClickListener(v -> playQueue(collection.songs, queueIndex));

            String revealKey = song.key() + "#" + i;
            boolean shouldStage = !smartCollectionRevealKeys.contains(revealKey);
            if (!shouldStage || SpringMotion.isReducedMotion()) {
                box.addView(row, marginTop(i == 0 ? 0 : 7));
                smartCollectionRevealKeys.add(revealKey);
                continue;
            }

            CurtainRevealFrame frame = new CurtainRevealFrame(this);
            int revealAccent = progressiveLoadingAccent(collection.accent);
            frame.setAccent(revealAccent);
            FluidPlaceholderView placeholder = new FluidPlaceholderView(this, FluidPlaceholderView.SONG_ROW, revealAccent);
            placeholder.setShimmerStartDelay(stagedIndex * 62L);
            frame.setPlaceholder(placeholder);
            LinearLayout.LayoutParams fp = Ui.lp(-1, Ui.dp(this, 72));
            if (i > 0) fp.topMargin = Ui.dp(this, 7);
            box.addView(frame, fp);
            smartCollectionRevealKeys.add(revealKey);
            frame.reveal(row, 48L + stagedIndex * 62L);
            stagedIndex++;
        }
        return box;
    }

    private View playlistDetailPage(ImportedPlaylist p) {
        clearActivePlaylistUiRefs();
        activePlaylistPageModel = p;
        final int loadGeneration = ++playlistLoadGeneration;

        LinearLayout body = Ui.column(this);
        LinearLayout back = Ui.row(this);
        back.setGravity(Gravity.CENTER_VERTICAL);
        IconView bi = new IconView(this, IconView.Type.BACK, Ui.CYAN);
        back.addView(bi, Ui.lp(Ui.dp(this, 24), Ui.dp(this, 24)));
        TextView bt = Ui.text(this, "返回", 13, Ui.CYAN, true);
        back.addView(bt, new LinearLayout.LayoutParams(0, Ui.dp(this, 34), 1f));
        back.setClickable(true);
        back.setOnClickListener(v -> closePlaylistToLibrary());
        activePlaylistDetailBack = back;
        body.addView(back, Ui.lp(-1, Ui.dp(this, 38)));

        LinearLayout hero = Ui.column(this);
        hero.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18));
        activePlaylistHeroAccent = playlistArtworkAccent(p);
        hero.setBackground(Ui.tintedGlass(activePlaylistHeroAccent, 23, this));
        activePlaylistHeroCard = hero;
        LinearLayout heroTop = Ui.row(this);
        View heroThumb = playlistThumb(p, 88, (bitmap, color) -> applyPlaylistHeroArtworkAccent(p.id, hero, color));
        activePlaylistHeroThumb = heroThumb;
        heroTop.addView(heroThumb, Ui.lp(Ui.dp(this, 88), Ui.dp(this, 88)));
        LinearLayout heroText = Ui.column(this);
        heroText.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), 0, Ui.dp(this, 2));
        TextView name = Ui.text(this, p.name, 21.2f, Ui.TEXT, true);
        activePlaylistHeroTitle = name;
        name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
        heroText.addView(name, new LinearLayout.LayoutParams(-1, 0, 1f));
        String playlistKind = p.source.equals("local") ? "本地歌单" : (p.source.equals("tx") ? "QQ 音乐"
                : (p.source.equals("kw") ? "酷我音乐" : (p.source.equals("kg") ? "酷狗音乐" : "网易云")));
        activePlaylistHeroMeta = Ui.text(this, p.songs.size() + " 首 · " + playlistKind, 11.5f, Ui.TEXT_2, false);
        heroText.addView(activePlaylistHeroMeta, new LinearLayout.LayoutParams(-1, 0, 1f));
        heroTop.addView(heroText, new LinearLayout.LayoutParams(0, Ui.dp(this, 88), 1f));
        hero.addView(heroTop, Ui.lp(-1, Ui.dp(this, 88)));
        body.addView(hero, Ui.lp(-1, -2));

        View playlistHeader = sortableSongsHeader("歌曲", p.songs.size(), () -> showManualSongOrder(p.name, p.songs, p.id));
        if (playlistHeader instanceof ViewGroup && ((ViewGroup) playlistHeader).getChildCount() > 1
                && ((ViewGroup) playlistHeader).getChildAt(1) instanceof TextView)
            activePlaylistSongCount = (TextView) ((ViewGroup) playlistHeader).getChildAt(1);
        activePlaylistDetailSongHeader = playlistHeader;
        body.addView(playlistHeader, marginTop(22));

        final LinearLayout host = Ui.column(this);
        activePlaylistDetailSongHost = host;
        final int[] loadedRef = new int[]{0};
        final boolean[] scheduledRef = new boolean[]{false};
        activePlaylistSongHost = host;
        activePlaylistLoadedRef = loadedRef;
        if (p.songs.isEmpty()) {
            body.addView(compactEmpty("这个歌单还没有歌曲，点上方 + 从收藏、搜索或其他歌单添加"), marginTop(6));
        } else {
            body.addView(host, marginTop(6));
            // The page is usable immediately.  The first rows are only lightweight geometry until
            // the next UI turns, then each real row wipes in from left to right without moving layout.
            appendPlaylistPlaceholderBatch(host, Math.min(PLAYLIST_PLACEHOLDER_COUNT, p.songs.size()), Ui.CYAN);
            FrameLayout loaderSlot = new FrameLayout(this);
            activePlaylistLoader = new FluidLoadingIconView(this, Ui.CYAN);
            FrameLayout.LayoutParams lip = Ui.frame(Ui.dp(this, 32), Ui.dp(this, 32), Gravity.CENTER);
            loaderSlot.addView(activePlaylistLoader, lip);
            body.addView(loaderSlot, Ui.lp(-1, Ui.dp(this, 54)));
        }

        LinearLayout playlistActions = Ui.row(this);
        playlistActions.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout manage = Ui.iconButton(this, IconView.Type.MORE, 42, Ui.GOLD,
                Color.argb(22, Color.red(Ui.GOLD), Color.green(Ui.GOLD), Color.blue(Ui.GOLD)));
        manage.setContentDescription("批量管理歌曲");
        manage.setOnClickListener(v -> showSongBatchManager(p.name, p.songs, p.id));
        playlistActions.addView(manage, Ui.lp(Ui.dp(this, 42), Ui.dp(this, 42)));
        Space actionGap1 = new Space(this); playlistActions.addView(actionGap1, Ui.lp(Ui.dp(this, 7), 1));
        FrameLayout add = Ui.iconButton(this, IconView.Type.PLUS, 42, Ui.GREEN,
                Color.argb(22, Color.red(Ui.GREEN), Color.green(Ui.GREEN), Color.blue(Ui.GREEN)));
        add.setContentDescription("向歌单添加歌曲");
        add.setOnClickListener(v -> showAddSongsToPlaylist(p));
        playlistActions.addView(add, Ui.lp(Ui.dp(this, 42), Ui.dp(this, 42)));
        Space actionGap2 = new Space(this); playlistActions.addView(actionGap2, Ui.lp(Ui.dp(this, 7), 1));
        FrameLayout playlistSearch = Ui.iconButton(this, IconView.Type.SEARCH, 42, Ui.CYAN,
                Color.argb(22, Color.red(Ui.CYAN), Color.green(Ui.CYAN), Color.blue(Ui.CYAN)));
        playlistSearch.setContentDescription("搜索当前歌单");
        playlistSearch.setOnClickListener(v -> showPlaylistSearch(p));
        playlistActions.addView(playlistSearch, Ui.lp(Ui.dp(this, 42), Ui.dp(this, 42)));

        View page = pageScaffold("歌单详情", "已保存到本机", body, playlistActions);
        if (page instanceof ScrollView && ((ScrollView) page).getChildCount() > 0
                && ((ScrollView) page).getChildAt(0) instanceof ViewGroup) {
            ViewGroup scaffold = (ViewGroup) ((ScrollView) page).getChildAt(0);
            if (scaffold.getChildCount() > 0) activePlaylistDetailTitleRow = scaffold.getChildAt(0);
            if (scaffold.getChildCount() > 1) activePlaylistDetailSubtitle = scaffold.getChildAt(1);
        }
        if (pendingPlaylistHeroPush && !SpringMotion.isReducedMotion()) preparePlaylistDetailForSharedEntrance();
        if (page instanceof ScrollView) {
            activePlaylistScroll = (ScrollView) page;
            ScrollView scroll = (ScrollView) page;
            scroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (loadGeneration != playlistLoadGeneration || activePlaylistPageModel == null
                        || !p.id.equals(activePlaylistPageModel.id)) return;
                maybeAppendPlaylistRows(scroll, host, p, loadedRef, scheduledRef);
            });
            if (!p.songs.isEmpty()) {
                // Give navigation one rendered frame first.  Heavy row construction must never own
                // the tap that opened the page.
                final long firstMaterializeDelay = pendingPlaylistHeroPush && !SpringMotion.isReducedMotion() ? 560L : 34L;
                page.postOnAnimation(() -> page.postDelayed(() -> {
                    if (loadGeneration == playlistLoadGeneration && activePlaylistPageModel != null
                            && p.id.equals(activePlaylistPageModel.id))
                        materializePlaylistBatch(host, p, loadedRef, scheduledRef, PLAYLIST_INITIAL_RENDER_COUNT, loadGeneration);
                }, firstMaterializeDelay));
                // A small prewarm continues after the page has landed, never enough to inflate an
                // 800-song list at once.  The user sees a calm stream of resolved rows, then deeper
                // batches are driven by actual scrolling.
                page.postDelayed(() -> {
                    if (loadGeneration == playlistLoadGeneration && activePlaylistPageModel != null
                            && p.id.equals(activePlaylistPageModel.id) && !scheduledRef[0])
                        materializePlaylistBatch(host, p, loadedRef, scheduledRef,
                                Math.min(p.songs.size(), PLAYLIST_INITIAL_RENDER_COUNT + PLAYLIST_APPEND_BATCH), loadGeneration);
                }, pendingPlaylistHeroPush && !SpringMotion.isReducedMotion() ? 980L : 430L);
                page.postDelayed(() -> {
                    if (loadGeneration == playlistLoadGeneration && activePlaylistPageModel != null
                            && p.id.equals(activePlaylistPageModel.id) && !scheduledRef[0])
                        materializePlaylistBatch(host, p, loadedRef, scheduledRef,
                                Math.min(p.songs.size(), PLAYLIST_INITIAL_RENDER_COUNT + PLAYLIST_APPEND_BATCH * 2), loadGeneration);
                }, pendingPlaylistHeroPush && !SpringMotion.isReducedMotion() ? 1460L : 820L);
            }
        }

        if (!p.songs.isEmpty()) {
            final long playlistCoachDelay = pendingPlaylistHeroPush && !SpringMotion.isReducedMotion() ? 1180L : 420L;
            page.postDelayed(() -> {
                if (loadGeneration != playlistLoadGeneration) return;
                View target = firstPlaylistCoachTarget(host);
                maybeShowContextCoach(COACH_PLAYLIST_QUEUE, target,
                        "点一首，会从这里接管整张歌单",
                        "歌单会边看边补齐：点击任意已经出现的歌曲仍会把完整歌单设为播放队列，并从你点的这一首开始。" );
            }, playlistCoachDelay);
        }
        return page;
    }

    private View playlistDetailVirtualPage(ImportedPlaylist p) {
        clearActivePlaylistUiRefs();
        activePlaylistPageModel = p;

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(android.R.color.transparent);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setVerticalScrollBarEnabled(false);
        list.setScrollingCacheEnabled(true);
        list.setClipToPadding(false);
        list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        list.setRecyclerListener(view -> {
            if (view == null) return;
            View row = view;
            if (view instanceof ViewGroup && ((ViewGroup) view).getChildCount() > 0)
                row = ((ViewGroup) view).getChildAt(0);
            FluidTrackHaloDrawable halo = playbackRowHalos.get(row);
            if (halo != null) halo.stopRendering();
        });

        LinearLayout header = Ui.column(this);
        header.setPadding(Ui.dp(this, 20), Ui.dp(this, 23), Ui.dp(this, 20), 0);

        LinearLayout titleRow = Ui.row(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, "歌单详情", 30.5f, Ui.TEXT, true);
        title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f));

        LinearLayout playlistActions = Ui.row(this);
        playlistActions.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout manage = Ui.iconButton(this, IconView.Type.MORE, 42, Ui.GOLD,
                Color.argb(22, Color.red(Ui.GOLD), Color.green(Ui.GOLD), Color.blue(Ui.GOLD)));
        manage.setContentDescription("批量管理歌曲");
        manage.setOnClickListener(v -> showSongBatchManager(p.name, p.songs, p.id));
        playlistActions.addView(manage, Ui.lp(Ui.dp(this, 42), Ui.dp(this, 42)));
        Space actionGap1 = new Space(this); playlistActions.addView(actionGap1, Ui.lp(Ui.dp(this, 5), 1));
        FrameLayout add = Ui.iconButton(this, IconView.Type.PLUS, 42, Ui.GREEN,
                Color.argb(22, Color.red(Ui.GREEN), Color.green(Ui.GREEN), Color.blue(Ui.GREEN)));
        add.setContentDescription("向歌单添加歌曲");
        add.setOnClickListener(v -> showAddSongsToPlaylist(p));
        playlistActions.addView(add, Ui.lp(Ui.dp(this, 42), Ui.dp(this, 42)));
        Space actionGap2 = new Space(this); playlistActions.addView(actionGap2, Ui.lp(Ui.dp(this, 5), 1));
        FrameLayout playlistSearch = Ui.iconButton(this, IconView.Type.SEARCH, 42, Ui.CYAN,
                Color.argb(22, Color.red(Ui.CYAN), Color.green(Ui.CYAN), Color.blue(Ui.CYAN)));
        playlistSearch.setContentDescription("搜索当前歌单");
        playlistSearch.setOnClickListener(v -> showPlaylistSearch(p));
        playlistActions.addView(playlistSearch, Ui.lp(Ui.dp(this, 42), Ui.dp(this, 42)));
        titleRow.addView(playlistActions, Ui.lp(-2, Ui.dp(this, 48)));
        header.addView(titleRow, Ui.lp(-1, Ui.dp(this, 48)));
        header.addView(Ui.text(this, "已保存到本机", 13.5f, Ui.TEXT_2, false), Ui.lp(-1, Ui.dp(this, 31)));

        LinearLayout back = Ui.row(this);
        back.setGravity(Gravity.CENTER_VERTICAL);
        IconView bi = new IconView(this, IconView.Type.BACK, Ui.CYAN);
        back.addView(bi, Ui.lp(Ui.dp(this, 24), Ui.dp(this, 24)));
        TextView bt = Ui.text(this, "返回", 13, Ui.CYAN, true);
        back.addView(bt, new LinearLayout.LayoutParams(0, Ui.dp(this, 34), 1f));
        back.setClickable(true); back.setFocusable(true); Ui.applyRipple(back, Color.TRANSPARENT);
        back.setOnClickListener(v -> closePlaylistToLibrary());
        LinearLayout.LayoutParams bp = Ui.lp(-1, Ui.dp(this, 38)); bp.topMargin = Ui.dp(this, 15);
        header.addView(back, bp);

        LinearLayout hero = Ui.column(this);
        hero.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        activePlaylistHeroAccent = playlistArtworkAccent(p);
        hero.setBackground(Ui.tintedGlass(activePlaylistHeroAccent, 23, this));
        activePlaylistHeroCard = hero;
        LinearLayout heroTop = Ui.row(this);
        View virtualHeroThumb = playlistThumb(p, 78, (bitmap, color) -> applyPlaylistHeroArtworkAccent(p.id, hero, color));
        activePlaylistHeroThumb = virtualHeroThumb;
        heroTop.addView(virtualHeroThumb, Ui.lp(Ui.dp(this, 78), Ui.dp(this, 78)));
        LinearLayout heroText = Ui.column(this);
        heroText.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), 0, Ui.dp(this, 2));
        TextView name = Ui.text(this, p.name, 20, Ui.TEXT, true);
        name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
        heroText.addView(name, new LinearLayout.LayoutParams(-1, 0, 1f));
        String playlistKind = p.source.equals("local") ? "本地歌单" : (p.source.equals("tx") ? "QQ 音乐"
                : (p.source.equals("kw") ? "酷我音乐" : (p.source.equals("kg") ? "酷狗音乐" : "网易云")));
        activePlaylistHeroMeta = Ui.text(this, p.songs.size() + " 首 · " + playlistKind, 11.5f, Ui.TEXT_2, false);
        heroText.addView(activePlaylistHeroMeta, new LinearLayout.LayoutParams(-1, 0, 1f));
        heroTop.addView(heroText, new LinearLayout.LayoutParams(0, Ui.dp(this, 78), 1f));
        hero.addView(heroTop, Ui.lp(-1, Ui.dp(this, 78)));
        LinearLayout.LayoutParams hp = Ui.lp(-1, -2); hp.topMargin = Ui.dp(this, 4); header.addView(hero, hp);

        View playlistHeader = sortableSongsHeader("歌曲", p.songs.size(), () -> showManualSongOrder(p.name, p.songs, p.id));
        if (playlistHeader instanceof ViewGroup && ((ViewGroup) playlistHeader).getChildCount() > 1
                && ((ViewGroup) playlistHeader).getChildAt(1) instanceof TextView)
            activePlaylistSongCount = (TextView) ((ViewGroup) playlistHeader).getChildAt(1);
        LinearLayout.LayoutParams shp = marginTop(22); header.addView(playlistHeader, shp);

        list.addHeaderView(header, null, false);
        Space footer = new Space(this);
        footer.setLayoutParams(Ui.lp(-1, Ui.dp(this, 162)));
        list.addFooterView(footer, null, false);

        PlaylistDetailAdapter adapter = new PlaylistDetailAdapter(p);
        list.setAdapter(adapter);
        activePlaylistList = list;
        activePlaylistAdapter = adapter;

        if (restorePlaylistPosition && p.id.equals(playlistListRestoreId)) {
            final int pos = Math.max(0, playlistListRestorePosition);
            final int top = playlistListRestoreTop;
            list.post(() -> {
                if (activePlaylistList == list && activePlaylistPageModel != null && p.id.equals(activePlaylistPageModel.id))
                    list.setSelectionFromTop(Math.min(pos, Math.max(0, list.getCount() - 1)), top);
            });
            restorePlaylistPosition = false;
        }
        list.postDelayed(() -> {
            View target = list.getChildCount() > 1 ? list.getChildAt(1) : list;
            maybeShowContextCoach(COACH_PLAYLIST_QUEUE, target,
                    "点一首，会从这里接管整张歌单",
                    "长歌单只渲染屏幕附近的行，但点击任意歌曲时仍会把完整歌单设为播放队列，并从你点的这一首开始。" );
        }, 180L);
        return list;
    }

    private static final class PlaylistDetailHolder {
        LinearLayout row;
        ImageView cover;
        TextView title;
        TextView artist;
        FrameLayout favorite;
        IconView favoriteIcon;
        FrameLayout next;
        FrameLayout more;
    }

    private final class PlaylistDetailAdapter extends BaseAdapter {
        private final ImportedPlaylist playlist;
        PlaylistDetailAdapter(ImportedPlaylist playlist) { this.playlist = playlist; }
        @Override public int getCount() { return playlist == null || playlist.songs == null ? 0 : playlist.songs.size(); }
        @Override public Object getItem(int position) { return playlist.songs.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            PlaylistDetailHolder holder;
            LinearLayout wrapper;
            if (convertView == null) {
                wrapper = Ui.column(MainActivity.this);
                wrapper.setPadding(Ui.dp(MainActivity.this, 20), 0, Ui.dp(MainActivity.this, 20), 0);
                holder = new PlaylistDetailHolder();
                holder.row = Ui.row(MainActivity.this);
                holder.row.setGravity(Gravity.CENTER_VERTICAL);
                holder.row.setPadding(Ui.dp(MainActivity.this, 9), Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 8));
                holder.row.setClickable(true); holder.row.setFocusable(true);
                Ui.applyRipple(holder.row, Color.argb(33, 255, 255, 255));

                holder.cover = new ImageView(MainActivity.this);
                holder.cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                holder.cover.setBackground(Ui.round(Color.rgb(22, 22, 28), 12, MainActivity.this));
                holder.cover.setClipToOutline(true);
                holder.row.addView(holder.cover, Ui.lp(Ui.dp(MainActivity.this, 56), Ui.dp(MainActivity.this, 56)));

                LinearLayout text = Ui.column(MainActivity.this);
                text.setPadding(Ui.dp(MainActivity.this, 12), Ui.dp(MainActivity.this, 2), Ui.dp(MainActivity.this, 7), Ui.dp(MainActivity.this, 2));
                holder.title = Ui.text(MainActivity.this, "", 14.2f, Ui.TEXT, true);
                holder.title.setSingleLine(true); holder.title.setEllipsize(TextUtils.TruncateAt.END);
                holder.artist = Ui.text(MainActivity.this, "", 11.2f, Ui.TEXT_2, false);
                holder.artist.setSingleLine(true); holder.artist.setEllipsize(TextUtils.TruncateAt.END);
                text.addView(holder.title, new LinearLayout.LayoutParams(-1, 0, 1f));
                text.addView(holder.artist, new LinearLayout.LayoutParams(-1, 0, 1f));
                holder.row.addView(text, new LinearLayout.LayoutParams(0, Ui.dp(MainActivity.this, 56), 1f));

                holder.favorite = Ui.iconButton(MainActivity.this, IconView.Type.HEART, 38, Ui.DIM, Color.TRANSPARENT);
                holder.favoriteIcon = (IconView) holder.favorite.getChildAt(0);
                holder.row.addView(holder.favorite, Ui.lp(Ui.dp(MainActivity.this, 34), Ui.dp(MainActivity.this, 34)));
                holder.next = Ui.iconButton(MainActivity.this, IconView.Type.PLUS, 34, Ui.TEXT_2, Color.TRANSPARENT);
                holder.next.setContentDescription("添加到下一首");
                holder.row.addView(holder.next, Ui.lp(Ui.dp(MainActivity.this, 32), Ui.dp(MainActivity.this, 32)));
                holder.more = Ui.iconButton(MainActivity.this, IconView.Type.MORE, 31, Ui.DIM, Color.TRANSPARENT);
                holder.row.addView(holder.more, Ui.lp(Ui.dp(MainActivity.this, 30), Ui.dp(MainActivity.this, 30)));
                wrapper.addView(holder.row, Ui.lp(-1, Ui.dp(MainActivity.this, 72)));
                wrapper.addView(new Space(MainActivity.this), Ui.lp(-1, Ui.dp(MainActivity.this, 7)));
                wrapper.setTag(holder);
            } else {
                wrapper = (LinearLayout) convertView;
                holder = (PlaylistDetailHolder) wrapper.getTag();
            }

            Song song = playlist.songs.get(position);
            holder.row.animate().cancel(); holder.row.setAlpha(1f); holder.row.setTranslationX(0f);
            holder.title.setText(song.title);
            holder.artist.setText(subtitleFor(song));
            String coverKey = song.coverUrl == null ? "" : song.coverUrl;
            Object oldCoverKey = holder.cover.getTag();
            if (!coverKey.equals(oldCoverKey == null ? "" : String.valueOf(oldCoverKey))) {
                holder.cover.setImageDrawable(null);
                holder.cover.setTag(coverKey);
                if (!coverKey.trim().isEmpty()) ImageLoader.load(coverKey, holder.cover, null);
            }
            boolean favorite = isFavorite(song);
            holder.favoriteIcon.setIconColor(favorite ? Ui.PINK : Ui.DIM);
            holder.favoriteIcon.setFavoriteState(favorite, false);
            PlaylistDetailHolder bound = holder;
            holder.favorite.setOnClickListener(v -> {
                favorites = store.toggleFavorite(song);
                boolean nowFavorite = isFavorite(song);
                bound.favoriteIcon.setIconColor(nowFavorite ? Ui.PINK : Ui.DIM);
                bound.favoriteIcon.setFavoriteState(nowFavorite, true);
                if (nowFavorite) animateHeartBurst(bound.favorite, Ui.PINK);
            });
            holder.next.setOnClickListener(v -> {
                ensurePlaybackServiceStarted();
                if (playback != null) playback.enqueueNext(song); else pendingNextSongs.add(song);
                toast("已添加到下一首");
            });
            holder.more.setOnClickListener(v -> showSongActions(song, playlist.id, bound.row));
            holder.row.setOnClickListener(v -> playQueue(playlist.songs, position));
            applyActivePlaylistRowState(holder.row, song);
            return wrapper;
        }
    }


    private final class PlaylistFilterAdapter extends BaseAdapter {
        private final ImportedPlaylist owner;
        private final List<Song> items = new ArrayList<>();
        PlaylistFilterAdapter(ImportedPlaylist owner) { this.owner = owner; }
        void replace(List<Song> source) {
            items.clear();
            if (source != null) items.addAll(source);
            notifyDataSetChanged();
        }
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            PlaylistDetailHolder holder;
            LinearLayout wrapper;
            if (convertView == null) {
                wrapper = Ui.column(MainActivity.this);
                holder = new PlaylistDetailHolder();
                holder.row = Ui.row(MainActivity.this);
                holder.row.setGravity(Gravity.CENTER_VERTICAL);
                holder.row.setPadding(Ui.dp(MainActivity.this, 9), Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 8));
                holder.row.setBackground(Ui.glass(128, 17, 18, MainActivity.this));
                holder.row.setClickable(true); holder.row.setFocusable(true);
                Ui.applyRipple(holder.row, Color.argb(33, 255, 255, 255));
                holder.cover = new ImageView(MainActivity.this);
                holder.cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                holder.cover.setBackground(Ui.round(Color.rgb(22, 22, 28), 12, MainActivity.this));
                holder.cover.setClipToOutline(true);
                holder.row.addView(holder.cover, Ui.lp(Ui.dp(MainActivity.this, 54), Ui.dp(MainActivity.this, 54)));
                LinearLayout text = Ui.column(MainActivity.this);
                text.setPadding(Ui.dp(MainActivity.this, 11), Ui.dp(MainActivity.this, 1), Ui.dp(MainActivity.this, 6), Ui.dp(MainActivity.this, 1));
                holder.title = Ui.text(MainActivity.this, "", 13.8f, Ui.TEXT, true);
                holder.title.setSingleLine(true); holder.title.setEllipsize(TextUtils.TruncateAt.END);
                holder.artist = Ui.text(MainActivity.this, "", 10.9f, Ui.TEXT_2, false);
                holder.artist.setSingleLine(true); holder.artist.setEllipsize(TextUtils.TruncateAt.END);
                text.addView(holder.title, new LinearLayout.LayoutParams(-1, 0, 1f));
                text.addView(holder.artist, new LinearLayout.LayoutParams(-1, 0, 1f));
                holder.row.addView(text, new LinearLayout.LayoutParams(0, Ui.dp(MainActivity.this, 54), 1f));
                holder.favorite = Ui.iconButton(MainActivity.this, IconView.Type.HEART, 36, Ui.DIM, Color.TRANSPARENT);
                holder.favoriteIcon = (IconView) holder.favorite.getChildAt(0);
                holder.row.addView(holder.favorite, Ui.lp(Ui.dp(MainActivity.this, 32), Ui.dp(MainActivity.this, 32)));
                holder.next = Ui.iconButton(MainActivity.this, IconView.Type.PLUS, 32, Ui.TEXT_2, Color.TRANSPARENT);
                holder.row.addView(holder.next, Ui.lp(Ui.dp(MainActivity.this, 30), Ui.dp(MainActivity.this, 30)));
                holder.more = Ui.iconButton(MainActivity.this, IconView.Type.MORE, 30, Ui.DIM, Color.TRANSPARENT);
                holder.row.addView(holder.more, Ui.lp(Ui.dp(MainActivity.this, 29), Ui.dp(MainActivity.this, 29)));
                wrapper.addView(holder.row, Ui.lp(-1, Ui.dp(MainActivity.this, 70)));
                wrapper.addView(new Space(MainActivity.this), Ui.lp(-1, Ui.dp(MainActivity.this, 7)));
                wrapper.setTag(holder);
            } else {
                wrapper = (LinearLayout) convertView;
                holder = (PlaylistDetailHolder) wrapper.getTag();
            }
            Song song = items.get(position);
            holder.title.setText(song.title);
            holder.artist.setText(subtitleFor(song));
            holder.row.setBackground(Ui.glass(128, 17, 18, MainActivity.this));
            String coverKey = song.coverUrl == null ? "" : song.coverUrl;
            Object oldCoverKey = holder.cover.getTag();
            if (!coverKey.equals(oldCoverKey == null ? "" : String.valueOf(oldCoverKey))) {
                holder.cover.setImageDrawable(null); holder.cover.setTag(coverKey);
                if (!coverKey.trim().isEmpty()) ImageLoader.load(coverKey, holder.cover, null);
            }
            boolean favorite = isFavorite(song);
            holder.favoriteIcon.setIconColor(favorite ? Ui.PINK : Ui.DIM);
            holder.favoriteIcon.setFavoriteState(favorite, false);
            PlaylistDetailHolder bound = holder;
            holder.favorite.setOnClickListener(v -> {
                favorites = store.toggleFavorite(song);
                boolean nowFavorite = isFavorite(song);
                bound.favoriteIcon.setIconColor(nowFavorite ? Ui.PINK : Ui.DIM);
                bound.favoriteIcon.setFavoriteState(nowFavorite, true);
                if (nowFavorite) animateHeartBurst(bound.favorite, Ui.PINK);
            });
            holder.next.setOnClickListener(v -> {
                ensurePlaybackServiceStarted();
                if (playback != null) playback.enqueueNext(song); else pendingNextSongs.add(song);
                toast("已添加到下一首");
            });
            holder.more.setOnClickListener(v -> showSongActions(song, owner == null ? null : owner.id, bound.row));
            holder.row.setOnClickListener(v -> { dismissModalNow(); playPickedSong(song); });
            return wrapper;
        }
    }

    private View firstPlaylistCoachTarget(LinearLayout lazyHost) {
        if (lazyHost == null || lazyHost.getChildCount() == 0) return null;
        for (int i = 0; i < lazyHost.getChildCount(); i++) {
            View child = lazyHost.getChildAt(i);
            if (child instanceof CurtainRevealFrame && ((CurtainRevealFrame) child).getChildCount() > 1)
                return ((CurtainRevealFrame) child).getChildAt(((CurtainRevealFrame) child).getChildCount() - 1);
        }
        return lazyHost.getChildAt(0);
    }

    private void appendPlaylistPlaceholderBatch(LinearLayout host, int count, int accent) {
        if (host == null || count <= 0) return;
        int shimmerAccent = progressiveLoadingAccent(accent);
        for (int i = 0; i < count; i++) {
            CurtainRevealFrame frame = new CurtainRevealFrame(this);
            frame.setAccent(shimmerAccent);
            FluidPlaceholderView placeholder = new FluidPlaceholderView(this, FluidPlaceholderView.SONG_ROW, shimmerAccent);
            placeholder.setShimmerStartDelay((host.getChildCount() + i) * 62L);
            frame.setPlaceholder(placeholder);
            LinearLayout.LayoutParams fp = Ui.lp(-1, Ui.dp(this, 72));
            if (host.getChildCount() > 0) fp.topMargin = Ui.dp(this, 7);
            host.addView(frame, fp);
            if (activePlaylistHeroMorph != null && activePlaylistTransitionIncoming != null) {
                int index = host.getChildCount() - 1;
                float start = .58f + Math.min(.18f, index * .035f);
                float rowP = playlistStage(activePlaylistSharedProgress, start, Math.min(.98f, start + .22f));
                frame.setAlpha(rowP);
                frame.setTranslationY(Ui.dp(this, 26 + Math.min(12, index * 2)) * (1f - rowP));
            }
        }
    }

    private void ensurePlaylistPlaceholderCount(LinearLayout host, int required, int accent) {
        if (host == null) return;
        int missing = required - host.getChildCount();
        if (missing > 0) appendPlaylistPlaceholderBatch(host, missing, accent);
    }

    private View buildPlaylistRow(ImportedPlaylist playlist, int position) {
        if (playlist == null || playlist.songs == null || position < 0 || position >= playlist.songs.size()) return new View(this);
        Song song = playlist.songs.get(position);
        View row = songRow(song, playlist.songs, position, playlist.id);
        row.setOnClickListener(v -> {
            int liveIndex = indexByIdentity(playlist.songs, song);
            if (liveIndex >= 0) playQueue(playlist.songs, liveIndex);
        });
        registerActivePlaylistRow(playlist.id, song, row);
        return row;
    }

    /**
     * Materialize at most one song row per display frame.  Navigation owns the first frame; rows
     * arrive afterwards behind already-laid-out skeleton geometry, then wipe in left -> right.
     */
    private void materializePlaylistBatch(LinearLayout host, ImportedPlaylist playlist, int[] loadedRef,
                                          boolean[] scheduledRef, int targetCount, int generation) {
        if (host == null || playlist == null || playlist.songs == null || loadedRef == null || loadedRef.length == 0
                || scheduledRef == null || scheduledRef.length == 0 || scheduledRef[0]) return;
        int from = Math.max(0, loadedRef[0]);
        int to = Math.min(playlist.songs.size(), Math.max(from, targetCount));
        if (to <= from) {
            setPlaylistLoaderActive(false);
            return;
        }
        ensurePlaylistPlaceholderCount(host, to, Ui.CYAN);
        scheduledRef[0] = true;
        setPlaylistLoaderActive(true);
        final int[] index = new int[]{from};
        Runnable[] step = new Runnable[1];
        step[0] = () -> {
            if (generation != playlistLoadGeneration || activePlaylistPageModel == null
                    || !playlist.id.equals(activePlaylistPageModel.id) || host != activePlaylistSongHost) {
                scheduledRef[0] = false;
                return;
            }
            int pos = index[0];
            if (pos >= to || pos >= playlist.songs.size()) {
                loadedRef[0] = Math.max(loadedRef[0], Math.min(to, playlist.songs.size()));
                playlistVisibleCount = Math.max(playlistVisibleCount, loadedRef[0]);
                scheduledRef[0] = false;
                setPlaylistLoaderActive(false);
                return;
            }
            View slot = pos < host.getChildCount() ? host.getChildAt(pos) : null;
            if (!(slot instanceof CurtainRevealFrame)) {
                ensurePlaylistPlaceholderCount(host, pos + 1, Ui.CYAN);
                slot = host.getChildAt(pos);
            }
            View row = buildPlaylistRow(playlist, pos);
            ((CurtainRevealFrame) slot).reveal(row, 0L);
            loadedRef[0] = pos + 1;
            playlistVisibleCount = Math.max(playlistVisibleCount, loadedRef[0]);
            index[0] = pos + 1;
            // A short overlapping stagger keeps construction off the tap frame while making the
            // rows visibly arrive one after another instead of resolving as one instantaneous block.
            host.postDelayed(step[0], 62L);
        };
        host.postOnAnimation(step[0]);
    }

    private void setPlaylistLoaderActive(boolean active) {
        if (activePlaylistLoader == null) return;
        View parent = activePlaylistLoader.getParent() instanceof View ? (View) activePlaylistLoader.getParent() : null;
        if (parent != null) parent.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
        activePlaylistLoader.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
        if (active) activePlaylistLoader.start(); else activePlaylistLoader.stop();
    }

    /** Immediate helper retained for edit/remove refresh paths; normal scrolling uses frame batches. */
    private void appendPlaylistRows(LinearLayout host, ImportedPlaylist playlist, int from, int to) {
        if (host == null || playlist == null || playlist.songs == null || from < 0 || to <= from) return;
        int safeTo = Math.min(to, playlist.songs.size());
        ensurePlaylistPlaceholderCount(host, safeTo, Ui.CYAN);
        for (int pos = from; pos < safeTo; pos++) {
            View slot = host.getChildAt(pos);
            View row = buildPlaylistRow(playlist, pos);
            if (slot instanceof CurtainRevealFrame) ((CurtainRevealFrame) slot).reveal(row, 0L);
        }
    }

    private void maybeAppendPlaylistRows(ScrollView scroll, LinearLayout host, ImportedPlaylist playlist,
                                         int[] loadedRef, boolean[] scheduledRef) {
        if (scroll == null || host == null || playlist == null || playlist.songs == null
                || loadedRef == null || loadedRef.length == 0 || scheduledRef == null || scheduledRef.length == 0
                || scheduledRef[0]) return;
        int loaded = loadedRef[0];
        if (loaded >= playlist.songs.size()) { setPlaylistLoaderActive(false); return; }
        View content = scroll.getChildCount() > 0 ? scroll.getChildAt(0) : null;
        if (content == null) return;
        int remainingPx = content.getHeight() - (scroll.getScrollY() + scroll.getHeight());
        if (remainingPx > Ui.dp(this, PLAYLIST_PREFETCH_DISTANCE_DP)) return;

        int next = Math.min(playlist.songs.size(), loaded + PLAYLIST_APPEND_BATCH);
        // Skeleton geometry appears immediately at the tail; actual rows then resolve one frame at a time.
        ensurePlaylistPlaceholderCount(host, next, Ui.CYAN);
        scroll.postOnAnimation(() -> materializePlaylistBatch(host, playlist, loadedRef, scheduledRef, next, playlistLoadGeneration));
    }

    /** V92.9.7: local playlist search is background-filtered and ListView-backed for 1000+ songs. */
    private void showPlaylistSearch(ImportedPlaylist playlist) {
        if (playlist == null) return;
        LinearLayout card = modalCard("搜索当前歌单", "按歌名或歌手筛选《" + playlist.name + "》 · 仅检索本机歌单，不发起网络搜索");

        EditText input = new EditText(this);
        input.setTypeface(Typeface.DEFAULT);
        input.setSingleLine(true);
        input.setHint("搜索歌名、歌手");
        input.setHintTextColor(AppearanceSystem.isLight() ? Color.rgb(115, 115, 126) : Color.rgb(105, 105, 123));
        input.setTextColor(Ui.TEXT);
        input.setTextSize(13.5f);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setBackground(Ui.contentSurface(18, this));
        input.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        LinearLayout.LayoutParams ip = Ui.lp(-1, Ui.dp(this, 52));
        ip.topMargin = Ui.dp(this, 13);
        card.addView(input, ip);

        TextView count = Ui.text(this, playlist.songs.isEmpty() ? "歌单为空" : "输入关键词开始筛选",
                10.8f, Ui.DIM, false);
        LinearLayout.LayoutParams cp = Ui.lp(-1, Ui.dp(this, 30));
        cp.topMargin = Ui.dp(this, 5);
        card.addView(count, cp);

        ListView results = new ListView(this);
        results.setDivider(null);
        results.setDividerHeight(0);
        results.setSelector(android.R.color.transparent);
        results.setCacheColorHint(Color.TRANSPARENT);
        results.setVerticalScrollBarEnabled(false);
        results.setScrollingCacheEnabled(false);
        results.setClipToPadding(false);
        results.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 8));
        results.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        PlaylistFilterAdapter adapter = new PlaylistFilterAdapter(playlist);
        results.setAdapter(adapter);
        card.addView(results, new LinearLayout.LayoutParams(-1, 0, 1f));

        final java.util.concurrent.atomic.AtomicInteger generation = new java.util.concurrent.atomic.AtomicInteger();
        final Runnable[] pending = new Runnable[1];
        final List<Song> sourceSnapshot = new ArrayList<>(playlist.songs);
        final Runnable[] schedule = new Runnable[1];
        schedule[0] = () -> {
            if (pending[0] != null) input.removeCallbacks(pending[0]);
            final int ticket = generation.incrementAndGet();
            String raw = input.getText() == null ? "" : input.getText().toString().trim();
            final String needle = normalizeSuggestion(raw);
            if (needle.isEmpty()) {
                adapter.replace(new ArrayList<>());
                count.setText(sourceSnapshot.isEmpty() ? "歌单为空" : "输入关键词开始筛选");
                return;
            }
            // Keep the old rows readable until the new filter is ready; no blank intermediary frame.
            count.setText("筛选中… · 当前结果保持可用");
            pending[0] = () -> playlistFilterExecutor.submit(() -> {
                ArrayList<Song> matched = new ArrayList<>();
                for (Song song : sourceSnapshot) {
                    // The executor is single-threaded, so stale searches must cooperatively stop or
                    // fast typing would queue several full 1000-song scans in front of the newest one.
                    if (ticket != generation.get() || Thread.currentThread().isInterrupted()) return;
                    if (song == null) continue;
                    String haystack = normalizeSuggestion((song.title == null ? "" : song.title)
                            + " " + (song.artist == null ? "" : song.artist));
                    if (haystack.contains(needle)) matched.add(song);
                }
                main.post(() -> {
                    if (ticket != generation.get() || isFinishing() || !input.isAttachedToWindow()) return;
                    adapter.replace(matched);
                    count.setText(matched.isEmpty() ? "没有找到匹配歌曲" : "找到 " + matched.size() + " 首 · 结果已完整加载");
                    if (!matched.isEmpty()) results.setSelection(0);
                });
            });
            // Short debounce absorbs IME composition without creating the long drawer-like dead gap.
            input.postDelayed(pending[0], 110L);
        };

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { schedule[0].run(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                try {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
                } catch (Exception ignored) { }
                return true;
            }
            return false;
        });

        previousSoftInputMode = getWindow().getAttributes().softInputMode;
        inputModalOpen = true;
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        presentFractionModal(card, .78f);
        input.requestFocus();
        input.postDelayed(() -> {
            try {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            } catch (Exception ignored) { }
        }, 180);
    }

    private View songList(List<Song> display, List<Song> queue) {
        LinearLayout box = Ui.column(this);
        for (int i = 0; i < display.size(); i++) {
            Song s = display.get(i);
            box.addView(songRow(s, queue, i), marginTop(i == 0 ? 0 : 7));
        }
        return box;
    }

    /**
     * V24: a real playlist detail installs the whole playlist as the playback queue when a row
     * is tapped, then starts from that exact row. This intentionally differs from generic search
     * results, where tapping one result still inserts/plays only that single song.
     */
    private View playlistSongList(List<Song> display, List<Song> fullPlaylist, String playlistId) {
        LinearLayout box = Ui.column(this);
        for (int i = 0; i < display.size(); i++) {
            Song song = display.get(i);
            int resolvedIndex = -1;
            // display is normally a slice copied from fullPlaylist, so identity gives the exact
            // occurrence even if a user-created playlist happens to contain duplicate song keys.
            for (int q = 0; q < fullPlaylist.size(); q++) {
                if (fullPlaylist.get(q) == song) { resolvedIndex = q; break; }
            }
            if (resolvedIndex < 0) resolvedIndex = indexByKey(fullPlaylist, song.key());
            final int queueIndex = resolvedIndex >= 0 ? resolvedIndex : Math.min(i, Math.max(0, fullPlaylist.size() - 1));

            View row = songRow(song, fullPlaylist, queueIndex, playlistId);
            row.setOnClickListener(v -> {
                int liveIndex = indexByIdentity(fullPlaylist, song);
                if (liveIndex >= 0) playQueue(fullPlaylist, liveIndex);
            });
            box.addView(row, marginTop(i == 0 ? 0 : 7));
        }
        return box;
    }

    /**
     * V46 playlist lazy-render fast path. The slice already comes from the full playlist, so its
     * queue index is known and does not need the legacy identity/key scan used by generic slices.
     * This changes view construction cost only; the click still installs the complete playlist.
     */
    private View playlistSongListRange(List<Song> fullPlaylist, int from, int to, String playlistId) {
        LinearLayout box = Ui.column(this);
        if (fullPlaylist == null || fullPlaylist.isEmpty()) return box;
        int safeFrom = Math.max(0, from);
        int safeTo = Math.min(to, fullPlaylist.size());
        for (int i = safeFrom; i < safeTo; i++) {
            final int queueIndex = i;
            Song song = fullPlaylist.get(i);
            View row = songRow(song, fullPlaylist, queueIndex, playlistId);
            row.setOnClickListener(v -> {
                int liveIndex = indexByIdentity(fullPlaylist, song);
                if (liveIndex >= 0) playQueue(fullPlaylist, liveIndex);
            });
            registerActivePlaylistRow(playlistId, song, row);
            box.addView(row, marginTop(i == safeFrom ? 0 : 7));
        }
        return box;
    }

    private int indexByKey(List<Song> list, String key) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).key().equals(key)) return i;
        return -1;
    }

    private int indexByIdentity(List<Song> list, Song target) {
        if (list == null || target == null) return -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i) == target) return i;
        return indexByKey(list, target.key());
    }

    private View songRow(Song song, List<Song> queue, int index) {
        return songRow(song, queue, index, null);
    }

    private View songRow(Song song, List<Song> queue, int index, String playlistId) {
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 9), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        row.setBackground(Ui.glass(128, 17, 18, this));
        row.setClickable(true);
        row.setFocusable(true);
        Ui.applyRipple(row, Color.argb(33, 255, 255, 255));

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(Ui.round(Color.rgb(22, 22, 28), 12, this));
        cover.setClipToOutline(true);
        row.addView(cover, Ui.lp(Ui.dp(this, 56), Ui.dp(this, 56)));
        ImageLoader.load(song.coverUrl, cover, null);

        LinearLayout text = Ui.column(this);
        text.setPadding(Ui.dp(this, 12), Ui.dp(this, 2), Ui.dp(this, 7), Ui.dp(this, 2));
        TextView title = Ui.text(this, song.title, 14.2f, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView artist = Ui.text(this, subtitleFor(song), 11.2f, Ui.TEXT_2, false);
        artist.setSingleLine(true);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        text.addView(title, new LinearLayout.LayoutParams(-1, 0, 1f));
        text.addView(artist, new LinearLayout.LayoutParams(-1, 0, 1f));
        row.addView(text, new LinearLayout.LayoutParams(0, Ui.dp(this, 56), 1f));

        FrameLayout fav = Ui.iconButton(this, IconView.Type.HEART, 38, isFavorite(song) ? Ui.PINK : Ui.DIM, Color.TRANSPARENT);
        IconView favIcon = (IconView) fav.getChildAt(0);
        favIcon.setFavoriteState(isFavorite(song), false);
        fav.setOnClickListener(v -> {
            favorites = store.toggleFavorite(song);
            boolean favorite = isFavorite(song);
            favIcon.setIconColor(favorite ? Ui.PINK : Ui.DIM);
            favIcon.setFavoriteState(favorite, true);
            if (favorite) animateHeartBurst(fav, Ui.PINK);
            if (tab == 3) renderTab();
        });
        row.addView(fav, Ui.lp(Ui.dp(this, 34), Ui.dp(this, 34)));

        FrameLayout plus = Ui.iconButton(this, IconView.Type.PLUS, 34, Ui.TEXT_2, Color.TRANSPARENT);
        plus.setContentDescription("添加到下一首");
        plus.setOnClickListener(v -> {
            ensurePlaybackServiceStarted();
            if (playback != null) playback.enqueueNext(song);
            else pendingNextSongs.add(song);
            toast("已添加到下一首");
        });
        row.addView(plus, Ui.lp(Ui.dp(this, 32), Ui.dp(this, 32)));

        FrameLayout more = Ui.iconButton(this, IconView.Type.MORE, 31, Ui.DIM, Color.TRANSPARENT);
        more.setOnClickListener(v -> showSongActions(song, playlistId, row));
        row.addView(more, Ui.lp(Ui.dp(this, 30), Ui.dp(this, 30)));
        row.setOnClickListener(v -> playPickedSong(song));
        return row;
    }

    private boolean smartCollectionContains(SmartCollection collection, Song song) {
        if (collection == null || song == null) return false;
        for (Song s : collection.songs) if (s != null && s.key().equals(song.key())) return true;
        return false;
    }

    private void showSongActions(Song song) {
        showSongActions(song, null, null);
    }

    private void showSongActions(Song song, String playlistId) {
        showSongActions(song, playlistId, null);
    }

    private void showSongActions(Song song, String playlistId, View sourceRow) {
        if (song == null) return;
        boolean removableFromPlaylist = playlistId != null && !playlistId.isEmpty();
        boolean explainableRecommendation = openSmartCollection != null
                && ("daily".equals(openSmartCollection.kind) || "private".equals(openSmartCollection.kind) || "weather".equals(openSmartCollection.kind))
                && smartCollectionContains(openSmartCollection, song);
        String favText = isFavorite(song) ? "取消收藏" : "收藏";
        OfflineStore.Record offlineRecord = offlineStore == null ? null : offlineStore.get(song);
        boolean downloaded = offlineRecord != null && offlineRecord.exists();
        boolean localImported = downloaded && OfflineStore.ORIGIN_LOCAL.equals(offlineRecord.origin);
        boolean downloadActive = OfflineDownloadService.active(this, song);
        Song currentSong = playback == null ? null : playback.currentSong();
        boolean isCurrentSong = currentSong != null && currentSong.key().equals(song.key());
        ArrayList<String> rows = new ArrayList<>();
        rows.add(favText);
        rows.add("下一首播放");
        rows.add("加入播放列表");
        rows.add("添加到歌单");
        rows.add("歌曲信息");
        if (explainableRecommendation) rows.add("为什么推荐这首");
        if (isCurrentSong) {
            rows.add("歌词显示设置");
            rows.add(sleepTimerMenuLabel());
            rows.add(DesktopLyricService.enabled(this) ? "桌面歌词设置" : "开启桌面歌词");
        }
        if (downloaded) rows.add(localImported ? "从离线库移除" : "删除离线下载");
        else if (downloadActive) rows.add("取消下载");
        else rows.add("下载到离线");
        if (removableFromPlaylist) rows.add("从该歌单删除");
        String[] items = rows.toArray(new String[0]);
        showGlassOptions(song.title, song.artist, items, which -> {
            if (which < 0 || which >= items.length) return;
            String action = items[which];
            if (favText.equals(action)) {
                favorites = store.toggleFavorite(song);
                if (tab == 3) renderTab();
                toast(isFavorite(song) ? "已收藏" : "已取消收藏");
            } else if ("歌词显示设置".equals(action)) {
                showLyricStylePicker();
            } else if ("下一首播放".equals(action)) {
                ensurePlaybackServiceStarted();
                if (playback != null) playback.enqueueNext(song);
                else pendingNextSongs.add(song);
                toast("已添加到下一首");
            } else if ("加入播放列表".equals(action)) {
                if (playback != null) { playback.enqueueEnd(song); toast("已加入播放列表"); }
                else { playQueue(java.util.Collections.singletonList(song), 0); toast("播放器启动中"); }
            } else if ("添加到歌单".equals(action)) {
                showAddToPlaylist(song);
            } else if ("歌曲信息".equals(action)) {
                showSongInfo(song);
            } else if ("为什么推荐这首".equals(action)) {
                showRecommendationReason(song);
            } else if (action.startsWith("定时关闭")) {
                showSleepTimerOptions();
            } else if ("开启桌面歌词".equals(action) || "桌面歌词设置".equals(action)) {
                showDesktopLyricSettings();
            } else if ("下载到离线".equals(action)) {
                enqueueDownload(song);
            } else if ("取消下载".equals(action)) {
                OfflineDownloadService.cancel(this, song); toast("已取消下载");
            } else if ("删除离线下载".equals(action)) {
                showGlassMessage("删除离线文件", "只删除 Lunaxy 下载副本，不会删除收藏、歌单或在线歌曲。", "删除", () -> removeDownload(song), "取消", null);
            } else if ("从离线库移除".equals(action)) {
                showGlassMessage("从离线库移除", "只删除 Lunaxy 导入时创建的私有副本，手机原始音乐文件不会被删除。", "移除", () -> removeDownload(song), "取消", null);
            } else if ("从该歌单删除".equals(action) && removableFromPlaylist) {
                removeSongFromPlaylistSmooth(playlistId, song, sourceRow);
            }
        });
    }

    private void showSongInfo(Song song) {
        if (song == null) return;
        StringBuilder info = new StringBuilder();
        info.append("歌名：").append(song.title == null || song.title.trim().isEmpty() ? "未知歌曲" : song.title.trim());
        info.append("\n歌手：").append(song.artist == null || song.artist.trim().isEmpty() ? "未知歌手" : song.artist.trim());
        if (song.album != null && !song.album.trim().isEmpty()) info.append("\n专辑：").append(song.album.trim());
        if (song.durationMs > 0L) info.append("\n时长：").append(Ui.time(song.durationMs));
        info.append("\n\n可用来源：").append(catalogSourcesText(song));
        showGlassMessage("歌曲信息", info.toString(), "知道了", null, "", null);
    }


    private String sleepTimerMenuLabel() {
        if (playback == null) return "定时关闭";
        long remaining = playback.sleepTimerRemainingMs();
        if (remaining <= 0L) return "定时关闭";
        return "定时关闭 · 剩余" + formatSleepTimerRemaining(remaining);
    }

    private String formatSleepTimerRemaining(long ms) {
        long totalSeconds = Math.max(0L, (ms + 999L) / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) return hours + "小时" + minutes + "分钟" + seconds + "秒";
        return minutes + "分钟" + seconds + "秒";
    }

    private String formatSleepTimerClock(long ms) {
        long totalSeconds = Math.max(0L, (ms + 999L) / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private void showSleepTimerOptions() {
        if (playback == null) {
            toast("播放器尚未启动");
            return;
        }
        final long initialRemaining = playback.sleepTimerRemainingMs();
        LinearLayout card = modalCard("定时关闭", "到点只暂停播放，不清空队列、歌曲或当前进度");

        LinearLayout status = Ui.column(this);
        status.setGravity(Gravity.CENTER);
        status.setPadding(Ui.dp(this, 14), Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 13));
        status.setBackground(Ui.tintedGlass(Ui.CYAN, 20, this));
        TextView statusLabel = Ui.text(this, initialRemaining > 0L ? "当前剩余" : "当前未开启", 11.4f,
                initialRemaining > 0L ? Ui.CYAN : Ui.DIM, true);
        statusLabel.setGravity(Gravity.CENTER);
        TextView statusClock = Ui.text(this,
                initialRemaining > 0L ? formatSleepTimerClock(initialRemaining) : "00:00:00",
                28f, Ui.TEXT, true);
        statusClock.setGravity(Gravity.CENTER);
        statusClock.setLetterSpacing(.055f);
        TextView statusExact = Ui.text(this,
                initialRemaining > 0L ? formatSleepTimerRemaining(initialRemaining) : "可快速选择，也可精确到秒",
                11.7f, Ui.TEXT_2, false);
        statusExact.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams clockLp = Ui.lp(-1, Ui.dp(this, 42)); clockLp.topMargin = Ui.dp(this, 3);
        LinearLayout.LayoutParams exactLp = Ui.lp(-1, Ui.dp(this, 24)); exactLp.topMargin = Ui.dp(this, 1);
        status.addView(statusLabel, Ui.lp(-1, Ui.dp(this, 20)));
        status.addView(statusClock, clockLp);
        status.addView(statusExact, exactLp);
        LinearLayout.LayoutParams statusLp = Ui.lp(-1, Ui.dp(this, 102)); statusLp.topMargin = Ui.dp(this, 12);
        card.addView(status, statusLp);

        TextView quickTitle = Ui.text(this, "快速定时", 11.3f, Ui.DIM, true);
        LinearLayout.LayoutParams quickTitleLp = Ui.lp(-1, Ui.dp(this, 24)); quickTitleLp.topMargin = Ui.dp(this, 12);
        card.addView(quickTitle, quickTitleLp);
        LinearLayout quickRow = Ui.row(this);
        quickRow.setGravity(Gravity.CENTER_VERTICAL);
        String[] presetLabels = {"15分", "30分", "45分", "60分", "90分"};
        int[] presetMinutes = {15, 30, 45, 60, 90};
        for (int i = 0; i < presetLabels.length; i++) {
            final int preset = presetMinutes[i];
            TextView chip = Ui.text(this, presetLabels[i], 11.6f, Ui.TEXT, true);
            chip.setGravity(Gravity.CENTER);
            chip.setClickable(true); chip.setFocusable(true);
            chip.setBackground(Ui.glass(92, 14, 24, this));
            Ui.applyRipple(chip, Color.TRANSPARENT);
            chip.setOnClickListener(v -> {
                if (playback == null) return;
                playback.setSleepTimerDurationMs(preset * 60_000L);
                dismissModalNow();
                toast("已设置 · " + preset + "分钟后暂停");
            });
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(0, Ui.dp(this, 38), 1f);
            if (i > 0) chipLp.leftMargin = Ui.dp(this, 6);
            quickRow.addView(chip, chipLp);
        }
        card.addView(quickRow, Ui.lp(-1, Ui.dp(this, 38)));

        TextView customTitle = Ui.text(this, "自定义", 11.3f, Ui.DIM, true);
        LinearLayout.LayoutParams customTitleLp = Ui.lp(-1, Ui.dp(this, 24)); customTitleLp.topMargin = Ui.dp(this, 12);
        card.addView(customTitle, customTitleLp);

        long startSeconds = initialRemaining > 0L ? Math.max(1L, (initialRemaining + 999L) / 1000L) : 30L * 60L;
        int[] custom = {
                (int) Math.min(99L, startSeconds / 3600L),
                (int) ((startSeconds % 3600L) / 60L),
                (int) (startSeconds % 60L)
        };
        LinearLayout customRow = Ui.row(this);
        String[] units = {"小时", "分钟", "秒"};
        int[] maxValues = {99, 59, 59};
        int[] longSteps = {5, 10, 10};
        for (int i = 0; i < 3; i++) {
            LinearLayout cell = sleepTimerStepper(units[i], i, custom, maxValues[i], longSteps[i]);
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, Ui.dp(this, 118), 1f);
            if (i > 0) cellLp.leftMargin = Ui.dp(this, 8);
            customRow.addView(cell, cellLp);
        }
        card.addView(customRow, Ui.lp(-1, Ui.dp(this, 118)));
        TextView hint = Ui.text(this, "轻点 ＋/− 调 1；长按一次快速调 5/10。至少设置 1 秒。", 10.6f, Ui.DIM, false);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = Ui.lp(-1, Ui.dp(this, 30)); hintLp.topMargin = Ui.dp(this, 4);
        card.addView(hint, hintLp);

        LinearLayout actions = Ui.row(this); actions.setGravity(Gravity.END);
        TextView secondary = modalButton(initialRemaining > 0L ? "取消定时" : "关闭", false);
        secondary.setOnClickListener(v -> {
            if (initialRemaining > 0L && playback != null) {
                playback.cancelSleepTimer();
                toast("已取消定时关闭");
            }
            dismissModalNow();
        });
        actions.addView(secondary, Ui.lp(Ui.dp(this, 106), Ui.dp(this, 46)));
        Space gap = new Space(this); actions.addView(gap, Ui.lp(Ui.dp(this, 8), 1));
        TextView apply = modalButton(initialRemaining > 0L ? "重新设定" : "开始定时", true);
        apply.setOnClickListener(v -> {
            long totalSeconds = custom[0] * 3600L + custom[1] * 60L + custom[2];
            if (totalSeconds <= 0L) {
                toast("定时时长至少 1 秒");
                return;
            }
            if (playback == null) return;
            playback.setSleepTimerDurationMs(totalSeconds * 1000L);
            dismissModalNow();
            toast("已设置 · " + formatSleepTimerRemaining(totalSeconds * 1000L) + "后暂停");
        });
        actions.addView(apply, Ui.lp(Ui.dp(this, 116), Ui.dp(this, 46)));
        LinearLayout.LayoutParams actionsLp = Ui.lp(-1, Ui.dp(this, 46)); actionsLp.topMargin = Ui.dp(this, 10);
        card.addView(actions, actionsLp);

        presentModal(card);
        final Runnable countdownTicker = new Runnable() {
            @Override public void run() {
                if (card.getParent() == null || playback == null) return;
                long remaining = playback.sleepTimerRemainingMs();
                if (remaining > 0L) {
                    statusLabel.setText("当前剩余");
                    statusLabel.setTextColor(Ui.CYAN);
                    statusClock.setText(formatSleepTimerClock(remaining));
                    statusExact.setText(formatSleepTimerRemaining(remaining));
                } else {
                    statusLabel.setText("当前未开启");
                    statusLabel.setTextColor(Ui.DIM);
                    statusClock.setText("00:00:00");
                    statusExact.setText("可快速选择，也可精确到秒");
                }
                main.postDelayed(this, 250L);
            }
        };
        main.post(countdownTicker);
    }

    private LinearLayout sleepTimerStepper(String unit, int index, int[] values,
                                           int maxValue, int longStep) {
        LinearLayout cell = Ui.column(this);
        cell.setGravity(Gravity.CENTER);
        cell.setBackground(Ui.glass(108, 18, 26, this));

        TextView plus = Ui.text(this, "+", 18f, Ui.CYAN, true);
        plus.setGravity(Gravity.CENTER); plus.setClickable(true); plus.setFocusable(true);
        Ui.applyRipple(plus, Color.TRANSPARENT);
        TextView value = Ui.text(this, String.format(Locale.US, "%02d", values[index]), 22f, Ui.TEXT, true);
        value.setGravity(Gravity.CENTER); value.setLetterSpacing(.04f);
        TextView unitLabel = Ui.text(this, unit, 10.2f, Ui.DIM, false);
        unitLabel.setGravity(Gravity.CENTER);
        TextView minus = Ui.text(this, "−", 18f, Ui.TEXT_2, true);
        minus.setGravity(Gravity.CENTER); minus.setClickable(true); minus.setFocusable(true);
        Ui.applyRipple(minus, Color.TRANSPARENT);
        SimpleAction increment = () -> updateSleepTimerStepper(index, values, value, 1, maxValue);
        SimpleAction decrement = () -> updateSleepTimerStepper(index, values, value, -1, maxValue);
        plus.setOnClickListener(v -> increment.run());
        minus.setOnClickListener(v -> decrement.run());
        plus.setOnLongClickListener(v -> { updateSleepTimerStepper(index, values, value, longStep, maxValue); return true; });
        minus.setOnLongClickListener(v -> { updateSleepTimerStepper(index, values, value, -longStep, maxValue); return true; });

        cell.addView(plus, Ui.lp(-1, Ui.dp(this, 28)));
        cell.addView(value, Ui.lp(-1, Ui.dp(this, 34)));
        cell.addView(unitLabel, Ui.lp(-1, Ui.dp(this, 22)));
        cell.addView(minus, Ui.lp(-1, Ui.dp(this, 28)));
        return cell;
    }

    private void updateSleepTimerStepper(int index, int[] values, TextView view, int delta, int maxValue) {
        int next = Math.max(0, Math.min(maxValue, values[index] + delta));
        values[index] = next;
        view.setText(String.format(Locale.US, "%02d", next));
    }

    private void clearActivePlaylistUiRefs() {
        playlistLoadGeneration++;
        if (playlistLocateAnimator != null) { playlistLocateAnimator.cancel(); playlistLocateAnimator = null; }
        if (activePlaylistHeroAccentAnimator != null) { activePlaylistHeroAccentAnimator.cancel(); activePlaylistHeroAccentAnimator = null; }
        if (activePlaylistLoader != null) { activePlaylistLoader.stop(); activePlaylistLoader = null; }
        activePlaylistScroll = null;
        activePlaylistList = null;
        activePlaylistAdapter = null;
        activePlaylistSongHost = null;
        activePlaylistLoadedRef = null;
        activePlaylistPageModel = null;
        activePlaylistHeroCard = null;
        activePlaylistHeroThumb = null;
        activePlaylistHeroTitle = null;
        activePlaylistHeroMeta = null;
        activePlaylistDetailTitleRow = null;
        activePlaylistDetailSubtitle = null;
        activePlaylistDetailBack = null;
        activePlaylistDetailSongHeader = null;
        activePlaylistDetailSongHost = null;
        activePlaylistSongCount = null;
        activePlaylistHighlightedKey = "";
        activePlaylistHighlightedAccent = 0;
        activePlaylistRows.clear();
    }

    private void registerActivePlaylistRow(String playlistId, Song song, View row) {
        if (playlistId == null || song == null || row == null || activePlaylistPageModel == null
                || !playlistId.equals(activePlaylistPageModel.id)) return;
        ArrayList<View> rows = activePlaylistRows.get(song.key());
        if (rows == null) { rows = new ArrayList<>(); activePlaylistRows.put(song.key(), rows); }
        rows.add(row);
        applyActivePlaylistRowState(row, song);
    }

    private void applyActivePlaylistRowState(View row, Song song) {
        if (row == null || song == null) return;
        Song current = playback == null ? null : playback.currentSong();
        boolean playing = current != null && current.key().equals(song.key());
        styleActivePlaylistRow(row, playing);
    }

    @android.annotation.SuppressLint("UnsafeOptInUsageError") // Existing PlaybackService API, pinned Media3 version.
    private PlaybackHighlightState currentHighlightState() {
        Song song = playback == null ? null : playback.currentSong();
        String key = song == null ? "" : song.key();
        PlaybackHighlightState state = songHighlights.get(key);
        if (state == null) {
            state = new PlaybackHighlightState(key, playlistPlaybackAccent, SystemClock.uptimeMillis());
            songHighlights.put(key, state);
            if (songHighlights.size() > 32) songHighlights.remove(songHighlights.keySet().iterator().next());
        }
        return state;
    }

    @android.annotation.SuppressLint("UnsafeOptInUsageError")
    private void syncPlaybackHighlightState() {
        long now = SystemClock.uptimeMillis();
        PlaybackHighlightState current = currentHighlightState();
        boolean playing = playback != null && playback.snapshot().playing;
        for (PlaybackHighlightState state : songHighlights.values()) state.setPlaying(state == current && playing, now);
        current.setAccent(playlistPlaybackAccent, now);
        for (FluidTrackHaloDrawable renderer : highlightRenderers.keySet()) renderer.refreshState();
    }

    private void bindPlaybackHalo(FluidTrackHaloDrawable halo, View row) {
        syncPlaybackHighlightState();
        halo.bindState(currentHighlightState());
        highlightRenderers.put(halo, Boolean.TRUE);
        if (row.getBackground() != halo) row.setBackground(halo);
        halo.refreshState();
    }

    private void styleActivePlaylistRow(View row, boolean playing) {
        if (row == null) return;
        FluidTrackHaloDrawable halo = playbackRowHalos.get(row);
        if (playing) {
            if (halo == null) {
                halo = new FluidTrackHaloDrawable(getResources().getDisplayMetrics().density);
                playbackRowHalos.put(row, halo);
            }
            bindPlaybackHalo(halo, row);
        } else {
            if (halo != null) halo.stopRendering();
            row.setBackground(Ui.glass(128, 17, 18, this));
        }
        row.setElevation(playing ? Ui.dp(this, 1) : 0f);
        if (row instanceof ViewGroup && ((ViewGroup) row).getChildCount() > 1
                && ((ViewGroup) row).getChildAt(1) instanceof ViewGroup) {
            ViewGroup texts = (ViewGroup) ((ViewGroup) row).getChildAt(1);
            if (texts.getChildCount() > 0 && texts.getChildAt(0) instanceof TextView)
                ((TextView) texts.getChildAt(0)).setTextColor(playing ? playlistPlaybackAccent : Ui.TEXT);
            if (texts.getChildCount() > 1 && texts.getChildAt(1) instanceof TextView)
                ((TextView) texts.getChildAt(1)).setTextColor(playing ? Ui.TEXT : Ui.TEXT_2);
        }
    }

    private void refreshActivePlaylistHighlights() {
        Song current = playback == null ? null : playback.currentSong();
        String key = current == null ? "" : current.key();
        // Playback snapshots also carry position updates. Rebinding a recycled 1000-song ListView
        // on every progress tick defeats virtualization and can visibly hitch while scrolling.
        // Only rebind when the highlighted song or its artwork-derived accent actually changes.
        if (key.equals(activePlaylistHighlightedKey) && activePlaylistHighlightedAccent == playlistPlaybackAccent) return;
        activePlaylistHighlightedKey = key;
        activePlaylistHighlightedAccent = playlistPlaybackAccent;
        if (activePlaylistAdapter != null && activePlaylistList != null) {
            activePlaylistAdapter.notifyDataSetChanged();
            return;
        }
        if (activePlaylistRows.isEmpty()) return;
        for (Map.Entry<String, ArrayList<View>> entry : activePlaylistRows.entrySet()) {
            boolean playing = !key.isEmpty() && key.equals(entry.getKey());
            for (View row : entry.getValue()) {
                if (row == null) continue;
                styleActivePlaylistRow(row, playing);
            }
        }
    }

    private void updatePlaylistLocateButtonAccent() {
        if (playlistLocateButton == null) return;
        playlistLocateButton.setBackground(Ui.tintedGlass(playlistPlaybackAccent, 19, this));
        if (playlistLocateButton.getChildCount() > 0 && playlistLocateButton.getChildAt(0) instanceof IconView)
            ((IconView) playlistLocateButton.getChildAt(0)).setIconColor(playlistPlaybackAccent);
    }

    private void updatePlaylistLocateButtonVisibility() {
        if (playlistLocateButton == null) return;
        boolean playerOpen = playerOverlay != null && playerOverlay.getVisibility() == View.VISIBLE;
        Song current = playback == null ? null : playback.currentSong();
        boolean inPlaylist = openPlaylist != null && current != null && indexByKey(openPlaylist.songs, current.key()) >= 0;
        boolean visible = !playerOpen && inPlaylist;
        playlistLocateButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) playlistLocateButton.postDelayed(() -> maybeShowContextCoach(COACH_PLAYLIST_LOCATE, playlistLocateButton,
                "快速找到正在播放的歌", "当前歌曲会保持高亮。列表滑远以后点这里，就会平滑回到正在播放的位置。"), 220L);
    }

    private void locateCurrentSongInOpenPlaylist() {
        if (openPlaylist == null || playback == null || playback.currentSong() == null) return;
        Song current = playback.currentSong();
        int index = indexByKey(openPlaylist.songs, current.key());
        if (index < 0) { toast("当前播放不在这个歌单"); return; }
        if (activePlaylistList != null) {
            int headerCount = activePlaylistList.getHeaderViewsCount();
            int targetPosition = index + headerCount;
            int first = activePlaylistList.getFirstVisiblePosition();
            int distance = Math.abs(targetPosition - first);
            int duration = Math.max(320, Math.min(760, 320 + distance * 8));
            activePlaylistList.smoothScrollToPositionFromTop(targetPosition, Ui.dp(this, 86), duration);
            activePlaylistList.postDelayed(() -> {
                if (activePlaylistList == null) return;
                int child = targetPosition - activePlaylistList.getFirstVisiblePosition();
                if (child < 0 || child >= activePlaylistList.getChildCount()) return;
                View target = activePlaylistList.getChildAt(child);
                if (target == null) return;
                target.animate().cancel();
                target.setScaleX(.992f); target.setScaleY(.992f);
                target.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
            }, Math.min(820, duration + 60));
            return;
        }
        if (activePlaylistScroll == null) return;
        ensurePlaylistIndexRendered(index, () -> {
            ArrayList<View> rows = activePlaylistRows.get(current.key());
            View target = rows == null || rows.isEmpty() ? null : rows.get(0);
            if (target == null || activePlaylistScroll == null) return;
            int targetY = Math.max(0, viewTopInsideScroll(target, activePlaylistScroll) - Ui.dp(this, 92));
            smoothPlaylistScrollTo(targetY);
            target.animate().cancel();
            target.setScaleX(.992f); target.setScaleY(.992f);
            target.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
        });
    }

    private int viewTopInsideScroll(View target, ScrollView scroll) {
        int y = 0;
        View current = target;
        while (current != null && current != scroll) {
            y += current.getTop();
            android.view.ViewParent parent = current.getParent();
            if (!(parent instanceof View)) break;
            current = (View) parent;
        }
        return y;
    }

    private void ensurePlaylistIndexRendered(int index, Runnable done) {
        if (activePlaylistPageModel == null || activePlaylistSongHost == null || activePlaylistLoadedRef == null) {
            if (done != null) done.run();
            return;
        }
        if (index < activePlaylistLoadedRef[0]) { if (done != null) done.run(); return; }
        final ImportedPlaylist playlist = activePlaylistPageModel;
        final LinearLayout host = activePlaylistSongHost;
        final int generation = playlistLoadGeneration;
        final int targetCount = Math.min(playlist.songs.size(), index + 12);
        ensurePlaylistPlaceholderCount(host, targetCount, Ui.CYAN);
        setPlaylistLoaderActive(true);
        Runnable[] step = new Runnable[1];
        step[0] = () -> {
            if (generation != playlistLoadGeneration || activePlaylistPageModel != playlist
                    || activePlaylistSongHost != host || activePlaylistLoadedRef == null) {
                setPlaylistLoaderActive(false);
                return;
            }
            int pos = activePlaylistLoadedRef[0];
            if (pos >= targetCount || pos >= playlist.songs.size()) {
                setPlaylistLoaderActive(false);
                if (done != null) done.run();
                return;
            }
            View slot = pos < host.getChildCount() ? host.getChildAt(pos) : null;
            if (!(slot instanceof CurtainRevealFrame)) {
                ensurePlaylistPlaceholderCount(host, pos + 1, Ui.CYAN);
                slot = host.getChildAt(pos);
            }
            View row = buildPlaylistRow(playlist, pos);
            ((CurtainRevealFrame) slot).reveal(row, 0L);
            activePlaylistLoadedRef[0] = pos + 1;
            playlistVisibleCount = Math.max(playlistVisibleCount, pos + 1);
            host.postOnAnimation(step[0]);
        };
        host.postOnAnimation(step[0]);
    }

    private void smoothPlaylistScrollTo(int targetY) {
        if (activePlaylistScroll == null) return;
        if (playlistLocateAnimator != null) playlistLocateAnimator.cancel();
        final int start = activePlaylistScroll.getScrollY();
        final int end = Math.max(0, targetY);
        final int distance = Math.abs(end - start);
        if (distance < Ui.dp(this, 12)) { activePlaylistScroll.scrollTo(0, end); return; }
        long duration = Math.max(360L, Math.min(820L, 360L + distance / 7L));
        playlistLocateAnimator = ValueAnimator.ofInt(start, end);
        playlistLocateAnimator.setDuration(duration);
        playlistLocateAnimator.setInterpolator(new DecelerateInterpolator(1.35f));
        playlistLocateAnimator.addUpdateListener(a -> {
            if (activePlaylistScroll != null) activePlaylistScroll.scrollTo(0, (Integer) a.getAnimatedValue());
        });
        playlistLocateAnimator.start();
    }

    private void rememberPlaylistPositionForRefresh() {
        if (activePlaylistList != null) {
            playlistListRestorePosition = Math.max(0, activePlaylistList.getFirstVisiblePosition());
            View first = activePlaylistList.getChildCount() > 0 ? activePlaylistList.getChildAt(0) : null;
            playlistListRestoreTop = first == null ? 0 : first.getTop();
            playlistListRestoreId = activePlaylistPageModel == null ? "" : activePlaylistPageModel.id;
            restorePlaylistPosition = true;
            return;
        }
        if (activePlaylistScroll == null) return;
        playlistScrollRestoreY = Math.max(0, activePlaylistScroll.getScrollY());
        restorePlaylistPosition = true;
    }

    private void removeSongFromPlaylistSmooth(String playlistId, Song song, View sourceRow) {
        if (playlistId == null || playlistId.isEmpty() || song == null) return;
        ArrayList<String> keys = new ArrayList<>(); keys.add(song.key());
        if (activePlaylistList != null && activePlaylistAdapter != null && activePlaylistPageModel != null
                && playlistId.equals(activePlaylistPageModel.id)) {
            final int first = Math.max(0, activePlaylistList.getFirstVisiblePosition());
            final View firstChild = activePlaylistList.getChildCount() > 0 ? activePlaylistList.getChildAt(0) : null;
            final int firstTop = firstChild == null ? 0 : firstChild.getTop();
            if (sourceRow != null) {
                sourceRow.animate().cancel();
                sourceRow.animate().alpha(.36f).translationX(-Ui.dp(this, 14)).setDuration(110L).start();
            }
            io.submit(() -> {
                List<ImportedPlaylist> updated = store.removeSongsFromPlaylist(playlistId, keys);
                ImportedPlaylist latest = null;
                for (ImportedPlaylist candidate : updated)
                    if (candidate != null && playlistId.equals(candidate.id)) { latest = candidate; break; }
                final ImportedPlaylist resolved = latest;
                main.post(() -> {
                    if (isFinishing()) return;
                    playlists = updated;
                    if (resolved == null || activePlaylistPageModel == null || !playlistId.equals(activePlaylistPageModel.id)) {
                        openPlaylist = resolved;
                        suppressNextPageAnimation = true;
                        renderTab();
                        toast("已从歌单删除");
                        return;
                    }
                    activePlaylistPageModel.songs.clear();
                    activePlaylistPageModel.songs.addAll(resolved.songs);
                    openPlaylist = activePlaylistPageModel;
                    if (activePlaylistHeroMeta != null) {
                        String kind = activePlaylistPageModel.source.equals("local") ? "本地歌单"
                                : (activePlaylistPageModel.source.equals("tx") ? "QQ 音乐"
                                : (activePlaylistPageModel.source.equals("kw") ? "酷我音乐"
                                : (activePlaylistPageModel.source.equals("kg") ? "酷狗音乐" : "网易云")));
                        activePlaylistHeroMeta.setText(activePlaylistPageModel.songs.size() + " 首 · " + kind);
                    }
                    if (activePlaylistSongCount != null) activePlaylistSongCount.setText(activePlaylistPageModel.songs.size() + " 首");
                    activePlaylistAdapter.notifyDataSetChanged();
                    activePlaylistList.post(() -> {
                        if (activePlaylistList != null) activePlaylistList.setSelectionFromTop(
                                Math.min(first, Math.max(0, activePlaylistList.getCount() - 1)), firstTop);
                    });
                    updatePlaylistLocateButtonVisibility();
                    refreshActivePlaylistHighlights();
                    toast("已从歌单删除");
                });
            });
            return;
        }
        int removedFromRendered = 0;
        int loadedBefore = activePlaylistLoadedRef == null ? 0 : activePlaylistLoadedRef[0];
        if (activePlaylistPageModel != null && playlistId.equals(activePlaylistPageModel.id)) {
            for (int i = 0; i < Math.min(loadedBefore, activePlaylistPageModel.songs.size()); i++)
                if (song.key().equals(activePlaylistPageModel.songs.get(i).key())) removedFromRendered++;
        }
        playlists = store.removeSongsFromPlaylist(playlistId, keys);
        ImportedPlaylist latest = playlistById(playlistId);

        if (activePlaylistPageModel == null || !playlistId.equals(activePlaylistPageModel.id) || activePlaylistScroll == null || latest == null) {
            rememberPlaylistPositionForRefresh();
            openPlaylist = latest;
            playlistVisibleCount = Math.max(PLAYLIST_INITIAL_RENDER_COUNT,
                    Math.min(playlistVisibleCount, latest == null ? PLAYLIST_INITIAL_RENDER_COUNT : latest.songs.size()));
            suppressNextPageAnimation = true;
            renderTab();
            toast("已从歌单删除");
            return;
        }

        // Keep the current page alive: update the mutable page model and remove only affected rows.
        // This avoids rebuilding dozens of cards and preserves the exact viewport after deletion.
        activePlaylistPageModel.songs.clear();
        activePlaylistPageModel.songs.addAll(latest.songs);
        openPlaylist = activePlaylistPageModel;
        if (activePlaylistLoadedRef != null)
            activePlaylistLoadedRef[0] = Math.max(0, Math.min(activePlaylistPageModel.songs.size(), loadedBefore - removedFromRendered));
        playlistVisibleCount = Math.max(PLAYLIST_INITIAL_RENDER_COUNT,
                Math.min(Math.max(activePlaylistLoadedRef == null ? 0 : activePlaylistLoadedRef[0], PLAYLIST_INITIAL_RENDER_COUNT), activePlaylistPageModel.songs.size()));
        if (activePlaylistHeroMeta != null) {
            String kind = activePlaylistPageModel.source.equals("local") ? "本地歌单"
                    : (activePlaylistPageModel.source.equals("tx") ? "QQ 音乐" : "网易云");
            activePlaylistHeroMeta.setText(activePlaylistPageModel.songs.size() + " 首 · " + kind);
        }
        if (activePlaylistSongCount != null) activePlaylistSongCount.setText(activePlaylistPageModel.songs.size() + " 首");

        ArrayList<View> rows = activePlaylistRows.remove(song.key());
        if ((rows == null || rows.isEmpty()) && sourceRow != null) {
            rows = new ArrayList<>(); rows.add(sourceRow);
        }
        if (rows != null) for (View row : new ArrayList<>(rows)) animatePlaylistRowRemoval(row);
        updatePlaylistLocateButtonVisibility();
        refreshActivePlaylistHighlights();
        toast("已从歌单删除");
    }

    private void animatePlaylistRowRemoval(View row) {
        if (row == null) return;
        final View collapseTarget = row.getParent() instanceof CurtainRevealFrame
                ? (View) row.getParent() : row;
        final ViewGroup collapseParent = collapseTarget.getParent() instanceof ViewGroup
                ? (ViewGroup) collapseTarget.getParent() : null;
        if (collapseParent == null) return;
        row.animate().cancel();
        collapseTarget.animate().cancel();
        row.animate().alpha(0f).translationX(-Ui.dp(this, 18)).setDuration(120).withEndAction(() -> {
            final int startH = Math.max(1, collapseTarget.getHeight());
            final ViewGroup.LayoutParams original = collapseTarget.getLayoutParams();
            if (original == null) {
                if (collapseTarget.getParent() == collapseParent) collapseParent.removeView(collapseTarget);
                return;
            }
            ValueAnimator collapse = ValueAnimator.ofInt(startH, 0);
            collapse.setDuration(150L);
            collapse.addUpdateListener(a -> {
                ViewGroup.LayoutParams lp = collapseTarget.getLayoutParams();
                if (lp != null) {
                    lp.height = (Integer) a.getAnimatedValue();
                    collapseTarget.setLayoutParams(lp);
                }
            });
            collapse.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    if (collapseTarget.getParent() == collapseParent) collapseParent.removeView(collapseTarget);
                }
            });
            collapse.start();
        }).start();
    }


    // V91.1 Motion hotfix: one physical drag proxy + one stable placeholder.
    // The dragged card follows the finger directly; only neighbour cards use spring motion.
    // This avoids Android's system DragShadow + transformed ListView hit-testing feedback loop,
    // which caused target slots to oscillate around a boundary and looked like visible twitching.
    private int remapMovedIndex(int index, int from, int to) {
        if (index < 0 || from == to) return index;
        if (index == from) return to;
        if (from < to && index > from && index <= to) return index - 1;
        if (from > to && index >= to && index < from) return index + 1;
        return index;
    }

    private interface ReorderCallbacks {
        int itemCount();
        void commit(int from, int to);
        /** Called after the backing order has changed but while the opaque drop proxy still covers it. */
        default void onDropLayoutReady() { }
        default void onVisualSettled() { }
    }

    private static float smoothstep01(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        return t * t * (3f - 2f * t);
    }

    /**
     * V92.2 continuous reorder field. Rows do not jump one complete slot as soon as the target
     * index changes. Instead each neighbour is displaced in proportion to how deeply the floating
     * card overlaps its centre, so the list visibly compresses and opens the destination gradually.
     */
    private void animateSpringReorderTarget(ListView list, int from, int target, int count,
                                            float proxyCenterScreenY, float sourceCenterScreenY) {
        if (list == null || from < 0 || count <= 0) return;
        int first = list.getFirstVisiblePosition();
        int[] listLoc = new int[2];
        list.getLocationOnScreen(listLoc);
        int direction = target == from
                ? Float.compare(proxyCenterScreenY, sourceCenterScreenY)
                : Integer.compare(target, from);

        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child == null) continue;
            int position = first + i;
            if (position == from) {
                SpringMotion.shiftYFollow(child, 0f, 0f);
                continue;
            }

            int stride = child.getHeight() + Math.max(0, list.getDividerHeight());
            float center = listLoc[1] + child.getTop() + child.getHeight() * .5f;
            // Start yielding before the dragged card reaches the row centre and open the gap over
            // a wider travel range. This is intentionally slower than V92.1's slot-like jump.
            float activation = Math.max(Ui.dp(this, 28), child.getHeight() * .88f);
            float progress = 0f;
            float shift = 0f;

            if (direction > 0 && position > from) {
                // As the dragged card approaches from above, this row gradually yields upward.
                progress = smoothstep01((proxyCenterScreenY - (center - activation)) / (activation * 1.56f));
                shift = -stride * progress;
            } else if (direction < 0 && position < from) {
                // Symmetric behaviour while dragging upward.
                progress = smoothstep01(((center + activation) - proxyCenterScreenY) / (activation * 1.56f));
                shift = stride * progress;
            }

            // Rows far beyond the active destination should remain untouched. This also limits the
            // number of continuously animated views during long edge-scroll drags.
            if (direction > 0 && position > target + 1) { progress = 0f; shift = 0f; }
            if (direction < 0 && position < target - 1) { progress = 0f; shift = 0f; }
            SpringMotion.shiftYFollow(child, shift, progress);
        }
    }

    private void resetSpringReorderVisual(ListView list, boolean immediate) {
        if (list == null) return;
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (immediate) SpringMotion.resetNow(child); else SpringMotion.restore(child);
        }
    }

    private final class ReorderGestureController {
        private final ListView list;
        private final ReorderCallbacks callbacks;
        private final int hysteresisPx = Ui.dp(MainActivity.this, 8);
        private View activeHandle;
        private View sourceView;
        private View sourceContent;
        private Drawable sourceBackground;
        private float sourceContentAlpha = 1f;
        private FrameLayout dragProxy;
        private ImageView dragImage;
        private Bitmap dragBitmap;
        private FrameLayout overlayRoot;
        private int from = -1;
        private int target = -1;
        private float lastRawY;
        private float touchOffsetY;
        private float proxyLeftInRoot;
        private float sourceCenterScreenY;
        private int edgeScrollDirection;
        private float edgePenetration;
        private long edgeEnteredAt;
        private boolean edgeScrollScheduled;
        private boolean reorderFrameScheduled;
        private float pendingModelRawY;
        private boolean active;
        private final Runnable reorderFrame = new Runnable() {
            @Override public void run() {
                reorderFrameScheduled = false;
                if (!active) return;
                try { refreshReorderModel(pendingModelRawY); }
                catch (Throwable ignored) { emergencyAbort(); }
            }
        };
        private final Runnable edgeScrollTick = new Runnable() {
            @Override public void run() {
                edgeScrollScheduled = false;
                if (!active || edgeScrollDirection == 0) return;
                // V92.2: edge scrolling is deliberately slower than pointer sampling. A long drag
                // should not create a continuous ListView layout/rebind storm.
                float step=com.xingyu.music.ui.Player926Policy.edgeStep(edgePenetration,
                        android.os.SystemClock.uptimeMillis()-edgeEnteredAt);
                try { list.scrollListBy(edgeScrollDirection * Ui.dp(MainActivity.this, step)); }
                catch (Throwable failure) { emergencyAbort(); return; }
                scheduleReorderFrame(lastRawY);
                scheduleEdgeScroll();
            }
        };

        ReorderGestureController(ListView list, ReorderCallbacks callbacks) {
            this.list = list;
            this.callbacks = callbacks;
        }

        boolean isActive() { return active; }

        void consumeGlobalTouch(MotionEvent event) {
            if (!active || event == null) return;
            lastRawY = event.getRawY();
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_MOVE) update(lastRawY);
            else if (action == MotionEvent.ACTION_UP) finish(true);
            else if (action == MotionEvent.ACTION_CANCEL) finish(false);
        }

        void emergencyAbort() {
            active = false;
            if (activeReorderController == this) activeReorderController = null;
            try { stopEdgeScroll(); } catch (Throwable ignored) {}
            try { stopReorderFrame(); } catch (Throwable ignored) {}
            try { restoreSourcePlaceholder(); } catch (Throwable ignored) {}
            try { resetSpringReorderVisual(list, true); } catch (Throwable ignored) {}
            try { cleanupProxy(); } catch (Throwable ignored) {}
            reorderGestureActive = false;
            refreshVisibleRowsAfterDrag();
            try { callbacks.onVisualSettled(); } catch (Throwable ignored) { }
            modalDismissSuppressedUntil = SystemClock.uptimeMillis() + 320L;
            clearState();
        }

        void attach(View handle, View source, int position) {
            if (handle == null || source == null) return;
            handle.setLongClickable(true);
            handle.setOnTouchListener((v, event) -> {
                lastRawY = event.getRawY();
                if (!active || activeHandle != v) return false;
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_MOVE) {
                    update(event.getRawY());
                    return true;
                }
                if (action == MotionEvent.ACTION_UP) {
                    finish(true);
                    return true;
                }
                if (action == MotionEvent.ACTION_CANCEL) {
                    finish(false);
                    return true;
                }
                return true;
            });
            handle.setOnLongClickListener(v -> {
                begin(v, source, position, lastRawY);
                return true;
            });
        }

        private void begin(View handle, View source, int position, float rawY) {
            if(list instanceof com.xingyu.music.ui.VelocityListView)
                ((com.xingyu.music.ui.VelocityListView)list).suspendFastScroll();
            if (reorderGestureActive || active || source == null || position < 0 || callbacks.itemCount() < 2) return;
            int width = source.getWidth();
            int height = source.getHeight();
            if (width <= 0 || height <= 0) return;

            overlayRoot = findViewById(android.R.id.content);
            if (overlayRoot == null) return;
            from = Math.max(0, Math.min(position, callbacks.itemCount() - 1));
            target = from;
            activeHandle = handle;
            sourceView = source;
            active = true;
            activeReorderController = this;
            reorderGestureActive = true;
            modalDismissSuppressedUntil = Long.MAX_VALUE;

            int[] sourceLoc = new int[2];
            int[] rootLoc = new int[2];
            source.getLocationOnScreen(sourceLoc);
            overlayRoot.getLocationOnScreen(rootLoc);
            float safeRawY = rawY > 0f ? rawY : sourceLoc[1] + height / 2f;
            sourceCenterScreenY = sourceLoc[1] + height * .5f;
            touchOffsetY = Math.max(0f, Math.min(height, safeRawY - sourceLoc[1]));
            proxyLeftInRoot = sourceLoc[0] - rootLoc[0];

            View visual = source instanceof ViewGroup && ((ViewGroup) source).getChildCount() > 0
                    ? ((ViewGroup) source).getChildAt(0) : source;
            Drawable visualBackground = visual.getBackground();
            boolean liveHalo = visualBackground instanceof FluidTrackHaloDrawable;
            try {
                if (liveHalo) visual.setBackground(null);
                dragBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(dragBitmap);
                source.draw(canvas);
            } catch (Throwable error) {
                cleanupProxy();
                active = false;
                if (activeReorderController == this) activeReorderController = null;
                reorderGestureActive = false;
                modalDismissSuppressedUntil = SystemClock.uptimeMillis() + 120L;
                clearState();
                return;
            } finally {
                if (liveHalo) visual.setBackground(visualBackground);
            }

            // Pin the source row for the lifetime of the drag. ListView must not recycle that row
            // into another adapter position while its visual content is hidden. Long edge-scroll
            // drags in V92.1 could otherwise bind a new song into the same physical View.
            source.setHasTransientState(true);

            dragProxy = new FrameLayout(MainActivity.this);
            dragProxy.setClipChildren(false);
            dragProxy.setClipToPadding(false);
            dragProxy.setBackground(Ui.round(Color.argb(238, 12, 12, 18), 16, MainActivity.this));
            if (liveHalo) bindPlaybackHalo(new FluidTrackHaloDrawable(getResources().getDisplayMetrics().density), dragProxy);
            dragProxy.setElevation(Ui.dp(MainActivity.this, 24));
            dragImage = new ImageView(MainActivity.this);
            dragImage.setScaleType(ImageView.ScaleType.FIT_XY);
            dragImage.setImageBitmap(dragBitmap);
            dragProxy.addView(dragImage, new FrameLayout.LayoutParams(-1, -1));
            overlayRoot.addView(dragProxy, new FrameLayout.LayoutParams(width, height));
            dragProxy.setX(proxyLeftInRoot);
            dragProxy.setY(sourceLoc[1] - rootLoc[1]);
            dragProxy.setScaleX(.988f);
            dragProxy.setScaleY(.988f);
            dragProxy.setAlpha(.96f);
            dragProxy.animate().cancel();
            dragProxy.animate().scaleX(1.025f).scaleY(1.025f).alpha(1f)
                    .setDuration(120L).setInterpolator(SpringMotion.SNAPPY).start();

            sourceBackground = source.getBackground();
            source.setBackground(Ui.stroke(Color.argb(12, 255, 255, 255), 15,
                    Color.argb(34, 145, 214, 255), MainActivity.this));
            if (source instanceof ViewGroup && ((ViewGroup) source).getChildCount() > 0) {
                sourceContent = ((ViewGroup) source).getChildAt(0);
            } else {
                sourceContent = source;
            }
            sourceContentAlpha = sourceContent.getAlpha();
            sourceContent.setAlpha(0f);

            handle.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            list.requestDisallowInterceptTouchEvent(true);
            if (list.getParent() != null) list.getParent().requestDisallowInterceptTouchEvent(true);
            update(safeRawY);
        }

        private View childForAdapterPosition(int position) {
            int index = position - list.getFirstVisiblePosition();
            if (index < 0 || index >= list.getChildCount()) return null;
            return list.getChildAt(index);
        }

        private float childCenterOnScreen(int position) {
            View child = childForAdapterPosition(position);
            if (child == null) return Float.NaN;
            int[] listLoc = new int[2];
            list.getLocationOnScreen(listLoc);
            // getTop() is layout-space and deliberately ignores translationY, so spring-shifted
            // neighbours cannot move the threshold underneath the finger.
            return listLoc[1] + child.getTop() + child.getHeight() / 2f;
        }

        private int resolveTarget(float proxyCenterScreenY) {
            int count = callbacks.itemCount();
            int next = Math.max(0, Math.min(target, Math.max(0, count - 1)));
            boolean advanced;
            do {
                advanced = false;
                if (next < count - 1) {
                    int q = next; // non-source item crossed while moving the gap downward
                    int originalPos = q < from ? q : q + 1;
                    float center = childCenterOnScreen(originalPos);
                    if (!Float.isNaN(center) && proxyCenterScreenY > center + hysteresisPx) {
                        next++;
                        advanced = true;
                        continue;
                    }
                }
                if (next > 0) {
                    int q = next - 1; // non-source item crossed while moving the gap upward
                    int originalPos = q < from ? q : q + 1;
                    float center = childCenterOnScreen(originalPos);
                    if (!Float.isNaN(center) && proxyCenterScreenY < center - hysteresisPx) {
                        next--;
                        advanced = true;
                    }
                }
            } while (advanced);
            return Math.max(0, Math.min(next, Math.max(0, count - 1)));
        }

        private void update(float rawY) {
            if (!active || dragProxy == null || overlayRoot == null) return;
            lastRawY = rawY;
            int[] rootLoc = new int[2];
            int[] listLoc = new int[2];
            overlayRoot.getLocationOnScreen(rootLoc);
            list.getLocationOnScreen(listLoc);
            float top = rawY - touchOffsetY - rootLoc[1];
            dragProxy.setX(proxyLeftInRoot);
            dragProxy.setY(top); // grabbed card remains exactly under the finger

            float localY = rawY - listLoc[1];
            int edge = Ui.dp(MainActivity.this, 58);
            int nextEdgeDirection = localY < edge ? -1 : (localY > list.getHeight() - edge ? 1 : 0);
            edgePenetration=nextEdgeDirection<0 ? (edge-localY)/edge
                    : nextEdgeDirection>0 ? (localY-list.getHeight()+edge)/edge : 0f;
            if (nextEdgeDirection != edgeScrollDirection) {
                edgeEnteredAt=android.os.SystemClock.uptimeMillis();
                edgeScrollDirection = nextEdgeDirection;
                if (edgeScrollDirection == 0) stopEdgeScroll();
                else scheduleEdgeScroll();
            } else if (edgeScrollDirection != 0) {
                scheduleEdgeScroll();
            }

            scheduleReorderFrame(rawY);
        }

        private void scheduleReorderFrame(float rawY) {
            pendingModelRawY = rawY;
            if (!active || reorderFrameScheduled) return;
            reorderFrameScheduled = true;
            list.postOnAnimation(reorderFrame);
        }

        private void refreshReorderModel(float rawY) {
            if (!active || dragProxy == null || overlayRoot == null) return;
            int[] rootLoc = new int[2];
            overlayRoot.getLocationOnScreen(rootLoc);
            int proxyHeight = dragProxy.getHeight() > 0 ? dragProxy.getHeight()
                    : (sourceView == null ? 0 : sourceView.getHeight());
            float proxyCenterScreenY = rootLoc[1] + dragProxy.getY() + proxyHeight * .5f;
            int nextTarget = resolveTarget(proxyCenterScreenY);
            if (nextTarget != target) {
                target = nextTarget;
                handleSlotHaptic();
            }
            animateSpringReorderTarget(list, from, target, callbacks.itemCount(),
                    proxyCenterScreenY, sourceCenterScreenY);
        }

        private void scheduleEdgeScroll() {
            if (!active || edgeScrollDirection == 0 || edgeScrollScheduled) return;
            edgeScrollScheduled = true;
            main.postDelayed(edgeScrollTick, 42L);
        }

        private void stopEdgeScroll() {
            edgeScrollDirection = 0;
            if (edgeScrollScheduled) main.removeCallbacks(edgeScrollTick);
            edgeScrollScheduled = false;
        }

        private void stopReorderFrame() {
            if (reorderFrameScheduled) list.removeCallbacks(reorderFrame);
            reorderFrameScheduled = false;
        }

        private void refreshVisibleRowsAfterDrag() {
            // Do not invalidate/rebind every child after a drop: that produced the visible one-frame
            // blink on the current-playing halo and on the dragged row. Adapter commits already own
            // data rebinding; here we only request a paint pass.
            try { list.postOnAnimation(list::invalidate); } catch (Throwable ignored) { }
        }

        private void handleSlotHaptic() {
            if (activeHandle == null) return;
            activeHandle.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        }

        private float targetTopInRoot() {
            if (overlayRoot == null || sourceView == null) return dragProxy == null ? 0f : dragProxy.getY();
            int[] rootLoc = new int[2];
            int[] listLoc = new int[2];
            overlayRoot.getLocationOnScreen(rootLoc);
            list.getLocationOnScreen(listLoc);
            View targetChild = childForAdapterPosition(target);
            if (targetChild != null) return listLoc[1] + targetChild.getTop() - rootLoc[1];
            int direction = Integer.compare(target, from);
            int stride = sourceView.getHeight() + Math.max(0, list.getDividerHeight());
            int[] sourceLoc = new int[2];
            sourceView.getLocationOnScreen(sourceLoc);
            return sourceLoc[1] - rootLoc[1] + (target - from) * stride * (direction == 0 ? 0 : 1);
        }

        private void finish(boolean commit) {
            if (!active) return;
            active = false;
            if (activeReorderController == this) activeReorderController = null;
            stopEdgeScroll();
            stopReorderFrame();
            if (activeHandle != null) activeHandle.setPressed(false);
            // Keep the modal outside-tap layer inert until the entire drop/adapter update is over.
            modalDismissSuppressedUntil = SystemClock.uptimeMillis() + 520L;
            list.requestDisallowInterceptTouchEvent(false);
            if (list.getParent() != null) list.getParent().requestDisallowInterceptTouchEvent(false);
            final int fromIndex = from;
            final int targetIndex = Math.max(0, Math.min(target, Math.max(0, callbacks.itemCount() - 1)));
            if (!commit || dragProxy == null) {
                restoreSourcePlaceholder();
                resetSpringReorderVisual(list, false);
                cleanupProxy();
                clearState();
                reorderGestureActive = false;
                try { callbacks.onVisualSettled(); } catch (Throwable ignored) { }
                refreshVisibleRowsAfterDrag();
                return;
            }

            float landY = targetTopInRoot();
            dragProxy.animate().cancel();
            dragProxy.animate().x(proxyLeftInRoot).y(landY).scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(165L).setInterpolator(SpringMotion.LAND)
                    .withEndAction(() -> {
                        restoreSourcePlaceholder();
                        resetSpringReorderVisual(list, true);
                        try { callbacks.commit(fromIndex, targetIndex); }
                        catch (Throwable commitError) { toast("排序未完成，已恢复"); }
                        list.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                            @Override public boolean onPreDraw() {
                                if (list.getViewTreeObserver().isAlive()) list.getViewTreeObserver().removeOnPreDrawListener(this);
                                // This is after ListView.layoutChildren(), before the first new frame.
                                resetSpringReorderVisual(list, true);
                                try { callbacks.onDropLayoutReady(); } catch (Throwable ignored) { }
                                cleanupProxy();
                                clearState();
                                reorderGestureActive = false;
                                try { callbacks.onVisualSettled(); } catch (Throwable ignored) { }
                                refreshVisibleRowsAfterDrag();
                                modalDismissSuppressedUntil = SystemClock.uptimeMillis() + 320L;
                                return true;
                            }
                        });
                        list.requestLayout();
                        list.invalidate();
                    }).start();
        }

        private void restoreSourcePlaceholder() {
            if (sourceView != null) {
                sourceView.setBackground(sourceBackground);
                sourceView.setHasTransientState(false);
            }
            if (sourceContent != null) sourceContent.setAlpha(sourceContentAlpha);
        }

        private void cleanupProxy() {
            if (dragProxy != null) {
                dragProxy.animate().cancel();
                if (dragProxy.getParent() instanceof ViewGroup) ((ViewGroup) dragProxy.getParent()).removeView(dragProxy);
            }
            if (dragImage != null) dragImage.setImageDrawable(null);
            // Never explicitly recycle a bitmap that was recently attached to a hardware layer.
            // Modern Android owns bitmap lifetime through GC; manual recycle can race RenderThread
            // on some OEM devices and is not worth the risk for a single short-lived drag snapshot.
            dragProxy = null;
            dragImage = null;
            dragBitmap = null;
        }

        private void clearState() {
            activeHandle = null;
            sourceView = null;
            sourceContent = null;
            sourceBackground = null;
            overlayRoot = null;
            sourceCenterScreenY = 0f;
            edgeScrollDirection = 0;
            edgeScrollScheduled = false;
            reorderFrameScheduled = false;
            pendingModelRawY = 0f;
            from = -1;
            target = -1;
        }
    }

    private void showPlaybackQueue() {
        if (playback == null) { toast("播放器正在连接"); return; }
        List<Song> initial = playback.queueSnapshot();
        if (initial.isEmpty()) { showGlassMessage("播放列表", "当前还没有待播放歌曲。", "知道了", null, "", null); return; }

        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 14));
        card.setBackground(Ui.transientGlass(25, this));
        card.setElevation(Ui.dp(this, 10));

        View handle = new View(this);
        handle.setBackground(Ui.round(AppearanceSystem.isLight()
                ? Color.argb(62, 88, 100, 122) : Color.argb(80, 255, 255, 255), 2, this));
        LinearLayout.LayoutParams hp = Ui.lp(Ui.dp(this, 42), Ui.dp(this, 4));
        hp.gravity = Gravity.CENTER_HORIZONTAL; hp.bottomMargin = Ui.dp(this, 10);
        card.addView(handle, hp);

        LinearLayout header = Ui.row(this);
        LinearLayout titles = Ui.column(this);
        titles.addView(Ui.text(this, "播放列表", 18.5f, Ui.TEXT, true), new LinearLayout.LayoutParams(-1, 0, 1f));
        titles.addView(Ui.text(this, initial.size() + " 首 · 点击切歌 · 长按拖动排序", 10.8f, Ui.TEXT_2, false), new LinearLayout.LayoutParams(-1, 0, 1f));
        header.addView(titles, new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f));

        int mode = playback.getPlayMode();
        int queueModeAccent = playModeAccent(mode);
        FrameLayout modeButton = Ui.iconButton(this, playModeIcon(mode), 44, Ui.playerControlIconColor(queueModeAccent), Color.TRANSPARENT);
        modeButton.setBackground(Ui.playerControlSurface(queueModeAccent, 22, this));
        IconView modeIcon = (IconView) modeButton.getChildAt(0);
        modeButton.setContentDescription(playModeLabel(mode));
        modeButton.setOnClickListener(v -> {
            if (playback == null) return;
            int nextMode = playback.cyclePlayMode();
            modeIcon.setType(playModeIcon(nextMode));
            int accent = playModeAccent(nextMode);
            modeIcon.setIconColor(Ui.playerControlIconColor(accent));
            modeButton.setBackground(Ui.playerControlSurface(accent, 22, this));
            modeButton.setContentDescription(playModeLabel(nextMode));
            if (nowModeIcon != null) { nowModeIcon.setType(playModeIcon(nextMode)); nowModeIcon.setIconColor(Ui.playerControlIconColor(accent)); }
            if (nowModeButton != null) nowModeButton.setBackground(Ui.playerIconRipple(accent, 23, this));
            showModeToast(playModeLabel(nextMode), accent);
        });
        header.addView(modeButton, Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)));
        card.addView(header, Ui.lp(-1, Ui.dp(this, 52)));

        ListView list = new com.xingyu.music.ui.VelocityListView(this);
        list.setDivider(null);
        list.setDividerHeight(Ui.dp(this, 4));
        list.setSelector(android.R.color.transparent);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setVerticalScrollBarEnabled(false);
        list.setChoiceMode(ListView.CHOICE_MODE_NONE);
        QueueAdapter adapter = new QueueAdapter(initial, playback.currentQueueIndex());
        visibleQueueAdapter = new java.lang.ref.WeakReference<>(adapter);
        // The row itself also has an explicit click listener in QueueAdapter. This ListView
        // callback is kept as a secondary guarantee for devices with different descendant focus behavior.
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (playback == null) return;
            playback.playAt(position);
            main.postDelayed(() -> { if (playback != null) adapter.refresh(playback.queueSnapshot(), playback.currentQueueIndex()); }, 90);
        });
        final int[] queueDropSettlePosition = {-1};
        ReorderGestureController queueReorder = new ReorderGestureController(list, new ReorderCallbacks() {
            @Override public int itemCount() { return adapter.getCount(); }
            @Override public void commit(int from, int to) {
                if (playback == null || from == to) {
                    adapter.refresh(playback == null ? initial : playback.queueSnapshot(),
                            playback == null ? -1 : playback.currentQueueIndex());
                    return;
                }
                playback.moveQueueItem(from, to);
                adapter.refresh(playback.queueSnapshot(), playback.currentQueueIndex());
                queueDropSettlePosition[0] = Math.max(0, Math.min(to, Math.max(0, adapter.getCount() - 1)));
            }
            @Override public void onDropLayoutReady() {
                adapter.refreshCurrentVisibleHalo(list);
            }
            @Override public void onVisualSettled() {
                int safe = queueDropSettlePosition[0];
                queueDropSettlePosition[0] = -1;
                if (safe >= 0) list.post(() -> list.smoothScrollToPosition(safe));
            }
        });
        adapter.setReorderController(queueReorder);
        list.setAdapter(adapter);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = Ui.dp(this, 8);
        card.addView(list, lp);

        presentFractionModal(card, .50f);
        list.post(() -> {
            if (playback == null || adapter.getCount() == 0) return;
            int current = Math.max(0, Math.min(playback.currentQueueIndex(), adapter.getCount() - 1));
            list.setSelection(Math.max(0, current - 2));
        });
    }

    private int playModeAccent(int mode) {
        if (mode == PlaybackService.MODE_SHUFFLE) return Ui.GREEN;
        if (mode == PlaybackService.MODE_REPEAT_ONE) return Ui.PINK;
        return Ui.CYAN;
    }

    private FrameLayout controlSlot(View child, int childSizeDp) {
        FrameLayout slot = new FrameLayout(this);
        slot.addView(child, Ui.frame(Ui.dp(this, childSizeDp), Ui.dp(this, childSizeDp), Gravity.CENTER));
        return slot;
    }

    /**
     * Player micro-interactions are semantic, not decorative queues: the playback action runs
     * immediately while the glyph gives a short directional/state acknowledgement.  Each helper
     * cancels its prior presentation animation so rapid input retargets from the current state.
     */
    private void animatePlayerDirectionalTap(FrameLayout control, int direction) {
        if (control == null || control.getChildCount() == 0) return;
        View glyph = control.getChildAt(0);
        glyph.animate().cancel();
        if (SpringMotion.isReducedMotion()) {
            glyph.setAlpha(.72f);
            glyph.animate().alpha(1f).setDuration(SpringMotion.fadeDuration()).start();
            return;
        }
        float dx = Ui.dp(this, 4) * (direction < 0 ? -1f : 1f);
        glyph.animate().translationX(dx).scaleX(.92f).scaleY(.92f)
                .setDuration(70L).setInterpolator(SpringMotion.SNAPPY)
                .withEndAction(() -> glyph.animate().translationX(0f).scaleX(1f).scaleY(1f)
                        .setDuration(145L).setInterpolator(SpringMotion.PRESS).start()).start();
    }

    private void animatePlayerModeTap(FrameLayout control) {
        if (control == null || control.getChildCount() == 0) return;
        View glyph = control.getChildAt(0);
        glyph.animate().cancel();
        if (SpringMotion.isReducedMotion()) {
            glyph.setAlpha(.72f);
            glyph.animate().alpha(1f).setDuration(SpringMotion.fadeDuration()).start();
            return;
        }
        glyph.animate().rotation(14f).scaleX(.91f).scaleY(.91f)
                .setDuration(78L).setInterpolator(SpringMotion.SNAPPY)
                .withEndAction(() -> glyph.animate().rotation(0f).scaleX(1f).scaleY(1f)
                        .setDuration(155L).setInterpolator(SpringMotion.PRESS).start()).start();
    }

    private void animatePlayerQueueTap(FrameLayout control) {
        if (control == null || control.getChildCount() == 0) return;
        View glyph = control.getChildAt(0);
        glyph.animate().cancel();
        if (SpringMotion.isReducedMotion()) {
            glyph.setAlpha(.74f);
            glyph.animate().alpha(1f).setDuration(SpringMotion.fadeDuration()).start();
            return;
        }
        glyph.animate().translationY(-Ui.dp(this, 2.5f)).scaleX(1.065f).scaleY(.94f)
                .setDuration(82L).setInterpolator(SpringMotion.SNAPPY)
                .withEndAction(() -> glyph.animate().translationY(0f).scaleX(1f).scaleY(1f)
                        .setDuration(150L).setInterpolator(SpringMotion.PRESS).start()).start();
    }

    private void animatePlayerAddTap(FrameLayout control) {
        if (control == null || control.getChildCount() == 0) return;
        View glyph = control.getChildAt(0);
        glyph.animate().cancel();
        if (SpringMotion.isReducedMotion()) {
            glyph.setAlpha(.72f);
            glyph.animate().alpha(1f).setDuration(SpringMotion.fadeDuration()).start();
            return;
        }
        glyph.animate().translationY(-Ui.dp(this, 3)).scaleX(1.10f).scaleY(1.10f)
                .setDuration(82L).setInterpolator(SpringMotion.SNAPPY)
                .withEndAction(() -> glyph.animate().translationY(0f).scaleX(1f).scaleY(1f)
                        .setDuration(160L).setInterpolator(SpringMotion.PRESS).start()).start();
    }

    private void animateDesktopLyricTap(FrameLayout control) {
        if (control == null || control.getChildCount() == 0) return;
        View glyph = control.getChildAt(0);
        glyph.animate().cancel();
        if (SpringMotion.isReducedMotion()) {
            glyph.setAlpha(.72f);
            glyph.animate().alpha(1f).setDuration(SpringMotion.fadeDuration()).start();
            return;
        }
        glyph.animate().translationY(-Ui.dp(this, 2)).scaleX(.94f).scaleY(.94f).alpha(.82f)
                .setDuration(74L).setInterpolator(SpringMotion.SNAPPY)
                .withEndAction(() -> glyph.animate().translationY(0f).scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(155L).setInterpolator(SpringMotion.PRESS).start()).start();
    }

    private void animatePrimaryTransportTap(FrameLayout control) {
        if (control == null) return;
        control.animate().cancel();
        if (SpringMotion.isReducedMotion()) {
            control.setAlpha(.88f);
            control.animate().alpha(1f).setDuration(SpringMotion.fadeDuration()).start();
            return;
        }
        // The ACTION_DOWN press already compresses the button. This short post-click bloom makes
        // Play/Pause feel like the dominant transport without adding a second bouncy gesture.
        control.animate().scaleX(1.035f).scaleY(1.035f)
                .setDuration(72L).setInterpolator(SpringMotion.SNAPPY)
                .withEndAction(() -> SpringMotion.pressUp(control)).start();
    }

    private void animateSeekInteraction(SeekBar seek, boolean active) {
        if (seek == null) return;
        seek.animate().cancel();
        long duration = active ? SpringMotion.pressDownDuration() : SpringMotion.pressUpDuration();
        if (active) {
            seek.animate().scaleY(SpringMotion.isReducedMotion() ? 1.03f : 1.12f).alpha(1f)
                    .setDuration(duration).setInterpolator(SpringMotion.SNAPPY).start();
            if (nowTime != null) nowTime.setTextColor(nowAccent);
            if (nowDuration != null) nowDuration.setTextColor(Ui.TEXT_2);
        } else {
            seek.animate().scaleY(1f).alpha(1f)
                    .setDuration(duration).setInterpolator(SpringMotion.PRESS).start();
            if (nowTime != null) nowTime.setTextColor(Ui.DIM);
            if (nowDuration != null) nowDuration.setTextColor(Ui.DIM);
        }
    }

    private String playModeLabel(int mode) {
        if (mode == PlaybackService.MODE_SHUFFLE) return "随机播放";
        if (mode == PlaybackService.MODE_REPEAT_ONE) return "单曲循环";
        return "顺序播放";
    }

    private IconView.Type playModeIcon(int mode) {
        if (mode == PlaybackService.MODE_SHUFFLE) return IconView.Type.SHUFFLE;
        if (mode == PlaybackService.MODE_REPEAT_ONE) return IconView.Type.REPEAT_ONE;
        return IconView.Type.REPEAT;
    }

    private final class QueueAdapter extends BaseAdapter {
        private final List<Song> items = new ArrayList<>();
        private int current;
        private ReorderGestureController reorderController;

        private final class Holder {
            LinearLayout wrapper;
            LinearLayout row;
            TextView order;
            TextView title;
            TextView artist;
            FrameLayout drag;
            FrameLayout delete;
            FluidTrackHaloDrawable halo;
        }

        QueueAdapter(List<Song> songs, int currentIndex) { refresh(songs, currentIndex); }
        void refresh(List<Song> songs, int currentIndex) {
            if (current == currentIndex && songs != null && items.equals(songs)) return;
            items.clear();
            if (songs != null) items.addAll(songs);
            current = currentIndex;
            notifyDataSetChanged();
        }
        void setReorderController(ReorderGestureController controller) { reorderController = controller; }
        int currentIndex() { return current; }
        void refreshCurrentVisibleHalo(ListView list) {
            if (list == null || current < 0) return;
            int childIndex = current - list.getFirstVisiblePosition();
            if (childIndex < 0 || childIndex >= list.getChildCount()) return;
            View child = list.getChildAt(childIndex);
            if (child == null || !(child.getTag() instanceof Holder)) return;
            Holder h = (Holder) child.getTag();
            bindPlaybackHalo(h.halo, h.row);
            h.row.invalidate();
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        private Holder createHolder() {
            Holder h = new Holder();
            h.wrapper = Ui.column(MainActivity.this);
            h.wrapper.setClipChildren(false);

            h.row = Ui.row(MainActivity.this);
            h.row.setGravity(Gravity.CENTER_VERTICAL);
            h.row.setPadding(Ui.dp(MainActivity.this, 10), Ui.dp(MainActivity.this, 5),
                    Ui.dp(MainActivity.this, 6), Ui.dp(MainActivity.this, 5));

            h.order = Ui.text(MainActivity.this, "", 10.2f, Ui.DIM, true);
            h.order.setGravity(Gravity.CENTER);
            h.row.addView(h.order, Ui.lp(Ui.dp(MainActivity.this, 28), Ui.dp(MainActivity.this, 44)));

            LinearLayout texts = Ui.column(MainActivity.this);
            texts.setPadding(Ui.dp(MainActivity.this, 7), 0, 0, 0);
            h.title = Ui.text(MainActivity.this, "", 12.9f, Ui.TEXT_2, false);
            h.title.setSingleLine(true); h.title.setEllipsize(TextUtils.TruncateAt.END);
            h.artist = Ui.text(MainActivity.this, "", 10.4f, Ui.DIM, false);
            h.artist.setSingleLine(true); h.artist.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(h.title, new LinearLayout.LayoutParams(-1, 0, 1f));
            texts.addView(h.artist, new LinearLayout.LayoutParams(-1, 0, 1f));
            h.row.addView(texts, new LinearLayout.LayoutParams(0, Ui.dp(MainActivity.this, 46), 1f));

            h.drag = Ui.iconButton(MainActivity.this, IconView.Type.DRAG, 38, Ui.DIM, Color.TRANSPARENT);
            h.drag.setContentDescription("长按拖动排序");
            h.drag.setFocusable(false);
            h.row.addView(h.drag, Ui.lp(Ui.dp(MainActivity.this, 38), Ui.dp(MainActivity.this, 38)));

            h.delete = Ui.iconButton(MainActivity.this, IconView.Type.DELETE, 32, Ui.DIM, Color.TRANSPARENT);
            h.delete.setContentDescription("从播放列表删除");
            h.delete.setFocusable(false);
            h.row.addView(h.delete, Ui.lp(Ui.dp(MainActivity.this, 32), Ui.dp(MainActivity.this, 32)));

            h.halo = new FluidTrackHaloDrawable(getResources().getDisplayMetrics().density);
            h.row.setClickable(true); h.row.setFocusable(true); Ui.applyRipple(h.row, Color.TRANSPARENT);
            h.wrapper.addView(h.row, Ui.lp(-1, Ui.dp(MainActivity.this, 56)));
            h.wrapper.setTag(h);
            return h;
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            Holder h;
            if (convertView != null && convertView.getTag() instanceof Holder) {
                h = (Holder) convertView.getTag();
            } else {
                h = createHolder();
                convertView = h.wrapper;
            }

            Song song = items.get(position);
            boolean isCurrent = position == current;
            h.order.setText(isCurrent ? "●" : String.valueOf(position + 1));
            h.order.setTextColor(isCurrent ? playlistPlaybackAccent : Ui.DIM);
            h.title.setText(song.title);
            h.title.setTextColor(isCurrent ? Ui.TEXT : Ui.TEXT_2);
            h.title.getPaint().setFakeBoldText(isCurrent);
            h.artist.setText(song.artist);
            if (isCurrent) {
                bindPlaybackHalo(h.halo, h.row);
            } else {
                h.halo.stopRendering();
                // Moonlight rows are real paper-like content surfaces, not dark queue cells dimmed
                // through a white sheet.  This removes the broad grey blocks seen in device QA.
                h.row.setBackground(AppearanceSystem.isLight()
                        ? Ui.contentSurface(15, MainActivity.this)
                        : Ui.stroke(Color.argb(135, 8, 8, 14), 15,
                                Color.argb(22, 255, 255, 255), MainActivity.this));
            }

            if (reorderController != null) reorderController.attach(h.drag, h.wrapper, position);
            h.delete.setOnClickListener(v -> {
                if (playback == null) return;
                playback.removeAt(position);
                List<Song> latest = playback.queueSnapshot();
                if (latest.isEmpty()) hideModal();
                else refresh(latest, playback.currentQueueIndex());
            });
            h.row.setOnClickListener(v -> {
                if (playback == null || reorderGestureActive) return;
                playback.playAt(position);
                main.postDelayed(() -> {
                    if (playback != null) refresh(playback.queueSnapshot(), playback.currentQueueIndex());
                }, 80);
            });
            return convertView;
        }
    }

    private void showManualSongOrder(String collectionName, List<Song> source, String playlistId) {
        if (source == null || source.size() < 2) { toast("至少需要两首歌才能排序"); return; }
        LinearLayout card = modalCard("手动排序", collectionName + " · 长按右侧三横线拖动；顺序会保存到本机歌单");

        LinearLayout tools = Ui.row(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        TextView count = Ui.text(this, source.size() + " 首", 11.2f, Ui.TEXT_2, true);
        tools.addView(count, new LinearLayout.LayoutParams(0, Ui.dp(this, 38), 1f));
        TextView done = modalButton("完成", true);
        done.setBackground(Ui.primaryFill(Ui.CYAN, 15, this));
        tools.addView(done, Ui.lp(Ui.dp(this, 82), Ui.dp(this, 38)));
        LinearLayout.LayoutParams tp = Ui.lp(-1, Ui.dp(this, 38)); tp.topMargin = Ui.dp(this, 8); card.addView(tools, tp);

        ListView list = new com.xingyu.music.ui.VelocityListView(this);
        list.setDivider(null);
        list.setDividerHeight(Ui.dp(this, 4));
        list.setSelector(android.R.color.transparent);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setVerticalScrollBarEnabled(false);
        ManualOrderAdapter adapter = new ManualOrderAdapter(source);
        final boolean[] orderChanged = {false};
        modalDismissCompletion = () -> {
            if (!orderChanged[0]) return;
            suppressNextPageAnimation = true;
            renderTab();
        };

        final int[] manualDropSettlePosition = {-1};
        ReorderGestureController manualReorder = new ReorderGestureController(list, new ReorderCallbacks() {
            @Override public int itemCount() { return adapter.getCount(); }
            @Override public void commit(int from, int to) {
                if (from == to) return;
                adapter.move(from, to);
                List<Song> ordered = adapter.snapshot();
                if (playlistId == null) {
                    favorites = store.setFavoriteOrder(ordered);
                } else {
                    playlists = store.setPlaylistSongOrder(playlistId, ordered);
                    openPlaylist = playlistById(playlistId);
                }
                // Persist immediately, but do not rebuild the page hierarchy while the drag sheet and
                // landing proxy are still alive. The parent refresh is deferred until sheet dismissal.
                orderChanged[0] = true;
                toast("顺序已保存");
                manualDropSettlePosition[0] = Math.max(0, Math.min(to, Math.max(0, adapter.getCount() - 1)));
            }
            @Override public void onDropLayoutReady() {
                adapter.refreshVisibleCovers(list, true);
            }
            @Override public void onVisualSettled() {
                adapter.refreshVisibleCovers(list, false);
                int safe = manualDropSettlePosition[0];
                manualDropSettlePosition[0] = -1;
                if (safe >= 0) list.post(() -> list.smoothScrollToPosition(safe));
            }
        });
        adapter.setReorderController(manualReorder);
        list.setAdapter(adapter);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f); lp.topMargin = Ui.dp(this, 8); card.addView(list, lp);
        done.setOnClickListener(v -> dismissModalNow());
        presentFractionModal(card, .82f);
    }

    private final class ManualOrderAdapter extends BaseAdapter {
        private final List<Song> items = new ArrayList<>();
        private ReorderGestureController reorderController;

        private final class Holder {
            LinearLayout wrapper;
            LinearLayout row;
            ImageView cover;
            String boundCoverUrl;
            TextView title;
            TextView artist;
            FrameLayout drag;
        }

        ManualOrderAdapter(List<Song> source) { if (source != null) items.addAll(source); }
        List<Song> snapshot() { return new ArrayList<>(items); }
        void setReorderController(ReorderGestureController controller) { reorderController = controller; }
        void move(int from, int to) {
            if (from < 0 || from >= items.size() || to < 0 || to >= items.size() || from == to) return;
            Song moved = items.remove(from);
            items.add(to, moved);
            notifyDataSetChanged();
        }

        void refreshVisibleCovers(ListView list, boolean forceDuringDrop) {
            if (list == null || (reorderGestureActive && !forceDuringDrop)) return;
            int first = list.getFirstVisiblePosition();
            for (int i = 0; i < list.getChildCount(); i++) {
                View child = list.getChildAt(i);
                if (child == null || !(child.getTag() instanceof Holder)) continue;
                int position = first + i;
                if (position < 0 || position >= items.size()) continue;
                Holder h = (Holder) child.getTag();
                Song song = items.get(position);
                String url = song.coverUrl == null ? "" : song.coverUrl;
                if (!url.equals(h.boundCoverUrl)) {
                    h.boundCoverUrl = url;
                    h.cover.setImageDrawable(null);
                    if (!url.isEmpty()) ImageLoader.load(url, h.cover, null);
                }
            }
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        private Holder createHolder() {
            Holder h = new Holder();
            h.wrapper = Ui.column(MainActivity.this);
            h.wrapper.setClipChildren(false);

            h.row = Ui.row(MainActivity.this);
            h.row.setGravity(Gravity.CENTER_VERTICAL);
            h.row.setPadding(Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 6),
                    Ui.dp(MainActivity.this, 6), Ui.dp(MainActivity.this, 6));
            h.row.setBackground(Ui.stroke(Color.argb(132, 8, 8, 14), 15,
                    Color.argb(24, 255, 255, 255), MainActivity.this));

            h.cover = new ImageView(MainActivity.this);
            h.cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            h.cover.setClipToOutline(true);
            h.cover.setBackground(Ui.round(Color.rgb(22, 22, 28), 11, MainActivity.this));
            h.row.addView(h.cover, Ui.lp(Ui.dp(MainActivity.this, 48), Ui.dp(MainActivity.this, 48)));

            LinearLayout texts = Ui.column(MainActivity.this);
            texts.setPadding(Ui.dp(MainActivity.this, 11), Ui.dp(MainActivity.this, 1),
                    Ui.dp(MainActivity.this, 7), Ui.dp(MainActivity.this, 1));
            h.title = Ui.text(MainActivity.this, "", 13.2f, Ui.TEXT, true);
            h.title.setSingleLine(true); h.title.setEllipsize(TextUtils.TruncateAt.END);
            h.artist = Ui.text(MainActivity.this, "", 10.6f, Ui.TEXT_2, false);
            h.artist.setSingleLine(true); h.artist.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(h.title, new LinearLayout.LayoutParams(-1, 0, 1f));
            texts.addView(h.artist, new LinearLayout.LayoutParams(-1, 0, 1f));
            h.row.addView(texts, new LinearLayout.LayoutParams(0, Ui.dp(MainActivity.this, 48), 1f));

            h.drag = Ui.iconButton(MainActivity.this, IconView.Type.DRAG, 42, Ui.TEXT_2, Color.TRANSPARENT);
            h.drag.setContentDescription("长按拖动排序");
            h.drag.setFocusable(false);
            h.row.addView(h.drag, Ui.lp(Ui.dp(MainActivity.this, 42), Ui.dp(MainActivity.this, 42)));

            h.wrapper.addView(h.row, Ui.lp(-1, Ui.dp(MainActivity.this, 62)));
            h.wrapper.setTag(h);
            return h;
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            Holder h;
            if (convertView != null && convertView.getTag() instanceof Holder) {
                h = (Holder) convertView.getTag();
            } else {
                h = createHolder();
                convertView = h.wrapper;
            }

            Song song = items.get(position);
            h.title.setText(song.title);
            h.artist.setText(song.artist);
            // A long edge-scroll reorder must not enqueue artwork decode/network work. Newly
            // recycled rows use the neutral placeholder while the finger is down; one invalidate
            // after drop/cancel rebinds the visible covers normally.
            String coverUrl = song.coverUrl == null ? "" : song.coverUrl;
            if (reorderGestureActive) {
                h.boundCoverUrl = null;
                h.cover.setTag(null);
                h.cover.setImageDrawable(null);
            } else if (!coverUrl.equals(h.boundCoverUrl)) {
                h.boundCoverUrl = coverUrl;
                h.cover.setImageDrawable(null);
                ImageLoader.load(coverUrl, h.cover, null);
            }
            if (reorderController != null) reorderController.attach(h.drag, h.wrapper, position);
            return convertView;
        }
    }

    private void showSongBatchManager(String collectionName, List<Song> source, String playlistId) {
        if (source == null || source.isEmpty()) { toast("这里还没有歌曲"); return; }
        final List<Song> items = new ArrayList<>(source);
        final LinkedHashMap<String, Song> selected = new LinkedHashMap<>();
        LinearLayout card = modalCard("批量管理", collectionName + " · 多选后可以删除、加入歌单、下载或插入下一首");

        LinearLayout topTools = Ui.row(this);
        TextView counter = Ui.text(this, "已选 0 首", 11.3f, Ui.TEXT_2, true);
        topTools.addView(counter, new LinearLayout.LayoutParams(0, Ui.dp(this, 38), 1f));
        TextView all = outlineAction("全选");
        all.setTextColor(Ui.CYAN);
        topTools.addView(all, Ui.lp(Ui.dp(this, 72), Ui.dp(this, 38)));
        LinearLayout.LayoutParams ttp = Ui.lp(-1, Ui.dp(this, 38)); ttp.topMargin = Ui.dp(this, 8); card.addView(topTools, ttp);

        ListView list = new ListView(this);
        list.setDivider(null); list.setDividerHeight(0); list.setVerticalScrollBarEnabled(false);
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int position) { return items.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                Song song = items.get(position);
                LinearLayout row = Ui.row(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 6), Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 6));
                row.setBackground(Ui.glass(84,14,16,MainActivity.this));
                ImageView cover = new ImageView(MainActivity.this); cover.setScaleType(ImageView.ScaleType.CENTER_CROP); cover.setClipToOutline(true);
                cover.setBackground(Ui.round(Color.rgb(22,22,28),10,MainActivity.this)); ImageLoader.load(song.coverUrl, cover, null);
                row.addView(cover, Ui.lp(Ui.dp(MainActivity.this,42),Ui.dp(MainActivity.this,42)));
                LinearLayout texts = Ui.column(MainActivity.this); texts.setPadding(Ui.dp(MainActivity.this,10),0,Ui.dp(MainActivity.this,4),0);
                TextView title = Ui.text(MainActivity.this,song.title,12.6f,Ui.TEXT,true); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
                TextView artist = Ui.text(MainActivity.this,song.artist,10.2f,Ui.DIM,false); artist.setSingleLine(true); artist.setEllipsize(TextUtils.TruncateAt.END);
                texts.addView(title,new LinearLayout.LayoutParams(-1,0,1f)); texts.addView(artist,new LinearLayout.LayoutParams(-1,0,1f));
                row.addView(texts,new LinearLayout.LayoutParams(0,Ui.dp(MainActivity.this,42),1f));
                CheckBox check = new CheckBox(MainActivity.this);
                check.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Ui.CYAN,Ui.DIM}));
                check.setChecked(selected.containsKey(song.key()));
                row.addView(check,Ui.lp(Ui.dp(MainActivity.this,42),Ui.dp(MainActivity.this,42)));
                SimpleAction toggle = () -> {
                    if(selected.containsKey(song.key())) selected.remove(song.key()); else selected.put(song.key(),song);
                    check.setChecked(selected.containsKey(song.key())); counter.setText("已选 "+selected.size()+" 首");
                    all.setText(selected.size()==items.size()?"取消全选":"全选");
                };
                row.setClickable(true); Ui.applyRipple(row,Color.TRANSPARENT); row.setOnClickListener(v->toggle.run());
                check.setOnClickListener(v->{ if(check.isChecked()) selected.put(song.key(),song); else selected.remove(song.key()); counter.setText("已选 "+selected.size()+" 首"); all.setText(selected.size()==items.size()?"取消全选":"全选"); });
                return row;
            }
        };
        list.setAdapter(adapter);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,0,1f); lp.topMargin=Ui.dp(this,8); card.addView(list,lp);
        all.setOnClickListener(v->{
            if(selected.size()==items.size()) selected.clear(); else { selected.clear(); for(Song song:items) if(song!=null) selected.put(song.key(),song); }
            counter.setText("已选 "+selected.size()+" 首"); all.setText(selected.size()==items.size()?"取消全选":"全选"); adapter.notifyDataSetChanged();
        });

        LinearLayout actions = Ui.row(this);
        TextView delete = modalButton("删除", false); delete.setTextColor(Ui.PINK);
        TextView addPlaylist = modalButton("加到歌单", false); addPlaylist.setTextColor(Ui.GREEN);
        TextView download = modalButton("下载", false); download.setTextColor(Ui.CYAN);
        TextView next = modalButton("下一首", false); next.setTextColor(Ui.mix(Ui.PURPLE,Color.WHITE,.35f));
        actions.addView(delete,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1f));
        LinearLayout.LayoutParams ap2=new LinearLayout.LayoutParams(0,Ui.dp(this,46),1f); ap2.leftMargin=Ui.dp(this,6); actions.addView(addPlaylist,ap2);
        LinearLayout.LayoutParams ap3=new LinearLayout.LayoutParams(0,Ui.dp(this,46),1f); ap3.leftMargin=Ui.dp(this,6); actions.addView(download,ap3);
        LinearLayout.LayoutParams ap4=new LinearLayout.LayoutParams(0,Ui.dp(this,46),1f); ap4.leftMargin=Ui.dp(this,6); actions.addView(next,ap4);
        LinearLayout.LayoutParams afp=Ui.lp(-1,Ui.dp(this,46)); afp.topMargin=Ui.dp(this,10); card.addView(actions,afp);

        delete.setOnClickListener(v->{
            if(selected.isEmpty()){ toast("先选择歌曲"); return; }
            ArrayList<String> keys=new ArrayList<>(selected.keySet()); int count=keys.size();
            dismissModalNow();
            showGlassMessage("删除歌曲", "确定删除已选的 "+count+" 首吗？", "删除", ()->{
                if(playlistId==null){ favorites=store.removeFavorites(keys); }
                else {
                    rememberPlaylistPositionForRefresh();
                    playlists=store.removeSongsFromPlaylist(playlistId,keys); openPlaylist=playlistById(playlistId);
                    playlistVisibleCount = Math.max(PLAYLIST_INITIAL_RENDER_COUNT,
                            Math.min(playlistVisibleCount, openPlaylist == null ? PLAYLIST_INITIAL_RENDER_COUNT : openPlaylist.songs.size()));
                    suppressNextPageAnimation = true;
                }
                renderTab(); toast("已删除 "+count+" 首");
            }, "取消", null);
        });
        addPlaylist.setOnClickListener(v->{
            if(selected.isEmpty()){ toast("先选择歌曲"); return; }
            ArrayList<Song> songs=new ArrayList<>(selected.values());
            dismissModalNow(); showBatchAddToPlaylist(songs,playlistId);
        });
        download.setOnClickListener(v->{
            if(selected.isEmpty()){ toast("先选择歌曲"); return; }
            ArrayList<Song> songs=new ArrayList<>(selected.values());
            OfflineDownloadService.enqueueAll(this, collectionName + " · 批量下载", songs);
            dismissModalNow(); toast("已创建下载任务 · "+songs.size()+" 首 · 可在离线页查看进度");
        });
        next.setOnClickListener(v->{
            if(selected.isEmpty()){ toast("先选择歌曲"); return; }
            if(playback==null){ toast("播放器还在连接"); return; }
            ArrayList<Song> songs=new ArrayList<>(selected.values());
            // enqueueNext always inserts immediately after current, therefore reverse
            // iteration preserves the order visible in this manager.
            for(int i=songs.size()-1;i>=0;i--) playback.enqueueNext(songs.get(i));
            dismissModalNow(); toast("已添加到下一首 · "+songs.size()+" 首");
        });
        presentFractionModal(card,.72f);
    }

    private void showBatchAddToPlaylist(List<Song> songs, String excludePlaylistId) {
        if(songs==null||songs.isEmpty()) return;
        List<ImportedPlaylist> targets=new ArrayList<>();
        for(ImportedPlaylist p:playlists) if(p!=null&&(excludePlaylistId==null||!excludePlaylistId.equals(p.id))) targets.add(p);
        if(targets.isEmpty()){ toast("没有其它歌单可以添加"); return; }
        String[] names=new String[targets.size()];
        for(int i=0;i<targets.size();i++) names[i]=targets.get(i).name+"  ·  "+targets.get(i).songs.size()+" 首";
        showGlassOptions("添加到歌单", "已选 "+songs.size()+" 首", names, which->{
            ImportedPlaylist target=targets.get(which);
            playlists=store.addSongsToPlaylist(target.id,songs);
            if(openPlaylist!=null) openPlaylist=playlistById(openPlaylist.id);
            renderTab(); toast("已加入《"+target.name+"》 · "+songs.size()+" 首");
        });
    }

    private void showAddToPlaylist(Song song) {
        List<ImportedPlaylist> locals = new ArrayList<>();
        for (ImportedPlaylist p : playlists) if ("local".equals(p.source)) locals.add(p);
        if (locals.isEmpty()) {
            showGlassInput("先建一个歌单", "歌单名称", false, "创建并添加", name -> {
                playlists = store.createPlaylist(name);
                ImportedPlaylist target = playlists.isEmpty() ? null : playlists.get(0);
                if (target != null) { playlists = store.addSongToPlaylist(target.id, song); toast("已添加到 " + target.name); }
            });
            return;
        }
        String[] names = new String[locals.size()]; for (int i=0;i<locals.size();i++) names[i]=locals.get(i).name;
        showGlassOptions("添加到歌单", song.title, names, which -> {
            ImportedPlaylist target = locals.get(which); playlists = store.addSongToPlaylist(target.id, song); toast("已添加到 " + target.name);
            if (tab == 2) renderTab();
        });
    }

    private void showAddSongsToPlaylist(ImportedPlaylist target) {
        if (target == null) return;
        LinkedHashMap<String, Song> selected = new LinkedHashMap<>();
        LinearLayout card = modalCard("添加歌曲", "从收藏、最近播放、搜索或其他歌单多选加入《" + target.name + "》");

        LinearLayout tabs = Ui.row(this);
        String[] names = {"收藏", "最近播放", "搜索歌曲", "其他歌单"};
        TextView[] tabViews = new TextView[names.length];
        int[] active = {0};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView tv = Ui.text(this, names[i], 10.7f, Ui.TEXT_2, true);
            tv.setGravity(Gravity.CENTER);
            tv.setClickable(true); tv.setFocusable(true); Ui.applyRipple(tv, Color.TRANSPARENT);
            tabViews[i] = tv;
            tabs.addView(tv, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1f));
            tv.setOnClickListener(v -> { active[0] = idx; });
        }
        LinearLayout.LayoutParams tp = Ui.lp(-1, Ui.dp(this, 42)); tp.topMargin = Ui.dp(this, 12); card.addView(tabs, tp);

        FrameLayout content = new FrameLayout(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, 0, 1f); cp.topMargin = Ui.dp(this, 8); card.addView(content, cp);

        LinearLayout footer = Ui.row(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView count = Ui.text(this, "已选 0 首", 11.2f, Ui.TEXT_2, true);
        footer.addView(count, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f));
        TextView commit = modalButton("加入歌单", true);
        commit.setAlpha(.45f); commit.setEnabled(false);
        footer.addView(commit, Ui.lp(Ui.dp(this, 116), Ui.dp(this, 46)));
        LinearLayout.LayoutParams fp = Ui.lp(-1, Ui.dp(this, 46)); fp.topMargin = Ui.dp(this, 10); card.addView(footer, fp);

        SimpleAction updateFooter = () -> {
            count.setText("已选 " + selected.size() + " 首");
            boolean enabled = !selected.isEmpty();
            commit.setEnabled(enabled); commit.setAlpha(enabled ? 1f : .45f);
        };

        View[] cachedPickerViews = new View[4];
        Runnable[] renderPicker = new Runnable[1];
        renderPicker[0] = () -> {
            for (int i = 0; i < tabViews.length; i++) {
                boolean sel = i == active[0];
                int tint = i == 0 ? Ui.PINK : (i == 1 ? Ui.GOLD : (i == 2 ? Ui.CYAN : Ui.GREEN));
                tabViews[i].setTextColor(sel ? tint : Ui.DIM);
                tabViews[i].setBackground(sel
                        ? Ui.gradient(new int[]{Color.argb(38, Color.red(tint), Color.green(tint), Color.blue(tint)), Color.argb(18, Color.red(Ui.BLUE), Color.green(Ui.BLUE), Color.blue(Ui.BLUE))}, 15, this)
                        : Ui.round(Color.TRANSPARENT, 15, this));
            }
            content.removeAllViews();
            int index = active[0];
            if (cachedPickerViews[index] == null) {
                if (index == 0) cachedPickerViews[index] = buildPickerSongList(favorites, target, selected, updateFooter);
                else if (index == 1) cachedPickerViews[index] = buildPickerSongList(history, target, selected, updateFooter);
                else if (index == 2) cachedPickerViews[index] = buildPickerSearch(target, selected, updateFooter);
                else cachedPickerViews[index] = buildOtherPlaylistPicker(target, selected, updateFooter);
            }
            View page = cachedPickerViews[index];
            if (page.getParent() instanceof ViewGroup) ((ViewGroup) page.getParent()).removeView(page);
            content.addView(page, Ui.frame(-1, -1, Gravity.FILL));
            refreshPickerAdapters(page);
        };
        for (TextView tv : tabViews) {
            tv.setOnClickListener(v -> {
                for (int i = 0; i < tabViews.length; i++) if (tabViews[i] == v) { active[0] = i; break; }
                renderPicker[0].run();
            });
        }

        commit.setOnClickListener(v -> {
            if (selected.isEmpty()) return;
            playlists = store.addSongsToPlaylist(target.id, new ArrayList<>(selected.values()));
            openPlaylist = playlistById(target.id);
            dismissModalNow();
            renderTab();
            toast("已加入 " + selected.size() + " 首");
        });

        renderPicker[0].run();
        presentFractionModal(card, .72f);
    }

    private ImportedPlaylist playlistById(String id) {
        if (id == null) return null;
        for (ImportedPlaylist p : playlists) if (id.equals(p.id)) return p;
        return null;
    }

    private static final class PickerSongHolder {
        LinearLayout row;
        ImageView cover;
        TextView title;
        TextView artist;
        CheckBox check;
    }

    private final class PickerSongAdapter extends BaseAdapter {
        private final List<Song> items;
        private final LinkedHashMap<String, Song> selected;
        private final SimpleAction updateFooter;
        private final LinkedHashSet<String> targetKeys = new LinkedHashSet<>();

        PickerSongAdapter(List<Song> source, ImportedPlaylist target,
                          LinkedHashMap<String, Song> selected, SimpleAction updateFooter) {
            this.items = source == null ? new ArrayList<>() : new ArrayList<>(source);
            this.selected = selected;
            this.updateFooter = updateFooter;
            if (target != null && target.songs != null) {
                for (Song song : target.songs) if (song != null) targetKeys.add(song.key());
            }
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            PickerSongHolder holder;
            LinearLayout wrapper;
            if (convertView == null) {
                wrapper = Ui.column(MainActivity.this);
                holder = new PickerSongHolder();
                holder.row = Ui.row(MainActivity.this);
                holder.row.setGravity(Gravity.CENTER_VERTICAL);
                holder.row.setPadding(Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 6), Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 6));
                holder.row.setBackground(Ui.glass(88, 15, 16, MainActivity.this));
                Ui.applyRipple(holder.row, Color.TRANSPARENT);

                holder.cover = new ImageView(MainActivity.this);
                holder.cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                holder.cover.setClipToOutline(true);
                holder.cover.setBackground(Ui.round(Color.rgb(24,24,31), 11, MainActivity.this));
                holder.row.addView(holder.cover, Ui.lp(Ui.dp(MainActivity.this, 44), Ui.dp(MainActivity.this, 44)));

                LinearLayout texts = Ui.column(MainActivity.this);
                texts.setPadding(Ui.dp(MainActivity.this, 10), 0, Ui.dp(MainActivity.this, 6), 0);
                holder.title = Ui.text(MainActivity.this, "", 12.7f, Ui.TEXT, true);
                holder.title.setSingleLine(true); holder.title.setEllipsize(TextUtils.TruncateAt.END);
                holder.artist = Ui.text(MainActivity.this, "", 10.2f, Ui.DIM, false);
                holder.artist.setSingleLine(true); holder.artist.setEllipsize(TextUtils.TruncateAt.END);
                texts.addView(holder.title, new LinearLayout.LayoutParams(-1, 0, 1f));
                texts.addView(holder.artist, new LinearLayout.LayoutParams(-1, 0, 1f));
                holder.row.addView(texts, new LinearLayout.LayoutParams(0, Ui.dp(MainActivity.this, 44), 1f));

                holder.check = new CheckBox(MainActivity.this);
                holder.check.setButtonTintList(new ColorStateList(
                        new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                        new int[]{Ui.GREEN, Ui.DIM}));
                holder.row.addView(holder.check, Ui.lp(Ui.dp(MainActivity.this, 42), Ui.dp(MainActivity.this, 42)));
                wrapper.addView(holder.row, Ui.lp(-1, Ui.dp(MainActivity.this, 56)));
                Space gap = new Space(MainActivity.this);
                wrapper.addView(gap, Ui.lp(-1, Ui.dp(MainActivity.this, 6)));
                wrapper.setTag(holder);
            } else {
                wrapper = (LinearLayout) convertView;
                holder = (PickerSongHolder) wrapper.getTag();
            }

            Song song = items.get(position);
            boolean exists = song == null || targetKeys.contains(song.key());
            if (song == null) {
                holder.title.setText("无效歌曲"); holder.artist.setText("");
                holder.cover.setTag(""); holder.cover.setImageDrawable(null);
                holder.check.setChecked(false); holder.check.setEnabled(false);
                holder.row.setClickable(false); holder.row.setAlpha(.42f);
                return wrapper;
            }

            holder.title.setText(song.title);
            holder.title.setTextColor(exists ? Ui.DIM : Ui.TEXT);
            holder.artist.setText(exists ? "已在歌单 · " + song.artist : song.artist);
            holder.cover.setImageDrawable(null);
            holder.cover.setTag(song.coverUrl == null ? "" : song.coverUrl);
            if (song.coverUrl != null && !song.coverUrl.trim().isEmpty()) ImageLoader.load(song.coverUrl, holder.cover, null);
            holder.check.setOnClickListener(null);
            holder.check.setChecked(selected.containsKey(song.key()));
            holder.check.setEnabled(!exists);
            holder.row.setClickable(!exists); holder.row.setFocusable(!exists); holder.row.setAlpha(exists ? .52f : 1f);

            PickerSongHolder bound = holder;
            SimpleAction toggle = () -> {
                if (exists) return;
                if (selected.containsKey(song.key())) selected.remove(song.key()); else selected.put(song.key(), song);
                bound.check.setChecked(selected.containsKey(song.key()));
                updateFooter.run();
            };
            holder.row.setOnClickListener(v -> toggle.run());
            holder.check.setOnClickListener(v -> {
                if (exists) return;
                if (bound.check.isChecked()) selected.put(song.key(), song); else selected.remove(song.key());
                updateFooter.run();
            });
            return wrapper;
        }
    }

    private View buildPickerSongList(List<Song> source, ImportedPlaylist target,
                                     LinkedHashMap<String, Song> selected, SimpleAction updateFooter) {
        return buildPickerSongList(source, target, selected, updateFooter, null);
    }

    private View buildPickerSongList(List<Song> source, ImportedPlaylist target,
                                     LinkedHashMap<String, Song> selected, SimpleAction updateFooter,
                                     PickerSongAdapter[] adapterOut) {
        if (source == null || source.isEmpty()) return compactEmpty("这里还没有可添加的歌曲");
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setVerticalScrollBarEnabled(false);
        list.setScrollingCacheEnabled(true);
        list.setFadingEdgeLength(0);
        list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        PickerSongAdapter adapter = new PickerSongAdapter(source, target, selected, updateFooter);
        list.setAdapter(adapter);
        if (adapterOut != null && adapterOut.length > 0) adapterOut[0] = adapter;
        return list;
    }

    private void refreshPickerAdapters(View root) {
        if (root == null) return;
        if (root instanceof ListView) {
            android.widget.ListAdapter adapter = ((ListView) root).getAdapter();
            if (adapter instanceof PickerSongAdapter) ((PickerSongAdapter) adapter).notifyDataSetChanged();
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) refreshPickerAdapters(group.getChildAt(i));
        }
    }

    private LinkedHashSet<String> songKeySet(List<Song> songs) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (songs != null) for (Song song : songs) if (song != null) keys.add(song.key());
        return keys;
    }

    private boolean allSelectableSelected(List<Song> source, LinkedHashSet<String> targetKeys,
                                          LinkedHashMap<String, Song> selected) {
        int selectable = 0;
        if (source != null) for (Song song : source) {
            if (song == null || targetKeys.contains(song.key())) continue;
            selectable++;
            if (!selected.containsKey(song.key())) return false;
        }
        return selectable > 0;
    }

    private View buildOtherPlaylistPicker(ImportedPlaylist target,
                                          LinkedHashMap<String, Song> selected, SimpleAction updateFooter) {
        FrameLayout host = new FrameLayout(this);
        Runnable[] showPlaylistList = new Runnable[1];
        PlaylistAction[] showSongs = new PlaylistAction[1];

        showSongs[0] = playlist -> {
            if (playlist == null) return;
            LinearLayout pane = Ui.column(this);
            LinearLayout header = Ui.row(this); header.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout back = Ui.iconButton(this, IconView.Type.BACK, 38, Ui.CYAN, Color.argb(24,123,211,255));
            back.setContentDescription("返回歌单列表");
            header.addView(back, Ui.lp(Ui.dp(this,38),Ui.dp(this,38)));
            LinearLayout texts = Ui.column(this); texts.setPadding(Ui.dp(this,10),0,0,0);
            TextView title = Ui.text(this, playlist.name, 13.2f, Ui.TEXT, true); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title,new LinearLayout.LayoutParams(-1,0,1f));
            texts.addView(Ui.text(this,playlist.songs.size()+" 首 · 选择要加入的歌曲",10.3f,Ui.DIM,false),new LinearLayout.LayoutParams(-1,0,1f));
            header.addView(texts,new LinearLayout.LayoutParams(0,Ui.dp(this,40),1f));
            LinkedHashSet<String> targetKeys = songKeySet(target.songs);
            int selectableCount = 0;
            for (Song candidate : playlist.songs) {
                if (candidate != null && !targetKeys.contains(candidate.key())) selectableCount++;
            }
            TextView selectAll = Ui.text(this, "全选", 10.8f, Ui.CYAN, true);
            selectAll.setGravity(Gravity.CENTER);
            selectAll.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
            selectAll.setClickable(selectableCount > 0); selectAll.setFocusable(selectableCount > 0);
            selectAll.setAlpha(selectableCount > 0 ? 1f : .42f);
            Ui.applyRipple(selectAll, Color.argb(34,255,255,255));
            header.addView(selectAll, Ui.lp(-2,Ui.dp(this,36)));
            pane.addView(header,Ui.lp(-1,Ui.dp(this,40)));

            PickerSongAdapter[] adapterRef = new PickerSongAdapter[1];
            SimpleAction refreshSelectionUi = () -> {
                updateFooter.run();
                boolean all = allSelectableSelected(playlist.songs, targetKeys, selected);
                selectAll.setText(all ? "取消全选" : "全选");
                selectAll.setTextColor(all ? Ui.GREEN : Ui.CYAN);
                selectAll.setBackground(Ui.tintedGlass(all ? Ui.GREEN : Ui.CYAN, 14, this));
            };
            selectAll.setOnClickListener(v -> {
                boolean clear = allSelectableSelected(playlist.songs, targetKeys, selected);
                if (clear) {
                    for (Song candidate : playlist.songs) {
                        if (candidate != null && !targetKeys.contains(candidate.key())) selected.remove(candidate.key());
                    }
                } else {
                    for (Song candidate : playlist.songs) {
                        if (candidate != null && !targetKeys.contains(candidate.key())) selected.put(candidate.key(), candidate);
                    }
                }
                refreshSelectionUi.run();
                if (adapterRef[0] != null) adapterRef[0].notifyDataSetChanged();
            });
            LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,0,1f); slp.topMargin=Ui.dp(this,8);
            pane.addView(buildPickerSongList(playlist.songs,target,selected,refreshSelectionUi,adapterRef),slp);
            refreshSelectionUi.run();
            back.setOnClickListener(v->showPlaylistList[0].run());
            host.removeAllViews(); host.addView(pane,Ui.frame(-1,-1,Gravity.FILL));
        };

        showPlaylistList[0] = () -> {
            LinearLayout rows = Ui.column(this);
            TextView intro = Ui.text(this,"先选择一个歌单，再从里面多选歌曲",10.8f,Ui.DIM,false);
            rows.addView(intro,Ui.lp(-1,Ui.dp(this,34)));
            int count=0;
            for(ImportedPlaylist p:playlists){
                if(p==null||p.id.equals(target.id)) continue;
                count++;
                LinearLayout row=Ui.row(this); row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(this,8),Ui.dp(this,7),Ui.dp(this,8),Ui.dp(this,7));
                row.setBackground(Ui.glass(88,15,16,this));
                row.addView(playlistThumb(p,42),Ui.lp(Ui.dp(this,42),Ui.dp(this,42)));
                LinearLayout texts=Ui.column(this); texts.setPadding(Ui.dp(this,10),0,0,0);
                TextView title=Ui.text(this,p.name,12.7f,Ui.TEXT,true); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
                texts.addView(title,new LinearLayout.LayoutParams(-1,0,1f));
                texts.addView(Ui.text(this,p.songs.size()+" 首",10.2f,Ui.DIM,false),new LinearLayout.LayoutParams(-1,0,1f));
                row.addView(texts,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1f));
                IconView arrow=new IconView(this,IconView.Type.BACK,Ui.DIM); arrow.setRotation(180f); row.addView(arrow,Ui.lp(Ui.dp(this,30),Ui.dp(this,30)));
                row.setClickable(true); row.setFocusable(true); Ui.applyRipple(row,Color.TRANSPARENT);
                row.setOnClickListener(v->showSongs[0].run(p));
                rows.addView(row,marginTop(count==1?6:6));
            }
            if(count==0) rows.addView(compactEmpty("没有其它歌单"),marginTop(10));
            ScrollView scroll=new ScrollView(this); scroll.setVerticalScrollBarEnabled(false); scroll.addView(rows);
            host.removeAllViews(); host.addView(scroll,Ui.frame(-1,-1,Gravity.FILL));
        };
        showPlaylistList[0].run();
        return host;
    }

    private View buildPickerSearch(ImportedPlaylist target, LinkedHashMap<String, Song> selected, SimpleAction updateFooter) {
        LinearLayout pane = Ui.column(this);
        LinearLayout row = Ui.row(this);
        EditText input = new EditText(this);
        input.setTypeface(Typeface.DEFAULT);
        input.setHint("搜索歌曲、歌手"); input.setHintTextColor(Ui.DIM); input.setTextColor(Ui.TEXT); input.setTextSize(13f);
        input.setSingleLine(true); input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setBackground(Ui.glass(156,17,22,this));
        input.setPadding(Ui.dp(this, 13), 0, Ui.dp(this, 13), 0);
        row.addView(input, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f));
        TextView go = Ui.text(this, "搜索", 11.5f, Color.rgb(10, 11, 18), true); go.setGravity(Gravity.CENTER);
        go.setBackground(Ui.primaryFill(Ui.CYAN,17,this)); go.setClickable(true); Ui.applyRipple(go, Color.argb(40,0,0,0));
        LinearLayout.LayoutParams gp = Ui.lp(Ui.dp(this, 70), Ui.dp(this, 46)); gp.leftMargin = Ui.dp(this, 8); row.addView(go, gp);
        pane.addView(row, Ui.lp(-1, Ui.dp(this, 46)));

        FrameLayout resultHost = new FrameLayout(this);
        LinearLayout.LayoutParams rh = new LinearLayout.LayoutParams(-1, 0, 1f); rh.topMargin = Ui.dp(this, 8); pane.addView(resultHost, rh);
        TextView intro = Ui.text(this, "搜索结果可以多选后一次加入歌单", 11.2f, Ui.DIM, false); intro.setGravity(Gravity.CENTER);
        resultHost.addView(intro, Ui.frame(-1, -1, Gravity.FILL));

        Runnable search = () -> {
            String keyword = input.getText().toString().trim();
            if (keyword.isEmpty()) { toast("输入歌曲或歌手名称"); return; }
            TextView loading = Ui.text(this, "正在搜索…", 11.5f, Ui.TEXT_2, true); loading.setGravity(Gravity.CENTER);
            resultHost.removeAllViews(); resultHost.addView(loading, Ui.frame(-1, -1, Gravity.FILL));
            io.submit(() -> {
                // Keep the picker consistent with the main V3 search without serially waiting for
                // four remote catalogs. The enclosing worker + four futures fit inside the 6-thread pool.
                Future<List<Song>> wyFuture = io.submit(() -> safeCatalogSearch(() -> netease.search(keyword, 20)));
                Future<List<Song>> txFuture = io.submit(() -> safeCatalogSearch(() -> qq.search(keyword, 20)));
                Future<List<Song>> kwFuture = io.submit(() -> safeCatalogSearch(() -> kuwo.search(keyword, 20)));
                Future<List<Song>> kgFuture = io.submit(() -> safeCatalogSearch(() -> kugou.search(keyword, 20)));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
                Map<String,List<Song>> catalogs = new LinkedHashMap<>();
                catalogs.put("wy", awaitSongs(wyFuture, deadline));
                catalogs.put("tx", awaitSongs(txFuture, deadline));
                catalogs.put("kw", awaitSongs(kwFuture, deadline));
                catalogs.put("kg", awaitSongs(kgFuture, deadline));
                List<Song> merged = SearchRanker.mergeAndRank(keyword, catalogs, 48);
                if (trackVariantStore != null) for (Song song : merged) trackVariantStore.remember(song);
                main.post(() -> {
                    if (modalOverlay.getVisibility() != View.VISIBLE) return;
                    resultHost.removeAllViews();
                    resultHost.addView(buildPickerSongList(merged, target, selected, updateFooter), Ui.frame(-1, -1, Gravity.FILL));
                });
            });
        };
        go.setOnClickListener(v -> search.run());
        input.setOnEditorActionListener((v, id, e) -> { if (id == EditorInfo.IME_ACTION_SEARCH) { search.run(); return true; } return false; });
        return pane;
    }

    private interface SongSearchCall { List<Song> run() throws Exception; }

    private static List<Song> safeCatalogSearch(SongSearchCall call) {
        try {
            List<Song> songs = call == null ? null : call.run();
            return songs == null ? new ArrayList<>() : songs;
        } catch (Exception ignored) { return new ArrayList<>(); }
    }

    private static List<Song> awaitSongs(Future<List<Song>> future, long deadlineNanos) {
        if (future == null) return new ArrayList<>();
        try { return future.get(Math.max(1L, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS); }
        catch (Exception e) { future.cancel(true); return new ArrayList<>(); }
    }

    private View pickerSongRow(Song song, ImportedPlaylist target, LinkedHashMap<String, Song> selected, SimpleAction updateFooter) {
        boolean exists = indexByKey(target.songs, song.key()) >= 0;
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        row.setBackground(Ui.glass(88,15,16,this));
        ImageView cover = new ImageView(this); cover.setScaleType(ImageView.ScaleType.CENTER_CROP); cover.setClipToOutline(true);
        cover.setBackground(Ui.round(Color.rgb(24,24,31), 11, this)); ImageLoader.load(song.coverUrl, cover, null);
        row.addView(cover, Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)));
        LinearLayout texts = Ui.column(this); texts.setPadding(Ui.dp(this,10),0,Ui.dp(this,6),0);
        TextView title = Ui.text(this, song.title, 12.7f, exists ? Ui.DIM : Ui.TEXT, true); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
        TextView artist = Ui.text(this, exists ? "已在歌单 · " + song.artist : song.artist, 10.2f, Ui.DIM, false); artist.setSingleLine(true); artist.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title,new LinearLayout.LayoutParams(-1,0,1f)); texts.addView(artist,new LinearLayout.LayoutParams(-1,0,1f));
        row.addView(texts,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1f));
        CheckBox check = new CheckBox(this); check.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{Ui.GREEN, Ui.DIM}));
        check.setChecked(selected.containsKey(song.key())); check.setEnabled(!exists);
        row.addView(check, Ui.lp(Ui.dp(this,42),Ui.dp(this,42)));
        SimpleAction toggle = () -> {
            if (exists) return;
            if (selected.containsKey(song.key())) selected.remove(song.key()); else selected.put(song.key(), song);
            check.setChecked(selected.containsKey(song.key())); updateFooter.run();
        };
        row.setClickable(!exists); row.setFocusable(!exists); row.setAlpha(exists ? .52f : 1f); Ui.applyRipple(row,Color.TRANSPARENT);
        row.setOnClickListener(v -> toggle.run()); check.setOnClickListener(v -> { if (check.isChecked()) selected.put(song.key(),song); else selected.remove(song.key()); updateFooter.run(); });
        return row;
    }

    private String subtitleFor(Song song) {
        if (song.album.isEmpty()) return song.artist;
        return song.artist + "  ·  " + song.album;
    }

    private View playlistCard(ImportedPlaylist p) {
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
        row.setBackground(Ui.glass(128, 17, 18, this));
        row.setClickable(true);
        Ui.applyRipple(row, Color.argb(32, 255, 255, 255));

        View thumb = playlistThumb(p, 52);
        row.addView(thumb, Ui.lp(Ui.dp(this, 52), Ui.dp(this, 52)));

        LinearLayout texts = Ui.column(this);
        texts.setPadding(Ui.dp(this, 12), 0, 0, 0);
        TextView title = Ui.text(this, p.name, 14.2f, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title, new LinearLayout.LayoutParams(-1, 0, 1f));
        String kind = p.source.equals("local") ? "本地歌单" : (p.source.equals("tx") ? "QQ 音乐" : (p.source.equals("kw") ? "酷我音乐" : (p.source.equals("kg") ? "酷狗音乐" : "网易云")));
        TextView meta = Ui.text(this, p.songs.size() + " 首 · " + kind, 11.3f, Ui.DIM, false);
        texts.addView(meta, new LinearLayout.LayoutParams(-1, 0, 1f));
        row.addView(texts, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1f));

        FrameLayout more = Ui.iconButton(this, IconView.Type.MORE, 34, Ui.DIM, Color.TRANSPARENT);
        more.setContentDescription("歌单更多操作");
        more.setOnClickListener(v -> showPlaylistOptions(p));
        row.addView(more, Ui.lp(Ui.dp(this, 34), Ui.dp(this, 34)));
        more.postDelayed(() -> { if (tab == 2) maybeShowContextCoach(COACH_PLAYLIST_EXPORT, more,
                "歌单可以直接带走", "这里可以导出 Lunaxy 歌单文件；之后在“导入歌单 → Lunaxy 文件”中即可把歌曲顺序带到其他 Lunaxy 版本。"); }, 320L);

        IconView arrow = new IconView(this, IconView.Type.BACK, Ui.DIM);
        arrow.setRotation(180f);
        row.addView(arrow, Ui.lp(Ui.dp(this, 30), Ui.dp(this, 30)));

        // When the library root is rebuilt for a reverse transition, remember the actual target
        // geometry if this is the playlist that owns the active shared-object identity.
        if (playlistHeroSnapshot != null && playlistHeroSnapshot.playlistId.equals(p.id)) {
            playlistHeroReturnRow = row;
            playlistHeroReturnThumb = thumb;
            playlistHeroReturnTitle = title;
            playlistHeroReturnMeta = meta;
        }

        row.setOnClickListener(v -> openPlaylistWithSharedHero(p, row, thumb, title, meta));
        return row;
    }

    /**
     * Staircase loading follows the artwork that is actually playing. If the current cover has not
     * resolved yet, keep the page-specific fallback instead of leaking the previous track's color.
     */
    private int progressiveLoadingAccent(int fallback) {
        Song current = playback == null ? null : playback.currentSong();
        if (current == null) return fallback;
        String key = current.key() + "|" + current.coverUrl;
        if (key.equals(playlistPlaybackAccentKey)) return playlistPlaybackAccent;
        Bitmap cached = ImageLoader.peek(current.coverUrl);
        return cached == null || cached.isRecycled() ? fallback : ImageLoader.accent(cached);
    }

    private void retintProgressiveLoadingRows(View root, int accent) {
        if (root == null) return;
        if (root instanceof CurtainRevealFrame) ((CurtainRevealFrame) root).setAccent(accent);
        if (root instanceof FluidPlaceholderView) {
            FluidPlaceholderView placeholder = (FluidPlaceholderView) root;
            if (placeholder.isSongRow()) placeholder.setAccent(accent);
            return;
        }
        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) retintProgressiveLoadingRows(group.getChildAt(i), accent);
    }

    private int playlistArtworkAccent(ImportedPlaylist p) {
        int fallback = playlistAccent(p);
        String artwork = playlistArtworkUrl(p);
        Bitmap cached = ImageLoader.peek(artwork);
        return cached == null || cached.isRecycled() ? fallback : ImageLoader.accent(cached);
    }

    private void applyPlaylistHeroArtworkAccent(String playlistId, View hero, int color) {
        if (playlistId == null || hero == null || activePlaylistPageModel == null
                || !playlistId.equals(activePlaylistPageModel.id) || activePlaylistHeroCard != hero) return;
        if (playlistHeroSnapshot != null && playlistId.equals(playlistHeroSnapshot.playlistId))
            playlistHeroSnapshot.accent = color;
        if (activePlaylistHeroMorph != null) activePlaylistHeroMorph.setAccent(color);
        if (activePlaylistHeroAccent == color) return;
        if (activePlaylistHeroAccentAnimator != null) activePlaylistHeroAccentAnimator.cancel();
        final int from = activePlaylistHeroAccent;
        activePlaylistHeroAccentAnimator = ValueAnimator.ofFloat(0f, 1f);
        activePlaylistHeroAccentAnimator.setDuration(SpringMotion.isReducedMotion() ? 1L : 260L);
        activePlaylistHeroAccentAnimator.setInterpolator(SpringMotion.TAB_SELECTION);
        activePlaylistHeroAccentAnimator.addUpdateListener(a -> {
            if (activePlaylistHeroCard != hero || activePlaylistPageModel == null
                    || !playlistId.equals(activePlaylistPageModel.id)) return;
            int mixed = Ui.mix(from, color, (Float) a.getAnimatedValue());
            activePlaylistHeroAccent = mixed;
            hero.setBackground(Ui.tintedGlass(mixed, 23, this));
        });
        activePlaylistHeroAccentAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (!cancelled && activePlaylistHeroAccentAnimator == animation) {
                    activePlaylistHeroAccent = color;
                    activePlaylistHeroAccentAnimator = null;
                }
            }
        });
        activePlaylistHeroAccentAnimator.start();
    }

    private int playlistAccent(ImportedPlaylist p) {
        if (p == null) return Ui.CYAN;
        if ("tx".equals(p.source)) return Ui.CYAN;
        if ("kw".equals(p.source)) return Ui.GREEN;
        if ("kg".equals(p.source)) return Ui.GOLD;
        if ("local".equals(p.source)) return Ui.PURPLE;
        return Ui.PINK;
    }

    private String playlistKind(ImportedPlaylist p) {
        if (p == null) return "歌单";
        if ("local".equals(p.source)) return "本地歌单";
        if ("tx".equals(p.source)) return "QQ 音乐";
        if ("kw".equals(p.source)) return "酷我音乐";
        if ("kg".equals(p.source)) return "酷狗音乐";
        return "网易云";
    }

    private String playlistArtworkUrl(ImportedPlaylist p) {
        if (p == null || p.songs == null || p.songs.isEmpty()) return "";
        String url = p.songs.get(0).coverUrl;
        return url == null ? "" : url;
    }

    private RectF rectInAppRoot(View view) {
        if (view == null || appRoot == null || view.getWidth() <= 0 || view.getHeight() <= 0) return new RectF();
        int[] root = new int[2];
        int[] child = new int[2];
        appRoot.getLocationOnScreen(root);
        view.getLocationOnScreen(child);
        return new RectF(child[0] - root[0], child[1] - root[1],
                child[0] - root[0] + view.getWidth(), child[1] - root[1] + view.getHeight());
    }

    private PlaylistHeroSnapshot capturePlaylistHeroSnapshot(ImportedPlaylist p, View row, View thumb,
                                                              TextView title, TextView meta) {
        if (p == null || row == null || thumb == null || title == null || meta == null || appRoot == null) return null;
        RectF container = rectInAppRoot(row);
        RectF artwork = rectInAppRoot(thumb);
        RectF titleRect = rectInAppRoot(title);
        RectF metaRect = rectInAppRoot(meta);
        if (container.width() < 2f || artwork.width() < 2f || titleRect.width() < 2f) return null;
        return new PlaylistHeroSnapshot(p.id, p.name, p.songs.size() + " 首 · " + playlistKind(p),
                playlistArtworkUrl(p), container, artwork, titleRect, metaRect,
                title.getTextSize(), meta.getTextSize(), playlistArtworkAccent(p));
    }

    private void openPlaylistWithSharedHero(ImportedPlaylist p, View row, View thumb, TextView title, TextView meta) {
        if (p == null) return;
        final int sourceTab = tab;
        // Save the exact library scroll position before changing route ownership. It gives the
        // reverse transition a stable destination even after an 800-song detail page has been used.
        rememberRootTabContext();
        // The shared-object contract is used when a playlist card and its detail belong to the same
        // library space. Home shortcuts still use the already-established root-tab physics because
        // mixing a tab-space jump and a container morph would give two competing spatial stories.
        playlistHeroSnapshot = (!SpringMotion.isReducedMotion() && sourceTab == 2)
                ? capturePlaylistHeroSnapshot(p, row, thumb, title, meta) : null;
        playlistHeroSourceRow = playlistHeroSnapshot == null ? null : row;
        playlistHeroSourceThumb = playlistHeroSnapshot == null ? null : thumb;
        pendingPlaylistHeroPush = playlistHeroSnapshot != null;
        pendingPlaylistHeroPop = false;
        tab = 2;
        openPlaylist = p;
        playlistVisibleCount = PLAYLIST_INITIAL_RENDER_COUNT;
        refreshNav();
        renderTab();
    }


    private void exportLunaxyPlaylist(ImportedPlaylist playlist) {
        if (playlist == null) return;
        pendingPlaylistExport = playlist;
        try {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, LunaxyPlaylistCodec.safeFileName(playlist.name));
            startActivityForResult(i, REQ_EXPORT_PLAYLIST);
        } catch (Exception e) {
            pendingPlaylistExport = null;
            toast("无法打开文件保存器");
        }
    }

    private void openLunaxyPlaylistFile() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_IMPORT_LUNAXY_PLAYLIST);
        } catch (Exception e) { toast("无法打开文件选择器"); }
    }

    private void writePlaylistBundle(Uri uri, ImportedPlaylist playlist) {
        if (uri == null || playlist == null) return;
        showBusy(true);
        io.submit(() -> {
            String error = "";
            try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) throw new IllegalStateException("无法写入文件");
                out.write(LunaxyPlaylistCodec.encode(playlist).getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (Exception e) { error = compactError(e); }
            String finalError = error;
            main.post(() -> {
                showBusy(false);
                if (finalError.isEmpty()) toast("已导出《" + playlist.name + "》");
                else toast("导出失败：" + finalError);
            });
        });
    }

    private void readPlaylistBundle(Uri uri) {
        if (uri == null) return;
        showBusy(true);
        io.submit(() -> {
            ImportedPlaylist decoded = null; String error = "";
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("无法读取文件");
                java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[32768]; int n; int total = 0;
                while ((n = in.read(chunk)) > 0) {
                    total += n; if (total > 8 * 1024 * 1024) throw new IllegalArgumentException("歌单文件过大");
                    buf.write(chunk, 0, n);
                }
                decoded = LunaxyPlaylistCodec.decode(buf.toString(StandardCharsets.UTF_8.name()));
            } catch (Exception e) { error = compactError(e); }
            ImportedPlaylist result = decoded; String finalError = error;
            main.post(() -> {
                showBusy(false);
                if (result == null) { toast("导入失败：" + (finalError.isEmpty() ? "无法识别歌单文件" : finalError)); return; }
                playlists = store.savePlaylist(result);
                renderTab();
                toast("已导入《" + result.name + "》 · " + result.songs.size() + " 首");
            });
        });
    }

    private void showPlaylistOptions(ImportedPlaylist playlist) {
        if (playlist == null) return;
        showGlassOptions(playlist.name, "歌单操作", new String[]{"下载全部", "导出 Lunaxy 歌单", "重命名", "删除"}, which -> {
            if (which == 0) {
                ArrayList<Song> planSongs = new ArrayList<>();
                if (playlist.songs != null) for (Song song : playlist.songs) if (song != null) planSongs.add(song);
                if (planSongs.isEmpty()) { toast("这个歌单还没有歌曲"); return; }
                OfflineDownloadService.enqueueAll(this, playlist.name, planSongs);
                toast("已创建《" + playlist.name + "》下载任务 · " + planSongs.size() + " 首 · 可在离线页查看进度");
            } else if (which == 1) {
                exportLunaxyPlaylist(playlist);
            } else if (which == 2) {
                showGlassInput("重命名歌单", playlist.name, false, "保存", value -> {
                    String clean = value == null ? "" : value.trim();
                    if (clean.isEmpty()) { toast("歌单名称不能为空"); return; }
                    playlists = store.renamePlaylist(playlist.id, clean);
                    if (openPlaylist != null && playlist.id.equals(openPlaylist.id)) openPlaylist = playlistById(playlist.id);
                    renderTab();
                    toast("已重命名为《" + clean + "》");
                });
            } else if (which == 3) {
                showGlassMessage("删除歌单", "确定从本机删除《" + playlist.name + "》吗？音乐平台上的原歌单不会受到影响。", "删除", () -> {
                    playlists = store.removePlaylist(playlist.id);
                    if (openPlaylist != null && playlist.id.equals(openPlaylist.id)) openPlaylist = null;
                    renderTab();
                    toast("已删除《" + playlist.name + "》");
                }, "取消", null);
            }
        });
    }

    private View playlistThumb(ImportedPlaylist p, int sizeDp) {
        return playlistThumb(p, sizeDp, null);
    }

    private View playlistThumb(ImportedPlaylist p, int sizeDp, ImageLoader.Callback callback) {
        if (p != null && p.songs != null && !p.songs.isEmpty() && !p.songs.get(0).coverUrl.isEmpty()) {
            ImageView cover = new ImageView(this);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setClipToOutline(true);
            cover.setBackground(Ui.round(Color.rgb(22, 22, 28), Math.max(12, sizeDp * .25f), this));
            ImageLoader.load(p.songs.get(0).coverUrl, cover, callback);
            return cover;
        }
        FrameLayout shell = new FrameLayout(this);
        int tint = p != null && "tx".equals(p.source) ? Ui.CYAN : (p != null && "kw".equals(p.source) ? Ui.GREEN : (p != null && "kg".equals(p.source) ? Ui.GOLD : Ui.PURPLE));
        shell.setBackground(Ui.tintedGlass(tint, Math.max(12, sizeDp * .25f), this));
        IconView icon = new IconView(this, IconView.Type.PLAYLIST, tint);
        shell.addView(icon, Ui.frame(Ui.dp(this, Math.max(24, (int)(sizeDp * .5f))), Ui.dp(this, Math.max(24, (int)(sizeDp * .5f))), Gravity.CENTER));
        return shell;
    }

    private View statCard(IconView.Type iconType, String value, String label) {
        LinearLayout card = Ui.column(this);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.glass(120, 18, 18, this));
        IconView icon = new IconView(this, iconType, Ui.PURPLE);
        card.addView(icon, Ui.lp(Ui.dp(this, 25), Ui.dp(this, 25)));
        TextView number = Ui.text(this, value, 17, Ui.TEXT, true);
        number.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = Ui.lp(-1, Ui.dp(this, 27)); np.topMargin = Ui.dp(this, 4);
        card.addView(number, np);
        TextView name = Ui.text(this, label, 10.5f, Ui.DIM, false);
        name.setGravity(Gravity.CENTER);
        card.addView(name, Ui.lp(-1, Ui.dp(this, 22)));
        return card;
    }

    private View sortableSongsHeader(String title, int count, Runnable onSort) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView t = Ui.text(this, title, 16.5f, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(t, Ui.lp(-2, Ui.dp(this, 42)));
        TextView amount = Ui.text(this, count + " 首", 10.8f, Ui.DIM, false);
        amount.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ap = Ui.lp(-2, Ui.dp(this, 42));
        ap.leftMargin = Ui.dp(this, 10);
        row.addView(amount, ap);

        Space fill = new Space(this);
        row.addView(fill, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1f));

        FrameLayout sort = Ui.iconButton(this, IconView.Type.DRAG, 42, Ui.TEXT_2,
                Color.argb(20, 255, 255, 255));
        sort.setContentDescription("手动排序歌曲");
        sort.setOnClickListener(v -> { if (onSort != null) onSort.run(); });
        row.addView(sort, Ui.lp(Ui.dp(this, 42), Ui.dp(this, 42)));
        return row;
    }

    private View sectionHeader(String title, String hint) {
        LinearLayout row = Ui.row(this);
        TextView t = Ui.text(this, title, 16.5f, Ui.TEXT, true);
        row.addView(t, new LinearLayout.LayoutParams(0, Ui.dp(this, 34), 1f));
        if (hint != null && !hint.isEmpty()) {
            TextView h = Ui.text(this, hint, 10.8f, Ui.DIM, false);
            h.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            row.addView(h, Ui.lp(-2, Ui.dp(this, 34)));
        }
        return row;
    }

    private View sectionHeaderAction(String title, String action, Runnable onClick) {
        LinearLayout row = Ui.row(this);
        TextView t = Ui.text(this, title, 16.5f, Ui.TEXT, true);
        row.addView(t, new LinearLayout.LayoutParams(0, Ui.dp(this, 36), 1f));
        TextView button = Ui.text(this, action + "  ›", 11.2f, Ui.CYAN, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 10), 0);
        button.setBackground(Ui.tintedGlass(Ui.CYAN, 14, this));
        button.setClickable(true);
        Ui.applyRipple(button, Color.argb(34, 255, 255, 255));
        button.setOnClickListener(v -> { if (onClick != null) onClick.run(); });
        row.addView(button, Ui.lp(-2, Ui.dp(this, 32)));
        return row;
    }

    private View compactEmpty(String message) {
        LinearLayout card = Ui.row(this);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        card.setBackground(Ui.glass(92, 17, 16, this));
        IconView icon = new IconView(this, IconView.Type.MUSIC, Ui.DIM);
        card.addView(icon, Ui.lp(Ui.dp(this, 28), Ui.dp(this, 28)));
        TextView text = Ui.text(this, message, 12, Ui.TEXT_2, false);
        text.setPadding(Ui.dp(this, 10), 0, 0, 0);
        card.addView(text, new LinearLayout.LayoutParams(0, Ui.dp(this, 38), 1f));
        return card;
    }

    private TextView filledAction(String text, int background, int foreground) {
        TextView b = Ui.text(this, text, 13.2f, foreground, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.primaryFill(background, 17, this));
        b.setClickable(true);
        Ui.applyRipple(b, Color.argb(52, 255, 255, 255));
        return b;
    }

    private TextView outlineAction(String text) {
        TextView b = Ui.text(this, text, 13, Ui.TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.glass(68, 16, 22, this));
        b.setClickable(true);
        Ui.applyRipple(b, Color.argb(42, 255, 255, 255));
        return b;
    }

    private LinearLayout.LayoutParams marginTop(int top) {
        LinearLayout.LayoutParams p = Ui.lp(-1, -2);
        p.topMargin = Ui.dp(this, top);
        return p;
    }

    private void animateHeartBurst(View anchor, int color) {
        if (anchor == null || appRoot == null || !anchor.isShown()) return;
        int[] root = new int[2], loc = new int[2];
        appRoot.getLocationOnScreen(root); anchor.getLocationOnScreen(loc);
        float cx = loc[0] - root[0] + anchor.getWidth() * .5f;
        float cy = loc[1] - root[1] + anchor.getHeight() * .5f;
        final int count = 6;
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            int size = Ui.dp(this, i % 2 == 0 ? 4f : 3f);
            dot.setBackground(Ui.round(Color.argb(185, Color.red(color), Color.green(color), Color.blue(color)), 8, this));
            FrameLayout.LayoutParams lp = Ui.frame(size, size, Gravity.TOP | Gravity.START);
            appRoot.addView(dot, lp);
            dot.setX(cx - size * .5f); dot.setY(cy - size * .5f);
            double angle = Math.PI * 2d * i / count - Math.PI / 2d;
            float radius = Ui.dp(this, 16f + (i % 3) * 3f);
            dot.setScaleX(.55f); dot.setScaleY(.55f); dot.setAlpha(.95f);
            dot.animate().translationX((float)Math.cos(angle) * radius)
                    .translationY((float)Math.sin(angle) * radius)
                    .scaleX(1.05f).scaleY(1.05f).alpha(0f)
                    .setDuration(330L).setInterpolator(SpringMotion.SOFT)
                    .withEndAction(() -> { if (dot.getParent() instanceof ViewGroup) ((ViewGroup)dot.getParent()).removeView(dot); })
                    .start();
        }
    }

    private boolean isFavorite(Song s) {
        for (Song x : favorites) if (x.key().equals(s.key())) return true;
        return false;
    }

    private void ensurePlaybackServiceStarted() {
        try {
            Intent i = new Intent(this, PlaybackService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Exception ignored) { }
    }

    private void playPickedSong(Song song) {
        if (song == null) return;
        ensurePlaybackServiceStarted();
        if (playback != null) playback.insertAndPlay(song);
        else pendingPickedSong = song;
    }

    private void playQueue(List<Song> songs, int index) {
        if (songs == null || songs.isEmpty()) return;
        ensurePlaybackServiceStarted();
        if (playback != null) playback.playQueue(new ArrayList<>(songs), Math.max(0, Math.min(index, songs.size() - 1)));
        else {
            pendingQueue = new ArrayList<>(songs);
            pendingIndex = index;
        }
    }

    @android.annotation.SuppressLint("UnsafeOptInUsageError")
    @Override public void onPlaybackChanged(PlaybackSnapshot snapshot) {
        runOnUiThread(() -> {
            if (playbackBehaviorTracker != null) playbackBehaviorTracker.onSnapshot(snapshot);
            syncPlaybackHighlightState();
            QueueAdapter queueAdapter = visibleQueueAdapter.get();
            if (queueAdapter != null && playback != null && !reorderGestureActive)
                queueAdapter.refresh(playback.queueSnapshot(), playback.currentQueueIndex());
            if (snapshot.song == null) {
                miniBar.setVisibility(View.GONE);
                if (playlistLocateButton != null) playlistLocateButton.setVisibility(View.GONE);
                refreshActivePlaylistHighlights();
                if (playerOverlay != null) closeNowPlaying();
                return;
            }
            String visualRoute = snapshot.song.source + "|" + (playback != null && playback.isUsingFallback());
            if (!visualRoute.equals(lastVisualPlaybackRoute)) {
                lastVisualPlaybackRoute = visualRoute;
                lastVisualRoutePulseAt = SystemClock.uptimeMillis();
                if (activeSourceFlowView != null && activeSourceFlowView.isShown()) {
                    activeSourceFlowView.setRouteLabel((playback != null && playback.isUsingFallback() ? "回退 · " : "") + Song.providerLabel(snapshot.song.source));
                    activeSourceFlowView.pulse();
                }
            }
            boolean playerPageOpen = playerOverlay != null && playerOverlay.getVisibility() == View.VISIBLE;
            miniBar.setVisibility(playerPageOpen ? View.GONE : View.VISIBLE);
            miniTitle.setText(snapshot.song.title);
            miniArtist.setText(snapshot.song.artist);
            miniPlayIcon.setPlaybackState(snapshot.playing, true);
            refreshActivePlaylistHighlights();
            updatePlaylistLocateButtonVisibility();

            int available = Math.max(1, miniBar.getWidth() - Ui.dp(this, 44));
            FrameLayout.LayoutParams pp = (FrameLayout.LayoutParams) miniProgress.getLayoutParams();
            pp.width = (int) (available * (snapshot.durationMs > 0 ? Math.min(1d, (double) snapshot.positionMs / snapshot.durationMs) : 0));
            miniProgress.setLayoutParams(pp);

            String artworkKey = snapshot.song.key() + "|" + snapshot.song.coverUrl;
            if (!artworkKey.equals(miniArtworkKey)) {
                miniArtworkKey = artworkKey;
                miniProgress.setBackground(Ui.round(Ui.PURPLE, 1.5f, this));
                ImageLoader.load(snapshot.song.coverUrl, miniCover, (bmp, color) -> {
                    stars.setAccentColor(color);
                    DesktopLyricService.setAccent(this, color);
                    if (!artworkKey.equals(miniArtworkKey)) return;
                    playlistPlaybackAccent = color;
                    playlistPlaybackAccentKey = artworkKey;
                    retintProgressiveLoadingRows(pageHost, color);
                    syncPlaybackHighlightState();
                    miniProgress.setBackground(Ui.round(color, 1.5f, this));
                    updatePlaylistLocateButtonAccent();
                    refreshActivePlaylistHighlights();
                    if (playback != null) playback.setArtwork(bmp);
                });
            }

            if (lastHistorySong == null || !lastHistorySong.key().equals(snapshot.song.key())) {
                lastHistorySong = snapshot.song;
                history = store.addHistory(snapshot.song);
                loadLyrics(snapshot.song);
                boolean modalOpen = modalOverlay != null && modalOverlay.getVisibility() == View.VISIBLE;
                boolean playerOpen = playerOverlay != null && playerOverlay.getVisibility() == View.VISIBLE;
                // Smart collections (最近播放 / 每日推荐 / 私人电台 / 天气电台) are
                // playlist-like detail pages. Playing a row must not rebuild the page or reset
                // its ScrollView to the top merely because history received the newly played song.
                if (tab == 0 && openSmartCollection == null && !modalOpen && !playerOpen && onboardingOverlay == null) renderTab();
            }
            if (snapshot.error == null || snapshot.error.trim().isEmpty()) {
                lastErrorShown = null;
            } else if (!snapshot.error.equals(lastErrorShown)) {
                lastErrorShown = snapshot.error;
                showPlaybackError(snapshot.error);
            }
            updateNowPlaying(snapshot);
        });
    }

    private void showPlaybackError(String error) {
        cancelPlaybackErrorAutoSkip();
        Song failedSong = playback == null ? null : playback.currentSong();
        final String failedKey = failedSong == null ? "" : failedSong.key();
        playbackErrorAutoSkipSongKey = failedKey;
        String message = (error == null ? "当前线路播放失败，已经停止重复请求。" : error)
                + (failedKey.isEmpty() ? "" : "\n\n3 秒后自动切到下一首");
        showGlassMessage("这首暂时没接通", message,
                "查看诊断", () -> {
                    String detail = playback == null ? "暂无诊断信息" : playback.diagnostics();
                    String httpObservation = ResolverHttpDiagnostics.text();
                    if (!httpObservation.isEmpty()) detail = detail + "\n\n" + httpObservation;
                    final String diagnosticText = detail;
                    showGlassMessage("播放诊断", diagnosticText, "关闭", null, "复制", () -> copyText(diagnosticText));
                }, "关闭", null);
        // PlaybackService owns failure recovery even without an attached Activity.
    }

    private void cancelPlaybackErrorAutoSkip() {
        if (playbackErrorAutoSkipRunnable != null) main.removeCallbacks(playbackErrorAutoSkipRunnable);
        playbackErrorAutoSkipRunnable = null;
        playbackErrorAutoSkipSongKey = "";
    }

    private void openNowPlaying() { openNowPlayingFrom(miniCover); }

    /**
     * Shared-artwork hero transition. The mini artwork itself grows into the full-player cover;
     * the full page fades in behind it, so there is never a "cover disappears -> cover appears" cut.
     */
    private void openNowPlayingFrom(View sourceArtwork) {
        if (playback == null || playback.currentSong() == null) return;
        configureWindow();
        if (playerOverlay == null) buildNowPlaying();
        cancelPlayerOpenMotion();
        final int openGeneration = ++playerOpenGeneration;
        playerOverlay.setVisibility(View.VISIBLE);
        updateNowPlaying(playback.snapshot());

        // V92.9.6: never scale the 50–60dp rendered Mini Player snapshot into a 300dp record
        // when the original artwork bitmap is already in ImageLoader's memory cache. The old
        // proxy became visibly soft during the flight, then snapped sharp at the final handoff.
        // Reusing the cached source bitmap keeps the same crop but preserves full-resolution pixels.
        Bitmap cachedArtwork = null;
        Song heroSong = playback == null ? null : playback.currentSong();
        if (heroSong != null) cachedArtwork = ImageLoader.peek(heroSong.coverUrl);
        final Bitmap artwork = cachedArtwork != null
                ? cachedArtwork
                : captureViewBitmap(sourceArtwork != null ? sourceArtwork : miniCover);
        playerOverlay.setVisibility(View.VISIBLE);
        playerOverlay.bringToFront();
        playerOverlay.setAlpha(0f);
        if (nowVinyl != null) nowVinyl.setAlpha(0f);
        if (nowVinylStack != null) nowVinylStack.setSidesReveal(0f);
        appRoot.post(() -> {
            if (openGeneration != playerOpenGeneration) { retireMotionBitmap(artwork); return; }
            if (nowVinyl == null || nowVinyl.getWidth() <= 0 || artwork == null) {
                completePlayerOpenWithoutHero();
                retireMotionBitmap(artwork);
                return;
            }
            nowVinyl.beginHeroTransition();
            if (nowVinylStack != null) nowVinylStack.setCenterRevealSuppressed(true);
            int[] root = new int[2], src = new int[2], dst = new int[2];
            appRoot.getLocationOnScreen(root);
            (sourceArtwork != null ? sourceArtwork : miniCover).getLocationOnScreen(src);
            nowVinyl.getLocationOnScreen(dst);
            int sw = Math.max(1, sourceArtwork != null ? sourceArtwork.getWidth() : miniCover.getWidth());
            int sh = Math.max(1, sourceArtwork != null ? sourceArtwork.getHeight() : miniCover.getHeight());
            float artDiameter = Math.max(1f, nowVinyl.artworkDiameterPx());
            float targetX = dst[0] - root[0] + (nowVinyl.getWidth() - artDiameter) * .5f;
            float targetY = dst[1] - root[1] + (nowVinyl.getHeight() - artDiameter) * .5f;
            // Size the Hero layer at its FINAL record resolution and scale it down to the Mini
            // geometry at p=0. A hardware layer whose layout itself is only ~56dp would be
            // rasterized small and then magnified by View.scale, which is exactly the blur seen
            // on-device. Final-size rasterization keeps every intermediate frame sharp.
            float startScaleX = sw / artDiameter;
            float startScaleY = sh / artDiameter;
            float startX = src[0] - root[0];
            float startY = src[1] - root[1];
            int morphSize = Math.max(1, Math.round(artDiameter));

            ArtworkMorphView morph = new ArtworkMorphView(this);
            activeArtworkMorph = morph;
            morph.setBitmap(artwork);
            float minStartScale = Math.max(.01f, Math.min(startScaleX, startScaleY));
            morph.setStartRadiusDp(12f / minStartScale);
            morph.setPivotX(0f); morph.setPivotY(0f);
            FrameLayout.LayoutParams mp = Ui.frame(morphSize, morphSize, Gravity.TOP | Gravity.START);
            appRoot.addView(morph, mp);
            morph.setX(startX);
            morph.setY(startY);
            morph.setScaleX(startScaleX);
            morph.setScaleY(startScaleY);
            morph.setElevation(Ui.dp(this, 28));
            morph.bringToFront();

            activePlayerHeroSource = sourceArtwork;
            if (sourceArtwork != null) sourceArtwork.setAlpha(0f);

            // V92.8: the whole player now has spatial continuity, not only the artwork. A dedicated
            // surface unfolds from the mini-player rectangle to the full viewport underneath the
            // real player UI. Keeping this as a sibling avoids squashing the actual controls and
            // keeps the artwork's destination geometry exact.
            int[] mini = new int[2];
            miniBar.getLocationOnScreen(mini);
            PlayerSurfaceMorphView surface = new PlayerSurfaceMorphView(this);
            activePlayerSurfaceMorph = surface;
            surface.setAccentColor(nowAccent);
            surface.setStartRadiusDp(20f);
            surface.setStartRect(mini[0] - root[0], mini[1] - root[1],
                    mini[0] - root[0] + miniBar.getWidth(), mini[1] - root[1] + miniBar.getHeight());
            surface.setEndRect(0f, 0f, appRoot.getWidth(), appRoot.getHeight());
            FrameLayout.LayoutParams sp = Ui.frame(-1, -1, Gravity.FILL);
            int overlayIndex = Math.max(0, appRoot.indexOfChild(playerOverlay));
            appRoot.addView(surface, overlayIndex, sp);

            playerOverlay.animate().cancel();
            if (pageHost != null) pageHost.animate().cancel();
            if (navHost != null) navHost.animate().cancel();
            miniBar.animate().cancel();

            // A single monotonic clock owns surface expansion, artwork geometry and all cross-fades.
            // This prevents the old "hero lands, page catches up one frame later" feeling.
            ValueAnimator hero = ValueAnimator.ofFloat(0f, 1f);
            activePlayerHeroAnimator = hero;
            hero.setDuration(playerOpenMorphDurationMs);
            hero.setInterpolator(SpringMotion.PLAYER_OPEN);
            hero.addUpdateListener(a -> {
                float p = (Float) a.getAnimatedValue();
                surface.setProgress(p);

                float artP = smoothstep01(Math.min(1f, p / .94f));
                morph.setX(startX + (targetX - startX) * artP);
                morph.setY(startY + (targetY - startY) * artP);
                morph.setScaleX(startScaleX + (1f - startScaleX) * artP);
                morph.setScaleY(startScaleY + (1f - startScaleY) * artP);
                morph.setMorphProgress(artP);

                // Let the expanding surface establish itself first; then the starfield and controls
                // resolve into place. This reads as the mini player becoming the full page instead of
                // a black page fading in behind a flying cover.
                float contentReveal = smoothstep01((p - .34f) / .56f);
                playerOverlay.setAlpha(contentReveal);
                // V92.8.3: the destination record must stay fully hidden while the Hero proxy
                // is travelling. Revealing it early makes the eye see two records: one already
                // parked at the destination and another still flying toward it. Keep ownership
                // exclusive to the proxy until onAnimationEnd performs a single-frame handoff.
                if (nowVinyl != null) nowVinyl.setAlpha(0f);
                if (nowVinylStack != null) nowVinylStack.setSidesReveal(smoothstep01((p - .58f) / .38f));
                morph.setAlpha(1f);

                float baseFade = smoothstep01((p - .08f) / .74f);
                if (pageHost != null) pageHost.setAlpha(1f - .28f * baseFade);
                if (navHost != null) navHost.setAlpha(1f - .54f * baseFade);
                if (stars != null) stars.setAlpha(1f - .46f * baseFade);
                float miniFade = smoothstep01((p - .10f) / .58f);
                miniBar.setAlpha(1f - .94f * miniFade);
                miniBar.setTranslationY(-Ui.dp(this, 12f) * miniFade);
                float miniScale = 1f - .012f * miniFade;
                miniBar.setScaleX(miniScale);
                miniBar.setScaleY(miniScale);
            });
            hero.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    if (openGeneration != playerOpenGeneration) return;
                    activePlayerHeroAnimator = null;
                    activePlayerHeroSource = null;
                    if (nowVinyl != null) {
                        nowVinyl.setScaleX(1f); nowVinyl.setScaleY(1f);
                        nowVinyl.setTranslationX(0f); nowVinyl.setTranslationY(0f);
                        nowVinyl.endHeroTransition();
                    }
                    // Single-frame visual handoff: reveal the real record only after the proxy
                    // has reached the exact destination; the proxy is removed immediately below.
                    if (nowVinylStack != null) {
                        nowVinylStack.setCenterRevealSuppressed(false);
                        nowVinylStack.setSidesReveal(1f);
                    } else if (nowVinyl != null) nowVinyl.setAlpha(1f);
                    if (sourceArtwork != null) sourceArtwork.setAlpha(1f);
                    // Keep the already-sharp Hero proxy for one final display frame after the real
                    // vinyl becomes visible. The two images are now sourced from the same cached
                    // bitmap, so this is a seamless ownership handoff rather than a cross-fade.
                    // It avoids a one-frame texture/upload gap on slower GPUs.
                    finalizePlayerOpen();
                    appRoot.postOnAnimation(() -> {
                        if (activeArtworkMorph == morph && morph.getParent() instanceof ViewGroup)
                            ((ViewGroup) morph.getParent()).removeView(morph);
                        morph.setBitmap(null);
                        if (activeArtworkMorph == morph) activeArtworkMorph = null;
                        if (activePlayerSurfaceMorph == surface && surface.getParent() instanceof ViewGroup)
                            ((ViewGroup) surface.getParent()).removeView(surface);
                        if (activePlayerSurfaceMorph == surface) activePlayerSurfaceMorph = null;
                        retireMotionBitmap(artwork);
                    });
                }
            });
            hero.start();
        });
    }

    private Bitmap captureViewBitmap(View view) {
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        try {
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);
            return bitmap;
        } catch (Throwable ignored) { return null; }
    }

    /**
     * Transition snapshots are deliberately left to normal GC. Explicit Bitmap.recycle() after a
     * hardware-layer animation can race RenderThread on some devices and is unnecessary on modern
     * Android; the overlay drops its strong reference as soon as the transition completes.
     */
    private void retireMotionBitmap(Bitmap bitmap) {
        // no-op by design
    }

    private void completePlayerOpenWithoutHero() {
        if (playerOverlay == null) return;
        playerOverlay.animate().alpha(1f).setDuration(190L).withEndAction(this::finalizePlayerOpen).start();
        if (nowVinyl != null) { nowVinyl.setAlpha(1f); nowVinyl.endHeroTransition(); }
        if (nowVinylStack != null) {
            nowVinylStack.setCenterRevealSuppressed(false);
            nowVinylStack.setSidesReveal(1f);
        }
    }

    private void finalizePlayerOpen() {
        if (pageHost != null) { pageHost.setAlpha(1f); pageHost.setVisibility(View.GONE); }
        if (navHost != null) { navHost.setAlpha(1f); navHost.setVisibility(View.GONE); }
        if (miniBar != null) {
            miniBar.setAlpha(1f); miniBar.setScaleX(1f); miniBar.setScaleY(1f);
            miniBar.setTranslationY(0f); miniBar.setVisibility(View.GONE);
        }
        if (playlistLocateButton != null) playlistLocateButton.setVisibility(View.GONE);
        if (stars != null) { stars.setAlpha(1f); stars.setVisibility(View.GONE); }
        if (playerOverlay != null) { playerOverlay.setAlpha(1f); playerOverlay.setVisibility(View.VISIBLE); playerOverlay.bringToFront(); }
        miniPlayerMorphProgress = 1f;
        miniPlayerMorphDragging = false;
        main.removeCallbacks(lyricKaraokeTicker);
        main.post(lyricKaraokeTicker);
    }

    /**
     * Full Player is intentionally persistent between opens.  When a global appearance or
     * transparency mode changes while it is hidden, rebuild only that hidden visual tree so
     * locally-created icon/text/glass drawables inherit the new palette. PlaybackService, the
     * queue and lyric data stay untouched.  The rebuild happens on the UI thread before another
     * frame is drawn, so users never see the transient visible state created by buildNowPlaying().
     */
    private void rebuildHiddenPlayerForAppearance() {
        if (playerOverlay == null || playerOverlay.getVisibility() == View.VISIBLE || appRoot == null) return;
        main.removeCallbacks(lyricKaraokeTicker);
        if (nowVinylStack != null) {
            nowVinylStack.cancelAnimations();
            nowVinylStack.setSpinning(false);
        } else if (nowVinyl != null) {
            nowVinyl.setSpinning(false);
        }
        playerOpenGeneration++;
        cancelPlayerOpenMotion();
        if (playerOverlay.getParent() instanceof ViewGroup)
            ((ViewGroup) playerOverlay.getParent()).removeView(playerOverlay);
        playerOverlay = null;
        playerStars = null;
        playerStars2D = null;
        playerStarBackdrop = null;
        buildNowPlaying();
        if (playerOverlay != null) {
            playerOverlay.setAlpha(1f);
            playerOverlay.setVisibility(View.GONE);
        }
    }

    private void toggleArtworkLyrics() {
        if (artworkFlip == null || trackSwipeAnimating || trackBrowseGestureActive || activePlayerHeroAnimator != null || miniPlayerMorphDragging) return;
        if (!artworkFlip.isLyricsVisible() && metadataParticles!=null) metadataParticles.capture(nowTitle,nowArtist);
        artworkFlip.showLyrics(!artworkFlip.isLyricsVisible());
        if (artworkFlip.isLyricsVisible()) scrollLyricToActive(true);
    }

    private void toggleVinylBrowse() {
        if (nowVinylStack == null || trackSwipeAnimating || trackBrowseGestureActive || activePlayerHeroAnimator != null || miniPlayerMorphDragging
                || (artworkFlip != null && artworkFlip.isFlipping())) return;
        if (artworkFlip != null && artworkFlip.isLyricsVisible()) artworkFlip.showLyrics(false);
        nowVinylStack.setExpanded(!nowVinylStack.isExpanded());
        nowVinylStack.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        toast(nowVinylStack.isExpanded() ? "唱片浏览 · 长按唱片可收起" : "已收起为单唱片");
    }

    private void closeNowPlaying() {
        if (artworkFlip != null) artworkFlip.reset();
        playerOpenGeneration++;
        cancelPlayerOpenMotion();
        miniMorphGeometryReady = false;
        trackSwipeGeneration++;
        trackSwipeAnimating = false;
        trackSwipeAdoptionStarted = false;
        trackSwipeDirection = 0;
        trackBrowseGestureActive = false;
        trackBrowseCommitInFlight = false;
        trackBrowseStartIndex = -1;
        trackBrowseWindowOffset = 0;
        trackBrowsePosition = 0f;
        trackBrowseExpectedKey = "";
        if (nowVinylStack != null) {
            nowVinylStack.cancelAnimations();
            nowVinylStack.resetPresentation();
            nowVinylStack.setSpinning(false);
        }
        nowArtworkKey = "";
        java.util.Arrays.fill(stackArtworkKeys, "#reload");
        configureWindow();
        main.removeCallbacks(lyricKaraokeTicker);
        if (playerOverlay != null) {
            playerOverlay.animate().cancel();
            playerOverlay.setAlpha(1f);
            playerOverlay.setVisibility(View.GONE);
        }
        resetMiniFullMorphTransforms();
        stars.setVisibility(View.VISIBLE); stars.setAlpha(1f);
        pageHost.setVisibility(View.VISIBLE); pageHost.setAlpha(1f);
        if (navHost != null) { navHost.setVisibility(View.VISIBLE); navHost.setAlpha(1f); }
        if (playback != null && playback.currentSong() != null) miniBar.setVisibility(View.VISIBLE);
        miniBar.setAlpha(1f); miniBar.setTranslationY(0f); miniBar.setScaleX(1f); miniBar.setScaleY(1f);
        miniPlayerMorphProgress = 0f;
        updatePlaylistLocateButtonVisibility();
    }

    private void cancelPlayerOpenMotion() {
        if (activePlayerHeroAnimator != null) {
            activePlayerHeroAnimator.removeAllListeners();
            activePlayerHeroAnimator.cancel();
            activePlayerHeroAnimator = null;
        }
        if (miniPlayerMorphAnimator != null) {
            miniPlayerMorphAnimator.removeAllListeners();
            miniPlayerMorphAnimator.cancel();
            miniPlayerMorphAnimator = null;
        }
        if (activePlayerHeroSource != null) activePlayerHeroSource.setAlpha(1f);
        activePlayerHeroSource = null;
        if (activeArtworkMorph != null) {
            if (activeArtworkMorph.getParent() instanceof ViewGroup)
                ((ViewGroup)activeArtworkMorph.getParent()).removeView(activeArtworkMorph);
            activeArtworkMorph.setBitmap(null);
            activeArtworkMorph = null;
        }
        if (activePlayerSurfaceMorph != null) {
            if (activePlayerSurfaceMorph.getParent() instanceof ViewGroup)
                ((ViewGroup)activePlayerSurfaceMorph.getParent()).removeView(activePlayerSurfaceMorph);
            activePlayerSurfaceMorph = null;
        }
        if (nowVinylStack != null) nowVinylStack.setCenterRevealSuppressed(false);
        if (nowVinyl != null) {
            // Any interrupted Hero must immediately give visual ownership back to the real record.
            nowVinyl.setAlpha(1f);
            nowVinyl.endHeroTransition();
        }
        if (miniBar != null) miniBar.animate().cancel();
        if (pageHost != null) pageHost.animate().cancel();
        if (navHost != null) navHost.animate().cancel();
    }

    /** Mini Player -> Full Player continuous upward gesture. */
    private void installMiniPlayerMorphGesture() {
        if (miniBar == null) return;
        miniBar.setOnTouchListener((v, e) -> {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                miniPlayerDownX = e.getRawX(); miniPlayerDownY = e.getRawY();
                miniMorphGeometryReady = false;
                miniPlayerMorphDragging = false;
                if (miniPlayerVelocity != null) miniPlayerVelocity.recycle();
                miniPlayerVelocity = VelocityTracker.obtain(); miniPlayerVelocity.addMovement(e);
                SpringMotion.pressDown(miniBar, .985f);
                return false;
            }
            if (miniPlayerVelocity != null) miniPlayerVelocity.addMovement(e);
            if (action == MotionEvent.ACTION_MOVE) {
                float dx = e.getRawX() - miniPlayerDownX;
                float dy = e.getRawY() - miniPlayerDownY;
                if (!miniPlayerMorphDragging) {
                    if (-dy < Ui.dp(this, 8) || Math.abs(dy) < Math.abs(dx) * 1.15f) return false;
                    if (playback == null || playback.currentSong() == null) return false;
                    miniPlayerMorphDragging = true;
                    if (playerOverlay == null) buildNowPlaying();
                    playerOverlay.setVisibility(View.VISIBLE); playerOverlay.bringToFront();
                    updateNowPlaying(playback.snapshot());
                    playerOverlay.setAlpha(0f);
                }
                float p = Math.max(0f, Math.min(1f, (-dy) / Ui.dp(this, 230f)));
                applyMiniFullMorphProgress(p);
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                float vy = 0f;
                if (miniPlayerVelocity != null) {
                    miniPlayerVelocity.computeCurrentVelocity(1000); vy = miniPlayerVelocity.getYVelocity();
                    miniPlayerVelocity.recycle(); miniPlayerVelocity = null;
                }
                if (!miniPlayerMorphDragging) {
                    SpringMotion.pressUp(miniBar);
                    return false; // regular click opens via shared-artwork transition
                }
                boolean open = action == MotionEvent.ACTION_UP && (miniPlayerMorphProgress >= .34f || vy < -Ui.dp(this, 720));
                settleMiniFullMorph(open ? 1f : 0f);
                return true;
            }
            return miniPlayerMorphDragging;
        });
    }

    private void applyMiniFullMorphProgress(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        miniPlayerMorphProgress = p;
        if (playerOverlay == null) return;
        if (!miniMorphGeometryReady && nowVinyl != null && nowVinyl.getWidth() > 0 && miniCover.getWidth() > 0) {
            // Capture untransformed endpoints once. Reading the animated view every frame feeds
            // its previous translation/scale back into the next position and causes drift.
            int[] src = new int[2], dst = new int[2];
            miniCover.getLocationOnScreen(src);
            ((View)nowVinyl.getParent()).getLocationOnScreen(dst);
            miniMorphDx = src[0] + miniCover.getWidth() * .5f - dst[0] - nowVinyl.getLeft() - nowVinyl.getWidth() * .5f;
            miniMorphDy = src[1] + miniCover.getHeight() * .5f - dst[1] - nowVinyl.getTop() - nowVinyl.getHeight() * .5f;
            miniMorphStartScale = miniCover.getWidth() / Math.max(1f, nowVinyl.artworkDiameterPx());
            miniMorphGeometryReady = true;
            miniBar.animate().cancel();
        }
        playerOverlay.setVisibility(View.VISIBLE);
        playerOverlay.setAlpha(Math.min(1f, p * 1.10f));
        miniBar.setTranslationY(-Ui.dp(this, 28f) * p);
        miniBar.setScaleX(1f + .018f * p); miniBar.setScaleY(1f + .018f * p);
        miniBar.setAlpha(1f - .82f * p);
        if (pageHost != null) pageHost.setAlpha(1f - .18f * p);
        if (navHost != null) navHost.setAlpha(1f - .28f * p);
        if (stars != null) stars.setAlpha(1f - .28f * p);

        if (nowVinyl != null && miniMorphGeometryReady) {
            float scale = miniMorphStartScale + (1f - miniMorphStartScale) * p;
            nowVinyl.setScaleX(scale); nowVinyl.setScaleY(scale);
            nowVinyl.setTranslationX(miniMorphDx * (1f - p));
            nowVinyl.setTranslationY(miniMorphDy * (1f - p));
            nowVinyl.setAlpha(Math.min(1f, .16f + p * 1.05f));
        }
        if (nowTitle != null && miniTitle != null && nowTitle.getWidth() > 0) {
            int[] src = new int[2], dst = new int[2]; miniTitle.getLocationOnScreen(src); nowTitle.getLocationOnScreen(dst);
            nowTitle.setTranslationX((src[0] - dst[0]) * (1f - p));
            nowTitle.setTranslationY((src[1] - dst[1]) * (1f - p));
            nowTitle.setScaleX(.72f + .28f * p); nowTitle.setScaleY(.72f + .28f * p);
            nowTitle.setAlpha(p);
        }
        if (nowVinylStack != null) nowVinylStack.setSidesReveal(smoothstep01((p - .55f) / .45f));
        if (nowSeek != null) {
            nowSeek.setAlpha(Math.max(0f, (p - .18f) / .82f));
            nowSeek.setTranslationY(Ui.dp(this, 28f) * (1f - p));
        }
    }

    private void settleMiniFullMorph(float target) {
        if (miniPlayerMorphAnimator != null) { miniPlayerMorphAnimator.removeAllListeners(); miniPlayerMorphAnimator.cancel(); }
        final float start = miniPlayerMorphProgress;
        miniPlayerMorphAnimator = ValueAnimator.ofFloat(0f, 1f);
        miniPlayerMorphAnimator.setDuration(target > start
                ? scaledPlayerGestureSettleDuration(520L)
                : scaledPlayerGestureSettleDuration(440L));
        miniPlayerMorphAnimator.setInterpolator(target > start ? SpringMotion.PAGE : SpringMotion.SNAPPY);
        miniPlayerMorphAnimator.addUpdateListener(a -> {
            float t = (Float)a.getAnimatedValue();
            applyMiniFullMorphProgress(start + (target - start) * t);
        });
        miniPlayerMorphAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                miniPlayerMorphDragging = false;
                if (target >= .999f) finalizePlayerOpen();
                else {
                    if (playerOverlay != null) playerOverlay.setVisibility(View.GONE);
                    resetMiniFullMorphTransforms();
                    miniBar.setAlpha(1f); miniBar.setTranslationY(0f); miniBar.setScaleX(1f); miniBar.setScaleY(1f);
                    if (pageHost != null) pageHost.setAlpha(1f);
                    if (navHost != null) navHost.setAlpha(1f);
                    if (stars != null) stars.setAlpha(1f);
                    miniPlayerMorphProgress = 0f;
                }
            }
        });
        miniPlayerMorphAnimator.start();
    }

    private long scaledPlayerGestureSettleDuration(long durationAt900Ms) {
        float scale = playerOpenMorphDurationMs / 900f;
        return Math.max(160L, Math.round(durationAt900Ms * scale));
    }

    private void resetMiniFullMorphTransforms() {
        if (nowVinyl != null) { nowVinyl.setTranslationX(0f); nowVinyl.setTranslationY(0f); nowVinyl.setScaleX(1f); nowVinyl.setScaleY(1f); nowVinyl.setAlpha(1f); }
        if (nowVinylStack != null) nowVinylStack.setSidesReveal(1f);
        if (nowTitle != null) { nowTitle.setTranslationX(0f); nowTitle.setTranslationY(0f); nowTitle.setScaleX(1f); nowTitle.setScaleY(1f); nowTitle.setAlpha(1f); }
        if (nowSeek != null) { nowSeek.setTranslationY(0f); nowSeek.setAlpha(1f); }
    }

    /** Direct-manipulation vinyl-stack swipe: all three records follow the finger continuously. */
    private void beginTrackBrowseIfNeeded() {
        if (trackBrowseGestureActive || playback == null) return;
        List<Song> queue = playback.queueSnapshot();
        if (queue.size() < 2) return;
        trackBrowseGestureActive = true;
        trackBrowseStartIndex = Math.max(0, Math.min(playback.currentQueueIndex(), queue.size() - 1));
        trackBrowseWindowOffset = 0;
        trackBrowsePosition = 0f;
    }

    /**
     * V92.5 queue scrub. `fraction` is intentionally unbounded: every 1.0 represents one
     * queue slot, so a single physical drag can walk through several retained records.
     */
    private void applyTrackSwipeProgress(float dxPx, float fraction) {
        if (artworkFlip != null && (artworkFlip.isLyricsVisible() || artworkFlip.isFlipping())) return;
        if (activePlayerHeroAnimator != null || miniPlayerMorphDragging) return;
        if (trackSwipeAnimating || nowVinylStack == null || playback == null) return;
        beginTrackBrowseIfNeeded();
        if (!trackBrowseGestureActive) return;
        List<Song> queue = playback.queueSnapshot();
        int maxSteps = nowVinylStack.isExpanded() ? Math.max(1, queue.size() - 1) : 1;
        float signed = dxPx < 0f ? fraction : -fraction; // + = next, - = previous
        signed = clampFloat(signed, -maxSteps, maxSteps);
        int whole = (int) signed; // Java truncates toward zero, giving a stable local [-1,1) remainder.
        while (trackBrowseWindowOffset < whole) shiftTrackBrowseWindow(+1);
        while (trackBrowseWindowOffset > whole) shiftTrackBrowseWindow(-1);
        float local = signed - trackBrowseWindowOffset;
        trackBrowsePosition = signed;
        nowVinylStack.setSwipeSlotPosition(local);

        float f = Math.min(1f, Math.abs(local));
        float dx = Math.copySign(Ui.dp(this, 28) * f, -local);
        if (nowTitle != null) {
            nowTitle.animate().cancel();
            nowTitle.setTranslationX(dx);
            nowTitle.setAlpha(1f - .18f * f);
        }
        if (nowArtist != null) {
            nowArtist.animate().cancel();
            nowArtist.setTranslationX(dx * .72f);
            nowArtist.setAlpha(1f - .22f * f);
        }
    }

    private void shiftTrackBrowseWindow(int direction) {
        if (!trackBrowseGestureActive || playback == null || nowVinylStack == null) return;
        int step = direction >= 0 ? 1 : -1;
        ImageView spareLoader = stackCoverLoaders[step > 0 ? 0 : 4];
        if (step > 0) {
            for (int i = 0; i < 4; i++) stackCoverLoaders[i] = stackCoverLoaders[i + 1];
            stackCoverLoaders[4] = spareLoader;
        } else {
            for (int i = 4; i > 0; i--) stackCoverLoaders[i] = stackCoverLoaders[i - 1];
            stackCoverLoaders[0] = spareLoader;
        }
        nowVinylStack.shiftPreviewCenter(step);
        nowVinyl = nowVinylStack.currentRecord();
        if (step > 0) {
            for (int i = 0; i < 4; i++) stackArtworkKeys[i] = stackArtworkKeys[i + 1];
            stackArtworkKeys[4] = "#preview-reload";
        } else {
            for (int i = 4; i > 0; i--) stackArtworkKeys[i] = stackArtworkKeys[i - 1];
            stackArtworkKeys[0] = "#preview-reload";
        }
        trackBrowseWindowOffset += step;
        int center = trackBrowseQueueIndex(trackBrowseWindowOffset);
        showTrackBrowseMetadata(center);
        loadVinylStackPreviewNeighbours(center);
    }

    private int trackBrowseQueueIndex(int offset) {
        if (playback == null) return -1;
        List<Song> queue = playback.queueSnapshot();
        if (queue.isEmpty() || trackBrowseStartIndex < 0) return -1;
        return Math.floorMod(trackBrowseStartIndex + offset, queue.size());
    }

    private void showTrackBrowseMetadata(int queueIndex) {
        if (playback == null || queueIndex < 0) return;
        List<Song> queue = playback.queueSnapshot();
        if (queueIndex >= queue.size()) return;
        Song song = queue.get(queueIndex);
        if (song == null) return;
        if (nowTitle != null) nowTitle.setText(song.title == null ? "" : song.title);
        if (nowArtist != null) {
            String artist = song.artist == null || song.artist.trim().isEmpty() ? "未知歌手" : song.artist.trim();
            String album = song.album == null ? "" : song.album.trim();
            nowArtist.setText(artist + (album.isEmpty() ? "" : "  ·  " + album));
        }
    }

    /** Warm the two records around an arbitrary preview center without touching audio routing. */
    private void loadVinylStackPreviewNeighbours(int centerIndex) {
        if (nowVinylStack == null || playback == null || centerIndex < 0) return;
        List<Song> queue = playback.queueSnapshot();
        if (queue.isEmpty()) return;
        for (int slot = 0; slot < 5; slot++) {
            final int offset = slot - 2;
            final int targetSlot = slot;
            int neighbour = VinylStackGeometry.neighbourIndex(centerIndex, offset, queue.size());
            Song song = neighbour < 0 ? null : queue.get(neighbour);
            String key = song == null ? "" : song.key() + "|" + song.coverUrl;
            if (key.equals(stackArtworkKeys[slot])) continue;
            stackArtworkKeys[slot] = key;
            setStackArtwork(slot, song != null, null, nowAccent);
            ImageView loader = stackCoverLoaders[slot];
            if (loader == null || song == null || song.coverUrl == null || song.coverUrl.isEmpty()) continue;
            final String expected = key;
            ImageLoader.load(song.coverUrl, loader, (bmp, color) -> {
                applyStackArtwork(expected, bmp, color);
            });
        }
    }

    private void restoreTrackSwipeSurface() {
        if (trackSwipeAnimating) return;
        if (trackBrowseWindowOffset != 0) resetTrackBrowsePreviewToPlayback();
        if (nowVinylStack != null) nowVinylStack.settleBack(() -> {
            if (trackBrowseWindowOffset != 0) resetTrackBrowsePreviewToPlayback();
        });
        if (playback != null && playback.currentSong() != null) {
            if (nowTitle != null) nowTitle.setText(playback.currentSong().title);
            setNowPlayingArtistText(playback.currentSong());
        }
        if (nowTitle != null) nowTitle.animate().translationX(0f).alpha(1f)
                .setDuration(220L).setInterpolator(SpringMotion.PAGE).start();
        if (nowArtist != null) nowArtist.animate().translationX(0f).alpha(1f)
                .setDuration(220L).setInterpolator(SpringMotion.PAGE).start();
        trackBrowseGestureActive = false;
        trackBrowsePosition = 0f;
    }

    @android.annotation.SuppressLint("UnsafeOptInUsageError")
    private void commitTrackSwipe(int direction, float velocityX) {
        if (artworkFlip != null && (artworkFlip.isLyricsVisible() || artworkFlip.isFlipping())) return;
        if (activePlayerHeroAnimator != null || miniPlayerMorphDragging) return;
        if (trackSwipeAnimating || playback == null || nowVinylStack == null) { restoreTrackSwipeSurface(); return; }
        List<Song> queue = playback.queueSnapshot();
        if (queue.size() < 2) { restoreTrackSwipeSurface(); return; }
        beginTrackBrowseIfNeeded();
        if (!trackBrowseGestureActive) { restoreTrackSwipeSurface(); return; }

        // Project only a restrained amount of release velocity; the visible finger position remains
        // the dominant decision, while a fast flick can carry the stack one additional slot.
        float projected = trackBrowsePosition;
        float velocitySlots = com.xingyu.music.ui.VinylGesturePolicy.velocitySlots(-velocityX,
                vinylGestureScroll == null ? getResources().getDisplayMetrics().widthPixels : vinylGestureScroll.getWidth(), vinylSwipeSensitivity);
        projected += velocitySlots;
        int targetOffset = Math.round(projected);
        if (targetOffset == 0) targetOffset = direction >= 0 ? 1 : -1;
        int maxSteps = nowVinylStack.isExpanded() ? queue.size() - 1 : 1;
        targetOffset = Math.max(-maxSteps, Math.min(maxSteps, targetOffset));
        // Keep the settle continuous from the currently retained window (at most one extra slot).
        targetOffset = Math.max(trackBrowseWindowOffset - 1, Math.min(trackBrowseWindowOffset + 1, targetOffset));
        final int finalTargetOffset = targetOffset;
        final int delta = finalTargetOffset - trackBrowseWindowOffset;
        trackSwipeAnimating = true;
        trackBrowseCommitInFlight = true;
        trackSwipeDirection = finalTargetOffset >= 0 ? 1 : -1;
        if (finalTargetOffset > 0 && playbackBehaviorTracker != null) playbackBehaviorTracker.markManualSkip();

        Runnable commit = () -> commitTrackBrowsePlayback(finalTargetOffset);
        if (delta == 0) {
            nowVinylStack.settleBack(commit);
        } else {
            nowVinylStack.settleToNeighbour(delta, velocityX, () -> {
                shiftTrackBrowseWindow(delta);
                commit.run();
            });
        }
    }

    private void commitTrackBrowsePlayback(int targetOffset) {
        if (playback == null) { finishTrackBrowseCommit(); return; }
        List<Song> queue = playback.queueSnapshot();
        if (queue.isEmpty()) { finishTrackBrowseCommit(); return; }
        int target = trackBrowseQueueIndex(targetOffset);
        if (target < 0 || target >= queue.size()) { finishTrackBrowseCommit(); return; }
        Song targetSong = queue.get(target);
        trackBrowseExpectedKey = targetSong == null ? "" : targetSong.key();
        trackSwipeSourceKey = nowArtworkKey;
        final int generation = ++trackSwipeGeneration;
        playback.playAt(target);
        main.postDelayed(() -> {
            if (generation == trackSwipeGeneration && trackBrowseCommitInFlight) finishTrackBrowseCommit();
        }, 1150L);
    }

    private void finishTrackBrowseCommit() {
        if (!trackBrowseCommitInFlight && !trackSwipeAnimating) return;
        trackBrowseCommitInFlight = false;
        trackSwipeAnimating = false;
        trackSwipeAdoptionStarted = false;
        trackBrowseGestureActive = false;
        trackBrowseStartIndex = -1;
        trackBrowseWindowOffset = 0;
        trackBrowsePosition = 0f;
        trackBrowseExpectedKey = "";
        trackSwipeDirection = 0;
        if (nowVinylStack != null) {
            nowVinylStack.setSwipeSlotPosition(0f);
            nowVinyl = nowVinylStack.currentRecord();
        }
        if (nowTitle != null) nowTitle.animate().translationX(0f).alpha(1f).setDuration(130L).start();
        if (nowArtist != null) nowArtist.animate().translationX(0f).alpha(1f).setDuration(130L).start();
        loadVinylStackNeighbours();
    }

    private void resetTrackBrowsePreviewToPlayback() {
        if (playback == null || nowVinylStack == null) return;
        // Rotate the retained roles back to the real center when a tiny gesture is cancelled.
        while (trackBrowseWindowOffset > 0) { nowVinylStack.shiftPreviewCenter(-1); trackBrowseWindowOffset--; }
        while (trackBrowseWindowOffset < 0) { nowVinylStack.shiftPreviewCenter(+1); trackBrowseWindowOffset++; }
        for (int i = 0; i < stackArtworkKeys.length; i++) stackArtworkKeys[i] = "#reload";
        nowVinyl = nowVinylStack.currentRecord();
        loadVinylStackPreviewNeighbours(playback.currentQueueIndex());
    }

    /** Legacy one-step adoption remains for non-browse callers, but V92.5 queue scrubbing does not use it. */
    private void adoptTrackSwipeCenterOnce(int direction, Bitmap bitmap, int accentColor) {
        if (!trackSwipeAnimating || trackBrowseCommitInFlight || nowVinylStack == null) return;
        if (trackSwipeAdoptionStarted) {
            if (bitmap != null) {
                nowVinylStack.setCurrentCover(bitmap);
                nowVinylStack.setAccentColor(accentColor);
            }
            return;
        }
        trackSwipeAdoptionStarted = true;
        nowVinylStack.adoptCommittedCenter(direction, bitmap, accentColor, this::finishTrackSwipeIncoming);
    }

    private void prepareTrackSwipeIncoming() {
        if (!trackSwipeAnimating || trackBrowseCommitInFlight) return;
        float in = (trackSwipeDirection > 0 ? 1f : -1f) * Ui.dp(this, 54);
        if (nowTitle != null) {
            nowTitle.animate().cancel(); nowTitle.setTranslationX(in * .22f); nowTitle.setAlpha(.16f);
            nowTitle.animate().translationX(0f).alpha(1f).setDuration(235L).setInterpolator(SpringMotion.PAGE).start();
        }
        if (nowArtist != null) {
            nowArtist.animate().cancel(); nowArtist.setTranslationX(in * .17f); nowArtist.setAlpha(.14f);
            nowArtist.animate().translationX(0f).alpha(1f).setDuration(245L).setInterpolator(SpringMotion.PAGE).start();
        }
    }

    private void finishTrackSwipeIncoming() {
        if (trackBrowseCommitInFlight) { finishTrackBrowseCommit(); return; }
        if (nowTitle != null) nowTitle.animate().translationX(0f).alpha(1f).setDuration(120L).start();
        if (nowArtist != null) nowArtist.animate().translationX(0f).alpha(1f).setDuration(120L).start();
        trackSwipeAnimating = false;
        trackSwipeAdoptionStarted = false;
        int committedDirection = trackSwipeDirection;
        trackSwipeDirection = 0;
        if (nowVinylStack != null) nowVinyl = nowVinylStack.currentRecord();
        if (committedDirection > 0) {
            for (int i = 0; i < 4; i++) stackArtworkKeys[i] = stackArtworkKeys[i + 1];
            stackArtworkKeys[4] = "#reload";
        } else {
            for (int i = 4; i > 0; i--) stackArtworkKeys[i] = stackArtworkKeys[i - 1];
            stackArtworkKeys[0] = "#reload";
        }
        loadVinylStackNeighbours();
    }

    private void buildNowPlaying() {
        playerOverlay = new FrameLayout(this);
        playerOverlay.setBackgroundColor(Ui.BG);
        appRoot.addView(playerOverlay, Ui.frame(-1, -1, Gravity.FILL));

        // Full-bleed star field extends behind the status/navigation bars.  V55 keeps the exact
        // V49 2D renderer as a switchable classic mode and layers the newer 3D engine beside it.
        // Only one renderer is visible/running at a time; both remain touch-transparent.
        playerStars2D = new StarfieldView(this);
        playerOverlay.addView(playerStars2D, Ui.frame(-1, -1, Gravity.FILL));
        playerStars = new Starfield3DView(this);
        playerOverlay.addView(playerStars, Ui.frame(-1, -1, Gravity.FILL));
        playerStarBackdrop = new StarfieldBackdropView(this);
        playerOverlay.addView(playerStarBackdrop, Ui.frame(-1, -1, Gravity.FILL));

        // Native Media3 PCM envelopes drive both the star field and cover ripple rings.
        // No RECORD_AUDIO permission/microphone capture is involved.
        AudioLevelProvider visualLevels = new AudioLevelProvider() {
            @Override public float energy() { return playback == null ? 0f : playback.visualEnergy(); }
            @Override public float bass() { return playback == null ? 0f : playback.visualBass(); }
            @Override public float treble() { return playback == null ? 0f : playback.visualTreble(); }
            @Override public float beat() { return playback == null ? 0f : playback.visualBeat(); }
        };
        playerStars.setAudioLevelProvider(visualLevels);
        playerStars2D.setAudioLevelProvider(visualLevels);
        applyStarfieldPreferences();

        FrameLayout playerSafe = new FrameLayout(this);
        playerSafe.setClipChildren(false);
        playerSafe.setClipToPadding(false);
        playerSafe.setPadding(0, systemTopInset, 0, systemBottomInset);
        playerSafe.setOnApplyWindowInsetsListener((v, insets) -> {
            int top; int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets sys = insets.getInsets(WindowInsets.Type.systemBars());
                android.graphics.Insets cutout = insets.getInsets(WindowInsets.Type.displayCutout());
                top = Math.max(sys.top, cutout.top); bottom = sys.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null)
                    top = Math.max(top, insets.getDisplayCutout().getSafeInsetTop());
                bottom = insets.getSystemWindowInsetBottom();
            }
            playerSafe.setPadding(0, top, 0, bottom);
            return insets;
        });
        playerOverlay.addView(playerSafe, Ui.frame(-1, -1, Gravity.FILL));
        playerSafe.requestApplyInsets();

        SwipeAwareScrollView scroll = new SwipeAwareScrollView(this);
        vinylGestureScroll = scroll;
        scroll.setSwipeSensitivity(vinylSwipeSensitivity);
        scroll.setClipChildren(false);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        // Remove platform EdgeEffect flashes at the top/bottom of the full-player page.
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setSwipeListener(new SwipeAwareScrollView.SwipeListener() {
            @Override public void onSwipeProgress(float dxPx, float fraction) {
                applyTrackSwipeProgress(dxPx, fraction);
            }
            @Override public void onSwipeCancelled() {
                restoreTrackSwipeSurface();
            }
            @Override public void onSwipeLeft() {
                commitTrackSwipe(+1, 0f);
            }
            @Override public void onSwipeRight() {
                commitTrackSwipe(-1, 0f);
            }
            @Override public void onSwipeCommit(int direction, float velocityX) {
                commitTrackSwipe(direction, velocityX);
            }
        });
        LinearLayout content = Ui.column(this);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        // V36: trim only the trailing bottom spacer of the outer now-playing page.
        // At the outer ScrollView's bottom limit this reduces maxScroll by ~32dp, so the
        // whole composition rests lower and exposes more of the vinyl at the top. All
        // internal spacing (especially transport -> lyricPanel's 44dp gap) stays unchanged.
        content.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 6));
        scroll.addView(content);
        playerSafe.addView(scroll, Ui.frame(-1, -1, Gravity.FILL));

        // Keep the title geometrically centered in the screen: left and right actions float
        // independently instead of participating in a weighted row.
        FrameLayout top = new FrameLayout(this);
        TextView label = Ui.text(this, "正在播放", 12.2f, Ui.TEXT, true);
        label.setGravity(Gravity.CENTER);
        top.addView(label, Ui.frame(-1, Ui.dp(this, 42), Gravity.CENTER));

        FrameLayout back = Ui.iconButton(this, IconView.Type.BACK, 42, Ui.playerControlIconColor(Ui.PURPLE), Color.TRANSPARENT);
        back.setBackground(Ui.playerIconRipple(Ui.PURPLE, 21, this));
        back.setOnClickListener(v -> closeNowPlaying());
        top.addView(back, Ui.frame(Ui.dp(this, 42), Ui.dp(this, 42), Gravity.START | Gravity.CENTER_VERTICAL));

        LinearLayout rightActions = Ui.row(this);
        FrameLayout starStudio = Ui.iconButton(this, IconView.Type.PALETTE, 42, Ui.playerControlIconColor(Ui.CYAN), Color.TRANSPARENT);
        starStudio.setBackground(Ui.playerIconRipple(Ui.CYAN, 21, this));
        starStudio.setContentDescription("自定义星空背景");
        starStudio.setOnClickListener(v -> { animatePlayerModeTap(starStudio); showStarfieldStudio(); });
        rightActions.addView(starStudio, Ui.lp(Ui.dp(this, 42), Ui.dp(this, 42)));
        starStudio.postDelayed(() -> maybeShowContextCoach(COACH_STARFIELD_STUDIO, starStudio,
                "星空实验室", "这里可以切换 2D / 3D 星空，并调整运动、星光、背景与侧唱片景深。"), 260L);
        FrameLayout more = Ui.iconButton(this, IconView.Type.MORE, 42, Ui.playerControlIconColor(Ui.PURPLE), Color.TRANSPARENT);
        more.setBackground(Ui.playerIconRipple(Ui.PURPLE, 21, this));
        more.setOnClickListener(v -> {
            animatePlayerQueueTap(more);
            Song song = playback == null ? null : playback.currentSong();
            if (song != null) showSongActions(song);
        });
        rightActions.addView(more, Ui.lp(Ui.dp(this, 42), Ui.dp(this, 42)));
        more.postDelayed(() -> maybeShowContextCoach(COACH_SLEEP_TIMER, more,
                "睡前定时", "播放详情右上角“更多”里新增定时关闭。到点只会暂停播放并保留队列与当前位置，之后可以继续听。"), 320L);
        top.addView(rightActions, Ui.frame(Ui.dp(this, 84), Ui.dp(this, 42), Gravity.END | Gravity.CENTER_VERTICAL));
        content.addView(top, Ui.lp(-1, Ui.dp(this, 46)));

        int widthDp = (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
        int discDp = Math.min(326, Math.max(278, widthDp - 62));
        // Give the concentric wave rings a transparent margin without changing the overall page layout.
        int visualDp = Math.min(Math.max(discDp, widthDp - 40), discDp + 28);
        nowVinylStack = new VinylStackView(this);
        nowVinylStack.setAudioLevelProvider(visualLevels);
        nowVinylStack.setNeighbourClarity(starfieldVinylClarity);
        nowVinyl = nowVinylStack.currentRecord();
        LinearLayout.LayoutParams vp = Ui.lp(Ui.dp(this, visualDp), Ui.dp(this, visualDp+84));
        vp.topMargin = Ui.dp(this, 20);
        artworkFlip = new com.xingyu.music.ui.ArtworkFlipView(this);
        FrameLayout artworkFront=new FrameLayout(this);
        artworkFront.setClipChildren(false); artworkFront.setClipToPadding(false);
        artworkFront.addView(nowVinylStack,Ui.frame(-1,Ui.dp(this,visualDp),Gravity.TOP));
        content.addView(artworkFlip, vp);
        nowVinylStack.setClickable(true);
        nowVinylStack.setContentDescription("点击翻到歌词，长按展开或收起唱片浏览");
        nowVinylStack.setOnClickListener(v -> toggleArtworkLyrics());
        nowVinylStack.setOnLongClickListener(v -> { toggleVinylBrowse(); return true; });

        nowCoverLoader = new ImageView(this);
        nowCoverLoader.setVisibility(View.INVISIBLE);
        content.addView(nowCoverLoader, Ui.lp(1, 1));
        for (int i = 0; i < 5; i++) {
            stackArtworkKeys[i] = "";
            stackCoverLoaders[i] = new ImageView(this);
            stackCoverLoaders[i].setVisibility(View.INVISIBLE);
            content.addView(stackCoverLoaders[i], Ui.lp(1, 1));
        }

        nowTitle = Ui.text(this, "", 22.5f, Ui.TEXT, true);
        nowTitle.setGravity(Gravity.CENTER);
        nowTitle.setSingleLine(true);
        nowTitle.setEllipsize(TextUtils.TruncateAt.END);
        nowTitle.setClickable(true);
        nowTitle.setFocusable(true);
        nowTitle.setContentDescription("点击歌名可搜索");
        Ui.applyRipple(nowTitle, Color.TRANSPARENT);
        nowTitle.setOnClickListener(v -> {
            Song current = playback == null ? null : playback.currentSong();
            if (current != null) openSongTitleSearch(current.title);
        });
        LinearLayout.LayoutParams nt = Ui.lp(-1, Ui.dp(this, 40));
        nt.topMargin = Ui.dp(this, 16);
        FrameLayout.LayoutParams titlePosition=Ui.frame(-1,Ui.dp(this,40),Gravity.TOP);
        titlePosition.topMargin=Ui.dp(this,visualDp+16);
        artworkFront.addView(nowTitle,titlePosition);

        // V85.1: keep this line visually identical to the long-stable V84 renderer.
        // The entire metadata line is a transparent hit target for artist search instead of
        // relying on ClickableSpan/LinkMovementMethod, which proved unreliable on-device.
        nowArtist = Ui.text(this, "", 12.6f, Ui.TEXT_2, false);
        nowArtist.setGravity(Gravity.CENTER);
        nowArtist.setSingleLine(true);
        nowArtist.setEllipsize(TextUtils.TruncateAt.END);
        nowArtist.setClickable(true);
        nowArtist.setFocusable(true);
        nowArtist.setContentDescription("点击歌手可搜索");
        Ui.applyRipple(nowArtist, Color.TRANSPARENT);
        nowArtist.setOnClickListener(v -> {
            Song current = playback == null ? null : playback.currentSong();
            if (current != null) openArtistSearch(primaryArtistForLyric(current.artist));
        });
        FrameLayout.LayoutParams artistPosition=Ui.frame(-1,Ui.dp(this,28),Gravity.TOP);
        artistPosition.topMargin=Ui.dp(this,visualDp+56);
        artworkFront.addView(nowArtist,artistPosition);

        // V75: keep the center favorite anchored to the exact screen/content center, while the
        // lyrics and add actions hug the usable left/right edges. This restores the wide, airy
        // composition from the original player instead of centering all three inside equal thirds.
        FrameLayout metaRow = new FrameLayout(this);

        FrameLayout desktopLyricQuick = new FrameLayout(this);
        desktopLyricQuick.setBackground(Ui.playerIconRipple(Ui.CYAN, 21, this));
        ImageView desktopLyricGlyph = new ImageView(this);
        desktopLyricGlyph.setImageResource(R.drawable.ic_lyric_toggle);
        desktopLyricGlyph.setColorFilter(Ui.playerControlIconColor(Ui.CYAN));
        desktopLyricGlyph.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        desktopLyricQuick.addView(desktopLyricGlyph, Ui.frame(-1, -1, Gravity.FILL));
        desktopLyricQuick.setContentDescription("开启桌面歌词");
        desktopLyricQuick.setClickable(true);
        Ui.applyRipple(desktopLyricQuick, Color.TRANSPARENT);
        desktopLyricQuick.setOnClickListener(v -> {
            animateDesktopLyricTap(desktopLyricQuick);
            enableDesktopLyricsDirectly();
        });
        metaRow.addView(desktopLyricQuick, Ui.frame(Ui.dp(this, 42), Ui.dp(this, 42), Gravity.START | Gravity.CENTER_VERTICAL));
        desktopLyricQuick.postDelayed(() -> maybeShowContextCoach(COACH_DESKTOP_LYRIC, desktopLyricQuick,
                "桌面歌词", "点这里可以直接把当前歌词显示到桌面。"), 260L);

        nowFavoriteButton = Ui.iconButton(this, IconView.Type.HEART, 42, Ui.playerControlIconColor(Ui.PINK), Color.TRANSPARENT);
        nowFavoriteButton.setBackground(Ui.playerIconRipple(Ui.PINK, 21, this));
        nowFavoriteIcon = (IconView) nowFavoriteButton.getChildAt(0);
        nowFavoriteIcon.setFavoriteState(false, false);
        nowFavoriteButton.setContentDescription("收藏");
        nowFavoriteButton.setOnClickListener(v -> {
            Song song = playback == null ? null : playback.currentSong();
            if (song != null) {
                favorites = store.toggleFavorite(song);
                boolean favorite = isFavorite(song);
                nowFavoriteIcon.setIconColor(favorite ? Ui.PINK : Ui.playerControlIconColor(Ui.PINK));
                nowFavoriteIcon.setFavoriteState(favorite, true);
                SpringMotion.pressUp(nowFavoriteButton);
                if (favorite) animateHeartBurst(nowFavoriteButton, Ui.PINK);
                nowFavoriteButton.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                if (tab == 3 && (modalOverlay == null || modalOverlay.getVisibility() != View.VISIBLE)) renderTab();
                toast(favorite ? "已收藏" : "已取消收藏");
            }
        });
        metaRow.addView(nowFavoriteButton, Ui.frame(Ui.dp(this, 42), Ui.dp(this, 42), Gravity.CENTER));

        FrameLayout addToPlaylist = Ui.iconButton(this, IconView.Type.PLUS, 42, Ui.playerControlIconColor(Ui.PURPLE), Color.TRANSPARENT);
        addToPlaylist.setBackground(Ui.playerIconRipple(Ui.PURPLE, 21, this));
        addToPlaylist.setContentDescription("添加到歌单");
        addToPlaylist.setOnClickListener(v -> {
            animatePlayerAddTap(addToPlaylist);
            Song song = playback == null ? null : playback.currentSong();
            if (song != null) showAddToPlaylist(song);
        });
        metaRow.addView(addToPlaylist, Ui.frame(Ui.dp(this, 42), Ui.dp(this, 42), Gravity.END | Gravity.CENTER_VERTICAL));

        LinearLayout.LayoutParams metaP = Ui.lp(-1, Ui.dp(this, 44)); metaP.topMargin = Ui.dp(this, 5);
        content.addView(metaRow, metaP);

        nowSeek = new SeekBar(this);
        nowSeek.setMax(1000);
        nowSeek.setPadding(0, 0, 0, 0);
        if (Build.VERSION.SDK_INT >= 21) {
            nowSeek.setProgressTintList(ColorStateList.valueOf(nowAccent));
            nowSeek.setThumbTintList(ColorStateList.valueOf(nowAccent));
        }
        LinearLayout.LayoutParams seekP = Ui.lp(-1, Ui.dp(this, 38));
        seekP.topMargin = Ui.dp(this, 12);
        content.addView(nowSeek, seekP);

        LinearLayout times = Ui.row(this);
        nowTime = Ui.text(this, "0:00", 10.5f, Ui.DIM, false);
        nowDuration = Ui.text(this, "0:00", 10.5f, Ui.DIM, false);
        nowDuration.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        times.addView(nowTime, new LinearLayout.LayoutParams(0, Ui.dp(this, 22), 1f));
        times.addView(nowDuration, new LinearLayout.LayoutParams(0, Ui.dp(this, 22), 1f));
        content.addView(times, Ui.lp(-1, Ui.dp(this, 22)));

        // One full-width transport row: mode / previous / play-pause / next / queue.
        LinearLayout controls = Ui.row(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        int initialMode = playback == null ? PlaybackService.MODE_SEQUENTIAL : playback.getPlayMode();
        int initialModeAccent = playModeAccent(initialMode);
        FrameLayout modeButton = Ui.iconButton(this, playModeIcon(initialMode), 46, Ui.playerControlIconColor(initialModeAccent), Color.TRANSPARENT);
        modeButton.setBackground(Ui.playerIconRipple(initialModeAccent, 23, this));
        nowModeIcon = (IconView) modeButton.getChildAt(0);
        nowModeButton = modeButton;
        modeButton.setContentDescription(playModeLabel(initialMode));
        modeButton.setOnClickListener(v -> {
            animatePlayerModeTap(modeButton);
            if (playback == null) return;
            int mode = playback.cyclePlayMode();
            int accent = playModeAccent(mode);
            nowModeIcon.setType(playModeIcon(mode));
            nowModeIcon.setIconColor(Ui.playerControlIconColor(accent));
            modeButton.setBackground(Ui.playerIconRipple(accent, 23, this));
            modeButton.setContentDescription(playModeLabel(mode));
            modeButton.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            showModeToast(playModeLabel(mode), accent);
        });

        FrameLayout prev = Ui.iconButton(this, IconView.Type.PREV, 50, Ui.playerControlIconColor(Ui.BLUE), Color.TRANSPARENT);
        prev.setBackground(Ui.playerIconRipple(Ui.BLUE, 25, this));
        prev.setOnClickListener(v -> {
            animatePlayerDirectionalTap(prev, -1);
            if (playback != null) playback.previous();
        });
        FrameLayout play = Ui.iconButton(this, IconView.Type.PLAY, 66, Color.rgb(13, 18, 22), Ui.CYAN);
        play.setBackground(Ui.primaryFill(Ui.CYAN, 33, this));
        nowPlayButton = play;
        nowPlayIcon = (IconView) play.getChildAt(0);
        play.setOnClickListener(v -> {
            animatePrimaryTransportTap(play);
            if (playback != null) playback.toggle();
        });
        FrameLayout next = Ui.iconButton(this, IconView.Type.NEXT, 50, Ui.playerControlIconColor(Ui.BLUE), Color.TRANSPARENT);
        next.setBackground(Ui.playerIconRipple(Ui.BLUE, 25, this));
        next.setOnClickListener(v -> {
            animatePlayerDirectionalTap(next, +1);
            if (playbackBehaviorTracker != null) playbackBehaviorTracker.markManualSkip();
            if (playback != null) playback.next();
        });
        FrameLayout queueButton = Ui.iconButton(this, IconView.Type.PLAYLIST, 46, Ui.playerControlIconColor(Ui.CYAN), Color.TRANSPARENT);
        queueButton.setBackground(Ui.playerIconRipple(Ui.CYAN, 23, this));
        queueButton.setContentDescription("播放列表");
        queueButton.setOnClickListener(v -> { animatePlayerQueueTap(queueButton); showPlaybackQueue(); });
        queueButton.setOnLongClickListener(v -> { toggleVinylBrowse(); return true; });

        controls.addView(controlSlot(modeButton, 46), new LinearLayout.LayoutParams(0, Ui.dp(this, 72), 1f));
        controls.addView(controlSlot(prev, 50), new LinearLayout.LayoutParams(0, Ui.dp(this, 72), 1f));
        controls.addView(controlSlot(play, 66), new LinearLayout.LayoutParams(0, Ui.dp(this, 76), 1.18f));
        controls.addView(controlSlot(next, 50), new LinearLayout.LayoutParams(0, Ui.dp(this, 72), 1f));
        controls.addView(controlSlot(queueButton, 46), new LinearLayout.LayoutParams(0, Ui.dp(this, 72), 1f));
        LinearLayout.LayoutParams ctrlP = Ui.lp(-1, Ui.dp(this, 78));
        ctrlP.topMargin = Ui.dp(this, 10);
        content.addView(controls, ctrlP);

        // Synced lyric viewport. It owns its own scroll position so the active line can
        // stay centered without moving the transport controls above it.
        lyricPanel = new com.xingyu.music.ui.FadingLyricsFrame(this);
        // Borderless glass avoids a horizontal "wall" flashing as the card crosses the viewport.
        lyricPanel.setBackgroundColor(Color.TRANSPARENT);
        lyricPanel.setAlpha(1f);

        lyricScroll = new LyricGestureScrollView(this);
        lyricScroll.setFillViewport(true);
        lyricScroll.setVerticalScrollBarEnabled(false);
        lyricScroll.setClipToPadding(false);
        lyricScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        lyricScroll.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 18));
        lyricScroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            lyricLastScrollAt=SystemClock.uptimeMillis();
            if (!lyricSeekMode) return;
            updateLyricSeekCandidate();
            if (!lyricManualGesture) scheduleLyricManualSelection();
        });
        lyricScroll.setGestureObserver(new LyricGestureScrollView.GestureObserver() {
            @Override public void onDown(MotionEvent e) {
                lyricTapStartedAt=SystemClock.uptimeMillis();
                lyricTapMayFlip=lyricManualSelection<0 && lyricTapStartedAt-lyricLastScrollAt>180;
                // This callback comes from dispatchTouchEvent(), before clickable lyric rows
                // can consume DOWN. Therefore the outer page can no longer steal the drag.
                lyricManualGesture = true;
                lyricGestureMoved = false;
                lyricSeekMode = true;
                lyricsManualUntil = Long.MAX_VALUE;
                if (lyricsResumeRunnable != null) main.removeCallbacks(lyricsResumeRunnable);
                if (lyricScrollSettleRunnable != null) main.removeCallbacks(lyricScrollSettleRunnable);
                showLyricSeekGuide(true);
                updateLyricSeekCandidate();
            }

            @Override public void onMove(MotionEvent e, boolean movedBeyondSlop) {
                if (!lyricGestureMoved && movedBeyondSlop) {
                    lyricGestureMoved = true;
                    // A previous manual selection is only a visual/tap target. The instant
                    // the user drags again it is released and a brand-new scrub can begin.
                    clearManualLyricSelection(false);
                    if (lyricsResumeRunnable != null) main.removeCallbacks(lyricsResumeRunnable);
                }
                lyricsManualUntil = Long.MAX_VALUE;
                showLyricSeekGuide(true);
                updateLyricSeekCandidate();
            }

            @Override public void onUp(MotionEvent e, boolean movedBeyondSlop, boolean cancelled) {
                boolean flipTap=lyricTapMayFlip && !movedBeyondSlop && !cancelled
                        && SystemClock.uptimeMillis()-lyricTapStartedAt<350;
                lyricManualGesture = false;
                lyricGestureMoved = lyricGestureMoved || movedBeyondSlop;
                if (lyricGestureMoved && !cancelled) {
                    scheduleLyricManualSelection();
                } else if (cancelled) {
                    scheduleLyricManualSelection();
                } else {
                    // Keep a plain tap available to LyricLineView.OnClick. If nothing is
                    // selected, leave manual mode and return to the live line.
                    lyricSeekMode = false;
                    showLyricSeekGuide(false);
                    if (lyricManualSelection < 0) {
                        lyricsManualUntil = 0L;
                        scrollLyricToActive(false);
                    }
                }
                lyricGestureMoved = false;
                if(flipTap) toggleArtworkLyrics();
            }
        });

        lyricBox = Ui.column(this);
        lyricBox.setGravity(Gravity.CENTER_HORIZONTAL);
        lyricBox.setPadding(Ui.dp(this, 10), Ui.dp(this, 44), Ui.dp(this, 10), Ui.dp(this, 34));
        lyricEmpty = Ui.text(this, "歌词加载中…", 12.5f, Ui.DIM, false);
        lyricEmpty.setGravity(Gravity.CENTER);
        lyricBox.addView(lyricEmpty, Ui.lp(-1, Ui.dp(this, 138)));
        lyricScroll.addView(lyricBox);
        lyricPanel.addView(lyricScroll, Ui.frame(-1, -1, Gravity.FILL));

        // NetEase-style scrub guide: appears only while the user manually scrolls lyrics.
        lyricSeekGuide = Ui.row(this);
        lyricSeekGuide.setGravity(Gravity.CENTER_VERTICAL);
        lyricSeekGuide.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        lyricSeekGuide.setVisibility(View.GONE);
        lyricSeekGuide.setAlpha(0f);

        lyricSeekGuideTime = Ui.text(this, "0:00", 10.5f, Color.argb(210, 230, 234, 244), true);
        lyricSeekGuideTime.setGravity(Gravity.CENTER);
        lyricSeekGuide.addView(lyricSeekGuideTime, Ui.lp(Ui.dp(this, 47), Ui.dp(this, 28)));

        lyricSeekGuideLeft = new View(this);
        lyricSeekGuideLeft.setBackgroundColor(Color.argb(82, 235, 239, 248));
        LinearLayout.LayoutParams guideLineLeftP = new LinearLayout.LayoutParams(0, Ui.dp(this, 1), 1f);
        guideLineLeftP.leftMargin = Ui.dp(this, 7);
        guideLineLeftP.rightMargin = Ui.dp(this, 70);
        lyricSeekGuide.addView(lyricSeekGuideLeft, guideLineLeftP);

        lyricSeekGuideRight = new View(this);
        lyricSeekGuideRight.setBackgroundColor(Color.argb(82, 235, 239, 248));
        LinearLayout.LayoutParams guideLineRightP = new LinearLayout.LayoutParams(0, Ui.dp(this, 1), 1f);
        guideLineRightP.leftMargin = Ui.dp(this, 70);
        guideLineRightP.rightMargin = Ui.dp(this, 7);
        lyricSeekGuide.addView(lyricSeekGuideRight, guideLineRightP);

        lyricSeekGuidePlay = Ui.iconButton(this, IconView.Type.PLAY, 30, Color.argb(220, 235, 239, 248), Color.TRANSPARENT);
        lyricSeekGuidePlay.setClickable(false);
        lyricSeekGuide.addView(lyricSeekGuidePlay, Ui.lp(Ui.dp(this, 30), Ui.dp(this, 30)));

        FrameLayout.LayoutParams guideP = Ui.frame(-1, Ui.dp(this, 36), Gravity.CENTER_VERTICAL);
        guideP.leftMargin = Ui.dp(this, 8);
        guideP.rightMargin = Ui.dp(this, 8);
        lyricPanel.addView(lyricSeekGuide, guideP);

        // Small, discoverable colour control; no extra textual lyric header is needed.

        LinearLayout.LayoutParams lyricP = Ui.lp(-1, Ui.dp(this, 258));
        // V31: keep the lyric card fully below the initial transport area instead of letting
        // a thin strip peek at the bottom of the now-playing page. This removes the visual
        // "flash past" feeling when the internally auto-scrolling lyrics are barely visible.
        lyricP.topMargin = Ui.dp(this, 82);
        artworkFlip.setFaces(artworkFront, lyricPanel);
        metadataParticles=new com.xingyu.music.ui.MetadataParticlesView(this);
        artworkFlip.addView(metadataParticles,Ui.frame(-1,-1,Gravity.FILL));
        artworkFlip.setProgressListener(progress -> {
            nowTitle.animate().cancel(); nowArtist.animate().cancel();
            float alpha=(1f-progress)*(1f-progress);
            nowTitle.setAlpha(alpha); nowArtist.setAlpha(alpha);
            metadataParticles.setProgress(progress);
        });
        // Hard boundary: any gesture whose DOWN lands inside the lyric card belongs to
        // LyricGestureScrollView. The exclusion rectangle is resolved from lyricPanel's live
        // on-screen bounds on every DOWN, so moving the panel down also moves the lyric
        // manual-scroll hit area automatically; the old location does not remain interactive.
        scroll.setGestureExclusionView(lyricPanel);

        nowSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar b) { animateSeekInteraction(b, true); }
            @Override public void onStopTrackingTouch(SeekBar b) {
                animateSeekInteraction(b, false);
                if (playback != null) {
                    PlaybackSnapshot snap = playback.snapshot();
                    if (snap.durationMs > 0) playback.seekTo((long) (snap.durationMs * (b.getProgress() / 1000d)));
                }
            }
            @Override public void onProgressChanged(SeekBar b, int p, boolean from) { }
        });

        if (!lyricLines.isEmpty()) rebuildLyrics();
        else if (playback != null && playback.currentSong() != null && lyricEmpty != null) {
            lyricEmpty.setText("歌词加载中…");
        }
    }

    private void setNowPlayingArtistText(Song song) {
        if (nowArtist == null || song == null) return;
        String artistText = song.artist == null || song.artist.trim().isEmpty()
                ? "未知歌手" : song.artist.trim();
        String albumText = song.album == null ? "" : song.album.trim();
        String full = artistText + (albumText.isEmpty() ? "" : "  ·  " + albumText);

        // Explicitly restore the stable V84 text path on every snapshot. This avoids a stale
        // span/movement-method state leaving the metadata row visually blank after updates.
        nowArtist.setTextColor(Ui.TEXT_2);
        nowArtist.setAlpha(1f);
        nowArtist.setVisibility(View.VISIBLE);
        nowArtist.setText(full);
    }

    private void openArtistSearch(String rawArtist) {
        String query = rawArtist == null ? "" : rawArtist.trim();
        if (query.isEmpty() || "未知歌手".equals(query)) return;
        openNowPlayingSearch(query);
    }

    private void openSongTitleSearch(String rawTitle) {
        String query = rawTitle == null ? "" : rawTitle.trim();
        if (query.isEmpty() || "未知歌曲".equals(query)) return;
        openNowPlayingSearch(query);
    }

    private void openNowPlayingSearch(String query) {
        pendingSearchQuery = query;
        lastQuery = query;
        openPlaylist = null;
        offlinePlaylistOpen = false;
        openSmartCollection = null;
        searchAllResultsOpen = false;
        tab = 1;

        closeNowPlaying();
        refreshNav();
        renderTab();
    }

    private void updateNowPlaying(PlaybackSnapshot s) {
        if (playerOverlay == null || playerOverlay.getVisibility() != View.VISIBLE || s.song == null) return;
        if (trackBrowseGestureActive && !trackBrowseCommitInFlight) return;
        nowTitle.setText(s.song.title);
        setNowPlayingArtistText(s.song);
        int sourceTint = s.song.variants().size() > 1 ? Ui.CYAN : ("tx".equals(s.song.source) ? Ui.GREEN : Ui.GOLD);
        nowPlayIcon.setPlaybackState(s.playing, true);
        nowFavoriteIcon.setIconColor(isFavorite(s.song) ? Ui.PINK : Ui.playerControlIconColor(Ui.PINK));
        nowFavoriteIcon.setFavoriteState(isFavorite(s.song), false);
        if (nowModeIcon != null && playback != null) {
            int mode = playback.getPlayMode();
            int accent = playModeAccent(mode);
            nowModeIcon.setType(playModeIcon(mode));
            nowModeIcon.setIconColor(Ui.playerControlIconColor(accent));
            if (nowModeButton != null) nowModeButton.setBackground(Ui.playerIconRipple(accent, 23, this));
        }
        nowTime.setText(Ui.time(s.positionMs));
        nowDuration.setText(Ui.time(s.durationMs));
        if (nowVinylStack != null) nowVinylStack.setSpinning(s.playing); else nowVinyl.setSpinning(s.playing);
        if (s.durationMs > 0) nowSeek.setProgress((int) Math.min(1000, s.positionMs * 1000 / s.durationMs));
        String artworkKey = s.song.key() + "|" + s.song.coverUrl;
        if (!artworkKey.equals(nowArtworkKey)) {
            stackArtworkKeys[2] = artworkKey;
            nowArtworkKey = artworkKey;
            prepareTrackSwipeIncoming();
            nowAccent = sourceTint;
            DesktopLyricService.setAccent(this, nowAccent);
            if (!trackSwipeAnimating) {
                if (nowVinylStack != null) nowVinylStack.setCurrentCover(null); else nowVinyl.setCoverBitmap(null);
            }
            if (!trackSwipeAnimating) {
                if (nowVinylStack != null) nowVinylStack.setAccentColor(nowAccent); else nowVinyl.setAccentColor(nowAccent);
            }
            applyPlayerStarfieldAccent();
            if (nowPlayButton != null) nowPlayButton.setBackground(Ui.primaryFill(nowAccent, 33, this));
            if (lyricUseCoverColor) applyCoverLyricGradient(nowAccent);
            if (Build.VERSION.SDK_INT >= 21) {
                nowSeek.setProgressTintList(ColorStateList.valueOf(nowAccent));
                nowSeek.setThumbTintList(ColorStateList.valueOf(nowAccent));
            }
            final int pendingSwipeDirection = trackSwipeDirection;
            final int pendingSwipeGeneration = trackSwipeGeneration;
            loadVinylStackNeighbours();
            if (trackSwipeAnimating && !trackBrowseCommitInFlight && nowVinylStack != null) {
                main.postDelayed(() -> {
                    if (pendingSwipeGeneration == trackSwipeGeneration && artworkKey.equals(nowArtworkKey)
                            && trackSwipeAnimating && !trackBrowseCommitInFlight
                            && trackSwipeDirection == pendingSwipeDirection && nowVinylStack != null) {
                        adoptTrackSwipeCenterOnce(pendingSwipeDirection, null, nowAccent);
                    }
                }, 920L);
            }
            if (trackBrowseCommitInFlight && (trackBrowseExpectedKey.isEmpty() || trackBrowseExpectedKey.equals(s.song.key()))) {
                main.postDelayed(() -> { if (trackBrowseCommitInFlight) finishTrackBrowseCommit(); }, 180L);
            }
            ImageLoader.load(s.song.coverUrl, nowCoverLoader, (bmp, color) -> {
                if (!artworkKey.equals(nowArtworkKey)) return;
                if (trackBrowseGestureActive && !trackBrowseCommitInFlight) {
                    applyStackArtwork(artworkKey, bmp, color);
                    return;
                }
                nowAccent = color;
                DesktopLyricService.setAccent(this, color);
                if (nowVinylStack != null) {
                    if (trackBrowseCommitInFlight) {
                        nowVinylStack.setAccentColor(color);
                        nowVinylStack.setCurrentCover(bmp);
                        finishTrackBrowseCommit();
                    } else if (trackSwipeAnimating && !artworkKey.equals(trackSwipeSourceKey)) {
                        final int committedDirection = trackSwipeDirection;
                        adoptTrackSwipeCenterOnce(committedDirection, bmp, color);
                    } else {
                        nowVinylStack.setAccentColor(color);
                        nowVinylStack.setCurrentCover(bmp);
                        loadVinylStackNeighbours();
                    }
                } else {
                    nowVinyl.setCoverBitmap(bmp);
                    nowVinyl.setAccentColor(color);
                    if (trackSwipeAnimating) finishTrackSwipeIncoming();
                }
                applyPlayerStarfieldAccent();
                if (nowPlayButton != null) nowPlayButton.setBackground(Ui.primaryFill(color, 33, this));
                if (lyricUseCoverColor) applyCoverLyricGradient(color);
                if (Build.VERSION.SDK_INT >= 21) {
                    nowSeek.setProgressTintList(ColorStateList.valueOf(color));
                    nowSeek.setThumbTintList(ColorStateList.valueOf(color));
                }
                if (playback != null) playback.setArtwork(bmp);
            });
        }
        loadVinylStackNeighbours();
        updateLyricHighlight(s.positionMs);
    }

    /** Keep the side vinyls warm from the existing artwork cache; no audio URL is involved. */
    @android.annotation.SuppressLint("UnsafeOptInUsageError")
    private void loadVinylStackNeighbours() {
        if (nowVinylStack == null || playback == null || trackSwipeAnimating || trackBrowseGestureActive) return;
        List<Song> queue = playback.queueSnapshot();
        int index = playback.currentQueueIndex();
        for (int slot = 0; slot < 5; slot++) {
            if (slot == 2) continue;
            final int offset = slot - 2;
            final int targetSlot = slot;
            int neighbour = VinylStackGeometry.neighbourIndex(index, offset, queue.size());
            Song song = neighbour < 0 ? null : queue.get(neighbour);
            String key = song == null ? "" : song.key() + "|" + song.coverUrl;
            if (key.equals(stackArtworkKeys[slot])) continue;
            stackArtworkKeys[slot] = key;
            nowVinylStack.setNeighbour(offset, song != null, null, nowAccent);
            ImageView loader = stackCoverLoaders[slot];
            if (loader == null) continue;
            if (song == null || song.coverUrl == null || song.coverUrl.isEmpty()) {
                loader.setTag(null);
                continue;
            }
            final String expected = key;
            ImageLoader.load(song.coverUrl, loader, (bmp, color) -> {
                applyStackArtwork(expected, bmp, color);
            });
        }
    }

    private void setStackArtwork(int slot, boolean exists, Bitmap bitmap, int color) {
        if (nowVinylStack == null) return;
        if (slot == 2) {
            nowVinylStack.setCurrentCover(bitmap);
            nowVinylStack.setAccentColor(color);
        } else nowVinylStack.setNeighbour(slot - 2, exists, bitmap, color);
    }

    /** Resolve at delivery time: pending requests travel with songs, not old slot numbers. */
    private void applyStackArtwork(String key, Bitmap bitmap, int color) {
        for (int slot = 0; slot < stackArtworkKeys.length; slot++) {
            if (key.equals(stackArtworkKeys[slot])) setStackArtwork(slot, true, bitmap, color);
        }
    }

    private void loadLyrics(Song song) {
        if (lyricsResumeRunnable != null) main.removeCallbacks(lyricsResumeRunnable);
        if (lyricScrollSettleRunnable != null) main.removeCallbacks(lyricScrollSettleRunnable);
        lyricManualGesture = false;
        lyricSeekMode = false;
        showLyricSeekGuide(false);
        lyricSongKey = song.key();
        lyricLines = new ArrayList<>();
        activeLyric = -2;
        lyricSeekCandidate = -1;
        lyricManualSelection = -1;
        if (lyricBox != null) {
            lyricBox.removeAllViews();
            lyricEmpty = Ui.text(this, "歌词加载中…", 12.5f, Ui.DIM, false);
            lyricEmpty.setGravity(Gravity.CENTER);
            lyricBox.addView(lyricEmpty, Ui.lp(-1, Ui.dp(this, 138)));
        }

        String cached = lyricCache == null ? "" : lyricCache.get(song);
        if (!cached.trim().isEmpty()) {
            lyricLines = LyricParser.parse(cached);
            rebuildLyrics();
            return;
        }

        io.submit(() -> {
            String lrc = "";
            String failure = "暂无歌词";
            try {
                com.xingyu.music.model.SourceVariant wy = song.variant("wy");
                if (wy != null) {
                    lrc = netease.lyricData(wy.sourceId).lrc;
                    failure = "网易歌词获取失败";
                } else {
                    com.xingyu.music.model.SourceVariant tx = song.variant("tx");
                    if (tx != null) {
                        // 1) Reuse a previously proven QQ -> NetEase lyric mapping.
                        String wyId = lyricMatches == null ? "" : lyricMatches.getNeteaseId(tx.sourceId);
                        if (!wyId.isEmpty()) {
                            try { lrc = netease.lyricData(wyId).lrc; }
                            catch (Exception ignored) { lrc = ""; }
                        }

                        // 2) Conservative metadata-only cross-match. This never changes Song
                        // variants, playback order, resolver health, or audio URL selection.
                        if (lrc.trim().isEmpty()) {
                            try {
                                List<Song> candidates = netease.search(song.title + " " + primaryArtistForLyric(song.artist), 12);
                                com.xingyu.music.model.SourceVariant match = LyricMatcher.bestNeteaseVariant(song, candidates);
                                if (match != null) {
                                    String candidateLrc = netease.lyricData(match.sourceId).lrc;
                                    if (!candidateLrc.trim().isEmpty()) {
                                        lrc = candidateLrc;
                                        if (lyricMatches != null) lyricMatches.putNeteaseId(tx.sourceId, match.sourceId);
                                    }
                                }
                            } catch (Exception ignored) { }
                        }

                        // 3) If NetEase has no trustworthy equivalent, fetch QQ's own LRC.
                        if (lrc.trim().isEmpty()) {
                            try { lrc = qqLyrics.lyric(tx.sourceId); }
                            catch (Exception e) { failure = "QQ 歌词获取失败"; }
                        }
                    } else {
                        failure = "当前歌曲暂未接入歌词";
                    }
                }
            } catch (Exception e) {
                failure = "歌词获取失败";
            }

            final String finalLrc = lrc == null ? "" : lrc;
            final String finalFailure = failure;
            final List<LyricLine> parsed = LyricParser.parse(finalLrc);
            main.post(() -> {
                if (!lyricSongKey.equals(song.key())) return;
                if (!parsed.isEmpty()) {
                    if (lyricCache != null) lyricCache.put(song, finalLrc);
                    lyricLines = parsed;
                    rebuildLyrics();
                } else if (lyricEmpty != null) {
                    lyricEmpty.setText(finalFailure);
                }
            });
        });
    }

    private String primaryArtistForLyric(String artist) {
        if (artist == null || artist.trim().isEmpty()) return "";
        String[] parts = artist.split("[/、&,，·]+");
        return parts.length == 0 ? artist.trim() : parts[0].trim();
    }

    private void rebuildLyrics() {
        if (lyricBox == null) return;
        lyricBox.removeAllViews();
        if (lyricLines.isEmpty()) {
            lyricEmpty = Ui.text(this, "暂无歌词", 12.5f, Ui.DIM, false);
            lyricEmpty.setGravity(Gravity.CENTER);
            lyricBox.addView(lyricEmpty, Ui.lp(-1, Ui.dp(this, 138)));
            return;
        }
        for (int i = 0; i < lyricLines.size(); i++) {
            LyricLine line = lyricLines.get(i);
            final int lyricIndex = i;
            LyricLineView t = new LyricLineView(this);
            t.setFontScale(lyricFontScale);
            t.setText(line.text);
            t.setGradientColors(lyricGradientStart, lyricGradientEnd);
            t.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
            t.setMinHeight(Ui.dp(this, 48));
            t.setOnClickListener(v -> {
                if (lyricManualSelection == lyricIndex && playback != null) {
                    long seekMs = lyricLines.get(lyricIndex).timeMs;
                    playback.seekTo(seekMs);
                    clearManualLyricSelection(true);
                    lyricsManualUntil = 0L;
                    main.postDelayed(() -> scrollLyricToActive(true), 120L);
                }
            });
            lyricBox.addView(t, Ui.lp(-1, -2));
        }
        activeLyric = -2;
        if (playback != null) updateLyricHighlight(playback.snapshot().positionMs);
        if (lyricPanel != null) lyricPanel.postDelayed(() -> maybeShowContextCoach(COACH_LYRIC_SEEK, lyricPanel,
                "歌词不只是用来看的",
                "在歌词框里上下滑动会出现定位线和时间。停下后选中一句，再点一次即可跳到那句对应的播放位置；之后歌词会继续自动跟随。"), 180L);
    }

    private void showLyricSeekGuide(boolean visible) {
        if (lyricSeekGuide == null) return;
        if (visible) {
            if (lyricSeekGuide.getVisibility() != View.VISIBLE) {
                lyricSeekGuide.setVisibility(View.VISIBLE);
                lyricSeekGuide.animate().cancel();
                lyricSeekGuide.animate().alpha(1f).setDuration(110).start();
            }
        } else if (lyricSeekGuide.getVisibility() == View.VISIBLE) {
            lyricSeekGuide.animate().cancel();
            lyricSeekGuide.animate().alpha(0f).setDuration(150)
                    .withEndAction(() -> lyricSeekGuide.setVisibility(View.GONE)).start();
        }
    }

    private void updateLyricSeekCandidate() {
        if (lyricScroll == null || lyricBox == null || lyricLines.isEmpty()) return;
        int targetY = lyricScroll.getScrollY() + lyricScroll.getHeight() / 2;
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        int count = Math.min(lyricLines.size(), lyricBox.getChildCount());
        for (int i = 0; i < count; i++) {
            View child = lyricBox.getChildAt(i);
            int center = child.getTop() + child.getHeight() / 2;
            int distance = Math.abs(center - targetY);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        lyricSeekCandidate = best;
        if (best >= 0 && lyricSeekGuideTime != null) {
            lyricSeekGuideTime.setText(lyricGuideTime(lyricLines.get(best).timeMs));
        }
    }

    private String lyricGuideTime(long ms) {
        long total = Math.max(0L, ms) / 1000L;
        return String.format(Locale.US, "%02d:%02d", total / 60L, total % 60L);
    }

    private void scheduleLyricManualSelection() {
        if (lyricScrollSettleRunnable != null) main.removeCallbacks(lyricScrollSettleRunnable);
        lyricScrollSettleRunnable = () -> {
            if (lyricManualGesture) return;
            updateLyricSeekCandidate();
            setManualLyricSelection(lyricSeekCandidate);
            lyricSeekMode = false;
            showLyricSeekGuide(false);
            // Keep the focused lyric selection briefly visible, then return to live lyrics.
            lyricsManualUntil = SystemClock.uptimeMillis() + 2000L;
            if (lyricsResumeRunnable != null) main.removeCallbacks(lyricsResumeRunnable);
            lyricsResumeRunnable = () -> {
                clearManualLyricSelection(false);
                lyricsManualUntil = 0L;
                scrollLyricToActive(true);
            };
            main.postDelayed(lyricsResumeRunnable, 2000L);
        };
        main.postDelayed(lyricScrollSettleRunnable, 170L);
    }

    private void setManualLyricSelection(int index) {
        lyricManualSelection = (index >= 0 && index < lyricLines.size()) ? index : -1;
        if (lyricBox == null) return;
        for (int i = 0; i < lyricBox.getChildCount(); i++) {
            View child = lyricBox.getChildAt(i);
            if (child instanceof LyricLineView) {
                ((LyricLineView) child).setSeekSelected(i == lyricManualSelection);
            }
        }
    }

    private void clearManualLyricSelection(boolean cancelResume) {
        lyricManualSelection = -1;
        lyricSeekCandidate = -1;
        if (cancelResume) {
            lyricSeekMode = false;
            showLyricSeekGuide(false);
            if (lyricsResumeRunnable != null) main.removeCallbacks(lyricsResumeRunnable);
            if (lyricScrollSettleRunnable != null) main.removeCallbacks(lyricScrollSettleRunnable);
        }
        if (lyricBox != null) {
            for (int i = 0; i < lyricBox.getChildCount(); i++) {
                View child = lyricBox.getChildAt(i);
                if (child instanceof LyricLineView) ((LyricLineView) child).setSeekSelected(false);
            }
        }
    }

    private void updateLyricHighlight(long pos) {
        if (lyricBox == null || lyricLines.isEmpty()) return;
        int idx = LyricParser.activeIndex(lyricLines, pos);
        boolean changed = idx != activeLyric;
        if (changed) {
            activeLyric = idx;
            for (int i = 0; i < lyricBox.getChildCount(); i++) {
                View v = lyricBox.getChildAt(i);
                if (v instanceof LyricLineView) {
                    LyricLineView line = (LyricLineView) v;
                    line.setGradientColors(lyricGradientStart, lyricGradientEnd);
                    line.setFocusDistance(Math.abs(i - idx));
                    line.setActiveLine(i == idx);
                    line.setKaraokeProgress(0f);
                }
            }
            scrollLyricToActive(false);
        }
    }

    private void updateLyricKaraokeProgress(long pos) {
        if (lyricBox == null || lyricLines.isEmpty()) return;
        int idx = LyricParser.activeIndex(lyricLines, pos);
        if (idx != activeLyric) updateLyricHighlight(pos);
        if (idx < 0 || idx >= lyricLines.size() || idx >= lyricBox.getChildCount()) return;

        LyricLine lyric = lyricLines.get(idx);
        float progress = -1f;
        // Prefer genuine enhanced-LRC word/syllable timing when the provider exposes it.  The
        // renderer consumes a normalized text fraction, so word timing stays independent from
        // font metrics and remains cheap enough for the 72 ms playback ticker.
        if (lyric.hasWordTiming()) {
            int timedChars = 0;
            for (LyricWord word : lyric.words) timedChars += Math.max(1, word.text.length());
            if (timedChars > 0) {
                float litChars = 0f;
                for (LyricWord word : lyric.words) {
                    int chars = Math.max(1, word.text.length());
                    if (pos >= word.endMs()) {
                        litChars += chars;
                    } else if (pos > word.startMs) {
                        float wp = (pos - word.startMs) / (float)Math.max(1L, word.durationMs);
                        litChars += chars * Math.max(0f, Math.min(1f, wp));
                        break;
                    } else {
                        break;
                    }
                }
                progress = Math.max(0f, Math.min(1f, litChars / timedChars));
            }
        }

        // Plain LRC has line timestamps only.  Fall back to a stable line-time sweep instead of
        // faking word timing; the next timestamp is a better bound than a fixed animation length.
        if (progress < 0f) {
            long start = Math.max(0L, lyric.timeMs);
            long end;
            if (lyric.endMs > start) end = lyric.endMs;
            else if (idx + 1 < lyricLines.size()) end = Math.max(start + 600L, lyricLines.get(idx + 1).timeMs);
            else {
                long duration = playback == null ? 0L : playback.snapshot().durationMs;
                end = duration > start ? duration : start + 4200L;
            }
            progress = Math.max(0f, Math.min(1f, (pos - start) / (float)Math.max(1L, end - start)));
        }

        View row = lyricBox.getChildAt(idx);
        if (row instanceof LyricLineView) ((LyricLineView) row).setKaraokeProgress(progress);
    }

    private void scrollLyricToActive(boolean force) {
        if (lyricScroll == null || lyricBox == null || activeLyric < 0 || activeLyric >= lyricBox.getChildCount()) return;
        if (!force && SystemClock.uptimeMillis() < lyricsManualUntil) return;
        View line = lyricBox.getChildAt(activeLyric);
        lyricScroll.post(() -> {
            if (lyricScroll == null || line.getParent() == null) return;
            int viewport = lyricScroll.getHeight() - lyricScroll.getPaddingTop() - lyricScroll.getPaddingBottom();
            int target = line.getTop() - Math.max(0, (viewport - line.getHeight()) / 2);
            target = Math.max(0, Math.min(target, Math.max(0, lyricBox.getHeight() - viewport)));
            int delta = Math.abs(lyricScroll.getScrollY() - target);
            if (delta < Ui.dp(this, 4)) return;
            if (force || delta > Ui.dp(this, 160)) lyricScroll.smoothScrollTo(0, target);
            else lyricScroll.smoothScrollBy(0, target - lyricScroll.getScrollY());
        });
    }

    /**
     * V55 Starfield Studio: a visual-only control surface inspired by compact commercial media
     * panels.  Four tabs keep mode/motion/colour/atmosphere separate; every change previews live
     * on the player behind the sheet and persists independently from playback/library state.
     */
    private void showStarfieldStudio() {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 14));
        card.setBackground(Ui.transientGlass(27, this));
        card.setElevation(Ui.dp(this, 12));

        LinearLayout header = Ui.row(this);
        LinearLayout titleStack = Ui.column(this);
        TextView title = Ui.text(this, "星空实验室", 19.5f, Ui.TEXT, true);
        TextView subtitle = Ui.text(this, "实时预览 · 播放详情视觉与手势", 10.8f, Ui.TEXT_2, false);
        titleStack.addView(title, Ui.lp(-1, Ui.dp(this, 29)));
        titleStack.addView(subtitle, Ui.lp(-1, Ui.dp(this, 22)));
        header.addView(titleStack, new LinearLayout.LayoutParams(0, Ui.dp(this, 54), 1f));

        TextView reset = Ui.text(this, "重置", 10.8f, Ui.TEXT_2, true);
        reset.setGravity(Gravity.CENTER);
        reset.setBackground(Ui.glass(72, 14, 20, this));
        reset.setClickable(true); Ui.applyRipple(reset, Color.TRANSPARENT);
        header.addView(reset, Ui.lp(Ui.dp(this, 54), Ui.dp(this, 34)));

        TextView done = Ui.text(this, "完成", 10.8f, Color.rgb(10, 17, 21), true);
        done.setGravity(Gravity.CENTER);
        done.setBackground(Ui.primaryFill(Ui.CYAN, 14, this));
        done.setClickable(true); Ui.applyRipple(done, Color.TRANSPARENT);
        LinearLayout.LayoutParams dp = Ui.lp(Ui.dp(this, 58), Ui.dp(this, 34)); dp.leftMargin = Ui.dp(this, 8);
        header.addView(done, dp);
        card.addView(header, Ui.lp(-1, Ui.dp(this, 56)));

        LinearLayout tabs = Ui.row(this);
        tabs.setPadding(Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3));
        tabs.setBackground(Ui.glass(58, 17, 18, this));
        LinearLayout.LayoutParams tp = Ui.lp(-1, Ui.dp(this, 44)); tp.topMargin = Ui.dp(this, 9);
        card.addView(tabs, tp);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout body = Ui.column(this);
        body.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        scroll.addView(body);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, 0, 1f); sp.topMargin = Ui.dp(this, 4);
        card.addView(scroll, sp);

        final Runnable[] redraw = new Runnable[1];
        redraw[0] = () -> {
            tabs.removeAllViews();
            body.removeAllViews();
            String[] names = {"模式", "动态", "星光", "背景"};
            for (int i = 0; i < names.length; i++) {
                final int idx = i;
                TextView t = starStudioTab(names[i], starfieldStudioTab == i);
                t.setOnClickListener(v -> { starfieldStudioTab = idx; redraw[0].run(); });
                LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, -1, 1f);
                if (i > 0) p2.leftMargin = Ui.dp(this, 3);
                tabs.addView(t, p2);
            }
            renderStarfieldStudioTab(body, redraw[0]);
        };

        reset.setOnClickListener(v -> {
            resetStarfieldPreferences();
            redraw[0].run();
            toast("星空设置已恢复默认");
        });
        done.setOnClickListener(v -> hideModal());
        redraw[0].run();
        presentFractionModal(card, .82f);
    }

    private TextView starStudioTab(String label, boolean selected) {
        TextView v = Ui.text(this, label, 11.1f, selected ? Ui.TEXT : Ui.DIM, true);
        v.setGravity(Gravity.CENTER);
        v.setClickable(true); Ui.applyRipple(v, Color.TRANSPARENT);
        if (selected) v.setBackground(Ui.tintedGlass(Ui.CYAN, 14, this));
        else v.setBackgroundColor(Color.TRANSPARENT);
        return v;
    }

    private void renderStarfieldStudioTab(LinearLayout body, Runnable redraw) {
        if (starfieldStudioTab == 0) renderStarfieldModeTab(body, redraw);
        else if (starfieldStudioTab == 1) renderStarfieldMotionTab(body, redraw);
        else if (starfieldStudioTab == 2) renderStarfieldLightTab(body, redraw);
        else renderStarfieldBackgroundTab(body, redraw);
    }

    private void renderStarfieldModeTab(LinearLayout body, Runnable redraw) {
        body.addView(starStudioHero(
                starfieldUse3D ? "3D 沉浸星空" : "2D 经典星河",
                starfieldUse3D ? "360° 姿态视角 · 星光持续运动" : "经典粒子轨迹 · 轻盈视差与柔光",
                starfieldUse3D ? Ui.CYAN : Ui.PURPLE), marginTop(3));

        body.addView(starStudioSection("渲染模式", "两种星空风格，切换后立即生效。"), marginTop(12));
        LinearLayout modes = Ui.row(this);
        View mode3d = starStudioChoice("3D 沉浸", "空间镜头", starfieldUse3D, Ui.CYAN, v -> {
            starfieldUse3D = true; saveStarfieldPreferences(); applyStarfieldPreferences(); redraw.run();
        });
        View mode2d = starStudioChoice("2D 经典", "经典星河", !starfieldUse3D, Ui.PURPLE, v -> {
            starfieldUse3D = false; saveStarfieldPreferences(); applyStarfieldPreferences(); redraw.run();
        });
        modes.addView(mode3d, new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1f));
        LinearLayout.LayoutParams m2 = new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1f); m2.leftMargin = Ui.dp(this, 8);
        modes.addView(mode2d, m2);
        body.addView(modes, marginTop(7));

        body.addView(starStudioSection("运动方式", starfieldUse3D
                ? "三种运动风格，随时切换。"
                : "经典星河保持原有运动手感。"), marginTop(16));
        LinearLayout motion = Ui.row(this);
        boolean living = starfieldMotionProfile == Starfield3DView.PROFILE_LIVING;
        boolean classicSelected = starfieldMotionProfile == Starfield3DView.PROFILE_CLASSIC_FORWARD;
        boolean deepSelected = starfieldMotionProfile == Starfield3DView.PROFILE_DEEP_SPACE;
        View mix = starStudioChoice("灵性混合", "Flow · Curve · Orbit", living, Ui.CYAN, v -> {
            starfieldMotionProfile = Starfield3DView.PROFILE_LIVING;
            saveStarfieldPreferences(); applyStarfieldPreferences(); redraw.run();
        });
        View classic = starStudioChoice("经典穿越", "纯净向前", classicSelected, Ui.GREEN, v -> {
            starfieldMotionProfile = Starfield3DView.PROFILE_CLASSIC_FORWARD;
            saveStarfieldPreferences(); applyStarfieldPreferences(); redraw.run();
        });
        mix.setAlpha(starfieldUse3D ? 1f : .42f); classic.setAlpha(starfieldUse3D ? 1f : .42f);
        mix.setEnabled(starfieldUse3D); classic.setEnabled(starfieldUse3D);
        motion.addView(mix, new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1f));
        LinearLayout.LayoutParams c2 = new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1f); c2.leftMargin = Ui.dp(this, 8);
        motion.addView(classic, c2);
        body.addView(motion, marginTop(7));

        View deep = starStudioChoice("深空航行", "旅行者 · 星河隧道", deepSelected, Ui.PURPLE, v -> {
            starfieldMotionProfile = Starfield3DView.PROFILE_DEEP_SPACE;
            saveStarfieldPreferences(); applyStarfieldPreferences(); redraw.run();
        });
        deep.setAlpha(starfieldUse3D ? 1f : .42f);
        deep.setEnabled(starfieldUse3D);
        LinearLayout.LayoutParams deepLp = Ui.lp(-1, Ui.dp(this, 64));
        deepLp.topMargin = Ui.dp(this, 8);
        body.addView(deep, deepLp);

    }

    private void renderStarfieldMotionTab(LinearLayout body, Runnable redraw) {
        body.addView(starStudioDurationSlider("播放器展开动画",
                "调整 Mini Player 进入播放详情的视觉时长；只影响界面动画，不影响音乐播放与进度。",
                playerOpenMorphDurationMs, value -> {
                    playerOpenMorphDurationMs = value;
                    saveStarfieldPreferences();
                }), marginTop(4));
        body.addView(starStudioSlider("切歌滑动灵敏度", "越低需要滑得越远；默认已降低灵敏度，支持连续浏览多首。",
                .5f, 1.5f, vinylSwipeSensitivity, v -> {
                    vinylSwipeSensitivity = v; saveStarfieldPreferences(); applyStarfieldPreferences();
                }, false), marginTop(4));
        body.addView(starStudioSection("快速手感", "一键调整速度与姿态灵敏度。"), marginTop(4));
        LinearLayout quick = Ui.row(this);
        String[] names = {"轻柔", "标准", "沉浸"};
        float[][] vals = {{.72f, .68f}, {1f, 1f}, {1.38f, 1.30f}};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            boolean selected = Math.abs(starfieldSpeed - vals[i][0]) < .05f && Math.abs(starfieldSensitivity - vals[i][1]) < .05f;
            TextView b = starQuickPreset(names[i], selected);
            b.setOnClickListener(v -> {
                starfieldSpeed = vals[idx][0]; starfieldSensitivity = vals[idx][1];
                saveStarfieldPreferences(); applyStarfieldPreferences(); redraw.run();
            });
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1f);
            if (i > 0) bp.leftMargin = Ui.dp(this, 7);
            quick.addView(b, bp);
        }
        body.addView(quick, marginTop(8));

        body.addView(starStudioSlider("粒子速度", "调节 3D 星空向前运动的节奏。",
                .45f, 1.90f, starfieldSpeed, v -> {
                    starfieldSpeed = v; saveStarfieldPreferences(); applyStarfieldPreferences();
                }, true), marginTop(16));
        body.addView(starStudioSlider("晃动灵敏度", "调节手机转动时的视角响应强度。",
                .35f, 1.85f, starfieldSensitivity, v -> {
                    starfieldSensitivity = v; saveStarfieldPreferences(); applyStarfieldPreferences();
                }, true), marginTop(10));

        if (starfieldUse3D && starfieldMotionProfile == Starfield3DView.PROFILE_CLASSIC_FORWARD) {
            body.addView(starInfoCard("经典穿越", "星点持续由深处向镜头推进，节奏纯净直接。", Ui.GREEN), marginTop(12));
        } else if (starfieldUse3D && starfieldMotionProfile == Starfield3DView.PROFILE_DEEP_SPACE) {
            body.addView(starInfoCard("深空航行", "低密度分层掠过与轻柔尾迹，营造持续航行的纵深感。", Ui.PURPLE), marginTop(12));
        }

        if (!starfieldUse3D) {
            body.addView(starInfoCard("2D 经典模式", "速度与姿态设置仅作用于 3D 星空。", Ui.PURPLE), marginTop(12));
        } else {
            body.addView(starInfoCard("姿态控制", "转动手机改变观察方向，粒子运动持续进行。", Ui.CYAN), marginTop(12));
        }
    }

    private void renderStarfieldLightTab(LinearLayout body, Runnable redraw) {
        body.addView(starStudioSection("粒子颜色", "选择星光色彩；“跟随封面”会随歌曲自动变化。"), marginTop(4));
        String[] names = {"跟随封面", "冰蓝", "暖黄", "星紫", "薄荷", "玫瑰", "银白"};
        int[] colors = {nowAccent, Color.rgb(151,220,255), Color.rgb(255,214,143), Color.rgb(191,166,255),
                Color.rgb(142,232,198), Color.rgb(255,167,210), Color.rgb(226,235,248)};
        for (int row = 0; row < 4; row++) {
            LinearLayout line = Ui.row(this);
            for (int col = 0; col < 2; col++) {
                int idx = row * 2 + col;
                if (idx >= names.length) {
                    line.addView(new Space(this), new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f));
                    continue;
                }
                final int mode = idx;
                View c = starColorChoice(names[idx], colors[idx], starfieldColorMode == mode, v -> {
                    starfieldColorMode = mode; saveStarfieldPreferences(); applyStarfieldPreferences(); redraw.run();
                });
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f);
                if (col > 0) cp.leftMargin = Ui.dp(this, 8);
                line.addView(c, cp);
            }
            body.addView(line, marginTop(row == 0 ? 8 : 7));
        }
        body.addView(starStudioSection("星点质感", "调节 3D 星空的大小、亮度与密度。"), marginTop(16));
        body.addView(starStudioSlider("星点大小", "调节星点整体视觉尺寸。",
                .70f, 1.55f, starfieldStarSize, v -> {
                    starfieldStarSize = v; saveStarfieldPreferences(); applyStarfieldPreferences();
                }, true), marginTop(8));
        body.addView(starStudioSlider("星点亮度", "调节星点与柔光的整体亮度。",
                .65f, 1.55f, starfieldStarBrightness, v -> {
                    starfieldStarBrightness = v; saveStarfieldPreferences(); applyStarfieldPreferences();
                }, true), marginTop(10));
        body.addView(starStudioSlider("星点密度", "实时增减可见星点数量。",
                .60f, 1.35f, starfieldStarDensity, v -> {
                    starfieldStarDensity = v; saveStarfieldPreferences(); applyStarfieldPreferences();
                }, true), marginTop(10));

        body.addView(starStudioSection("唱片景深", "控制播放详情左右历史/未来唱片的清晰度；默认完全清晰。"), marginTop(16));
        body.addView(starStudioSlider("侧唱片清晰度", starfieldVinylClarity > .92f
                        ? "当前为清晰；向左调会逐渐增加背景景深。"
                        : "只改变视觉虚化，不改变封面缓存或播放。",
                0f, 1f, starfieldVinylClarity, v -> {
                    starfieldVinylClarity = v; saveStarfieldPreferences(); applyStarfieldPreferences();
                }, false), marginTop(8));

        if (!starfieldUse3D) {
            body.addView(starInfoCard("2D 经典模式", "大小、亮度与密度设置仅作用于 3D 星空。", Ui.PURPLE), marginTop(12));
        }
    }

    private void renderStarfieldBackgroundTab(LinearLayout body, Runnable redraw) {
        body.addView(starStudioSection("背景氛围", "保持黑色宇宙底色，仅叠加克制的环境微光。"), marginTop(4));
        String[] names = {"纯黑", "封面微光", "深空蓝", "星云紫", "暖琥珀", "暮光玫瑰"};
        int[] colors = {Color.BLACK, nowAccent, Color.rgb(62,113,182), Color.rgb(126,92,201), Color.rgb(198,133,72), Color.rgb(180,81,132)};
        for (int row = 0; row < 3; row++) {
            LinearLayout line = Ui.row(this);
            for (int col = 0; col < 2; col++) {
                int idx = row * 2 + col;
                final int mode = idx;
                View c = starAmbientChoice(names[idx], colors[idx], starfieldBackgroundMode == mode, v -> {
                    starfieldBackgroundMode = mode; saveStarfieldPreferences(); applyStarfieldPreferences(); redraw.run();
                });
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, Ui.dp(this, 56), 1f);
                if (col > 0) cp.leftMargin = Ui.dp(this, 8);
                line.addView(c, cp);
            }
            body.addView(line, marginTop(row == 0 ? 8 : 7));
        }
        View glow = starStudioSlider("光晕强度", starfieldBackgroundMode == 0
                        ? "纯黑模式不绘制背景光晕；选择其它氛围后即可调节。"
                        : "只影响大范围背景空气感，不改变星点亮度和 UI 颜色。",
                0f, .62f, starfieldBackgroundGlow, v -> {
                    starfieldBackgroundGlow = v; saveStarfieldPreferences(); applyStarfieldPreferences();
                }, false);
        glow.setAlpha(starfieldBackgroundMode == 0 ? .42f : 1f);
        glow.setEnabled(starfieldBackgroundMode != 0);
        body.addView(glow, marginTop(14));
    }

    private View starStudioHero(String title, String subtitle, int accent) {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        card.setBackground(Ui.tintedGlass(accent, 18, this));
        TextView t = Ui.text(this, title, 14.2f, Ui.TEXT, true);
        TextView s = Ui.text(this, subtitle, 10.7f, Ui.TEXT_2, false);
        card.addView(t, Ui.lp(-1, Ui.dp(this, 27)));
        card.addView(s, Ui.lp(-1, Ui.dp(this, 24)));
        return card;
    }

    private View starStudioSection(String title, String subtitle) {
        LinearLayout c = Ui.column(this);
        TextView t = Ui.text(this, title, 13.2f, Ui.TEXT, true);
        TextView s = Ui.text(this, subtitle, 10.4f, Ui.DIM, false);
        s.setLineSpacing(0, 1.12f);
        c.addView(t, Ui.lp(-1, Ui.dp(this, 28)));
        c.addView(s, Ui.lp(-1, -2));
        return c;
    }

    private View starStudioChoice(String title, String subtitle, boolean selected, int accent, View.OnClickListener action) {
        LinearLayout c = Ui.column(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        c.setBackground(selected ? Ui.tintedGlass(accent, 17, this) : Ui.glass(62, 17, 18, this));
        c.setClickable(true); Ui.applyRipple(c, Color.TRANSPARENT); c.setOnClickListener(action);
        TextView t = Ui.text(this, (selected ? "✓  " : "") + title, 12f, selected ? Ui.TEXT : Ui.TEXT_2, true);
        TextView s = Ui.text(this, subtitle, 9.7f, selected ? Ui.mix(accent, Color.WHITE, .28f) : Ui.DIM, false);
        c.addView(t, Ui.lp(-1, Ui.dp(this, 28)));
        c.addView(s, Ui.lp(-1, Ui.dp(this, 21)));
        return c;
    }

    private TextView starQuickPreset(String label, boolean selected) {
        TextView t = Ui.text(this, label, 10.8f, selected ? Ui.TEXT : Ui.TEXT_2, true);
        t.setGravity(Gravity.CENTER); t.setClickable(true); Ui.applyRipple(t, Color.TRANSPARENT);
        t.setBackground(selected ? Ui.tintedGlass(Ui.CYAN, 14, this) : Ui.glass(58, 14, 18, this));
        return t;
    }

    private interface IntAction { void run(int value); }

    private View starStudioDurationSlider(String title, String subtitle, int value, IntAction action) {
        LinearLayout c = Ui.column(this);
        c.setPadding(Ui.dp(this, 13), Ui.dp(this, 11), Ui.dp(this, 13), Ui.dp(this, 9));
        c.setBackground(Ui.glass(62, 17, 18, this));
        LinearLayout top = Ui.row(this);
        TextView t = Ui.text(this, title, 12.4f, Ui.TEXT, true);
        TextView v = Ui.text(this, value + " ms", 11.2f, Ui.CYAN, true);
        v.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        top.addView(t, new LinearLayout.LayoutParams(0, Ui.dp(this, 28), 1f));
        top.addView(v, Ui.lp(Ui.dp(this, 72), Ui.dp(this, 28)));
        c.addView(top, Ui.lp(-1, Ui.dp(this, 28)));
        TextView sub = Ui.text(this, subtitle, 9.8f, Ui.DIM, false);
        c.addView(sub, Ui.lp(-1, Ui.dp(this, 24)));
        SeekBar bar = new SeekBar(this);
        bar.setMax(PLAYER_OPEN_DURATION_MAX_MS - PLAYER_OPEN_DURATION_MIN_MS);
        bar.setProgress(Math.max(0, Math.min(bar.getMax(), value - PLAYER_OPEN_DURATION_MIN_MS)));
        if (Build.VERSION.SDK_INT >= 21) {
            bar.setProgressTintList(ColorStateList.valueOf(Ui.CYAN));
            bar.setThumbTintList(ColorStateList.valueOf(Ui.mix(Ui.CYAN, Color.WHITE, .30f)));
        }
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                int next = PLAYER_OPEN_DURATION_MIN_MS + progress;
                v.setText(next + " ms");
                action.run(next);
            }
        });
        c.addView(bar, Ui.lp(-1, Ui.dp(this, 36)));
        return c;
    }

    private View starStudioSlider(String title, String subtitle, float min, float max, float value,
                                  FloatAction action, boolean multiplier) {
        LinearLayout c = Ui.column(this);
        c.setPadding(Ui.dp(this, 13), Ui.dp(this, 11), Ui.dp(this, 13), Ui.dp(this, 9));
        c.setBackground(Ui.glass(62, 17, 18, this));
        LinearLayout top = Ui.row(this);
        TextView t = Ui.text(this, title, 12.4f, Ui.TEXT, true);
        TextView v = Ui.text(this, formatStarSlider(value, min, max, multiplier), 11.2f, Ui.CYAN, true);
        v.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        top.addView(t, new LinearLayout.LayoutParams(0, Ui.dp(this, 28), 1f));
        top.addView(v, Ui.lp(Ui.dp(this, 64), Ui.dp(this, 28)));
        c.addView(top, Ui.lp(-1, Ui.dp(this, 28)));
        TextView sub = Ui.text(this, subtitle, 9.8f, Ui.DIM, false);
        c.addView(sub, Ui.lp(-1, Ui.dp(this, 24)));
        SeekBar bar = new SeekBar(this);
        bar.setMax(1000);
        int progress = Math.round((value - min) / Math.max(.0001f, max - min) * 1000f);
        bar.setProgress(Math.max(0, Math.min(1000, progress)));
        if (Build.VERSION.SDK_INT >= 21) {
            bar.setProgressTintList(ColorStateList.valueOf(Ui.CYAN));
            bar.setThumbTintList(ColorStateList.valueOf(Ui.mix(Ui.CYAN, Color.WHITE, .30f)));
        }
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                float next = min + (max - min) * progress / 1000f;
                v.setText(formatStarSlider(next, min, max, multiplier));
                action.run(next);
            }
        });
        c.addView(bar, Ui.lp(-1, Ui.dp(this, 36)));
        return c;
    }

    private String formatStarSlider(float value, float min, float max, boolean multiplier) {
        if (multiplier) return String.format(Locale.US, "%.2f×", value);
        return Math.round(value / Math.max(.001f, max) * 100f) + "%";
    }

    private View starColorChoice(String label, int color, boolean selected, View.OnClickListener action) {
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 11), 0, Ui.dp(this, 10), 0);
        row.setBackground(selected ? Ui.tintedGlass(color, 15, this) : Ui.glass(56, 15, 16, this));
        row.setClickable(true); Ui.applyRipple(row, Color.TRANSPARENT); row.setOnClickListener(action);
        View dot = new View(this);
        dot.setBackground(Ui.stroke(Ui.mix(color, Color.WHITE, .15f), 10, Color.argb(70,255,255,255), this));
        row.addView(dot, Ui.lp(Ui.dp(this, 20), Ui.dp(this, 20)));
        TextView t = Ui.text(this, (selected ? "✓  " : "") + label, 10.9f, selected ? Ui.TEXT : Ui.TEXT_2, true);
        t.setPadding(Ui.dp(this, 9), 0, 0, 0);
        row.addView(t, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f));
        return row;
    }

    private View starAmbientChoice(String label, int color, boolean selected, View.OnClickListener action) {
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        int tint = color == Color.BLACK ? Ui.TEXT_2 : color;
        row.setBackground(selected ? Ui.tintedGlass(tint, 16, this) : Ui.glass(56, 16, 16, this));
        row.setClickable(true); Ui.applyRipple(row, Color.TRANSPARENT); row.setOnClickListener(action);
        View swatch = new View(this);
        swatch.setBackground(Ui.stroke(color == Color.BLACK ? Color.rgb(3,3,5) : Ui.mix(color, Color.BLACK, .35f),
                11, color == Color.BLACK ? Color.argb(60,255,255,255) : Color.argb(80, Color.red(color), Color.green(color), Color.blue(color)), this));
        row.addView(swatch, Ui.lp(Ui.dp(this, 27), Ui.dp(this, 27)));
        TextView t = Ui.text(this, (selected ? "✓  " : "") + label, 10.6f, selected ? Ui.TEXT : Ui.TEXT_2, true);
        t.setPadding(Ui.dp(this, 9), 0, 0, 0);
        row.addView(t, new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f));
        return row;
    }

    private View starInfoCard(String title, String message, int accent) {
        LinearLayout c = Ui.column(this);
        c.setPadding(Ui.dp(this, 13), Ui.dp(this, 10), Ui.dp(this, 13), Ui.dp(this, 10));
        c.setBackground(Ui.tintedGlass(accent, 16, this));
        TextView t = Ui.text(this, title, 11.2f, Ui.mix(accent, Color.WHITE, .34f), true);
        TextView m = Ui.text(this, message, 9.8f, Ui.TEXT_2, false);
        m.setLineSpacing(0, 1.16f);
        c.addView(t, Ui.lp(-1, Ui.dp(this, 25)));
        c.addView(m, Ui.lp(-1, -2));
        return c;
    }

    private void showLyricStylePicker() {
        LinearLayout card = modalCard("歌词高亮", "每一项都直接预览真实高亮效果。颜色只影响歌词显示，不影响歌曲、音源或播放链。");
        ScrollView optionsScroll = new ScrollView(this);
        optionsScroll.setVerticalScrollBarEnabled(false);
        LinearLayout options = Ui.column(this);
        optionsScroll.addView(options);
        options.addView(starStudioSlider("歌词字号", "调整歌词大小，立即生效。",.8f,1.6f,lyricFontScale,value -> {
            lyricFontScale=value;
            getSharedPreferences("xingyu_ui_settings",MODE_PRIVATE).edit().putFloat("lyric_font_scale",value).apply();
            if(lyricBox!=null) for(int i=0;i<lyricBox.getChildCount();i++) {
                View row=lyricBox.getChildAt(i);
                if(row instanceof LyricLineView) ((LyricLineView)row).setFontScale(value);
            }
        },false),Ui.lp(-1,-2));
        LinearLayout.LayoutParams osp = new LinearLayout.LayoutParams(-1, 0, 1f);
        osp.topMargin = Ui.dp(this, 6);
        card.addView(optionsScroll, osp);
        int[][] presets = new int[][]{
                {Ui.LYRIC_ICE_START, Ui.LYRIC_ICE_END},
                {Color.rgb(222, 211, 255), Color.rgb(143, 126, 255)},
                {Color.rgb(205, 251, 235), Color.rgb(103, 225, 190)},
                {Color.rgb(255, 226, 190), Color.rgb(255, 153, 116)},
                {Color.rgb(255, 210, 232), Color.rgb(255, 135, 184)},
                {Color.rgb(255, 255, 255), Color.rgb(205, 220, 244)}
        };
        String[] names = {"冰蓝", "星紫", "薄荷", "日落", "玫瑰", "银白"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            LyricLineView preview = new LyricLineView(this);
            preview.setText(names[i] + "  ·  正在唱的这一句");
            preview.setGradientColors(presets[i][0], presets[i][1]);
            preview.setActiveLine(true);
            preview.setKaraokeProgress(1f); // style chooser must show the whole gradient, not the gray base pass
            preview.setGravity(Gravity.CENTER_VERTICAL);
            preview.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            preview.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
            preview.setBackground(Ui.glass(92, 16, 18, this));
            preview.setClickable(true);
            Ui.applyRipple(preview, Color.TRANSPARENT);
            preview.setOnClickListener(v -> {
                dismissModalNow();
                saveLyricGradient(presets[index][0], presets[index][1], false);
            });
            LinearLayout.LayoutParams rp = Ui.lp(-1, Ui.dp(this, 50));
            rp.topMargin = Ui.dp(this, 8);
            options.addView(preview, rp);
        }

        TextView cover = outlineAction("跟随歌曲封面颜色");
        cover.setOnClickListener(v -> {
            dismissModalNow();
            lyricUseCoverColor = true;
            getSharedPreferences("xingyu_ui_settings", MODE_PRIVATE).edit().putBoolean("lyric_gradient_cover", true).apply();
            applyCoverLyricGradient(nowAccent);
            toast("歌词高亮已跟随封面");
        });
        LinearLayout.LayoutParams cp = Ui.lp(-1, Ui.dp(this, 46)); cp.topMargin = Ui.dp(this, 10); options.addView(cover, cp);

        TextView custom = outlineAction("自定义渐变颜色");
        custom.setOnClickListener(v -> {
            dismissModalNow();
            main.postDelayed(this::showLyricCustomColorPicker, 90L);
        });
        LinearLayout.LayoutParams xp = Ui.lp(-1, Ui.dp(this, 46)); xp.topMargin = Ui.dp(this, 8); options.addView(custom, xp);
        presentFractionModal(card, .80f);
    }

    private void showLyricCustomColorPicker() {
        LinearLayout card = modalCard("自定义歌词高亮", "拖动选色器选颜色，再一键设为渐变起点或终点，最后应用即可。");
        final int[] draft = {lyricGradientStart, lyricGradientEnd};
        final int[] picked = {lyricGradientStart};

        ScrollView pickerScroll = new ScrollView(this);
        pickerScroll.setVerticalScrollBarEnabled(false);
        LinearLayout pickerBody = Ui.column(this);
        pickerScroll.addView(pickerBody);
        LinearLayout.LayoutParams psp = new LinearLayout.LayoutParams(-1, 0, 1f);
        psp.topMargin = Ui.dp(this, 6);
        card.addView(pickerScroll, psp);

        LyricLineView preview = new LyricLineView(this);
        preview.setText("正在唱的这一句");
        preview.setActiveLine(true);
        preview.setKaraokeProgress(1f);
        preview.setGradientColors(draft[0], draft[1]);
        preview.setGravity(Gravity.CENTER);
        preview.setBackground(Ui.glass(92, 16, 18, this));
        LinearLayout.LayoutParams pp = Ui.lp(-1, Ui.dp(this, 54));
        pp.topMargin = Ui.dp(this, 8);
        pickerBody.addView(preview, pp);

        ColorPickerView picker = new ColorPickerView(this);
        picker.setColor(picked[0]);
        LinearLayout.LayoutParams pickP = Ui.lp(-1, Ui.dp(this, 228));
        pickP.topMargin = Ui.dp(this, 12);
        pickerBody.addView(picker, pickP);

        LinearLayout chosenRow = Ui.row(this);
        chosenRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView chosenLabel = Ui.text(this, "当前颜色", 11f, Ui.TEXT_2, true);
        chosenRow.addView(chosenLabel, new LinearLayout.LayoutParams(0, Ui.dp(this, 38), 1f));
        View chosenSwatch = new View(this);
        chosenSwatch.setBackground(Ui.round(picked[0], 12, this));
        chosenRow.addView(chosenSwatch, Ui.lp(Ui.dp(this, 54), Ui.dp(this, 30)));
        LinearLayout.LayoutParams crp = Ui.lp(-1, Ui.dp(this, 38)); crp.topMargin = Ui.dp(this, 8); pickerBody.addView(chosenRow, crp);

        LinearLayout endpoints = Ui.row(this);
        TextView setStart = modalButton("设为起点", false);
        TextView setEnd = modalButton("设为终点", false);
        endpoints.addView(setStart, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f); ep.leftMargin = Ui.dp(this, 8); endpoints.addView(setEnd, ep);
        LinearLayout.LayoutParams erp = Ui.lp(-1, Ui.dp(this, 46)); erp.topMargin = Ui.dp(this, 8); pickerBody.addView(endpoints, erp);

        SimpleAction refreshDraft = () -> {
            preview.setGradientColors(draft[0], draft[1]);
            preview.setKaraokeProgress(1f);
            setStart.setText("设为起点 ✓");
            setEnd.setText("设为终点 ✓");
            GradientDrawable startBg = Ui.gradient(new int[]{Ui.mix(draft[0], Color.BLACK, .54f), Ui.mix(draft[0], Color.BLACK, .72f)}, 17, this);
            GradientDrawable endBg = Ui.gradient(new int[]{Ui.mix(draft[1], Color.BLACK, .54f), Ui.mix(draft[1], Color.BLACK, .72f)}, 17, this);
            setStart.setBackground(startBg);
            setEnd.setBackground(endBg);
        };
        picker.setListener(color -> {
            picked[0] = color;
            chosenSwatch.setBackground(Ui.round(color, 12, this));
        });
        setStart.setOnClickListener(v -> { draft[0] = picked[0]; refreshDraft.run(); });
        setEnd.setOnClickListener(v -> { draft[1] = picked[0]; refreshDraft.run(); });
        refreshDraft.run();

        TextView apply = modalButton("应用渐变", true);
        apply.setBackground(Ui.primaryFill(Ui.CYAN, 18, this));
        apply.setOnClickListener(v -> {
            dismissModalNow();
            saveLyricGradient(draft[0], draft[1], false);
        });
        LinearLayout.LayoutParams ap = Ui.lp(-1, Ui.dp(this, 48)); ap.topMargin = Ui.dp(this, 12); card.addView(apply, ap);
        presentFractionModal(card, .84f);
    }

    private void saveLyricGradient(int start, int end, boolean coverMode) {
        lyricUseCoverColor = coverMode;
        lyricGradientStart = start;
        lyricGradientEnd = end;
        getSharedPreferences("xingyu_ui_settings", MODE_PRIVATE).edit()
                .putInt("lyric_gradient_start", start)
                .putInt("lyric_gradient_end", end)
                .putBoolean("lyric_gradient_cover", coverMode)
                .apply();
        refreshLyricGradientViews();
        DesktopLyricService.refreshStyle(this);
        toast("歌词高亮已更新");
    }

    private void applyCoverLyricGradient(int coverAccent) {
        lyricGradientStart = Ui.mix(coverAccent, Color.WHITE, .66f);
        lyricGradientEnd = Ui.mix(coverAccent, Ui.CYAN, .18f);
        getSharedPreferences("xingyu_ui_settings", MODE_PRIVATE).edit()
                .putBoolean("lyric_gradient_cover", true).apply();
        refreshLyricGradientViews();
        DesktopLyricService.refreshStyle(this);
    }

    private void refreshLyricGradientViews() {
        if (lyricBox == null) return;
        for (int i = 0; i < lyricBox.getChildCount(); i++) {
            View v = lyricBox.getChildAt(i);
            if (v instanceof LyricLineView) ((LyricLineView) v).setGradientColors(lyricGradientStart, lyricGradientEnd);
        }
    }

    private void toast(String s) {
        if (snackbar == null) return;
        snackbar.animate().cancel();
        snackbar.bringToFront();
        snackbar.setBackground(Ui.functionalGlass(18, this));
        snackbar.setText(s == null ? "" : s);
        snackbar.setTextColor(Ui.TEXT);
        snackbar.setVisibility(View.VISIBLE);
        snackbar.setAlpha(0f);
        snackbar.setTranslationY(-Ui.dp(this, 8));
        snackbar.animate().alpha(1f).translationY(0f).setDuration(SpringMotion.fadeDuration()).start();
        snackbar.postDelayed(() -> snackbar.animate().alpha(0f).translationY(-Ui.dp(this, 8)).setDuration(SpringMotion.fadeDuration())
                .withEndAction(() -> snackbar.setVisibility(View.GONE)).start(), 2400);
    }

    private void showModeToast(String label, int accent) {
        if (snackbar == null) return;
        snackbar.animate().cancel();
        snackbar.bringToFront();
        snackbar.setText("  " + (label == null ? "播放模式" : label));
        snackbar.setTextColor(Color.rgb(12, 12, 18));
        snackbar.setBackground(Ui.primaryFill(accent, 18, this));
        snackbar.setVisibility(View.VISIBLE);
        snackbar.setAlpha(0f);
        snackbar.setTranslationY(-Ui.dp(this, 10));
        snackbar.setScaleX(.96f); snackbar.setScaleY(.96f);
        snackbar.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(165).start();
        snackbar.postDelayed(() -> snackbar.animate().alpha(0f).translationY(-Ui.dp(this, 8)).setDuration(170)
                .withEndAction(() -> snackbar.setVisibility(View.GONE)).start(), 1500);
    }

    private static String safeMessage(Exception e) {
        return e == null || e.getMessage() == null ? e == null ? "unknown" : e.getClass().getSimpleName() : e.getMessage();
    }

    private void installSystemBackHandler() {
        if (Build.VERSION.SDK_INT >= 33) {
            backInvokedCallback = this::handleBackNavigation;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backInvokedCallback);
        }
    }

    private void handleBackNavigation() {
        if (contextCoachOverlay != null && contextCoachOverlay.getVisibility() == View.VISIBLE) {
            deferActiveContextCoach();
            return;
        }
        if (onboardingOverlay != null && onboardingOverlay.getVisibility() == View.VISIBLE) {
            finishOnboarding();
            return;
        }
        if (activeSearchSuggestions != null && activeSearchSuggestions.getVisibility() == View.VISIBLE) {
            hideSearchSuggestions();
            return;
        }
        if (voicePanel != null && voicePanel.getVisibility() == View.VISIBLE) {
            hideVoicePanel();
            return;
        }
        // Queue, playlist search, rename/delete sheets and other temporary panels all live in this layer.
        if (modalOverlay != null && modalOverlay.getVisibility() == View.VISIBLE) {
            hideModal();
            return;
        }
        if (playerOverlay != null && playerOverlay.getVisibility() == View.VISIBLE) {
            if (artworkFlip != null && artworkFlip.isLyricsVisible()) { artworkFlip.showLyrics(false); return; }
            if (nowVinylStack != null && nowVinylStack.isExpanded()) { nowVinylStack.setExpanded(false); return; }
            closeNowPlaying();
            return;
        }
        if (searchAllResultsOpen) {
            searchAllResultsOpen = false;
            activeSearchAllList = null;
            searchAllSavedPosition = 0;
            searchAllSavedTop = 0;
            rootTabSearchAllResultsOpen[1] = false;
            suppressNextPageAnimation = true;
            renderTab();
            return;
        }
        if (openSmartCollection != null) {
            closeSmartCollectionToHome();
            return;
        }
        if (offlinePlaylistOpen) {
            closePlaylistToLibrary();
            return;
        }
        if (openPlaylist != null) {
            closePlaylistToLibrary();
            return;
        }
        // Root tabs are one navigation level below Home.
        if (tab != 0) {
            switchRootTab(0, navItems[0]);
            return;
        }
        // Home is the root: background instead of finishing so accidental Back never kills playback/UI state.
        moveTaskToBack(true);
    }

    @Override public void onBackPressed() {
        handleBackNavigation();
    }

    @Override protected void onDestroy() {
        libraryBootstrapGeneration++;
        if (startupStarSpeedAnimator != null) { startupStarSpeedAnimator.cancel(); startupStarSpeedAnimator = null; }
        if (activePlaylistSharedAnimator != null) { activePlaylistSharedAnimator.cancel(); activePlaylistSharedAnimator = null; }
        clearPlaylistSharedOverlay(false);
        playerOpenGeneration++;
        cancelPlayerOpenMotion();
        if (nowVinylStack != null) nowVinylStack.cancelAnimations();
        stopHomeLyricQuoteTicker();
        if (contextCoachOverlay != null) {
            try { ((ViewGroup) contextCoachOverlay.getParent()).removeView(contextCoachOverlay); } catch (Exception ignored) { }
            contextCoachOverlay = null;
            activeContextCoachId = "";
        }
        if (onboardingOverlay != null) {
            try { ((ViewGroup) onboardingOverlay.getParent()).removeView(onboardingOverlay); } catch (Exception ignored) { }
            onboardingOverlay = null;
        }
        if (Build.VERSION.SDK_INT >= 33 && backInvokedCallback != null) {
            try { getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backInvokedCallback); } catch (Exception ignored) { }
            backInvokedCallback = null;
        }
        if (playbackBehaviorTracker != null) playbackBehaviorTracker.finish(false);
        cancelPlaybackErrorAutoSkip();
        if (pendingSuggestionRunnable != null) main.removeCallbacks(pendingSuggestionRunnable);
        if (pendingSearchMergeRunnable != null) main.removeCallbacks(pendingSearchMergeRunnable);
        if (pendingSearchEnrichmentRunnable != null) main.removeCallbacks(pendingSearchEnrichmentRunnable);
        if (pendingLyricSearchRunnable != null) main.removeCallbacks(pendingLyricSearchRunnable);
        cancelActiveSearchNetworkJobs();
        if (activeSuggestionJob != null && !activeSuggestionJob.isDone()) activeSuggestionJob.cancel(true);
        if (lyricsResumeRunnable != null) main.removeCallbacks(lyricsResumeRunnable);
        if (lyricScrollSettleRunnable != null) main.removeCallbacks(lyricScrollSettleRunnable);
        main.removeCallbacks(lyricKaraokeTicker);
        stopRingtonePreview();
        stopLocalMusicPreview(true);
        suggestionRequestToken++;
        if (bound && playback != null) playback.clearListener(this);
        try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) { }
        try { unregisterReceiver(voiceReceiver); } catch (Exception ignored) { }
        if (voicePanelHideRunnable != null) main.removeCallbacks(voicePanelHideRunnable);
        if (bound) try { unbindService(connection); } catch (Exception ignored) { }
        variantPrefetch.shutdownNow();
        searchMergeExecutor.shutdownNow();
        playlistFilterExecutor.shutdownNow();
        lyricIndexExecutor.shutdownNow();
        lyricSearchExecutor.shutdownNow();
        if (backgroundVariantEnricher != null) backgroundVariantEnricher.destroy();
        io.shutdownNow();
        super.onDestroy();
    }
}
