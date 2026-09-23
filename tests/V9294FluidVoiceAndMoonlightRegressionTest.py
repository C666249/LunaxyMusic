from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root/'app/src/main/java/com/xingyu/music/MainActivity.java').read_text(encoding='utf-8')
icon = (root/'app/src/main/java/com/xingyu/music/ui/IconView.java').read_text(encoding='utf-8')
motion = (root/'app/src/main/java/com/xingyu/music/ui/SpringMotion.java').read_text(encoding='utf-8')
tooldock = (root/'app/src/main/java/com/xingyu/music/ui/FluidToolDock.java').read_text(encoding='utf-8')
panel = (root/'app/src/main/java/com/xingyu/music/ui/VoiceAssistantPanel.java').read_text(encoding='utf-8')
glass = (root/'app/src/main/java/com/xingyu/music/ui/VoiceGlassDrawable.java').read_text(encoding='utf-8')
contract = (root/'app/src/main/java/com/xingyu/music/voice/VoiceAssistantContract.java').read_text(encoding='utf-8')
service = (root/'app/src/main/java/com/xingyu/music/voice/VoiceAssistantService.java').read_text(encoding='utf-8')
checks=[]
def check(c,m):
    if not c: raise AssertionError(m)
    checks.append(m)

header = main[main.find('private View recommendationSectionHeader()'):main.find('private void showRecommendationStyleDrawer()')]
queue = main[main.find('private void showPlaybackQueue()'):main.find('private int playModeAccent')]
queue_adapter = main[main.find('private final class QueueAdapter'):main.find('private View rootTab') if 'private View rootTab' in main else len(main)]

# Icon-only contextual actions, using the product's own Android-safe coherent icon family.
check('LAYERS' in icon and 'case LAYERS' in icon, 'stacked-card icon belongs to Lunaxy IconView family')
check('IconView.Type.LAYERS' in header and 'IconView.Type.PALETTE' in header, 'recommendation actions are icon-only')
check('Ui.text(this, "卡片"' not in header and 'Ui.text(this, "主题"' not in header, 'visible card/theme labels removed')
check('HIT_DP = 48' in tooldock and 'VISUAL_DP = 40' in tooldock, 'icon drawers keep 48dp touch targets while using compact 40dp visuals')

# Moonlight queue is a clean paper sheet rather than grey blocks under a heavy scrim.
check('Color.argb(44, 42, 50, 66)' in main, 'Moonlight modal scrim is deliberately lighter')
check('AppearanceSystem.isLight()' in queue and 'Ui.transientGlass(25, this)' in queue, 'queue sheet is theme-aware transient material')
check('AppearanceSystem.isLight()' in queue_adapter and 'Ui.contentSurface(15, MainActivity.this)' in queue_adapter, 'Moonlight queue rows use real light content surfaces')
check('Color.argb(135, 8, 8, 14)' in queue_adapter, 'dark queue row material remains available only as dark branch')

# Root tab is a physical strip: opaque adjacent surfaces, carried velocity, interruptible retarget.
check('startRootTabPhysics' in main and 'Choreographer.getInstance().postFrameCallback' in main, 'root tab uses frame-driven physical settle')
check('rootTabVelocityPxPerSec' in main and 'velocity[0]' in main, 'root tab carries presentation velocity across retargets')
check('incoming.setAlpha(1f)' in main and 'outgoing.setAlpha(1f)' in main, 'root tab physical handoff remains fully opaque')
check('outgoing.setTranslationX(position[0] - direction * safeWidth)' in main, 'root surfaces remain exactly one viewport apart')
check('springTranslationX(navSelectionPill' in main and 'TranslationSpring' in motion, 'tab selection carrier uses interruptible spring motion')
check('WeakHashMap<View, TranslationSpring>' in motion, 'selection spring state is retained per presentation object')

# SpeechRecognizer connectivity diagnosis must not conflate provider failure with phone connectivity.
check('ConnectivityManager' in service and 'NET_CAPABILITY_INTERNET' in service, 'Voice checks device network capability before classifying provider errors')
check('queryIntentServices' in service and 'android.speech.RecognitionService' in service, 'Voice discovers installed recognition providers')
check('createSpeechRecognizer(this, activeSystemRecognizerProvider)' in service, 'Voice can bind an explicit recognition provider')
check('rotateSystemRecognizerProvider' in service and 'systemRecognizerProviderRotations' in service, 'Voice rotates provider after online provider/server failure')
check('系统语音服务连接失败' in service and '当前网络不可用' in service, 'Voice reports provider failure and network loss separately')
check('系统语音服务无法联网' not in service, 'misleading blanket cannot-network message removed')

# System overlay gesture/capsule model and inverse continuous transform.
check('KEY_OVERLAY_COMPACT' in contract and 'KEY_OVERLAY_SNOOZE_UNTIL' in contract, 'overlay compact/snooze state is persisted')
check('snoozeOverlay' in contract and 'overlaySnoozed' in contract, 'overlay snooze has real persistence semantics')
check('setCollapseAction' in panel and 'setExpandAction' in panel and 'setSnoozeAction' in panel and 'setOpenAppAction' in panel, 'overlay exposes semantic gesture actions')
check('← 稍后 10 分钟' in panel and '↓ 收起' in panel and '→ 打开 Lunaxy' in panel, 'long-press gesture guide explains physical actions')
check('flingAway(false, snoozeAction)' in panel and 'flingAway(true, openAppAction)' in panel, 'left/right flings map to snooze/open')
check('collapseAction.run()' in panel and 'dy >' in panel, 'downward direct manipulation collapses expanded banner')
check('compact && !wasDragging' in panel and 'expandAction.run()' in panel, 'tap on compact capsule restores banner')
check('setDuration(460L)' in panel and 'setDuration(520L)' in panel, 'rare compact/expand transition is deliberately slower than routine taps')
check('copy.animate().alpha(0f)' in panel and 'copy.animate().alpha(1f)' in panel, 'collapse/expand uses staged copy transition')
check('setCompactProgress' in glass and 'compactProgress' in glass, 'glass shape continuously morphs between banner and capsule')
check('compact ? Ui.dp(this, 156)' in service and 'Gravity.TOP | Gravity.START' in service, 'compact overlay becomes a real small top-left system window')
check('overlayPanel.animateToCompact' in service and 'overlayPanel.animateToExpanded' in service, 'WindowManager resize is coordinated with inverse panel morph')
check('snoozeSystemOverlay(10L * 60L * 1000L)' in service, 'left swipe snoozes overlay for ten minutes without stopping Voice')
check('openAppFromOverlay' in service, 'right swipe opens relevant Lunaxy destination')
check('phaseChanged' in panel and 'copy.setTranslationY' in panel, 'Voice phase labels settle continuously instead of swapping as unrelated objects')
check('SpringMotion.isReducedMotion()' in panel, 'overlay gesture/morph has reduced-motion fallback')

print(f'PASS: {len(checks)} V92.9.4 fluid/voice/moonlight regression checks')
