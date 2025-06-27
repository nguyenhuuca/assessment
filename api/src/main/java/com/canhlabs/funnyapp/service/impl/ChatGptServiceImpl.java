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
        ChatGptRequest request = getChatGptRequest();

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

    private static ChatGptRequest getChatGptRequest() {
        String prompt = """
                Bạn là một công cụ tìm kiếm video YouTube ngắn (Shorts).
   
                Hãy tìm 10 video YouTube Shorts, ngẫu nhiên bất kể thời gian đăng tải nào
                - Kết quả là danh sách video ID YouTube hợp lệ dạng ["id1", "id2", ...]
                Ví dụ kết quả đúng:
                ["9bZkp7q19f0", "dQw4w9WgXcQ", "abc123xyz"]
        """;

        return new ChatGptRequest(
                "gpt-4.1",
                List.of(new ChatGptRequest.Message("system", prompt))
        );
    }

    @Override
    public String makePoem(String title) {
        ChatGptRequest request = getChatGptRequest(title);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getGptKey()); // hoặc hardcode "Bearer sk-xxx"

        HttpEntity<ChatGptRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ChatGptResponse> response = restTemplate.exchange(
                props.getChatGptUrl(), // ví dụ: https://api.openai.com/v1/chat/completions
                HttpMethod.POST,
                entity,
                ChatGptResponse.class
        );
        String content = response.getBody().getChoices().get(0).getMessage().getContent();
        log.info("Poem content: {}", content);
        return content;
    }

    private static ChatGptRequest getChatGptRequest(String title) {
        String systemPrompt = "Bạn là nhà thơ cổ điển chuyên làm thơ thất ngôn tứ tuyệt bằng Hán Việt và dịch nghĩa ra tiếng Việt.";

        String userPrompt = String.format("""
            Viết bài thơ thất ngôn tứ tuyệt với tiêu đề: %s.
            
            Yêu cầu:
            - Hai câu đầu là thơ thất ngôn tứ tuyệt, dùng toàn từ gốc **Hán Việt** (viết bằng chữ Quốc ngữ), mang phong vị cổ.
            - Hai câu sau là bản **dịch nghĩa tiếng Việt hiện đại**, giữ đúng nội dung, dễ hiểu.
            - Không dùng từ thuần Việt như: lướt nhẹ, nở rộ, mơ màng, long lanh, xinh đẹp,...
            - Gợi ý từ Hán Việt nên dùng: xuân, phong, nguyệt, tửu, yến, trúc, tình, vọng, vân, thủy, thiền, vũ, luyến, hoài, sắc, mộng, hoan, ca...
            
            Ví dụ mẫu đúng:
            Thiên địa tiêu dao hoa vũ xứ  
            Trúc viên u tĩnh nguyệt sơ khai  
            (Mưa hoa lả tả giữa đất trời,  
            Vườn trúc yên bình, trăng mới lên)
            
            Chỉ trả lại đúng 4 dòng thơ (2 Hán Việt + 2 dịch nghĩa)
            """, title);

        return new ChatGptRequest(
                "gpt-4o",
                List.of(
                        new ChatGptRequest.Message("system", systemPrompt),
                        new ChatGptRequest.Message("user", userPrompt)
                )
        );
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
