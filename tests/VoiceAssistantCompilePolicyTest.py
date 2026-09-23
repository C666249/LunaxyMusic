from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = (root / "app/src/main/java/com/xingyu/music/voice/VoiceAssistantService.java").read_text(encoding="utf-8")
watchdog = src.find("private final Runnable recognizerReadyWatchdog")
restart = src.find("private final Runnable restartRecognition")
assert watchdog >= 0, "recognizerReadyWatchdog declaration missing"
assert restart >= 0, "restartRecognition declaration missing"
assert watchdog < restart, "recognizerReadyWatchdog must be declared before restartRecognition to avoid illegal forward reference"
assert "main.removeCallbacks(recognizerReadyWatchdog);" in src
assert "main.postDelayed(recognizerReadyWatchdog, usingOnDeviceRecognizer ? 3800L : 5200L);" in src
print("PASS: VoiceAssistantService forward-reference compile policy")
