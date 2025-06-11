package com.canhlabs.integrationtest;

import com.canhlabs.funnyapp.dto.LoginDto;
import com.canhlabs.funnyapp.dto.ShareRequestDto;
import com.canhlabs.funnyapp.dto.UserDetailDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
@ExtendWith(SpringExtension.class)
@SpringBootTest
 class IntegrationTest {
    private MockMvc mockMvc;


    @Autowired
    private WebApplicationContext wac;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    }


    @Test
    void testJoinSystem() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LoginDto loginDto = LoginDto.builder()
                .email("ca12@gmail.com")
                .password("123456")
                .build();
        ResultActions rsAction = mockMvc.perform(post("/v1/funny-app/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginDto))
                        .accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andDo(print());
        String contentJson = rsAction.andReturn().getResponse().getContentAsString();
        JsonNode jsonNode = mapper.readTree(contentJson);
        String jwt = jsonNode.get("data").get("jwt").asText();
        Assertions.assertThat(jwt).isNotNull();

    }

    @Test
    void testShareLink() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        // login first
        LoginDto loginDto = LoginDto.builder()
                .email("ca12@gmail.com")
                .password("123456")
                .build();
        ResultActions rsAction = mockMvc.perform(post("/v1/funny-app/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginDto))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(print());

        String contentJson = rsAction.andReturn().getResponse().getContentAsString();
        JsonNode jsonNode = mapper.readTree(contentJson);
        String jwt = jsonNode.get("data").get("jwt").asText();
        setContext(mapper, jsonNode);


        ShareRequestDto shareRequestDto = ShareRequestDto.builder().url("https://www.youtube.com/watch?v=PfdgzZlBF9A").build();

        // need to provided DP_HOST DB_PASS, GOOGLE_KEY via Enviroiment variable
        mockMvc.perform(post("/v1/funny-app/share-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("authorization", jwt)
                        .content(mapper.writeValueAsString(shareRequestDto))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(print());

    }

    private void setContext(ObjectMapper mapper, JsonNode jsonNode) {
        UserDetailDto user = mapper.convertValue( jsonNode.get("data").get("user"),UserDetailDto.class);
        UsernamePasswordAuthenticationToken authentication;
        authentication = new UsernamePasswordAuthenticationToken(user.getEmail(), null, Collections.emptyList());
        authentication.setDetails(user);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}

