package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.service.ChatGptService;
import com.canhlabs.funnyapp.share.AppProperties;
import com.canhlabs.funnyapp.share.dto.ChatGptRequest;
import com.canhlabs.funnyapp.share.dto.ChatGptResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
public class ChatGptServiceImpl implements ChatGptService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties props;

    public ChatGptServiceImpl(RestTemplate restTemplate, ObjectMapper objectMapper, AppProperties props) {
        this.restTemplate = restTemplate;
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
                "gpt-4.1",
                List.of(new ChatGptRequest.Message("system", prompt))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getGptKey());

        HttpEntity<ChatGptRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ChatGptResponse> response = restTemplate.exchange(
                props.getChatGptUrl(),
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
            log.info("return from chatGPT: {}", content);
            // Parse JSON array to List<String>
            List<String> rs = objectMapper.readValue(content, List.class);
            log.info("{}", rs.size());
            return rs;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse video ID list from ChatGPT response", e);
        }
    }
}
