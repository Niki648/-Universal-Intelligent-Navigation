package com.seewhy.syaiagent.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class TerminalOperationToolTest {

    @Test
    @Tag("integration")
    void executeTerminalCommand() {
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        String command = "dir";
        String result = terminalOperationTool.executeTerminalCommand(command);
        Assertions.assertNotNull(result);
    }
}
