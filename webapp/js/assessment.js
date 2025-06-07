/**
 * Video Object Class
 * Represents a video with its properties and methods
 */
class Video {
    constructor(id, userShared, title, src, desc) {
        this.id = id;
        this.userShared = userShared;
        this.title = title;
        this.src = src;
        this.desc = desc;
        this.upvotes = 0;
        this.downvotes = 0;
        this.category = this.determineCategory();
    }

    determineCategory() {
        const funnyKeywords = ['funny', 'hài', 'hài hước', 'comedy', 'vui', 'cười'];
        const titleAndDesc = (this.title + ' ' + this.desc).toLowerCase();
        return funnyKeywords.some(keyword => titleAndDesc.includes(keyword)) ? 'funny' : 'regular';
    }
}

/**
 * Video Template Module
 * Handles video template and data binding
 */
const VideoTemplate = {
    load() {
        return `
        <div class="row video-item">
            <div class="col-6">
                <div class="ratio ratio-16x9">
                    <iframe src="{{linkYotube}}" allowfullscreen></iframe>
                </div>
            </div>
            <div class="col-6 desc-video">
                <div class="video-title">{{movi_title}}</div>
                <div class="video-meta">
                    <div class="video-shared-by">
                        <span class="meta-label">Shared by:</span> 
                        <span class="meta-value">{{userName}}</span>
                    </div>
                </div>
                <div class="video-actions">
                    <div class="vote-container">
                        <div class="vote-button" id="{{id_upVote}}" onclick="VideoActions.voteUp(this)">
                            <i class="far fa-thumbs-up"></i>
                            <span class="vote-count" id="{{id_upCount}}">0</span>
                        </div>
                        <div class="vote-button" id="{{id_downVote}}" onclick="VideoActions.voteDown(this)">
                            <i class="far fa-thumbs-down"></i>
                            <span class="vote-count" id="{{id_downCount}}">0</span>
                        </div>
                    </div>
                </div>
                <div class="video-description-container">
                    <div class="description-header">
                        <div class="description-label">Description:</div>
                        <button class="description-toggle" onclick="VideoActions.toggleDescription(this)">
                            <i class="fas fa-chevron-down"></i>
                        </button>
                    </div>
                    <div class="video-description">
                        <div class="description-content">{{desc}}</div>
                    </div>
                </div>
            </div>
        </div>
        </br>`;
    },

    bindData(videoObj) {
        let template = this.load();
        
        // Handle Google Drive URL
        if (videoObj.src.includes('drive.google.com')) {
            const fileId = videoObj.src.match(/\/d\/(.*?)\//)?.[1];
            if (fileId) {
                videoObj.src = `https://drive.google.com/file/d/${fileId}/preview`;
            }
        }
        
        return template
            .replace("{{linkYotube}}", videoObj.src)
            .replace("{{movi_title}}", videoObj.title)
            .replace("{{userName}}", videoObj.userShared)
            .replace("{{desc}}", videoObj.desc)
            .replace("{{id_upCount}}", videoObj.id + "_upCount")
            .replace("{{id_downCount}}", videoObj.id + "_downCount")
            .replace("{{id_upVote}}", videoObj.id + "_upVote")
            .replace("{{id_downVote}}", videoObj.id + "_downVote");
    }
};

/**
 * Video Actions Module
 * Handles user interactions with videos
 */
const VideoActions = {
    voteStates: {},

    voteUp(element) {
        const id = $(element).attr("id");
        const videoId = id.split("_")[0];
        const upCountId = "#" + videoId + "_upCount";
        const downCountId = "#" + videoId + "_downCount";
        const currentState = this.voteStates[videoId] || 'none';
        
        if (currentState === 'down') {
            const downVal = parseInt($(downCountId).text());
            $(downCountId).text(Math.max(0, downVal - 1));
        }
        
        if (currentState === 'up') {
            const upVal = parseInt($(upCountId).text());
            $(upCountId).text(Math.max(0, upVal - 1));
            this.voteStates[videoId] = 'none';
            $(element).find('i').removeClass('fas').addClass('far');
        } else {
            const upVal = parseInt($(upCountId).text());
            $(upCountId).text(upVal + 1);
            this.voteStates[videoId] = 'up';
            $(element).find('i').removeClass('far').addClass('fas');
            $(`#${videoId}_downVote`).find('i').removeClass('fas').addClass('far');
        }
    },

    voteDown(element) {
        const id = $(element).attr("id");
        const videoId = id.split("_")[0];
        const upCountId = "#" + videoId + "_upCount";
        const downCountId = "#" + videoId + "_downCount";
        const currentState = this.voteStates[videoId] || 'none';
        
        if (currentState === 'up') {
            const upVal = parseInt($(upCountId).text());
            $(upCountId).text(Math.max(0, upVal - 1));
        }
        
        if (currentState === 'down') {
            const downVal = parseInt($(downCountId).text());
            $(downCountId).text(Math.max(0, downVal - 1));
            this.voteStates[videoId] = 'none';
            $(element).find('i').removeClass('fas').addClass('far');
        } else {
            const downVal = parseInt($(downCountId).text());
            $(downCountId).text(downVal + 1);
            this.voteStates[videoId] = 'down';
            $(element).find('i').removeClass('far').addClass('fas');
            $(`#${videoId}_upVote`).find('i').removeClass('fas').addClass('far');
        }
    },

    toggleDescription(element) {
        const descriptionSection = $(element).closest('.video-description-container').find('.video-description');
        const toggleIcon = $(element).find('i');
        
        descriptionSection.toggleClass('show');
        $(element).toggleClass('active');
        
        if (descriptionSection.hasClass('show')) {
            toggleIcon.removeClass('fa-chevron-down').addClass('fa-chevron-up');
        } else {
            toggleIcon.removeClass('fa-chevron-up').addClass('fa-chevron-down');
        }
    }
};

/**
 * Video Service Module
 * Handles video data operations and API calls
 */
const VideoService = {
    share() {
        const link = $("#urlYoutube").val();
        const shareObj = { url: link };

        if (appConst.offlineMode) {
            this.handleOfflineShare(shareObj);
        } else {
            this.handleOnlineShare(shareObj);
        }
    },

    handleOfflineShare(shareObj) {
        const mockVideoInfo = {
            id: Date.now().toString(),
            userShared: JSON.parse(localStorage.getItem("user"))?.email || "anonymous@example.com",
            title: "Shared Video " + Date.now(),
            embedLink: shareObj.url,
            desc: "Shared in offline mode"
        };
        const video = new Video(mockVideoInfo.id, mockVideoInfo.userShared, mockVideoInfo.title, mockVideoInfo.embedLink, mockVideoInfo.desc);
        const videoHtml = VideoTemplate.bindData(video);
        $("#list-video").prepend(videoHtml);
        $('#shareModal').modal('hide');
    },

    handleOnlineShare(shareObj) {
        $("#shareSpinner").show();
        $.ajax({
            url: appConst.baseUrl.concat("/share-links"),
            type: "POST",
            data: JSON.stringify(shareObj),
            contentType: "application/json",
            dataType: "json"
        }).done((rs) => {
            const videoInfo = rs.data;
            const video = new Video(videoInfo.id, videoInfo.userShared, videoInfo.title, videoInfo.embedLink, videoInfo.desc);
            const videoHtml = VideoTemplate.bindData(video);
            $("#list-video").prepend(videoHtml);
            $("#shareSpinner").hide();
            $('#shareModal').modal('hide');
        }).fail((err) => {
            showMessage(err.responseJSON.error.message, "error");
            $("#shareSpinner").hide();
        });
    },

    loadData() {
        if (appConst.offlineMode) {
            this.loadOfflineData();
        } else {
            this.loadOnlineData();
        }
    },

    loadOfflineData() {
        const mockVideos = [
            new Video("1", "user1@example.com", "Funny Cat Video", "https://www.youtube.com/embed/example1", "A hilarious cat video"),
            new Video("2", "user2@example.com", "Cooking Tutorial", "https://www.youtube.com/embed/example2", "Learn to cook"),
            new Video("3", "user3@example.com", "Hài Hước - Stand Up Comedy", "https://www.youtube.com/embed/example3", "Funny stand up comedy show")
        ];
        this.displayVideos(mockVideos);
    },

    loadOnlineData() {
        $.ajax({
            url: appConst.baseUrl.concat("/top-videos"),
            type: "GET",
            dataType: "json"
        }).done((rs) => {
            const videos = rs.data.map(videoInfo => 
                new Video(videoInfo.id, videoInfo.userShared, videoInfo.title, videoInfo.embedLink, videoInfo.desc)
            );
            this.displayVideos(videos);
        }).fail((err) => {
            showMessage(err.responseJSON.error.message, 'error');
        });
    },

    displayVideos(videos) {
        $("#list-video-popular").empty();
        $("#list-video-funny").empty();

        const sortedByPopularity = [...videos].sort((a, b) => b.upvotes - a.upvotes);

        sortedByPopularity.forEach(video => {
            const videoHtml = VideoTemplate.bindData(video);
            $("#list-video-popular").append(videoHtml);
        });
    }
};

/**
 * Theme Module
 * Handles theme switching and initialization
 */
const ThemeManager = {
    init() {
        const savedTheme = localStorage.getItem('theme');
        const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
        const currentTheme = savedTheme || (prefersDark ? 'dark' : 'light');
        
        this.setTheme(currentTheme);
    },

    setTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem('theme', theme);
        
        const icon = document.querySelector('.theme-toggle i');
        if (theme === 'dark') {
            icon.classList.remove('fa-moon');
            icon.classList.add('fa-sun');
        } else {
            icon.classList.remove('fa-sun');
            icon.classList.add('fa-moon');
        }
    },

    toggle() {
        const currentTheme = document.documentElement.getAttribute('data-theme');
        const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
        this.setTheme(newTheme);
    }
};

/**
 * When load page, need to init state for some element on page
 */
function initState() {
    Auth.initState();
    ThemeManager.init();
    $("#shareSpinner").hide();
}

/**
 * Delete a video
 * @param {*} element The delete button element
 */
function deleteVideo(element) {
    let id = $(element).attr("id");
    let videoId = id.split("_")[0];
    
    if (appConst.offlineMode) {
        // For offline mode, just remove the video element
        $(element).closest('.row').remove();
    } else {
        // For online mode, call the API to delete
        $.ajax({
            url: appConst.baseUrl.concat("/share-links/").concat(videoId),
            type: "DELETE",
            contentType: "application/json",
            dataType: "json"
        }).done(function() {
            // Remove the video element on successful deletion
            $(element).closest('.row').remove();
        }).fail(function(err) {
            showMessage(err.responseJSON.error.message, "error")
        });
    }
}

// Initialize when document is ready
$(document).ready(function() {
    // Handle share modal
    $('#shareModal').on('hidden.bs.modal', function () {
        $("#shareSpinner").hide();
        $("#mainMsg").hide();
        $("#urlYoutube").val('');
    });
});

