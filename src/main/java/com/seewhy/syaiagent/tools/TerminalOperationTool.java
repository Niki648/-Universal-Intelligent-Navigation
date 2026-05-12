package com.seewhy.syaiagent.tools;

import com.seewhy.syaiagent.guardrail.GuardrailService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TerminalOperationTool {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_OUTPUT_CHARS = 12_000;

    private final GuardrailService guardrailService;

    public TerminalOperationTool() {
        this(new GuardrailService());
    }

    public TerminalOperationTool(GuardrailService guardrailService) {
        this.guardrailService = guardrailService;
    }

    @Tool(description = "Execute a command in the terminal")
    public String executeTerminalCommand(@ToolParam(description = "Command to execute in the terminal") String command) {
        StringBuilder output = new StringBuilder();
        ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
        try {
            String safeCommand = guardrailService.validateTerminalCommand(command);
            ProcessBuilder builder = new ProcessBuilder(shellCommand(safeCommand));
            builder.redirectErrorStream(true);
            Process process = builder.start();
            Future<String> outputFuture = readerExecutor.submit(() -> readOutput(process.getInputStream()));
            boolean completed = process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return "Command execution timed out after " + COMMAND_TIMEOUT.toSeconds() + " seconds.";
            }
            output.append(outputFuture.get(1, TimeUnit.SECONDS));
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                output.append("Command execution failed with exit code: ").append(exitCode);
            }
        } catch (SecurityException e) {
            output.append("Blocked terminal command: ").append(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            output.append("Error executing command: ").append(e.getMessage());
        } catch (ExecutionException | TimeoutException e) {
            output.append("Error reading command output: ").append(e.getMessage());
        } catch (IOException e) {
            output.append("Error executing command: ").append(e.getMessage());
        } finally {
            readerExecutor.shutdownNow();
        }
        return output.toString();
    }

    private List<String> shellCommand(String safeCommand) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return List.of("cmd.exe", "/c", safeCommand);
        }
        return List.of("sh", "-c", safeCommand);
    }

    private String readOutput(InputStream inputStream) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, Charset.defaultCharset()))) {
            String line;
            while ((line = reader.readLine()) != null && output.length() < MAX_OUTPUT_CHARS) {
                output.append(line).append(System.lineSeparator());
            }
        }
        if (output.length() > MAX_OUTPUT_CHARS) {
            return output.substring(0, MAX_OUTPUT_CHARS) + System.lineSeparator() + "...";
        }
        return output.toString();
    }
}
