package com.seewhy.syaiagent.tools;

import com.seewhy.syaiagent.guardrail.GuardrailService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class TerminalOperationTool {

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
        try {
            String safeCommand = guardrailService.validateTerminalCommand(command);
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", safeCommand);
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                output.append("Command execution failed with exit code: ").append(exitCode);
            }
        } catch (SecurityException e) {
            output.append("Blocked terminal command: ").append(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            output.append("Error executing command: ").append(e.getMessage());
        } catch (IOException e) {
            output.append("Error executing command: ").append(e.getMessage());
        }
        return output.toString();
    }
}
