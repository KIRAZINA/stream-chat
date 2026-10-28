package com.streamchat.controller;

import com.streamchat.config.CacheProperties;
import com.streamchat.model.entity.Emote;
import com.streamchat.repository.EmoteRepository;
import com.streamchat.repository.StreamRepository;
import com.streamchat.service.EmoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class EmoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmoteRepository emoteRepository;

    @MockBean
    private StreamRepository streamRepository;

    @Test
    void globalEmotes_returnsCacheControlAndEtag() throws Exception {
        Emote emote = Emote.builder().id(1L).code("pepeKek").imageUrl("/img/pepe.png").isGlobal(true).build();
        when(emoteRepository.findByStreamIdOrGlobal(null)).thenReturn(List.of(emote));

        mockMvc.perform(get("/api/emotes/global"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=86400")));
    }
}
