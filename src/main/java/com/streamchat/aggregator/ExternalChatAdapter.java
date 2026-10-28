package com.streamchat.aggregator;

public interface ExternalChatAdapter {
    ExternalPlatform getPlatform();
    void connectChannel(String streamKey, String externalChannelId);
    void disconnectChannel(String streamKey, String externalChannelId);
    boolean isConnected(String streamKey);
}
