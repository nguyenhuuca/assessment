/**
 * Video Object Class
 * Represents a video with its properties and methods
 */
class Video {
    constructor(id, userShared, title, src, desc, isPrivate = false) {
        this.id = id;
        this.userShared = userShared;
        this.title = title;
        this.src = src;
        this.desc = desc;
        this.upvotes = 0;
        this.downvotes = 0;
        this.isPrivate = isPrivate;
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
        <div class="video-swipe-container" id="{{containerId}}">
            <div class="video-swipe-wrapper">
                <div class="video-swipe-item">
                    <video src="{{videoSrc}}" 
                           controls 
                           autoplay 
                           loop
                           muted
                           playsinline
                           preload="auto">
                        Sorry, your browser doesn't support embedded videos.
                    </video>
                </div>
            </div>
            <div class="video-swipe-controls">
                <button class="swipe-button" onclick="VideoActions.swipeLeft('{{containerId}}')">
                    <i class="fas fa-chevron-left"></i>
                </button>
                <button class="swipe-button" onclick="VideoActions.swipeRight('{{containerId}}')">
                    <i class="fas fa-chevron-right"></i>
                </button>
            </div>
        </div>`;
    },

    bindData(videoObj, containerId) {
        let template = this.load();
        
        const currentUser = JSON.parse(localStorage.getItem('user') || '{}');
        const deleteButton = videoObj.isPrivate && currentUser.email === videoObj.userShared
            ? `<div class="delete-button" id="${videoObj.id}_delete" onclick="VideoService.deleteVideo(this)">
                 <i class="fas fa-trash"></i>
                 <span>Delete</span>
               </div>`
            : '';
        
        return template
            .replace("{{videoSrc}}", "videoObj.src")
            .replace("{{movi_title}}", videoObj.title)
            .replace("{{userName}}", videoObj.userShared)
            .replace("{{desc}}", videoObj.desc)
            .replace("{{id_upCount}}", videoObj.id + "_upCount")
            .replace("{{id_downCount}}", videoObj.id + "_downCount")
            .replace("{{id_upVote}}", videoObj.id + "_upVote")
            .replace("{{id_downVote}}", videoObj.id + "_downVote")
            .replace("{{deleteButton}}", deleteButton)
            .replace(/{{containerId}}/g, containerId);
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
    },

    currentVideoIndex: {},
    videos: {},

    swipeLeft(containerId) {
        if (this.currentVideoIndex[containerId] > 0) {
            this.currentVideoIndex[containerId]--;
            this.updateVideo(containerId);
        }
    },

    swipeRight(containerId) {
        if (this.currentVideoIndex[containerId] < this.videos[containerId].length - 1) {
            this.currentVideoIndex[containerId]++;
            this.updateVideo(containerId);
        }
    },

    updateVideo(containerId) {
        const video = this.videos[containerId][this.currentVideoIndex[containerId]];
        if (!video) return;

        const videoElement = document.querySelector(`#${containerId} .video-swipe-item video`);
        if (videoElement) {
            let videoUrl = video.src;
            videoElement.src = videoUrl;
            videoElement.load();
            videoElement.play().catch(error => {
                console.log("Autoplay prevented: ", error);
            });
        }
    },

    initSwipe(videos, containerId) {
        this.videos[containerId] = videos;
        this.currentVideoIndex[containerId] = 0;
        this.updateVideo(containerId);
    }
};

/**
 * Video Service Module
 * Handles video data operations and API calls
 */
