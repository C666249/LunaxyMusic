from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root/'app/src/main/java/com/xingyu/music/MainActivity.java').read_text(encoding='utf-8')
ui = (root/'app/src/main/java/com/xingyu/music/ui/Ui.java').read_text(encoding='utf-8')
motion = (root/'app/src/main/java/com/xingyu/music/ui/SpringMotion.java').read_text(encoding='utf-8')
star2 = (root/'app/src/main/java/com/xingyu/music/ui/StarfieldView.java').read_text(encoding='utf-8')
star3 = (root/'app/src/main/java/com/xingyu/music/ui/Starfield3DView.java').read_text(encoding='utf-8')
checks=[]
def check(cond,msg):
    if not cond: raise AssertionError(msg)
    checks.append(msg)

# Root-tab transitions must remain spatial without translucent double exposure.
check('tabPageDuration' in motion and 'TAB_PAGE' in motion, 'dedicated monotonic root-tab motion token')
check('final float start = outgoing.getTranslationX() + direction * safeWidth' in main and 'incoming.setTranslationX(start)' in main, 'incoming root page starts adjacent to current presentation')
check('outgoing.setTranslationX(position[0] - direction * safeWidth)' in main, 'outgoing root page remains one full viewport from incoming without overlap')
check('incoming.setAlpha(1f)' in main, 'incoming root page stays opaque during lateral handoff')
check('outgoing.setAlpha(1f)' in main, 'outgoing root page stays opaque during lateral handoff')
check('lateralTravel' not in main, 'old partial-distance translucent root-tab transition removed')

# Selection carrier must be aligned in navHost coordinates, not double-count host padding.
check('targetCenterInHost' in main and 'pillBaseCenter' in main, 'tab selection carrier uses actual target center in host coordinates')
check('+ navBar.getLeft()' not in main[main.find('private void animateNavSelectionPill()'):main.find('private void rememberRootTabContext()')], 'pill alignment no longer double-counts nav host padding')
check('TAB_SELECTION' in motion and 'tabSelectionDuration' in motion, 'selection carrier uses restrained monotonic selection motion')

# Moonlight must be purpose-built: crisp white surfaces, stronger light-mode accents, calmer stars.
check('LIGHT_PURPLE' in ui and 'DARK_PURPLE' in ui, 'theme-aware accent families exist')
check('BG = Color.rgb(248, 249, 253)' in ui, 'Moonlight uses calm neutral canvas')
check('SURFACE = Color.rgb(255, 255, 255)' in ui, 'Moonlight functional surfaces are crisp white')
check('LIGHT_APPEARANCE ? .18f' in star2 or 'Ui.isLightAppearance() ? .18f' in star2, '2D light starfield intensity is reduced')
check('LIGHT_APPEARANCE ? .16f' in star3 or 'Ui.isLightAppearance() ? .16f' in star3, '3D light starfield intensity is reduced')
print(f'PASS: {len(checks)} V92.9.1 tab/light-theme regression checks')
