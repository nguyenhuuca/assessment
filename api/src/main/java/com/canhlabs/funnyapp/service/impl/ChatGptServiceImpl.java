package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.service.ChatGptService;
import com.canhlabs.funnyapp.service.YoutubeVideoCache;
import com.canhlabs.funnyapp.share.AppProperties;
import com.canhlabs.funnyapp.share.ChatGptRequest;
import com.canhlabs.funnyapp.share.ChatGptResponse;
import com.canhlabs.funnyapp.share.enums.CacheKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Objects;

@Service
public class ChatGptServiceImpl implements ChatGptService {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";// Replace with your actual OpenAI API key

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final AppProperties props;

    public ChatGptServiceImpl(ObjectMapper objectMapper, AppProperties props) {
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Override
    public List<String> getTopYoutubeVideoIds() {
        String prompt = """
        List 10 historically most viewed YouTube video IDs of all time, as of 2023.
        Only return a JSON array with the video IDs. No explanation.
        Example: ["9bZkp7q19f0", "dQw4w9WgXcQ", ...]
        """;

        ChatGptRequest request = new ChatGptRequest(
                "gpt-4o",
                List.of(new ChatGptRequest.Message("user", prompt))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getGptKey());

        HttpEntity<ChatGptRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ChatGptResponse> response = restTemplate.exchange(
                OPENAI_URL,
                HttpMethod.POST,
                entity,
                ChatGptResponse.class
        );

        String content = response.getBody().getChoices().get(0).getMessage().getContent();

        return parseJsonArray(content);
    }

    private List<String> parseJsonArray(String content) {
        try {
            // Remove markdown formatting if present
            if (content.startsWith("```")) {
                content = content.replaceAll("(?s)```json\\s*", "") // remove ```json
                        .replaceAll("(?s)```", "")         // remove ending ```
                        .trim();
            }

            // Parse JSON array to List<String>
            return objectMapper.readValue(content, List.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse video ID list from ChatGPT response", e);
        }
    }
}
