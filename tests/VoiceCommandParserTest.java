package com.xingyu.music.voice;

public final class VoiceCommandParserTest {
    public static void main(String[] args) {
        checkType("下一首", VoiceCommand.Type.NEXT, 1, "");
        checkType("下两首", VoiceCommand.Type.NEXT, 2, "");
        checkType("下三首", VoiceCommand.Type.NEXT, 3, "");
        checkType("上两首", VoiceCommand.Type.PREVIOUS, 2, "");
        checkType("暂停", VoiceCommand.Type.PAUSE, 1, "");
        checkType("继续播放", VoiceCommand.Type.RESUME, 1, "");
        checkType("播放我的深夜歌单", VoiceCommand.Type.PLAY_PLAYLIST, 1, "深夜");
        checkType("播放歌单深夜", VoiceCommand.Type.PLAY_PLAYLIST, 1, "深夜");
        checkType("搜索晴天", VoiceCommand.Type.SEARCH, 1, "晴天");
        checkType("搜索歌手周杰伦", VoiceCommand.Type.SEARCH_ARTIST, 1, "周杰伦");
        checkType("放周杰伦的歌", VoiceCommand.Type.PLAY_ARTIST, 1, "周杰伦");
        checkType("播放七里香", VoiceCommand.Type.PLAY_QUERY, 1, "七里香");
        checkType("播放下午茶", VoiceCommand.Type.PLAY_QUERY, 1, "下午茶");
        checkType("播放我收藏的歌", VoiceCommand.Type.PLAY_PLAYLIST, 1, "收藏");
        check(VoiceCommandParser.containsWakePhrase("嘿，露娜希，下一首", "露娜希"), "wake alias");
        check("下一首".equals(VoiceCommandParser.stripWakePhrase("露娜希下一首", "露娜希")), "wake tail");
        check("下三首".equals(VoiceCommandParser.stripWakePhrase("Hey，Lunaxy，下三首", "Hey Lunaxy", "Lunaxy")), "english wake punctuation tail");
        check("暂停".equals(VoiceCommandParser.stripWakePhrase("Hi，Lunaxy，暂停", "Hi Lunaxy", "Lunaxy")), "hi wake punctuation tail");
        checkType(VoiceCommandParser.stripWakePhrase("Hey Lunaxy 播放我的深夜歌单", "Hey Lunaxy"), VoiceCommand.Type.PLAY_PLAYLIST, 1, "深夜");
        System.out.println("PASS: 19 voice command parser checks");
    }

    private static void checkType(String input, VoiceCommand.Type type, int count, String query) {
        VoiceCommand command = VoiceCommandParser.parse(input);
        check(command.type == type, input + " type " + command.type);
        check(command.count == count, input + " count " + command.count);
        check(query.equals(command.query), input + " query " + command.query);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
