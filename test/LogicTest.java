package com.github.ad.reeder;

import java.util.TimeZone;

public class LogicTest {
    public static void main(String[] a) {
        boolean ea = false;
        assert ea = true;
        if (!ea) throw new AssertionError("run with -ea");

        long id = 0x643734c994c35L;
        String longId = Logic.longItemId(id);
        assert longId.equals("tag:google.com,2005:reader/item/000643734c994c35") : longId;
        assert Logic.parseItemId(longId) == id;
        assert Logic.parseItemId("1763012345678901") == id;
        assert Logic.parseItemId("tag:google.com,2005:reader/item/ffffffffffffffff") == -1L;

        assert Logic.releaseBuild("v0.1.26") == 26 && Logic.releaseBuild("v1") == -1 && Logic.releaseBuild("") == -1;

        assert Logic.apiBase("rss.example.com").equals("https://rss.example.com/api/greader.php");
        assert Logic.apiBase("https://rss.example.com/api/greader.php/").equals("https://rss.example.com/api/greader.php");
        assert Logic.apiBase("http://h/fr/api").equals("http://h/fr/api/greader.php");

        assert Logic.stripHtml("<p>Hi&nbsp;<b>there</b></p><script>x()</script>", 100).equals("Hi there");
        assert Logic.stripHtml("abcdef", 3).equals("abc");

        TimeZone tz = TimeZone.getTimeZone("Europe/Moscow");
        long now = 1794573000000L;
        assert Logic.dayLabel(now - 3600_000L, now, tz).equals("TODAY");
        assert Logic.dayLabel(now - 24 * 3600_000L, now, tz).equals("YESTERDAY");
        assert Logic.dayLabel(now - 3 * 24 * 3600_000L, now, tz).equals("TUESDAY, NOVEMBER 10") : Logic.dayLabel(now - 3 * 24 * 3600_000L, now, tz);
        assert Logic.dayLabel(now - 400 * 24 * 3600_000L, now, tz).endsWith(", 2025");
        System.out.println("LogicTest ok");
    }
}
