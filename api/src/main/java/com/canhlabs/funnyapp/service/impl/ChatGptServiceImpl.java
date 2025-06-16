package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.service.ChatGptService;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.ChatGptRequest;
import com.canhlabs.funnyapp.dto.ChatGptResponse;
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
                Bạn là một công cụ tìm kiếm video YouTube ngắn (Shorts).
   
                Hãy liệt kê 10 video YouTube Shorts đang theo xu hướng (trending) trong tuần này tại Việt Nam.\s
                Chỉ trả về một mảng JSON dạng: ["videoId1", "videoId2", ..., "videoId10"]
                
                ⚠️ Yêu cầu:
                - Chỉ lấy video ngắn (YouTube Shorts)
                - Ưu tiên video có nhiều lượt xem, thích hoặc bình luận gần đây
                - Video phải phổ biến tại thị trường Việt Nam
                - Không kèm tiêu đề, mô tả hay bất kỳ nội dung nào ngoài mảng JSON
                
                Ví dụ kết quả đúng:
                ["9bZkp7q19f0", "dQw4w9WgXcQ", "abc123xyz"]
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
