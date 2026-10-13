#!/usr/bin/env python3
"""V152 Cloud: tap-origin circular reveal and ripple for center Agent nav only."""
from pathlib import Path

root=Path.cwd()
source=Path("../.ci/v152-cloud/AgentRippleWaveView.java")
assert source.is_file()
for prefix in ("app","qa/android-project/app"):
    p=root/prefix/"src/main/java/com/xingyu/music/MainActivity.java"
    s=p.read_text(encoding="utf-8")
    def change(a,b):
        global s
        if s.count(a)!=1:raise SystemExit("Unexpected MainActivity: "+a[:90]+"="+str(s.count(a)))
        s=s.replace(a,b,1)
    change("import android.animation.ValueAnimator;","import android.animation.Animator;\nimport android.animation.AnimatorListenerAdapter;\nimport android.animation.AnimatorSet;\nimport android.animation.ValueAnimator;")
    change("import android.view.View;","import android.view.View;\nimport android.view.ViewAnimationUtils;")
    change("import com.xingyu.music.ui.AgentOrbView;","import com.xingyu.music.ui.AgentOrbView;\nimport com.xingyu.music.ui.AgentRippleWaveView;")
    change("    private AgentPanel agentPanel;","    private AgentPanel agentPanel;\n    private AnimatorSet agentNavRevealAnimator;\n    private AgentRippleWaveView agentNavRippleWave;")
    change('''                agentNavItem.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    showAgentPanel();
                });
                agentNavItem.setOnLongClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    showAgentPanel();
                    main.postDelayed(this::showAgentSettings, 180L);
                    return true;
                });''',
                '''                agentNavItem.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    showAgentPanelFromNav(v);
                });
                agentNavItem.setOnLongClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    showAgentPanelFromNav(v);
                    main.postDelayed(this::showAgentSettings,
                            SpringMotion.isReducedMotion() ? 180L : 550L);
                    return true;
                });''')
    block='''    /**
     * True origin-centered circular transition for center Agent tab only.
     * Other Agent entry points preserve their navigation and existing animation.
     */
    private void showAgentPanelFromNav(View source) {
        if (agentPanel == null || appRoot == null || source == null ||
                agentPanel.getVisibility() == View.VISIBLE || SpringMotion.isReducedMotion()) {
            showAgentPanel();
            return;
        }
        if (!agentSessionLoaded) {
            agentSessionLoaded = true;
            restoreAgentConversation(activeAgentConversationId);
        }
        if (agentPanel.getWidth() <= 0 || agentPanel.getHeight() <= 0) {
            showAgentPanel();
            return;
        }
        cancelAgentNavReveal();
        agentPanel.bringToFront();
        agentPanel.animate().cancel();
        agentPanel.setVisibility(View.VISIBLE);
        agentPanel.setAlpha(1f);
        agentPanel.setScaleX(1f); agentPanel.setScaleY(1f);
        agentPanel.setTranslationX(0f); agentPanel.setTranslationY(0f);
        int[] tap = new int[2], panel = new int[2];
        source.getLocationInWindow(tap);
        agentPanel.getLocationInWindow(panel);
        float cx = tap[0] - panel[0] + source.getWidth() * .5f;
        float cy = tap[1] - panel[1] + source.getHeight() * .5f;
        float dx = Math.max(Math.abs(cx), Math.abs(agentPanel.getWidth() - cx));
        float dy = Math.max(Math.abs(cy), Math.abs(agentPanel.getHeight() - cy));
        float radius = (float)Math.hypot(dx, dy) + Ui.dp(this, 4);
        final AgentRippleWaveView wave = new AgentRippleWaveView(this, cx, cy, radius);
        agentNavRippleWave = wave;
        appRoot.addView(wave, Ui.frame(-1, -1, Gravity.FILL));
        Animator clip = ViewAnimationUtils.createCircularReveal(agentPanel,
                Math.round(cx), Math.round(cy), 0f, radius);
        ValueAnimator waveClock = ValueAnimator.ofFloat(0f, 1f);
        waveClock.addUpdateListener(a -> wave.setProgress((Float) a.getAnimatedValue()));
        clip.setDuration(520L);
        waveClock.setDuration(520L);
        android.view.animation.Interpolator easing =
                new android.view.animation.PathInterpolator(.24f, .02f, .12f, 1f);
        clip.setInterpolator(easing);
        waveClock.setInterpolator(easing);
        AnimatorSet together = new AnimatorSet();
        agentNavRevealAnimator = together;
        together.playTogether(clip, waveClock);
        together.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { finish(); }
            @Override public void onAnimationCancel(Animator animation) { finish(); }
            private void finish() {
                if (agentNavRippleWave == wave) {
                    appRoot.removeView(wave);
                    agentNavRippleWave = null;
                }
                if (agentNavRevealAnimator == together) agentNavRevealAnimator = null;
            }
        });
        together.start();
    }

    private void cancelAgentNavReveal() {
        AnimatorSet active = agentNavRevealAnimator;
        agentNavRevealAnimator = null;
        if (active != null) active.cancel();
        if (agentNavRippleWave != null && appRoot != null) {
            appRoot.removeView(agentNavRippleWave);
            agentNavRippleWave = null;
        }
    }

'''
    change("    private void showAgentPanel() {\n",block+"    private void showAgentPanel() {\n")
    change("    private void showAgentPanel() {\n        if (agentPanel == null) return;",
           "    private void showAgentPanel() {\n        if (agentPanel == null) return;\n        cancelAgentNavReveal();")
    change("    private void hideAgentPanel() {\n        stopAgentVoiceInput();",
           "    private void hideAgentPanel() {\n        cancelAgentNavReveal();\n        stopAgentVoiceInput();")
    # INVISIBLE participates in layout so the very first tap can perform the circular reveal.
    assert s.count("agentPanel.setVisibility(View.GONE);")==2
    s=s.replace("agentPanel.setVisibility(View.GONE);","agentPanel.setVisibility(View.INVISIBLE);")
    change('''        String engine = agentConfig.wakeTemplateCount() >= 2 ? "自定义个人唤醒（独立本机训练）"
                : "哈哈".equals(agentConfig.wakeWord()) ? "双语 KWS 默认词（尚未训练）"
                : "系统兼容唤醒（请训练自定义唤醒词）";''',
           '''        String engine = agentConfig.wakeTemplateCount() >= 2 ? "自定义个人唤醒（独立本机训练）"
                : "系统兼容唤醒（建议训练自定义唤醒词）";''')
    change("对于未训练的“哈哈”，可使用内置 KWS。指令使用 SenseVoice 多语言识别，后台首次加载模型可能较慢，后续会复用。",
           "已恢复 Vosk 逐段流式识别和系统语音识别回退；说话时可看到实时文字。放歌时建议使用已训练的个人唤醒词，以避免系统兼容识别器反复抢占音频。")
    p.write_text(s,encoding="utf-8")
    dest=root/prefix/"src/main/java/com/xingyu/music/ui/AgentRippleWaveView.java"
    dest.write_bytes(source.read_bytes())

a=(root/"app/src/main/java/com/xingyu/music/MainActivity.java").read_bytes()
b=(root/"qa/android-project/app/src/main/java/com/xingyu/music/MainActivity.java").read_bytes()
assert a==b
print("V152 Agent circular reveal, full-screen synchronized ripple, reduced motion and cleanup enabled")
