package com.github.ad.reeder;

final class GoogleReader extends GReaderService {
    GoogleReader() {
        super("READER", "Reader", "Self-hosted, Google Reader API", null, "Use the Google Reader API endpoint and credentials of your server.");
    }

    String serverHint() {
        return "API endpoint, e.g. https://rss.example.com/api/greader";
    }
}
