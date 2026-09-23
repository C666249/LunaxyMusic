from pathlib import Path
import hashlib
import shutil
import zipfile
import xml.etree.ElementTree as ET
from collections import Counter

beta=Path(__file__).resolve().parents[1]
stable=beta.with_name('LunaxyMusic-V92.4-Stable')
original=Path('D:/edge download/Chatgpt-app-projects/beta/LunaxyMusic-V92.3-Beta/LunaxyMusic-V92.3-Beta')
qa=Path('D:/Claude/experiments/lunaxy-v924-validation/qa-final')
delivery=Path('D:/Claude/projects/deliverables/LunaxyMusic-V92.4')
delivery.mkdir(parents=True,exist_ok=True)

def digest(file): return hashlib.sha256(file.read_bytes()).hexdigest()
def java(project):
    folder=project/'app/src/main/java'
    return {p.relative_to(folder).as_posix():digest(p) for p in folder.rglob('*.java')}

assert java(beta)==java(stable), 'Stable and Beta runtime sources differ'
assert java(beta)==java(Path('D:/Claude/experiments/lunaxy-v924-validation')), 'Tested source differs from delivery'
before,after=java(original),java(beta)
changed=sorted(k for k in before if before[k]!=after.get(k))
added=sorted(set(after)-set(before))
assert changed==sorted('com/xingyu/music/'+p for p in ['MainActivity.java','ui/FluidTrackHaloDrawable.java','ui/SwipeAwareScrollView.java','ui/VinylStackView.java']),changed
assert len(added)==2 and len(after)==94
test_report=(qa/'android-results.txt').read_text(encoding='utf-8')
assert 'PASS: 93 Android assertions' in test_report and 'FAIL' not in test_report

def lint_counts(path):
    return Counter(i.get('severity') for i in ET.parse(path).getroot().findall('issue'))
old_lint=lint_counts(Path('D:/Claude/experiments/lunaxy-v923-lint/app/build/reports/lint-results-debug.xml'))
new_lint=lint_counts(beta/'app/build/reports/lint-results-debug.xml')
validation=f'''# V92.4 验证记录

## 基线与交付

- 基线：用户给定目录中的 V92.3 Beta 完整源码，原目录未修改。
- Stable：`com.xingyu.music` / `92.4.0` / versionCode `928`。
- Beta：`com.xingyu.music.beta1` / `92.4.0-beta29` / versionCode `929`。
- 两个渠道均在本机真实执行 `:app:assembleDebug --offline --no-daemon` 成功。
- Gradle 9.5.0 / AGP 9.2.1 / Java 21 运行时，源码级别 17 / SDK 36。
- APK 签名验证通过。与原目录 V92.3 Beta APK 使用相同证书：`57ad983bf70aec4d668d2a0e6f7ea850afd0f0724f021f926372bf27b28eeddb`。

## 自动回归与视觉检查

- JVM：223 项断言通过，覆盖暂停/恢复、重复绑定不重置调色过渡、共享时钟、近远层几何、队列首尾和短队列边界。
- Android：93 项断言通过。Android 15 / API 35 模拟器，1080×2400，420dpi，独立包名 `com.xingyu.music.motionqa`。
- 测试样本为本地生成的封面和静音 WAV，不依赖外部音乐服务；确实经过原 PlaybackService / Media3 本地播放流程。
- 真实五唱片 UI、侧层露边、内部容器裁剪、单次切歌、中央 View 连续接管、当前 Hero 目标同步检查通过。
- 慢拖 1.8 秒、返回边缘不触发切歌、纵向手势互斥、ACTION_CANCEL 检查通过。
- 队列拖拽跨越 hysteresis 阈值，确认数据实际移动至目标位置，落位行无旧位移、内容可见、当前歌曲持有正确 Halo 状态。
- 手动歌单排序实际移动至目标位置，各可见行内容恢复通过。
- 当前歌曲 65 秒边缘自动滚动往返拖拽，落位成功、18 项队列保留、播放不中断。
- Hero 展开中途返回不再重新弹出；Mini 相同进度坐标稳定；切歌待提交阶段返回不会继续切歌。
- 已目视检查截图。测试源码在 `tests/android/`，未编入任何交付 APK。

## Lint 与历史问题

- 原版扫描：{old_lint['Error']} errors / {old_lint['Warning']} warnings。
- 新版扫描：{new_lint['Error']} errors / {new_lint['Warning']} warnings。
- 按规则、文件、表达式和消息与原版比对，新增 Error 为 0。API31 模糊实现保留 SDK 条件保护并标注隔离类；本次调用的既有 PlaybackService 接口明确沿用固定 Media3 版本。
- 完整 `lintDebug` 仍因历史问题返回失败，不能称为“全项目 lint 通过”。历史项包括权限提示、Media3 opt-in、返回兼容提示等；没有在此版扩大范围做通用重构。
- lint 原始 XML 见本次构建的 `app/build/reports/`；原始源码独立扫描目录为 `D:/Claude/experiments/lunaxy-v923-lint`。

## 源码与包体审计

- Stable/Beta 各 {len(after)} 个 Java 运行时文件，字节级一致。
- 基线 {len(before)} 个 Java 文件中 {len(before)-len(changed)} 个保持字节级一致；修改 4 个 UI 文件，新增 2 个 UI 状态/几何文件。
- PlaybackService、SourceCoordinator、PlaybackUrlStore、RecommendationEngine、SearchPerformanceStore、ImageLoader、LyricCacheStore 及其余未列文件保持原版字节。
- 每个源码 ZIP 可独立用 Android Studio 打开；排除本机缓存、构建输出、local.properties 和临时测试数据。
- 每包 XML 解析、ZIP CRC、自带 SHA256SUMS 全项验证通过。包体 SHA-256 在交付目录 `SHA256SUMS.txt`。

## 待真机确认

未在 Android 8–11、其他 OEM 或 90/120Hz 真机上执行测试，也未做远程音源全链路回归。本次截图、自动回归和原版核心文件一致性构成当前证据；最终流体框观感与拖拽手感仍请在常用手机验收。

## 主要变更文件

'''+''.join('- `'+k+'`\n' for k in changed+added)

