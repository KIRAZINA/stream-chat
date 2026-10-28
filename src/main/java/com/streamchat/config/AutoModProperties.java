package com.streamchat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.automod")
@Getter
@Setter
public class AutoModProperties {
    private double capsThreshold = 0.7;
    private int spamScoreThreshold = 3;
    private double toxicityScoreThreshold = 0.85;
    private boolean shadowBanEnabled = true;
    private boolean aiInferenceEnabled = false;
    private String aiInferenceUrl = "http://localhost:5000/v1/analyze";
}
