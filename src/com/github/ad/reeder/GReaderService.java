package com.github.ad.reeder;

import java.io.IOException;

abstract class GReaderService extends Service {
    GReaderService(String id, String title, String subtitle, String host, String hint) {
        super(id, title, subtitle, host, hint);
    }

    String[] headers() {
        return new String[0];
    }

    String login(String base, String user, String pass) throws IOException {
        return Api.login(base, user, pass, headers());
    }

    Backend backend(String base, String auth) {
        return new GReader(new Api(base, auth, headers()));
    }
}
