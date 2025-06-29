package com.canhlabs.funnyapp.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Data
public class ChatGptResponse {
    private List<Choice> choices;

    @Data
    @Getter
    @Setter
    public static class Choice {
        private Message message;

    }

    @Data
    @Getter
    @Setter
    public static class Message {
        private String role;
        @Getter
        private String content;

    }
}
