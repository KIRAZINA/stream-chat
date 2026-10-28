package com.streamchat.aggregator.adapter;

import com.streamchat.aggregator.ExternalChatAdapter;
import com.streamchat.aggregator.ExternalPlatform;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GenericExternalAdapter implements ExternalChatAdapter {
    private final Map<String, String> connections = new ConcurrentHashMap<>();

    @Override
    public ExternalPlatform getPlatform() {
        return ExternalPlatform.INTERNAL;
    }

    @Override
    public void connectChannel(String streamKey, String externalChannelId) {
        connections.put(streamKey, externalChannelId);
    }

    @Override
    public void disconnectChannel(String streamKey, String externalChannelId) {
        connections.remove(streamKey);
    }

    @Override
    public boolean isConnected(String streamKey) {
        return connections.containsKey(streamKey);
    }
}
