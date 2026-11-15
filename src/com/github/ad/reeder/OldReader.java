package com.github.ad.reeder;

final class OldReader extends GReaderService {
    OldReader() {
        super("OLDREADER", "The Old Reader", "theoldreader.com", "https://theoldreader.com",
                "Use your email and password. Accounts made with Google or Facebook need a password set in Settings.");
    }

    String userHint() {
        return "Email";
    }
}
