from pathlib import Path
import shutil
import re

source=Path(__file__).resolve().parents[1]
target=Path('D:/Claude/experiments/lunaxy-v927-validation')
target.mkdir(parents=True,exist_ok=True)
for name in ['build.gradle.kts','settings.gradle.kts','gradle.properties']:
    shutil.copy2(source/name,target/name)
shutil.copytree(source/'app/src/main',target/'app/src/main',dirs_exist_ok=True)
shutil.copy2(source/'app/proguard-rules.pro',target/'app/proguard-rules.pro')
build=re.sub(r'applicationId = "[^"]+"', 'applicationId = "com.xingyu.music.motionqa"', (source/'app/build.gradle.kts').read_text(encoding='utf-8'))
build=build.replace('minSdk = 26','minSdk = 26\n        testInstrumentationRunner = "com.xingyu.music.qa.MotionInstrumentation"')
(target/'app/build.gradle.kts').write_text(build,encoding='utf-8')
test=target/'app/src/androidTest/java/com/xingyu/music/qa'
test.mkdir(parents=True,exist_ok=True)
shutil.copy2(source/'tests/android/MotionInstrumentation.java',test/'MotionInstrumentation.java')
print(target)
