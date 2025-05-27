package com.canhlabs.funnyapp.share;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ChatGptRequest {
    private String model;
    private List<Message> messages;

    public ChatGptRequest(String model, List<Message> messages) {
        this.model = model;
        this.messages = messages;
    }

    // getters & setters
    @Getter
    @Setter
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
