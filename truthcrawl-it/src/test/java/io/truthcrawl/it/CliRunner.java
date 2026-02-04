package io.truthcrawl.it;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the truthcrawl CLI as a subprocess and captures output/exit code.
 */
final class CliRunner {

    private CliRunner() {}

    record Result(int exitCode, String stdout, String stderr) {}

    static Result run(String... args) throws IOException, InterruptedException {
        String cliJar = System.getProperty("cli.jar");
        String coreJar = System.getProperty("core.jar");
        if (cliJar == null || coreJar == null) {
            throw new IllegalStateException("cli.jar and core.jar system properties must be set");
        }

        String classpath = cliJar + File.pathSeparator + coreJar;

        List<String> command = new ArrayList<>();
        command.add(ProcessHandle.current().info().command().orElse("java"));
        command.add("-cp");
        command.add(classpath);
        command.add("io.truthcrawl.cli.Main");
        command.addAll(List.of(args));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        int exitCode = process.waitFor();

        return new Result(exitCode, stdout, stderr);
    }
}
