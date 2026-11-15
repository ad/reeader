package com.github.ad.reeder;

import java.io.IOException;

// Only Google Reader API services whose item ids fit in a long belong here: Db keys items by long.
abstract class GReaderService extends Service {
    GReaderService(String id, String title, String subtitle, String host, String hint) {
        super(id, title, subtitle, host, hint);
    }

    String login(String base, String user, String pass) throws IOException {
        return Api.login(base, user, pass);
    }

    Backend backend(String base, String auth) {
        return new GReader(new Api(base, auth));
    }
}
