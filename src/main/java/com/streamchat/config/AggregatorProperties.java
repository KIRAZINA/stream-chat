package com.streamchat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.aggregator")
@Getter
@Setter
public class AggregatorProperties {
    private boolean enabled = true;
    private boolean persistExternalMessages = false;
}
