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
        String systemPrompt = "Bạn là nhà thơ cổ điển, chuyên sáng tác thơ thất ngôn tứ tuyệt theo phong cách Đường thi.";

        String userPrompt = String.format("""
            Yêu cầu:
           \s
            1. Viết một bài thơ thất ngôn tứ tuyệt với tiêu đề: %s.
            2. Hai câu đầu viết bằng chữ Quốc ngữ, nhưng toàn bộ dùng **từ Hán Việt** (tức là từ gốc Hán, không dùng từ thuần Việt).
            3. Hai câu sau là **bản dịch nghĩa hiện đại**, dùng từ thông dụng dễ hiểu, giữ đúng ý thơ.
            4. Chỉ trả về đúng 4 dòng thơ (2 Hán Việt + 2 dịch nghĩa).
            5. Luôn đảm bảo thơ có hình ảnh, nhạc tính, gợi cảm.
           \s
            Hạn chế:
            - Không dùng từ thuần Việt như: "lướt nhẹ", "rực rỡ", "xinh đẹp", "trong lành", "mơ màng",...
            - Không dùng tiếng lóng, hiện đại hóa ngôn từ.
            - Tránh vần điệu hiện đại hoặc diễn đạt thô.
           \s
            Danh sách từ Hán Việt nên ưu tiên sử dụng:
            - **Thiên nhiên**: xuân, phong, vũ, nguyệt, vân, tuyết, sơn, xuyên, giang, hồ, trúc, tùng, mai, hoa, liễu, lộc, tịch
            - **Tình cảm nội tâm**: tình, ý, mộng, hoài, luyến, vọng, ưu, sầu, bi, hận, tư, nguyện, mê, giác, ngộ
            - **Không gian thời gian**: dạ, canh, tịch, sơ, cổ, kim, vãn, mộng cảnh, thiên địa
            - **Con người/sinh hoạt**: tửu, yến, ca, vũ, thi, họa, khách, nhân, thi nhân, đạo
            - **Triết lý cổ điển**: hư, vô, sắc, tịnh, thiền, không, hữu, sinh, tử, luân hồi

            Ví dụ đúng:
            Thiên sơn vạn lý hành nhân ảnh \s
            Dạ nguyệt cô hoa vọng cố hương \s
            (Núi ngàn xa tít bóng người qua, \s
            Trăng đêm cô tịch nhớ quê nhà)
       \s""", title);

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
