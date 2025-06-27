package com.canhlabs.funnyapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatGptRequest {
    private String model;
    private List<Message> messages;
    private double temperature = 0.7;
    private int max_tokens = 200;

    public ChatGptRequest(String model, List<Message> messages) {
        this.model = model;
        this.messages = messages;
    }

    // getters & setters
    @Data
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Message {
        private String role;
        private String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        // getters & setters
    }
}
