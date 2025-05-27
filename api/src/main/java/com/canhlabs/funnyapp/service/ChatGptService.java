package com.canhlabs.funnyapp.service;

import java.util.List;

public interface ChatGptService {
    /**
     * Sends a prompt to ChatGPT asking for the top 10 most viewed YouTube video IDs.
     *
     * @return A list of YouTube video IDs (e.g., ["9bZkp7q19f0", "dQw4w9WgXcQ", ...])
     */
    List<String> getTopYoutubeVideoIds();
}
