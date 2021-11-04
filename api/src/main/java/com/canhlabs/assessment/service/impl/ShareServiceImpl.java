package com.canhlabs.assessment.service.impl;

import com.canhlabs.assessment.domain.ShareLink;
import com.canhlabs.assessment.repo.ShareLinkRepo;
import com.canhlabs.assessment.repo.UserRepo;
import com.canhlabs.assessment.service.ShareService;
import com.canhlabs.assessment.share.AppProperties;
import com.canhlabs.assessment.share.AppUtils;
import com.canhlabs.assessment.share.dto.ShareRequestDto;
import com.canhlabs.assessment.share.dto.UserDetailDto;
import com.canhlabs.assessment.share.dto.VideoDto;
import com.canhlabs.assessment.share.exception.CustomException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ShareServiceImpl implements ShareService {
    private static final String API_GOOGLE = "https://www.googleapis.com/youtube/v3/videos?";

    private AppProperties props;
    private ShareLinkRepo shareLinkRepo;
    private UserRepo userRepo;

    @Autowired
    public void injectShareLink(ShareLinkRepo shareLinkRepo){
        this.shareLinkRepo = shareLinkRepo;

    }

    @Autowired
    public  void injectUser(UserRepo userRepo) {
        this.userRepo = userRepo;
    }


    @Autowired
    public void injectProp(AppProperties props){
        this.props = props;
    }
    @Override
    public VideoDto shareLink(ShareRequestDto shareRequestDto) {
        VideoDto videoDto =  getInfoFromYoutube(shareRequestDto.getUrl());
        if(videoDto == null) {
            throw  CustomException.builder()
                    .message("Cannot get video info")
                    .build();
        }
        ShareLink shareLink = toEntity(videoDto);
        shareLink = shareLinkRepo.save(shareLink);
        videoDto.setId(shareLink.getId());
        videoDto.setUserShared(shareLink.getUser().getUserName());
        return videoDto;
    }

    @Override
    public List<VideoDto> getALLShare() {
        List<ShareLink> shareLinks = shareLinkRepo.findByOrderByCreatedAtDesc();
        return Converter.videoDtoList(shareLinks);
    }

    /**
     * Using to parse url post from user and get video info from youtube
     * @param link post by user
     * @return video info
     */
    private VideoDto getInfoFromYoutube(String link) {
        VideoDto videoDto = null;
        try {
            URL url = new URL(link);
            Map<String, String> map = getQueryParam(url.getQuery());
            String videoId = map.get("v");
            videoDto = requestYouTube(link,videoId);
            log.info(videoId);
        } catch (Exception ex) {
            throw  CustomException.builder()
                    .message("Cannot get video info")
                    .build();
        }
        return videoDto;
    }

    /**
     * Using RestTemplate to mask rest api to googleapi with api key
     * @param link url post by user
     * @param videoId detect from link
     * @return video info
     */
    private VideoDto requestYouTube(String link, String videoId) throws JsonProcessingException {
        RestTemplate rest = new RestTemplate();
        String url = API_GOOGLE +
                "id=" + videoId + "&" +
                "key=" + props.getGoogleApiKey() + "&" +
                "part=" + props.getGooglePart();

        // make an HTTP GET request
        String json = rest.getForObject(url, String.class);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(json);
        JsonNode snippet = jsonNode.get("items").get(0).get("snippet");
        String title = snippet.get("title").asText();
        String desc = snippet.get("description").asText();
        log.info(json);

        return VideoDto.builder()
                .title(title)
                .urlLink(link)
                .desc(desc)
                .embedLink("https://youtube.com/embed/".concat(videoId))
                .build();
    }

    /**
     * Map to hold key and value of query param
     * @param query theo query string follow fromat: a1=v1&=a2=v2....
     * @return hashmap
     */
    private Map<String, String> getQueryParam(String query) {
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<>();
        for (String param : params) {
            try {
                String name = param.split("=")[0];
                String value = param.split("=")[1];
                map.put(name, value);
            } catch (Exception ex) {
                log.error(ex.getMessage());
            }

        }
        return map;
    }

    private ShareLink toEntity(VideoDto videoDto) {
        UserDetailDto currentUser = AppUtils.getCurrentUser();
        if(currentUser == null) {
            throw  CustomException.builder().message("Error get current user").build();
        }
        return ShareLink.builder()
                .title(videoDto.getTitle())
                .desc(videoDto.getDesc())
                .embedLink(videoDto.getEmbedLink())
                .urlLink(videoDto.getUrlLink())
                .user(userRepo.findAllById(currentUser.getId()))
                .build();
    }
}
