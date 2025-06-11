package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.ChatGptResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ChatGptServiceImplTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private AppProperties props;

    @InjectMocks
    private ChatGptServiceImpl chatGptService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(props.getGptKey()).thenReturn("test-key");
        when(props.getChatGptUrl()).thenReturn("http://gpt.url");
    }

    @Test
    void getTopYoutubeVideoIds_returnsList() throws Exception {
        // Arrange
        String jsonArray = "[\"id1\",\"id2\"]";
        String markdownContent = "```json\n" + jsonArray + "\n```";

        ChatGptResponse.Message message = new ChatGptResponse.Message();
        message.setContent(markdownContent);
        ChatGptResponse.Choice choice = new ChatGptResponse.Choice();
        choice.setMessage(message);
        ChatGptResponse gptResponse = new ChatGptResponse();
        gptResponse.setChoices(List.of(choice));
        ResponseEntity<ChatGptResponse> responseEntity = new ResponseEntity<>(gptResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(ChatGptResponse.class)
        )).thenReturn(responseEntity);

        when(objectMapper.readValue(jsonArray, List.class)).thenReturn(List.of("id1", "id2"));

        // Act
        List<String> result = chatGptService.getTopYoutubeVideoIds();

        // Assert
        assertThat(result).containsExactly("id1", "id2");
    }

    @Test
    void getTopYoutubeVideoIds_throwsOnParseError() throws Exception {
        // Arrange
        String invalidJson = "not a json";
        ChatGptResponse.Message message = new ChatGptResponse.Message();
        message.setContent(invalidJson);
        ChatGptResponse.Choice choice = new ChatGptResponse.Choice();
        choice.setMessage(message);
        ChatGptResponse gptResponse = new ChatGptResponse();
        gptResponse.setChoices(List.of(choice));
        ResponseEntity<ChatGptResponse> responseEntity = new ResponseEntity<>(gptResponse, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(ChatGptResponse.class)))
                .thenReturn(responseEntity);
        when(objectMapper.readValue(invalidJson, List.class)).thenThrow(new RuntimeException("parse error"));

        // Act & Assert
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> chatGptService.getTopYoutubeVideoIds())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse video ID list");
    }
}