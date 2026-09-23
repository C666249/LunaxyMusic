package com.xingyu.music.ui;
public class Player926PolicyTest {
    public static void main(String[] args) {
        check(Player926Policy.singleAlpha(0)==1, "single center visible");
        check(Player926Policy.singleAlpha(1)==0, "resting neighbours hidden");
        check(Player926Policy.singleAlpha(.5f)==.5f, "half swipe blends records");
        check(Player926Policy.friction(300)==1, "slow fling keeps native friction");
        check(Player926Policy.friction(5000)<Player926Policy.friction(2000), "faster fling travels farther");
        check(Player926Policy.edgeStep(1,2000)>Player926Policy.edgeStep(.1f,0), "deep sustained edge hold accelerates");
        check(Player926Policy.edgeStep(8,999999)<=36, "edge speed bounded");
        System.out.println("PASS: 7 player policy checks");
    }
    static void check(boolean b,String message) { if(!b) throw new AssertionError(message); }
}
