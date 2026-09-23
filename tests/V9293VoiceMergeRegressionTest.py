from pathlib import Path
import xml.etree.ElementTree as ET
import re

root = Path(__file__).resolve().parents[1]
main = (root/'app/src/main/java/com/xingyu/music/MainActivity.java').read_text(encoding='utf-8')
manifest = (root/'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
contract = (root/'app/src/main/java/com/xingyu/music/voice/VoiceAssistantContract.java').read_text(encoding='utf-8')
service = (root/'app/src/main/java/com/xingyu/music/voice/VoiceAssistantService.java').read_text(encoding='utf-8')
panel = (root/'app/src/main/java/com/xingyu/music/ui/VoiceAssistantPanel.java').read_text(encoding='utf-8')
tooldock = (root/'app/src/main/java/com/xingyu/music/ui/FluidToolDock.java').read_text(encoding='utf-8')
glass = (root/'app/src/main/java/com/xingyu/music/ui/VoiceGlassDrawable.java').read_text(encoding='utf-8')
orb = (root/'app/src/main/java/com/xingyu/music/ui/VoiceOrbView.java').read_text(encoding='utf-8')
icon = (root/'app/src/main/java/com/xingyu/music/ui/IconView.java').read_text(encoding='utf-8')
appearance = (root/'app/src/main/java/com/xingyu/music/ui/AppearanceSystem.java').read_text(encoding='utf-8')
workflow = (root/'WORKFLOW.md').read_text(encoding='utf-8')

checks=[]
def check(c,m):
    if not c: raise AssertionError(m)
    checks.append(m)

ET.fromstring(manifest)
check('android.permission.RECORD_AUDIO' in manifest and 'FOREGROUND_SERVICE_MICROPHONE' in manifest, 'voice permissions merged')
check('.voice.VoiceAssistantService' in manifest and 'android:foregroundServiceType="microphone"' in manifest, 'voice FGS registered')
check('MIC' in icon and 'case MIC' in icon, 'microphone icon merged into icon family')
check(all((root/'app/src/main/java/com/xingyu/music/voice'/name).is_file() for name in [
    'VoiceAssistantContract.java','VoiceAssistantService.java','VoiceCommand.java','VoiceCommandParser.java','VoiceMusicSearch.java']), 'all voice runtime classes present')
check('VoiceAssistantPanel.java' in [p.name for p in (root/'app/src/main/java/com/xingyu/music/ui').glob('Voice*.java')], 'voice panel UI present')
check('voiceModeButton("本地优先"' in main and 'voiceModeButton("仅本地"' in main and 'voiceModeButton("系统识别"' in main, 'three recognition modes preserved')
check('requestVoiceOfflineModel()' in main and 'triggerModelDownload' in main, 'offline Chinese model flow preserved')
check('VoiceAssistantContract.enabled(this)' in main and 'startVoiceAssistant(true)' in main, 'home direct-listen behavior preserved')
check('addAction(IconView.Type.MIC' in main and 'Ui.contentSurface(15' in tooldock, 'home mic uses theme-aware progressive-disclosure surface')
check('FluidToolDock.Direction.DOWN' in main and 'IconView.Type.GRID' in main, 'voice/search controls are deliberately separated by top-right utility disclosure')
check('HIT_DP = 48' in tooldock and 'VISUAL_DP = 40' in tooldock, 'home voice mic keeps compact visual with 48dp hit target')
check('voicePanel.refreshAppearance()' in main, 'in-app voice panel refreshes current appearance')
check('SpringMotion.sheetDuration()' in main and 'SpringMotion.fadeDuration()' in main, 'voice panel uses semantic motion tokens')
check('if (voicePanel != null && voicePanel.getVisibility() == View.VISIBLE)' in main, 'voice panel participates in Back hierarchy')
check('rememberRootTabContext();' in main[main.index('private void openVoiceSearch'):main.index('private boolean voiceMicGranted')], 'voice search preserves outgoing tab context')
check('clearRootTabDetailState(1);' in main[main.index('private void openVoiceSearch'):main.index('private boolean voiceMicGranted')], 'voice search intentionally lands on Search root')
check('AppearanceSystem.load(this);' in service, 'background service resolves visual/accessibility tokens itself')
check('AppearanceSystem.isLight()' in glass and 'AppearanceSystem.reduceTransparency()' in glass, 'voice glass has Moonlight and reduced-transparency branches')
check('SpringMotion.isReducedMotion()' in orb and 'ValueAnimator.areAnimatorsEnabled()' in orb, 'voice orb respects reduced motion and system animator scale')
check('Ui.TEXT_2' in panel and 'Ui.DIM' in panel and 'Ui.playerControlSurface' in panel, 'voice panel uses current theme hierarchy')
check('setMinHeight(Ui.dp(context, 48))' in panel and 'Ui.dp(context, 48)' in panel, 'voice panel actions keep Android-sized targets')
check('addAction(IconView.Type.LAYERS' in main and 'addAction(IconView.Type.PALETTE' in main and 'FluidToolDock.Direction.LEFT' in main, 'card/appearance actions remain icon-only behind compact drawer')
check('Root Tab' in workflow and 'Lunaxy Voice' in workflow, 'project workflow documents both UI and Voice constraints')
check('MODE_AUTO = "auto"' in contract and 'MODE_LOCAL = "local"' in contract and 'MODE_SYSTEM = "system"' in contract, 'voice mode contract intact')
print(f'PASS: {len(checks)} V92.9.3 Voice/UI merge regression checks')
