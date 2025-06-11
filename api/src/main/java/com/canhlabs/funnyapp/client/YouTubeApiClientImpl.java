package com.canhlabs.funnyapp.client;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.YouTubeVideoDTO;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Component
public class YouTubeApiClientImpl implements YouTubeApiClient {

    private AppProperties props;

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    public void injectProp(AppProperties props){
        this.props = props;
    }

    @Override
    public List<YouTubeVideoDTO> fetchVideoDetails(List<String> videoIds) {
        if (videoIds.isEmpty()) return List.of();

        String ids = String.join(",", videoIds);
        String url = String.format(
                props.getYoutubeUrl().concat("?part=snippet,statistics&id=%s&key=%s"),
                ids, props.getGoogleApiKey()
        );

        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        JsonNode items = response.getBody().get("items");

        List<YouTubeVideoDTO> result = new ArrayList<>();
        for (JsonNode item : items) {
            String id = item.get("id").asText();
            JsonNode snippet = item.get("snippet");
            JsonNode stats = item.get("statistics");

            YouTubeVideoDTO dto = new YouTubeVideoDTO();
            dto.setVideoId(id);
            dto.setTitle(snippet.get("title").asText());
            dto.setDescription(snippet.get("description").asText());
            dto.setUrlLink("https://www.youtube.com/watch?v=" + id);
            dto.setEmbedLink("https://www.youtube.com/embed/" + id);
            dto.setUpCount(stats.has("likeCount") ? stats.get("likeCount").asLong() : 0L);
            dto.setDownCount(0L); // dislike count không còn được trả về

            result.add(dto);
        }

        return result;
    }
}
