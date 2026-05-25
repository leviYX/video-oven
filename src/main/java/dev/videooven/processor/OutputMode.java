package dev.videooven.processor;

public enum OutputMode {
    CHINESE,
    BILINGUAL;

    public static OutputMode parse(String value) {
        return switch (value.toLowerCase()) {
            case "zh", "chinese", "cn", "中文" -> CHINESE;
            case "bilingual", "bi", "dual", "双语" -> BILINGUAL;
            default -> throw new IllegalArgumentException("Unsupported output mode: " + value);
        };
    }
}
