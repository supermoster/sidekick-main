package com.sidekick.cli;

import com.sidekick.browser.BrowserConnectivityCheck;
import com.sidekick.browser.BrowserMode;
import com.sidekick.browser.BrowserSession;
import com.sidekick.hitl.HitlToolRegistry;
import com.sidekick.hitl.TerminalHitlHandler;
import com.sidekick.mcp.McpServer;
import com.sidekick.mcp.McpServerManager;
import com.sidekick.mcp.McpServerStatus;
import com.sidekick.mcp.config.McpConfigLoader;
import com.sidekick.mcp.config.McpServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MainBrowserCommandTest {

    @Test
    void browserStatusShowsCurrentMode(@TempDir Path tempDir) throws IOException {
        Harness h = new Harness(tempDir);

        String result = Main.handleBrowserCommand("status", h.session, h.connectivity, h.manager, h.registry, h.handler);

        assertTrue(result.contains("当前模式"));
        assertTrue(result.contains("isolated"));
    }

    @Test
    void browserConnectRejectsInvalidPort(@TempDir Path tempDir) throws IOException {
        Harness h = new Harness(tempDir);

        String result = Main.handleBrowserCommand("connect 80", h.session, h.connectivity, h.manager, h.registry, h.handler);

        assertTrue(result.contains("1024-65535"));
        assertEquals(BrowserMode.ISOLATED, h.session.mode());
    }

    @Test
    void browserConnectDefaultUsesAutoConnectWithoutLegacyProbe(@TempDir Path tempDir) {
        BrowserSession session = new BrowserSession();
        HitlToolRegistry registry = new HitlToolRegistry(new TerminalHitlHandler(false));
        CountingConnectivityCheck connectivity = new CountingConnectivityCheck();
        FakeMcpServerManager manager = new FakeMcpServerManager(registry, tempDir);

        String result = Main.handleBrowserCommand("connect", session, connectivity, manager, registry, new TerminalHitlHandler(false));

        assertTrue(result.contains("--autoConnect"));
        assertEquals(BrowserMode.SHARED, session.mode());
        assertEquals("autoConnect", session.browserUrl());
        assertEquals(0, connectivity.probeCount);
        assertEquals(List.of("-y", "chrome-devtools-mcp@latest", "--autoConnect"),
                manager.lastArgs.subList(0, 3));
        assertTrue(manager.lastArgs.size() >= 3);
        // --autoConnect must NOT include --executable-path — it would force
        // chrome-devtools-mcp to launch a new browser instead of reusing
        // the already-running one.
        assertFalse(manager.lastArgs.stream().anyMatch(a -> a.startsWith("--executable-path")),
                "--executable-path should not be present in autoConnect args");
    }

    @Test
    void browserDisconnectWithoutServerClearsSession(@TempDir Path tempDir) throws IOException {
        Harness h = new Harness(tempDir);
        h.session.switchToShared("http://127.0.0.1:9222");

        String result = Main.handleBrowserCommand("disconnect", h.session, h.connectivity, h.manager, h.registry, h.handler);

        assertTrue(result.contains("未配置"));
        assertEquals(BrowserMode.ISOLATED, h.session.mode());
    }

    @Test
    void browserTabsInIsolatedModeGivesConnectHint(@TempDir Path tempDir) throws IOException {
        Harness h = new Harness(tempDir);

        String result = Main.handleBrowserCommand("tabs", h.session, h.connectivity, h.manager, h.registry, h.handler);

        assertTrue(result.contains("isolated"));
        assertTrue(result.contains("/browser connect"));
    }

    @Test
    void unknownBrowserSubCommandShowsHelp(@TempDir Path tempDir) throws IOException {
        Harness h = new Harness(tempDir);

        String result = Main.handleBrowserCommand("wat", h.session, h.connectivity, h.manager, h.registry, h.handler);

        assertTrue(result.contains("未知 /browser 子命令"));
        assertTrue(result.contains("/browser connect"));
    }

    @Test
    void isEdgeExecutableDetectsEdgePaths() {
        assertTrue(Main.isEdgeExecutable("C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"));
        assertTrue(Main.isEdgeExecutable("C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe"));
        assertTrue(Main.isEdgeExecutable("/usr/bin/msedge"));
        assertTrue(Main.isEdgeExecutable("/Applications/Microsoft Edge.app/Contents/MacOS/msedge"));
    }

    @Test
    void isEdgeExecutableRejectsNonEdgePaths() {
        assertFalse(Main.isEdgeExecutable("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"));
        assertFalse(Main.isEdgeExecutable("/usr/bin/google-chrome"));
        assertFalse(Main.isEdgeExecutable(null));
        assertFalse(Main.isEdgeExecutable(""));
    }

    @Test
    void browserAutoConnectErrorSuggestsDebuggingGuide(@TempDir Path tempDir) {
        BrowserSession session = new BrowserSession();
        FakeMcpServerManager manager = new FakeMcpServerManager(
                new HitlToolRegistry(new TerminalHitlHandler(false)), tempDir);
        // Make restartWithArgs leave server in DISABLED so autoConnect appears to fail
        manager.restartResultStatus = McpServerStatus.DISABLED;

        String result = Main.handleBrowserCommand("connect", session, new CountingConnectivityCheck(), manager,
                new HitlToolRegistry(new TerminalHitlHandler(false)), new TerminalHitlHandler(false));

        assertTrue(result.contains("autoConnect 连接失败"));
        assertTrue(result.contains("Allow remote debugging"));
    }

    private static final class Harness {
        private final BrowserSession session = new BrowserSession();
        private final BrowserConnectivityCheck connectivity = new BrowserConnectivityCheck();
        private final TerminalHitlHandler handler = new TerminalHitlHandler(false);
        private final HitlToolRegistry registry = new HitlToolRegistry(handler);
        private final McpServerManager manager;

        private Harness(Path tempDir) throws IOException {
            manager = new McpServerManager(
                    registry,
                    tempDir,
                    new McpConfigLoader(tempDir.resolve("user.json"), tempDir.resolve("project.json"), tempDir));
            manager.loadConfiguredServers();
        }
    }

    private static final class CountingConnectivityCheck extends BrowserConnectivityCheck {
        private int probeCount;

        @Override
        public ProbeResult probe(int port) {
            probeCount++;
            return new ProbeResult(false, null, "should not probe");
        }
    }

    private static final class FakeMcpServerManager extends McpServerManager {
        final McpServer server;
        List<String> lastArgs = List.of();
        McpServerStatus restartResultStatus = McpServerStatus.READY;

        private FakeMcpServerManager(HitlToolRegistry registry, Path projectDir) {
            super(registry, projectDir);
            McpServerConfig config = new McpServerConfig();
            config.setCommand("npx");
            config.setArgs(List.of("-y", "chrome-devtools-mcp@latest", "--isolated=true"));
            this.server = new McpServer("chrome-devtools", config);
            this.server.status(McpServerStatus.READY);
        }

        @Override
        public synchronized String restartWithArgs(String name, List<String> args) {
            lastArgs = List.copyOf(args);
            server.config().setArgs(args);
            server.status(restartResultStatus);
            return restartResultStatus == McpServerStatus.READY
                    ? "✅ MCP server 已重启: " + name
                    : "❌ restart failed";
        }

        @Override
        public McpServer server(String name) {
            return "chrome-devtools".equals(name) ? server : null;
        }
    }
}