const VideoService = {
    currentDeleteVideoId: null,

    share() {
        const link = $("#urlYoutube").val();
        const isPrivate = $("#isPrivate").is(':checked');
        const description = $("#videoDescription").val();
        const title = $("#videoTitle").val();
        const shareObj = { 
            url: link,
            isPrivate: isPrivate,
            description: description,
            title: title
        };

        $("#shareSpinner").show();
        $.ajax({
            url: appConst.baseUrl.concat("/share-links"),
            type: "POST",
            data: JSON.stringify(shareObj),
            contentType: "application/json",
            dataType: "json"
        }).done((rs) => {
            const videoInfo = rs.data;
            const video = new Video(
                videoInfo.id, 
                videoInfo.userShared, 
                videoInfo.title, 
                videoInfo.embedLink, 
                videoInfo.desc,
                videoInfo.isPrivate
            );
            const videoHtml = VideoTemplate.bindData(video, 'popular-videos');
            
            // Add to appropriate list based on privacy
            if (video.isPrivate) {
                $("#list-video-private").prepend(videoHtml);
            } else {
                $("#list-video-popular").prepend(videoHtml);
            }
            
            $("#shareSpinner").hide();
            $('#shareModal').modal('hide');
        }).fail((err) => {
            showMessage(err.responseJSON.error.message, "error");
            $("#shareSpinner").hide();
        });
    },

    loadData() {
        // Load public videos
        $.ajax({
            url: appConst.baseUrl.concat("/top-videos"),
            type: "GET",
            dataType: "json"
        }).done((rs) => {
            const videos = rs.data.map(videoInfo => 
                new Video(
                    videoInfo.id, 
                    videoInfo.userShared, 
                    videoInfo.title, 
                    videoInfo.embedLink, 
                    videoInfo.desc,
                    videoInfo.isPrivate
                )
            );
            this.displayVideos(videos);
        }).fail((err) => {
            showMessage(err.responseJSON.error.message, 'error');
        });
    },

    loadPrivateVideos() {
        if (!localStorage.getItem("jwt")) return;

        $.ajax({
            url: appConst.baseUrl.concat("/private-videos"),
            type: "GET",
            dataType: "json"
        }).done((rs) => {
            const privateVideos = rs.data.map(videoInfo => 
                new Video(
                    videoInfo.id, 
                    videoInfo.userShared, 
                    videoInfo.title, 
                    videoInfo.embedLink, 
                    videoInfo.desc,
                    true
                )
            );
            this.displayPrivateVideos(privateVideos);
        }).fail((err) => {
            showMessage(err.responseJSON.error.message, 'error');
        });
    },

    displayVideos(videos) {
        $("#list-video-popular").empty();
        $("#list-video-funny").empty();

        const sortedByPopularity = [...videos].sort((a, b) => b.upvotes - a.upvotes);
        const funnyVideos = sortedByPopularity.filter(video => video.category === 'funny');
        const regularVideos = sortedByPopularity.filter(video => video.category === 'regular');

        // Initialize swipe with all videos
        VideoActions.initSwipe([...regularVideos, ...funnyVideos], 'popular-videos');

        // Display first video
        const firstVideo = regularVideos[0] || funnyVideos[0];
        if (firstVideo) {
            const videoHtml = VideoTemplate.bindData(firstVideo, 'popular-videos');
            $("#list-video-popular").append(videoHtml);
        }
    },

    displayPrivateVideos(videos) {
        $("#list-video-private").empty();
        
        const sortedByPopularity = [...videos].sort((a, b) => b.upvotes - a.upvotes);
        
        // Initialize swipe with private videos
        VideoActions.initSwipe(sortedByPopularity, 'private-videos');

        // Display first video
        const firstVideo = sortedByPopularity[0];
        if (firstVideo) {
            const videoHtml = VideoTemplate.bindData(firstVideo, 'private-videos');
            $("#list-video-private").append(videoHtml);
        }
    },

    deleteVideo(element) {
        const id = $(element).attr("id");
        this.currentDeleteVideoId = id.split("_")[0];
        
        // Show confirmation modal
        const deleteModal = new bootstrap.Modal(document.getElementById('deleteConfirmModal'));
        deleteModal.show();
    },

    confirmDelete() {
        if (!this.currentDeleteVideoId) return;

        $.ajax({
            url: appConst.baseUrl.concat("/share-links/").concat(this.currentDeleteVideoId),
            type: "DELETE",
            contentType: "application/json",
            dataType: "json"
        }).done(() => {
            // Remove the video element on successful deletion
            $(`#${this.currentDeleteVideoId}_delete`).closest('.video-item').remove();
            showMessage("Video deleted successfully", "success");
            
            // Hide the modal
            const deleteModal = bootstrap.Modal.getInstance(document.getElementById('deleteConfirmModal'));
            deleteModal.hide();
        }).fail((err) => {
            showMessage(err.responseJSON.error.message, "error");
        }).always(() => {
            this.currentDeleteVideoId = null;
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
    
    // Show/hide private tab based on login status
    if (localStorage.getItem("jwt")) {
        $("#private-tab").show();
    } else {
        $("#private-tab").hide();
    }
}

// Initialize when document is ready
$(document).ready(function() {
    // Handle share modal
    $('#shareModal').on('hidden.bs.modal', function () {
        $("#shareSpinner").hide();
        $("#mainMsg").hide();
        $("#urlYoutube").val('');
        $("#videoTitle").val('');
        $("#videoDescription").val('');
        $("#isPrivate").prop('checked', false);
    });

    // Handle private video tab click
    $('#private-tab-btn').on('click', function() {
        VideoService.loadPrivateVideos();
    });

    // Handle delete confirmation
    $('#confirmDeleteBtn').on('click', function() {
        VideoService.confirmDelete();
    });

    // Clear currentDeleteVideoId when modal is closed
    $('#deleteConfirmModal').on('hidden.bs.modal', function () {
        VideoService.currentDeleteVideoId = null;
    });
});

