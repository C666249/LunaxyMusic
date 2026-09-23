package com.xingyu.music.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;

/**
 * Small, free weather metadata helper used only by the recommendation UI.
 * It never resolves or touches audio URLs. Weather data comes from Open-Meteo's
 * key-free forecast endpoint and is cached locally for the home-card subtitle.
 */
public final class WeatherMoodProvider {
    private final SharedPreferences prefs;

    public WeatherMoodProvider(Context context) {
        prefs = context.getSharedPreferences("xingyu_weather_v21", Context.MODE_PRIVATE);
    }

    public WeatherSnapshot fetch(double latitude, double longitude) throws Exception {
        String url = "https://api.open-meteo.com/v1/forecast?latitude="
                + enc(String.format(Locale.US, "%.5f", latitude))
                + "&longitude=" + enc(String.format(Locale.US, "%.5f", longitude))
                + "&current=temperature_2m,apparent_temperature,precipitation,rain,weather_code,cloud_cover"
                + "&timezone=auto";
        Http.Response r = Http.get(url, Collections.singletonMap("User-Agent", "SunflowerMusic/1.0"), 6500, 8500);
        if (r.code < 200 || r.code >= 300) throw new IllegalStateException("天气服务 HTTP " + r.code);
        JSONObject root = new JSONObject(r.body == null ? "{}" : r.body);
        JSONObject current = root.optJSONObject("current");
        if (current == null) throw new IllegalStateException("天气数据为空");

        int code = current.optInt("weather_code", -1);
        double temp = current.optDouble("temperature_2m", Double.NaN);
        double apparent = current.optDouble("apparent_temperature", temp);
        double precipitation = current.optDouble("precipitation", 0d);
        double cloud = current.optDouble("cloud_cover", 0d);
        WeatherSnapshot out = new WeatherSnapshot(code, temp, apparent, precipitation, cloud,
                weatherLabel(code), daypart(), System.currentTimeMillis());
        save(out);
        return out;
    }

    public WeatherSnapshot cached() {
        if (!prefs.contains("fetched_at")) return null;
        long fetchedAt = prefs.getLong("fetched_at", 0L);
        if (fetchedAt <= 0L) return null;
        return new WeatherSnapshot(
                prefs.getInt("code", -1),
                Double.longBitsToDouble(prefs.getLong("temp_bits", Double.doubleToRawLongBits(Double.NaN))),
                Double.longBitsToDouble(prefs.getLong("apparent_bits", Double.doubleToRawLongBits(Double.NaN))),
                Double.longBitsToDouble(prefs.getLong("precip_bits", Double.doubleToRawLongBits(0d))),
                Double.longBitsToDouble(prefs.getLong("cloud_bits", Double.doubleToRawLongBits(0d))),
                prefs.getString("label", "天气"),
                daypart(),
                fetchedAt
        );
    }

    private void save(WeatherSnapshot s) {
        prefs.edit()
                .putInt("code", s.weatherCode)
                .putLong("temp_bits", Double.doubleToRawLongBits(s.temperatureC))
                .putLong("apparent_bits", Double.doubleToRawLongBits(s.apparentC))
                .putLong("precip_bits", Double.doubleToRawLongBits(s.precipitationMm))
                .putLong("cloud_bits", Double.doubleToRawLongBits(s.cloudCover))
                .putString("label", s.weatherLabel)
                .putLong("fetched_at", s.fetchedAt)
                .apply();
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Exception impossible) { return s == null ? "" : s; }
    }

    public static String daypart() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 6) return "深夜";
        if (hour < 11) return "早晨";
        if (hour < 14) return "中午";
        if (hour < 18) return "下午";
        if (hour < 23) return "夜晚";
        return "深夜";
    }

    public static String weatherLabel(int code) {
        if (code == 0) return "晴";
        if (code == 1 || code == 2) return "晴间多云";
        if (code == 3) return "阴云";
        if (code == 45 || code == 48) return "雾";
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "雨";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "雪";
        if (code >= 95) return "雷雨";
        return "天气";
    }

    public static final class WeatherSnapshot {
        public final int weatherCode;
        public final double temperatureC;
        public final double apparentC;
        public final double precipitationMm;
        public final double cloudCover;
        public final String weatherLabel;
        public final String daypart;
        public final long fetchedAt;

        WeatherSnapshot(int weatherCode, double temperatureC, double apparentC,
                        double precipitationMm, double cloudCover, String weatherLabel,
                        String daypart, long fetchedAt) {
            this.weatherCode = weatherCode;
            this.temperatureC = temperatureC;
            this.apparentC = apparentC;
            this.precipitationMm = precipitationMm;
            this.cloudCover = cloudCover;
            this.weatherLabel = weatherLabel == null ? "天气" : weatherLabel;
            this.daypart = daypart == null ? WeatherMoodProvider.daypart() : daypart;
            this.fetchedAt = fetchedAt;
        }

        public String compactLabel() {
            String temp = Double.isNaN(temperatureC) ? "" : " · " + Math.round(temperatureC) + "°C";
            return weatherLabel + temp + " · " + daypart;
        }

        public String cacheKey() {
            return weatherBucket() + "|" + daypart;
        }

        public String weatherBucket() {
            int c = weatherCode;
            if ((c >= 51 && c <= 67) || (c >= 80 && c <= 82) || c >= 95) return "rain";
            if ((c >= 71 && c <= 77) || c == 85 || c == 86) return "snow";
            if (c == 45 || c == 48) return "fog";
            if (c == 3 || cloudCover >= 70d) return "cloud";
            return "clear";
        }

        /** One broad discovery keyword; recommendation code keeps artist taste as the stronger signal. */
        public String discoveryWord() {
            String bucket = weatherBucket();
            if ("rain".equals(bucket)) return "雨";
            if ("snow".equals(bucket)) return "雪";
            if ("fog".equals(bucket)) return "雾";
            if ("cloud".equals(bucket)) return "云";
            if ("夜晚".equals(daypart) || "深夜".equals(daypart)) return "夜";
            if ("早晨".equals(daypart)) return "清晨";
            return "晴";
        }
    }
}
