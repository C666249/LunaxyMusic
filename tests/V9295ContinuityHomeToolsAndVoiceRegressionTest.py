from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root/'app/src/main/java/com/xingyu/music/MainActivity.java').read_text(encoding='utf-8')
dock = (root/'app/src/main/java/com/xingyu/music/ui/FluidToolDock.java').read_text(encoding='utf-8')
icon = (root/'app/src/main/java/com/xingyu/music/ui/IconView.java').read_text(encoding='utf-8')
voice = (root/'app/src/main/java/com/xingyu/music/voice/VoiceAssistantService.java').read_text(encoding='utf-8')
panel = (root/'app/src/main/java/com/xingyu/music/ui/VoiceAssistantPanel.java').read_text(encoding='utf-8')
glass = (root/'app/src/main/java/com/xingyu/music/ui/VoiceGlassDrawable.java').read_text(encoding='utf-8')
checks=[]
def check(c,m):
    if not c: raise AssertionError(m)
    checks.append(m)

home = main[main.find('private View homePage()'):main.find('private void maybeStartOnboarding()')]
header = main[main.find('private View recommendationSectionHeader()'):main.find('private void showRecommendationStyleDrawer()')]
render = main[main.find('private void startRootTabPhysics'):main.find('private void rememberHomePosition()')]
scaffold = main[main.find('private View pageScaffold(String title, String subtitle, View body, View titleAction, boolean'):main.find('/** V89 unified contextual engine entry')]

# Home progressive disclosure / cleaner search.
check('IconView.Type.MIC' not in home[:home.find('FluidToolDock homeTools')], 'microphone is no longer embedded in the search field')
check('FluidToolDock.Direction.DOWN' in home and 'IconView.Type.GRID' in home, 'top-right Home utility hub expands vertically')
check('addAction(IconView.Type.MIC' in home and 'addAction(IconView.Type.SCAN' in home, 'Voice and engine matrix live behind the top-right hub')
check('titleRow.addView(titleAction' in scaffold and 'subtitleAction' not in scaffold, 'Home utility hub is anchored to hero top-right instead of lyric row')
check('FluidToolDock.Direction.LEFT' in header and 'IconView.Type.TUNE' in header, 'recommendation settings collapse into one horizontal drawer hub')
check('addAction(IconView.Type.LAYERS' in header and 'addAction(IconView.Type.PALETTE' in header, 'card style and appearance remain second-level actions')
check('HIT_DP = 48' in dock and 'VISUAL_DP = 40' in dock, 'compact drawers preserve 48dp Android touch targets')
check('ValueAnimator.ofFloat(start, target)' in dock and 'Math.abs(target - start)' in dock, 'drawer retargets from current presentation progress')
check('Math.abs(target - start)' in dock and 'ValueAnimator.ofFloat(start, target)' in dock, 'drawer keeps trackable interruptible grow/collapse motion')
check('SpringMotion.isReducedMotion()' in dock, 'drawer has reduced-motion fallback')
check('TUNE' in icon and 'GRID' in icon and 'case TUNE' in icon and 'case GRID' in icon, 'new utility glyphs belong to Lunaxy IconView family')

# No full-page alpha overlap on push/pop.
check('startDepthPageTransition' in render and 'incoming.setAlpha(1f)' in render and 'outgoing.setAlpha(1f)' in render, 'detail transitions keep both pages fully opaque')
check('incoming.setTranslationX(safeWidth)' in render and 'outgoing.animate().translationX(-safeWidth)' in render, 'detail push preserves spatial source/destination relationship')
check('pageHost.addView(incoming, 0)' in render and 'outgoing.animate().translationX(safeWidth)' in render, 'detail pop is the inverse spatial path')
check('page.setAlpha(.28f)' not in render and 'alpha(.28f)' not in render and 'alpha(.24f)' not in render, 'legacy double-exposure push/pop alpha fades are removed')
check('setLayerType(View.LAYER_TYPE_HARDWARE' in render, 'full-page spatial travel uses temporary hardware layers')
check('carriedVelocity' in render and '-direction * 920f * density' in render, 'root tab page slide has an initial physical impulse and carries retarget velocity')

# SpeechRecognizer robustness: default binding first, provider rotation later, less false offline diagnosis.
create = voice[voice.find('private void createRecognizer(boolean preferOnDevice)'):voice.find('private boolean rotateSystemRecognizerProvider')]
network = voice[voice.find('private boolean hasUsableNetwork()'):voice.find('private String currentEngineLabel')]
check('activeSystemRecognizerProvider == null' in create and 'SpeechRecognizer.createSpeechRecognizer(this)' in create, 'system recognition starts through Android default binding path')
check('refreshSystemRecognizerProviders()' not in create, 'initial system recognizer no longer forces an explicit OEM provider')
check('rotateSystemRecognizerProvider' in voice and 'createSpeechRecognizer(this, activeSystemRecognizerProvider)' in create, 'explicit provider rotation remains available after real failures')
check('NET_CAPABILITY_INTERNET' in network and 'NET_CAPABILITY_VALIDATED' not in network.replace('// Do not require NET_CAPABILITY_VALIDATED here.', ''), 'network diagnosis no longer requires Android validation probe success')
check('usingOnDeviceRecognizer ? 3800L : 5200L' in voice, 'recognizer readiness watchdog allows slower OEM services to initialize')
check('Some OEM recognizers skip onReadyForSpeech' in voice and 'main.removeCallbacks(recognizerReadyWatchdog);' in voice[voice.find('onBeginningOfSpeech'):voice.find('onEndOfSpeech')], 'audio callbacks count as readiness when OEM omits onReadyForSpeech')

# Voice banner geometry morph tracks the finger and settles from current shape.
check('getCompactProgress' in glass and 'animateCompactProgress' in panel, 'voice banner glass shape morph is continuously animatable')
check('downward > 0f' in panel and 'glass.setCompactProgress' in panel, 'downward banner gesture directly manipulates capsule geometry')
check('animateCompactProgress(compact ? 1f : 0f' in panel, 'cancelled gesture settles geometry back from its current state')

print(f'PASS: {len(checks)} V92.9.5 continuity/home-tools/voice regression checks')