for project,channel,version,code in [(beta,'Beta','92.4.0-beta29',929),(stable,'Stable','92.4.0',928)]:
    # Documentation and tests are shared too; app identity resources stay channel-specific.
    if project==stable:
        shutil.copytree(beta/'tests',stable/'tests',dirs_exist_ok=True)
    logs=project/'dev-logs'; logs.mkdir(exist_ok=True)
    (logs/'V92.4-validation.md').write_text(validation,encoding='utf-8')
    (logs/'android-results.txt').write_text(test_report,encoding='utf-8')
    (project/'APK_BUILD_STATUS.txt').write_text(f'V92.4 {channel}\nversionName={version}\nversionCode={code}\n:app:assembleDebug — BUILD SUCCESSFUL (local Android SDK 36)\nAPK signature and application identity verified.\nFull lint has inherited errors; see dev-logs/V92.4-validation.md.\n',encoding='utf-8')
    memory=project/'LUNAXY_PROJECT_WORKFLOW_MEMORY.md'
    text=memory.read_text(encoding='utf-8')
    if '## V92.4 主线' not in text:
        memory.write_text(text+'\n\n## V92.4 主线\n\n本版以用户指定的 V92.3 Beta 源码为基线，同源制作 Stable/Beta。当前主线为 V92.4。五个保留唱片 View 共享连续堆叠空间并在切歌后交换角色；歌曲状态持有流体框调色和播放时钟；拖拽以实际 OnPreDraw 布局交接收尾。Hero/切歌取消必须使待执行回调失效，Mini 位置必须使用固定起止坐标。详见 RELEASE_NOTES_V92.4.md 和 dev-logs/V92.4-validation.md。后续优先结合真机录像和 Logcat 调整，不凭增加过冲或延长动画掩盖问题。\n',encoding='utf-8')
    excluded={'build','.gradle','.git','__pycache__','dist'}
    files=sorted(p for p in project.rglob('*') if p.is_file() and not any(x in excluded for x in p.relative_to(project).parts)
                 and p.name not in {'local.properties','SHA256SUMS.txt'} and not p.name.endswith('.log'))
    xml=0
    for file in files:
        if file.suffix=='.xml': ET.parse(file); xml+=1
    sums=''.join(f'{digest(p)}  {p.relative_to(project).as_posix()}\n' for p in files)
    manifest=project/'SHA256SUMS.txt';manifest.write_text(sums,encoding='utf-8')
    dest=delivery/f'LunaxyMusic-V92.4-{channel}.zip'
    with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
        for file in files+[manifest]: archive.write(file,f'{project.name}/{file.relative_to(project).as_posix()}')
    with zipfile.ZipFile(dest) as archive:
        assert archive.testzip() is None
        for line in sums.splitlines():
            expected,relative=line.split('  ',1)
            assert hashlib.sha256(archive.read(project.name+'/'+relative)).hexdigest()==expected
    apk=project/f'app/build/outputs/apk/debug/LunaxyMusic-{channel}-{version}-debug.apk'
    assert apk.is_file()
    with zipfile.ZipFile(apk) as archive:
        assert archive.testzip() is None
        assert all(b'MotionInstrumentation' not in archive.read(name) for name in archive.namelist() if name.endswith('.dex'))
    shutil.copy2(apk,delivery/apk.name)
    print(f'{channel}: {len(files)} files hashed, {xml} XML parsed, ZIP CRC and SHA256 all passed')

shutil.copy2(beta/'RELEASE_NOTES_V92.4.md',delivery/'RELEASE_NOTES_V92.4.md')
(delivery/'VALIDATION.md').write_text(validation,encoding='utf-8')
images=delivery/'screenshots';images.mkdir(exist_ok=True)
for name in ['final-player.png','player-stack.png','player-half-swipe.png','queue-before.png','queue-after.png']:
    shutil.copy2(qa/name,images/name)
checksums=''.join(f'{digest(p)}  {p.relative_to(delivery).as_posix()}\n' for p in sorted(delivery.rglob('*')) if p.is_file() and p.name!='SHA256SUMS.txt')
(delivery/'SHA256SUMS.txt').write_text(checksums,encoding='utf-8')
print('DELIVERY',delivery)
print('Changed:',*changed,sep='\n')
