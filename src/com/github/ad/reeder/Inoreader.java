package com.github.ad.reeder;

import java.io.IOException;

final class Inoreader extends GReaderService {
    Inoreader() {
        super("INOREADER", "Inoreader", "inoreader.com", "https://www.inoreader.com",
                "Requires an Inoreader Pro plan: free accounts get no API access.");
    }

    String userHint() {
        return "Email";
    }

    String[] headers() {
        return new String[]{"AppId", Keys.INOREADER_APP_ID, "AppKey", Keys.INOREADER_APP_KEY};
    }

    String login(String base, String user, String pass) throws IOException {
        if (Keys.INOREADER_APP_ID.isEmpty()) throw new IOException("This build has no Inoreader app keys");
        return super.login(base, user, pass);
    }
}
