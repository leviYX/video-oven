package dev.videooven.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ExternalCommand {
    private ExternalCommand() {
    }

    public static Result run(List<String> command, Duration timeout) throws IOException, InterruptedException {
        return run(command, timeout, null);
    }

    public static Result run(List<String> command, Duration timeout, Path workingDirectory)
            throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }
        Process process = processBuilder.start();
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Command timed out: " + String.join(" ", command));
        }
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(process.exitValue(), stdout, stderr);
    }

    public record Result(int exitCode, String stdout, String stderr) {
        public void requireSuccess() throws IOException {
            if (exitCode != 0) {
                throw new IOException("Command failed with exit code " + exitCode + ": " + stderr);
            }
        }
    }
}
