package com.canhlabs.funnyapp.client;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.YouTubeVideoDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class YouTubeApiClientImplTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private AppProperties props;

    @InjectMocks
    private YouTubeApiClientImpl client;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        client.injectRestTemplate(restTemplate);
        client.injectProp(props);
    }

    @Test
    void fetchVideoDetails_shouldReturnMappedDTOs() throws Exception {
        // Arrange
        List<String> videoIds = List.of("abc123");
        String apiUrl = "https://youtube.com/api";
        String apiKey = "key";
        when(props.getYoutubeUrl()).thenReturn(apiUrl);
        when(props.getGoogleApiKey()).thenReturn(apiKey);

        String json = """
        {
          "items": [
            {
              "id": "abc123",
              "snippet": {
                "title": "Funny Video",
                "description": "A funny video"
              },
              "statistics": {
                "likeCount": "42"
              }
            }
          ]
        }
        """;
        ObjectMapper mapper = new ObjectMapper();
        JsonNode responseNode = mapper.readTree(json);

        when(restTemplate.getForEntity(anyString(), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(responseNode));

        // Act
        List<YouTubeVideoDTO> result = client.fetchVideoDetails(videoIds);

        // Assert
        assertEquals(1, result.size());
        YouTubeVideoDTO dto = result.get(0);
        assertEquals("abc123", dto.getVideoId());
        assertEquals("Funny Video", dto.getTitle());
        assertEquals("A funny video", dto.getDescription());
        assertEquals("https://www.youtube.com/watch?v=abc123", dto.getUrlLink());
        assertEquals("https://www.youtube.com/embed/abc123", dto.getEmbedLink());
        assertEquals(42L, dto.getUpCount());
        assertEquals(0L, dto.getDownCount());
    }

    @Test
    void fetchVideoDetails_shouldReturnEmptyList_whenInputIsEmpty() {
        List<YouTubeVideoDTO> result = client.fetchVideoDetails(List.of());
        assertTrue(result.isEmpty());
        verifyNoInteractions(restTemplate);
    }
}