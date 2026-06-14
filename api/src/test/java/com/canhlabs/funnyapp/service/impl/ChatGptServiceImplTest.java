package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.client.ChatGptResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatGptServiceImplTest {

    @Mock RestTemplate restTemplate;
    @Mock ObjectMapper objectMapper;
    @Mock AppProperties props;

    @InjectMocks ChatGptServiceImpl service;

    private static final String GPT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String GPT_KEY = "sk-test";

    @BeforeEach
    void setUp() {
        when(props.getGptKey()).thenReturn(GPT_KEY);
        when(props.getChatGptUrl()).thenReturn(GPT_URL);
    }

    private ChatGptResponse buildResponse(String content) {
        ChatGptResponse.Message msg = new ChatGptResponse.Message();
        msg.setContent(content);
        ChatGptResponse.Choice choice = new ChatGptResponse.Choice();
        choice.setMessage(msg);
        ChatGptResponse response = new ChatGptResponse();
        response.setChoices(List.of(choice));
        return response;
    }

    @Test
    void getTopYoutubeVideoIds_parsesJsonArrayFromResponse() throws Exception {
        List<String> ids = List.of("id1", "id2", "id3");
        when(restTemplate.exchange(eq(GPT_URL), eq(HttpMethod.POST), any(), eq(ChatGptResponse.class)))
                .thenReturn(ResponseEntity.ok(buildResponse("[\"id1\",\"id2\",\"id3\"]")));
        when(objectMapper.readValue(any(String.class), eq(List.class))).thenReturn(ids);

        List<String> result = service.getTopYoutubeVideoIds();

        assertThat(result).containsExactly("id1", "id2", "id3");
    }

    @Test
    void getTopYoutubeVideoIds_stripsMarkdownCodeBlock() throws Exception {
        String markdownContent = "```json\n[\"id1\"]\n```";
        List<String> ids = List.of("id1");
        when(restTemplate.exchange(eq(GPT_URL), eq(HttpMethod.POST), any(), eq(ChatGptResponse.class)))
                .thenReturn(ResponseEntity.ok(buildResponse(markdownContent)));
        when(objectMapper.readValue(any(String.class), eq(List.class))).thenReturn(ids);

        List<String> result = service.getTopYoutubeVideoIds();

        assertThat(result).containsExactly("id1");
    }

    @Test
    void getTopYoutubeVideoIds_parseFailure_throwsRuntimeException() throws Exception {
        when(restTemplate.exchange(eq(GPT_URL), eq(HttpMethod.POST), any(), eq(ChatGptResponse.class)))
                .thenReturn(ResponseEntity.ok(buildResponse("not-json")));
        when(objectMapper.readValue(any(String.class), eq(List.class)))
                .thenThrow(new RuntimeException("parse error"));

        assertThatThrownBy(() -> service.getTopYoutubeVideoIds())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse video ID list");
    }

    @Test
    void makePoem_returnsContentFromResponse() {
        String poemContent = "Thiên sơn vạn lý hành nhân ảnh";
        when(restTemplate.exchange(eq(GPT_URL), eq(HttpMethod.POST), any(), eq(ChatGptResponse.class)))
                .thenReturn(ResponseEntity.ok(buildResponse(poemContent)));

        String result = service.makePoem("Test Title");

        assertThat(result).isEqualTo(poemContent);
    }

    @Test
    void makePoem_differentTitles_callsApiEachTime() {
        when(restTemplate.exchange(eq(GPT_URL), eq(HttpMethod.POST), any(), eq(ChatGptResponse.class)))
                .thenReturn(ResponseEntity.ok(buildResponse("poem1")))
                .thenReturn(ResponseEntity.ok(buildResponse("poem2")));

        assertThat(service.makePoem("Title1")).isEqualTo("poem1");
        assertThat(service.makePoem("Title2")).isEqualTo("poem2");
    }
}
