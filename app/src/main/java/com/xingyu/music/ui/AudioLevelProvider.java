package com.xingyu.music.ui;

/**
 * Tiny pull-based bridge from the Media3 audio thread to custom visual views.
 * Implementations should be allocation-free because the methods are sampled on every frame.
 */
public interface AudioLevelProvider {
    float energy();
    float bass();
    float treble();
    float beat();
}
