package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.client.YouTubeApiClient;
import com.canhlabs.funnyapp.domain.YouTubeVideo;
import com.canhlabs.funnyapp.repo.YoutubeVideoRepo;
import com.canhlabs.funnyapp.service.ChatGptService;
import com.canhlabs.funnyapp.share.dto.VideoDto;
import com.canhlabs.funnyapp.share.dto.YouTubeVideoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class YouTubeVideoServiceTest {

    @Mock
    private YoutubeVideoRepo repository;
    @Mock
    private ChatGptService chatGptService;
    @Mock
    private YouTubeApiClient youTubeApiClient;

    @InjectMocks
    private YouTubeVideoService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getVideoIds_returnsDtosFromDb() {
        YouTubeVideo video = new YouTubeVideo();
        video.setId(1L);
        video.setTitle("title");
        video.setDesc("desc");
        video.setEmbedLink("embed");
        video.setUrlLink("url");
        when(repository.findAllBySource("youtube")).thenReturn(List.of(video));

        List<VideoDto> result = service.getVideoIds();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("title");
    }

    @Test
    void getVideoIds_returnsEmptyListWhenDbEmpty() {
        when(repository.findAllBySource("youtube")).thenReturn(Collections.emptyList());
        List<VideoDto> result = service.getVideoIds();
        assertThat(result).isEmpty();
    }

    @Test
    void updateVideoDetails_upsertsVideos() {
        YouTubeVideoDTO dto = new YouTubeVideoDTO();
        dto.setVideoId("vid1");
        dto.setTitle("t");
        dto.setDescription("d");
        dto.setUrlLink("u");
        dto.setEmbedLink("e");
        dto.setUpCount(1L);
        dto.setDownCount(2L);

        when(repository.findByVideoIdIn(List.of("vid1"))).thenReturn(Collections.emptyList());

        service.updateVideoDetails(List.of(dto));

        ArgumentCaptor<List<YouTubeVideo>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        YouTubeVideo saved = captor.getValue().get(0);
        assertThat(saved.getVideoId()).isEqualTo("vid1");
        assertThat(saved.getTitle()).isEqualTo("t");
        assertThat(saved.getDesc()).isEqualTo("d");
        assertThat(saved.getUrlLink()).isEqualTo("u");
        assertThat(saved.getEmbedLink()).isEqualTo("e");
        assertThat(saved.getUpCount()).isEqualTo(1);
        assertThat(saved.getDownCount()).isEqualTo(2);
        assertThat(saved.getSource()).isEqualTo("youtube");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void processTop10YouTube_fetchesAndUpdates() {
        List<String> ids = List.of("id1", "id2");
        when(chatGptService.getTopYoutubeVideoIds()).thenReturn(ids);

        YouTubeVideoDTO dto1 = new YouTubeVideoDTO();
        dto1.setVideoId("id1");
        YouTubeVideoDTO dto2 = new YouTubeVideoDTO();
        dto2.setVideoId("id2");
        when(youTubeApiClient.fetchVideoDetails(ids)).thenReturn(List.of(dto1, dto2));

        // Fix: saveAll returns a list, so mock it accordingly
        when(repository.saveAll(anyList())).thenReturn(Collections.emptyList());

        service.processTop10YouTube();

        verify(chatGptService).getTopYoutubeVideoIds();
        verify(youTubeApiClient).fetchVideoDetails(ids);
        verify(repository).saveAll(anyList());
    }
}