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

        Service fr = Service.FRESHRSS;
        assert fr.apiBase("rss.example.com").equals("https://rss.example.com/api/greader.php");
        assert fr.apiBase("https://rss.example.com/api/greader.php/").equals("https://rss.example.com/api/greader.php");
        assert fr.apiBase("http://h/fr/api").equals("http://h/fr/api/greader.php");
        assert Service.of("MINIFLUX").apiBase(" mf.example.com/ ").equals("https://mf.example.com");
        assert Service.of("BAZQUX").apiBase("").equals("https://bazqux.com");
        assert Service.of(null) == fr && Service.of("READER") instanceof GoogleReader;

        assert Logic.stripHtml("<p>Hi&nbsp;<b>there</b></p><script>x()</script>", 100).equals("Hi there");
        assert Logic.stripHtml("abcdef", 3).equals("abc");

        assert Logic.theses("<meta name=\"description\" content=\"• Цены &quot;растут&quot;. \n • R&amp;D урезали.\">")
                .equals(java.util.Arrays.asList("Цены \"растут\".", "R&D урезали.")) : Logic.theses("x");
        assert Logic.theses("<html></html>").isEmpty();

        TimeZone tz = TimeZone.getTimeZone("Europe/Moscow");
        long now = 1794573000000L;
        assert Logic.dayLabel(now - 3600_000L, now, tz).equals("TODAY");
        assert Logic.dayLabel(now - 24 * 3600_000L, now, tz).equals("YESTERDAY");
        assert Logic.dayLabel(now - 3 * 24 * 3600_000L, now, tz).equals("TUESDAY, NOVEMBER 10") : Logic.dayLabel(now - 3 * 24 * 3600_000L, now, tz);
        assert Logic.dayLabel(now - 400 * 24 * 3600_000L, now, tz).endsWith(", 2025");
        assert Logic.syncLabel(now, now, tz).matches("Today at \\d\\d:\\d\\d") : Logic.syncLabel(now, now, tz);
        assert Logic.syncLabel(now - 24 * 3600_000L, now, tz).startsWith("Yesterday at ");
        assert Logic.syncLabel(now - 3 * 24 * 3600_000L, now, tz).startsWith("November 10 at ");
        assert Logic.syncLabel(now - 400 * 24 * 3600_000L, now, tz).contains(", 2025 at ");
        assert Logic.firstImage("<p><img width=\"1\" src=\"https://t/p.gif\"><IMG alt='x' SRC='//h/a.jpg?a=1&amp;b=2'></p>").equals("https://h/a.jpg?a=1&b=2");
        assert Logic.firstImage("<img src=\"data:image/png;base64,xx\"><img src=\"/rel.png\">") == null;
        assert Logic.firstImage(null) == null;
        assert Logic.firstImage("<img src='http://h/b.png'>").equals("https://h/b.png");
        System.out.println("LogicTest ok");
    }
}
