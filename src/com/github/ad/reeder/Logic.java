package com.github.ad.reeder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Logic {
    static final String ITEM_PREFIX = "tag:google.com,2005:reader/item/";

    static long parseItemId(String id) {
        if (id.startsWith(ITEM_PREFIX)) return Long.parseUnsignedLong(id.substring(ITEM_PREFIX.length()), 16);
        return Long.parseLong(id);
    }

    static String longItemId(long id) {
        String hex = Long.toHexString(id);
        return ITEM_PREFIX + "0000000000000000".substring(hex.length()) + hex;
    }

    static int releaseBuild(String tag) {
        try {
            return Integer.parseInt(tag.substring(tag.lastIndexOf('.') + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String apiBase(String url) {
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (u.endsWith("/api/greader.php")) return u;
        if (u.endsWith("/api")) return u + "/greader.php";
        return u + "/api/greader.php";
    }

    static String stripHtml(String html, int max) {
        if (html == null) return "";
        String s = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replaceAll("\\s+", " ").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    static List<String> theses(String yandex300Page) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("<meta name=\"description\" content=\"([^\"]*)\"").matcher(yandex300Page);
        if (!m.find()) return out;
        for (String t : stripHtml(m.group(1), Integer.MAX_VALUE).split("•")) if (!t.trim().isEmpty()) out.add(t.trim());
        return out;
    }

    private static final Pattern IMG = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SRC = Pattern.compile("\\ssrc\\s*=\\s*[\"']([^\"']+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PIXEL = Pattern.compile("\\s(width|height)\\s*=\\s*[\"']?1[\"'\\s/>]", Pattern.CASE_INSENSITIVE);

    static String firstImage(String html) {
        if (html == null) return null;
        Matcher m = IMG.matcher(html);
        while (m.find()) {
            String tag = m.group();
            Matcher src = SRC.matcher(tag);
            if (PIXEL.matcher(tag).find() || !src.find()) continue;
            String u = src.group(1).replace("&amp;", "&");
            if (u.startsWith("//")) u = "https:" + u;
            if (u.startsWith("http://")) u = "https://" + u.substring(7);
            if (u.startsWith("http")) return u;
        }
        return null;
    }

    static long startOfDay(long ms, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static String dayLabel(long ms, long now, TimeZone tz) {
        long day = startOfDay(ms, tz), today = startOfDay(now, tz);
        if (day == today) return "TODAY";
        if (day == startOfDay(today - 1, tz)) return "YESTERDAY";
        SimpleDateFormat f = new SimpleDateFormat(sameYear(ms, now, tz) ? "EEEE, MMMM d" : "EEEE, MMMM d, yyyy", Locale.US);
        f.setTimeZone(tz);
        return f.format(ms).toUpperCase(Locale.US);
    }

    static String syncLabel(long ms, long now, TimeZone tz) {
        long day = startOfDay(ms, tz), today = startOfDay(now, tz);
        String d = day == today ? "'Today'" : day == startOfDay(today - 1, tz) ? "'Yesterday'" : sameYear(ms, now, tz) ? "MMMM d" : "MMMM d, yyyy";
        SimpleDateFormat f = new SimpleDateFormat(d + " 'at' HH:mm", Locale.US);
        f.setTimeZone(tz);
        return f.format(ms);
    }

    static String articleDate(long ms, TimeZone tz) {
        SimpleDateFormat f = new SimpleDateFormat("EEEE, MMMM d, yyyy 'at' HH:mm", Locale.US);
        f.setTimeZone(tz);
        return f.format(ms).toUpperCase(Locale.US);
    }

    private static boolean sameYear(long a, long b, TimeZone tz) {
        Calendar ca = Calendar.getInstance(tz), cb = Calendar.getInstance(tz);
        ca.setTimeInMillis(a);
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR);
    }
}
