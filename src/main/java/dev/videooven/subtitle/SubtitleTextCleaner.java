package dev.videooven.subtitle;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SubtitleTextCleaner {
    private static final Pattern TAG = Pattern.compile("</?[^>]+>");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x[0-9a-fA-F]+|\\d+);");

    private SubtitleTextCleaner() {
    }

    static String clean(String text) {
        String withoutTags = TAG.matcher(text).replaceAll("");
        return decodeEntities(withoutTags).replace('\u00a0', ' ').trim();
    }

    private static String decodeEntities(String text) {
        String decoded = text
                .replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replace("&#xA0;", " ")
                .replace("&#xa0;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");

        Matcher matcher = NUMERIC_ENTITY.matcher(decoded);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String value = matcher.group(1);
            int codePoint = value.startsWith("x") || value.startsWith("X")
                    ? Integer.parseInt(value.substring(1), 16)
                    : Integer.parseInt(value);
            matcher.appendReplacement(builder, Matcher.quoteReplacement(Character.toString(codePoint)));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }
}
