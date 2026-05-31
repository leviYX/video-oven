package dev.videooven.platform;

import java.nio.file.Path;
import java.util.List;

public record YtDlpOptions(String cookiesFromBrowser, Path cookies) {
    public static YtDlpOptions none() {
        return new YtDlpOptions(null, null);
    }

    public void appendTo(List<String> command) {
        if (cookiesFromBrowser != null && !cookiesFromBrowser.isBlank()) {
            command.add("--cookies-from-browser");
            command.add(cookiesFromBrowser);
        }
        if (cookies != null) {
            command.add("--cookies");
            command.add(cookies.toString());
        }
    }
}
