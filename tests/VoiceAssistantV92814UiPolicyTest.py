from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/'app/src/main/java/com/xingyu/music/MainActivity.java').read_text(encoding='utf-8')
panel=(root/'app/src/main/java/com/xingyu/music/ui/VoiceAssistantPanel.java').read_text(encoding='utf-8')
checks=[]
def check(c,m):
    if not c: raise AssertionError(m)
    checks.append(m)
start=main.index('private void showVoiceAssistantSettings()')
end=main.index('private void installModalOutsideDismiss()', start)
voice=main[start:end]
check('说一句，音乐就动起来' in voice,'compact product subtitle')
check('后台待命中' in voice,'hero status')
check('识别方式' in voice,'recognition mode section')
check('Ui.dp(this, 56)' in voice,'large primary actions')
check('heightWithTop(54' in voice,'large settings rows')
check('可以说：\\n' not in voice,'long command wall removed')
check('开启后会持续使用麦克风等待唤醒词' not in voice,'privacy wall removed from default surface')
check('为什么有时需要联网？' in voice,'network/privacy help is progressive disclosure')
check('Ui.tintedGlass' in voice,'voice settings keep a tinted hero hierarchy')
check('语音设置' in panel and 'Ui.playerControlSurface' in panel,'voice panel has a theme-aware usable product action')
check('Ui.TEXT' in panel and 'Ui.DIM' in panel,'voice panel follows appearance text tokens')
print(f'PASS: {len(checks)} V92.8.14 voice UI compatibility checks')
