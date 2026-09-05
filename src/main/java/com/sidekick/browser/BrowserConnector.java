package com.sidekick.browser;

public interface BrowserConnector {
    String status();

    String connectDefault();

    String disconnect();
}
