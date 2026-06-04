package dev.videooven.article;

import java.io.IOException;
import java.net.URI;

public interface ArticleFetcher {
    String fetch(URI url) throws IOException, InterruptedException;
}
