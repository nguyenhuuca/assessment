/**
 * Using to holed video object
 * @param {*} id : id video after share
 * @param userShared
 * @param title
 * @param src
 * @param desc
 */
function VideoObj(id, userShared, title, src, desc) {
    this.id = id;
    this.userShared = userShared;
    this.title = title;
    this.src = src;
    this.desc = desc;
    this.upvotes = 0;
    this.downvotes = 0;
    this.category = this.determineCategory();
}

VideoObj.prototype.determineCategory = function() {
    // Simple logic to determine if video is funny based on title and description
    const funnyKeywords = ['funny', 'hài', 'hài hước', 'comedy', 'vui', 'cười'];
    const titleAndDesc = (this.title + ' ' + this.desc).toLowerCase();
    return funnyKeywords.some(keyword => titleAndDesc.includes(keyword)) ? 'funny' : 'regular';
};

/**
 * Using to share youtubue url
 */
function share() {
    let link = $("#urlYoutube").val();
    let shareObj = {
        url: link
    }

    if (appConst.offlineMode) {
        // Mock response for offline mode
        const mockVideoInfo = {
            id: Date.now().toString(),
            userShared: JSON.parse(localStorage.getItem("user"))?.email || "anonymous@example.com",
            title: "Shared Video " + Date.now(),
            embedLink: link,
            desc: "Shared in offline mode"
        };
        const video = new VideoObj(mockVideoInfo.id, mockVideoInfo.userShared, mockVideoInfo.title, mockVideoInfo.embedLink, mockVideoInfo.desc);
        let stringHtml = bindingDataWhenLoad(video, loadTemplate());
        $('#shareModal').modal('hide');
    } else {
        $("#shareSpinner").show();
        $.ajax({
            url: appConst.baseUrl.concat("/share-links"),
            type: "POST",
            data: JSON.stringify(shareObj),
            contentType: "application/json",
            dataType: "json"
        }).done(function(rs) {
            let videoInfo = rs.data;
            console.log(videoInfo);
            const video = new VideoObj(videoInfo.id, videoInfo.userShared, videoInfo.title, videoInfo.embedLink, videoInfo.desc);
            let stringHtml = bindingDataWhenLoad(video, loadTemplate());
            $("#list-video-private").prepend(stringHtml);
            $("#shareSpinner").hide();
            $('#shareModal').modal('hide');
        }).fail(function(err) {
            showMessage(err.responseJSON.error.message, "error")
            $("#shareSpinner").hide();
        });
    }
}

// Thêm xử lý khi modal đóng
$(document).ready(function() {
    $('#shareModal').on('hidden.bs.modal', function () {
        $("#shareSpinner").hide();
        $("#mainMsg").hide();
        $("#urlYoutube").val('');
    });
});

// Add vote state tracking
let voteStates = {};

/**
 * Using to increase vote when click icon voteUp
 * @param {*} element hold the value of voteUp 
 */
function voteUp(element) {
    let id = $(element).attr("id");
    let videoId = id.split("_")[0];
    let upCountId = "#" + videoId + "_upCount";
    let downCountId = "#" + videoId + "_downCount";
    let currentState = voteStates[videoId] || 'none';
    
    // Reset previous state
    if (currentState === 'down') {
        let downVal = parseInt($(downCountId).text());
        $(downCountId).text(Math.max(0, downVal - 1));
    }
    
    // Update new state
    if (currentState === 'up') {
        let upVal = parseInt($(upCountId).text());
        $(upCountId).text(Math.max(0, upVal - 1));
        voteStates[videoId] = 'none';
        $(element).find('i').removeClass('fas').addClass('far');
    } else {
        let upVal = parseInt($(upCountId).text());
        $(upCountId).text(upVal + 1);
        voteStates[videoId] = 'up';
        $(element).find('i').removeClass('far').addClass('fas');
        // Reset down vote if exists
        $(`#${videoId}_downVote`).find('i').removeClass('fas').addClass('far');
    }
}

/**
 * Using to decrease vote when click icon voteDown
 * @param {*} element hole the value of voteDown
 */
function voteDown(element) {
    let id = $(element).attr("id");
    let videoId = id.split("_")[0];
    let upCountId = "#" + videoId + "_upCount";
    let downCountId = "#" + videoId + "_downCount";
    let currentState = voteStates[videoId] || 'none';
    
    // Reset previous state
    if (currentState === 'up') {
        let upVal = parseInt($(upCountId).text());
        $(upCountId).text(Math.max(0, upVal - 1));
    }
    
    // Update new state
    if (currentState === 'down') {
        let downVal = parseInt($(downCountId).text());
        $(downCountId).text(Math.max(0, downVal - 1));
        voteStates[videoId] = 'none';
        $(element).find('i').removeClass('fas').addClass('far');
    } else {
        let downVal = parseInt($(downCountId).text());
        $(downCountId).text(downVal + 1);
        voteStates[videoId] = 'down';
        $(element).find('i').removeClass('far').addClass('fas');
        // Reset up vote if exists
        $(`#${videoId}_upVote`).find('i').removeClass('fas').addClass('far');
    }
}

/**
 * When load page, need to init state for some element on page
 */
function initState() {
    initAuthState();
    initTheme();
    $("#shareSpinner").hide();
    
    // Hide private tab initially
    $("#private-tab").hide();
    
    // Add click handler for private tab
    $("#private-tab").on('click', function() {
        loadPrivateVideos();
    });
}

/**
 * Load private videos from server
 */
function loadPrivateVideos() {
    if (!localStorage.getItem("jwt")) {
        return;
    }

    if (appConst.offlineMode) {
        // Mock data for offline mode
        const mockVideos = [
            new VideoObj("4", "user4@example.com", "Private Drive Video", "https://drive.google.com/file/d/abcdd3345ggf/preview", "Private video from Google Drive")
        ];
        displayPrivateVideos(mockVideos);
    } else {
        $.ajax({
            url: appConst.baseUrl.concat("/private-videos"),
            type: "GET",
            dataType: "json"
        }).done(function(rs) {
            const videos = rs.data.map(videoInfo => 
                new VideoObj(videoInfo.id, videoInfo.userShared, videoInfo.title, videoInfo.embedLink, videoInfo.desc)
            );
            displayPrivateVideos(videos);
        }).fail(function(err) {
            showMessage(err.responseJSON.error.message, 'error')
        });
    }
}

/**
 * Display private videos in the private tab
 */
function displayPrivateVideos(videos) {
    $("#list-video-private").empty();
    videos.forEach(video => {
        const videoHtml = bindingDataWhenLoad(video, loadTemplate());
        $("#list-video-private").append(videoHtml);
    });
}

/**
 * Process successful login
 * @param {Object} data - Login response data containing jwt and user info
 */
function processLoginSuccess(data) {
    $("#shareBtn").show();
    $("#logoutBtn").show();
    $("#profileBtn").show();
    $("#messageInfo").text(` Welcome ${data.user.email}`);
    $("#messageInfo").show();
    $("#loginBtn").hide();
    $("#loginSpinner").hide();
    $("#grUser").hide();
    $("#grPass").hide();
    $("#mainMsg").hide();
    
    // Show private tab when logged in
    $("#private-tab").show();
    
    // Save jwt and user info
    localStorage.setItem('jwt', data.jwt);
    localStorage.setItem('user', JSON.stringify(data.user));
    
    // Update AJAX headers
    $.ajaxSetup({
        headers:{
            'Authorization': localStorage.getItem("jwt")
        }
    });
}

/**
 * Logout user by removing JWT token and user info
 */
function logout() {
    console.log("logout system");
    localStorage.removeItem("jwt");
    localStorage.removeItem("user");
    initState();
    $("#loginBtn").show();
    $("#profileBtn").hide();
    // Hide private tab when logged out
    $("#private-tab").hide();
}

// Global variable to store YouTube players
let youtubePlayers = {};

// This function is called automatically when YouTube API is ready
function onYouTubeIframeAPIReady() {
    // Initialize any existing YouTube videos
    $('.youtube-player').each(function() {
        const videoId = $(this).data('video-id');
        if (videoId && !youtubePlayers[videoId]) {
            createYouTubePlayer(videoId, this);
        }
    });
}

/**
 * Create a new YouTube player
 * @param {string} videoId - YouTube video ID
 * @param {HTMLElement} element - Container element for the player
 */
function createYouTubePlayer(videoId, element) {
    youtubePlayers[videoId] = new YT.Player(element, {
        height: '100%',
        width: '100%',
        videoId: videoId,
        playerVars: {
            'playsinline': 1,
            'rel': 0,
            'modestbranding': 1
        },
        events: {
            'onReady': onPlayerReady,
            'onStateChange': onPlayerStateChange
        }
    });
}

/**
 * Called when YouTube player is ready
 * @param {Object} event - YouTube player event
 */
function onPlayerReady(event) {
    // You can add custom controls or setup here
}

/**
 * Called when YouTube player state changes
 * @param {Object} event - YouTube player event
 */
function onPlayerStateChange(event) {
    // You can handle video state changes here
    // event.data can be:
    // -1 (unstarted)
    // 0 (ended)
    // 1 (playing)
    // 2 (paused)
    // 3 (buffering)
    // 5 (video cued)
}

/**
 * Extract YouTube video ID from URL
 * @param {string} url - YouTube URL
 * @returns {string|null} YouTube video ID or null if not found
 */
function getYouTubeVideoId(url) {
    const regExp = /^.*(youtu.be\/|v\/|u\/\w\/|embed\/|watch\?v=|&v=)([^#&?]*).*/;
    const match = url.match(regExp);
    return (match && match[2].length === 11) ? match[2] : null;
}

/**
 * Using to return fix template for list video, each video will have item with fix fomat
 * @returns html with paramter by format: {{param}}
 */
function loadTemplate() {
    return `
    <div class="row video-item">
        <!-- 16:9 aspect ratio -->
        <div class="col-6">
        <div class="ratio ratio-16x9">
            {{#if isYouTube}}
            <div class="youtube-player" data-video-id="{{videoId}}"></div>
            {{else}}
            <iframe src="{{linkYotube}}" allowfullscreen></iframe>
            {{/if}}
        </div>
        </div>
        <div class="col-6 desc-video ">
        <div class="video-title">{{movi_title}}</div>
        <div class="video-meta">
            <div class="video-shared-by">
               <span class="meta-label">Shared by:</span> <span class="meta-value">{{userName}}</span>
            </div>
        </div>
        <div class="video-actions">
            <div class="vote-container">
                <div class="vote-button" id="{{id_upVote}}" onclick="voteUp(this)">
                    <i class="far fa-thumbs-up"></i>
                    <span class="vote-count" id="{{id_upCount}}">0</span>
                </div>
                <div class="vote-button" id="{{id_downVote}}" onclick="voteDown(this)">
                    <i class="far fa-thumbs-down"></i>
                    <span class="vote-count" id="{{id_downCount}}">0</span>
                </div>
            </div>
        </div>
        <div class="video-description-container">
            <div class="description-header">
                <div class="description-label">Description:</div>
                <button class="description-toggle" onclick="toggleDescription(this)">
                    <i class="fas fa-chevron-down"></i>
                </button>
            </div>
            <div class="video-description">
                <div class="description-content">{{desc}}</div>
            </div>
        </div>
        </div>
    </div>
    </br>
    `;
}

/**
 * Using to replace some data by real data that get froms server
 * @param {*} videoObj hold all data need to binding to html
 * @param {*} templateHtml  the static html from loadTemplate
 * @returns  string html after replace all data
 */
function bindingDataWhenLoad(videoObj, templateHtml) {
    let stringHtml = templateHtml;
    
    // Handle video source
    if (videoObj.src.includes('youtube.com') || videoObj.src.includes('youtu.be')) {
        const videoId = getYouTubeVideoId(videoObj.src);
        if (videoId) {
            stringHtml = stringHtml.replace("{{#if isYouTube}}", "");
            stringHtml = stringHtml.replace("{{else}}", "");
            stringHtml = stringHtml.replace("{{/if}}", "");
            stringHtml = stringHtml.replace("{{videoId}}", videoId);
        } else {
            stringHtml = stringHtml.replace("{{#if isYouTube}}", "");
            stringHtml = stringHtml.replace("{{else}}", "");
            stringHtml = stringHtml.replace("{{/if}}", "");
        }
    } else if (videoObj.src.includes('drive.google.com')) {
        // Handle Google Drive URL
        const fileId = videoObj.src.match(/\/d\/(.*?)\//)?.[1];
        if (fileId) {
            videoObj.src = `https://drive.google.com/file/d/${fileId}/preview`;
        }
        stringHtml = stringHtml.replace("{{#if isYouTube}}", "");
        stringHtml = stringHtml.replace("{{else}}", "");
        stringHtml = stringHtml.replace("{{/if}}", "");
    } else {
        stringHtml = stringHtml.replace("{{#if isYouTube}}", "");
        stringHtml = stringHtml.replace("{{else}}", "");
        stringHtml = stringHtml.replace("{{/if}}", "");
    }
    
    stringHtml = stringHtml.replace("{{linkYotube}}", videoObj.src);
    stringHtml = stringHtml.replace("{{movi_title}}", videoObj.title);
    stringHtml = stringHtml.replace("{{userName}}", videoObj.userShared);
    stringHtml = stringHtml.replace("{{desc}}", videoObj.desc);
    stringHtml = stringHtml.replace("{{id_upCount}}", videoObj.id +"_upCount");
    stringHtml = stringHtml.replace("{{id_downCount}}", videoObj.id+"_downCount");
    stringHtml = stringHtml.replace("{{id_upVote}}", videoObj.id+"_upVote");
    stringHtml = stringHtml.replace("{{id_downVote}}", videoObj.id+"_downVote");
    stringHtml = stringHtml.replace("{{id_delete}}", videoObj.id+"_delete");
    return stringHtml;
}

/**
 * Using to get all link share when page is loaded
 */
function loadData() {
    if (appConst.offlineMode) {
        // Mock data for offline mode
        const mockVideos = [
            new VideoObj("1", "user1@example.com", "Funny Cat Video", "https://www.youtube.com/embed/example1", "A hilarious cat video"),
            new VideoObj("2", "user2@example.com", "Cooking Tutorial", "https://www.youtube.com/embed/example2", "Learn to cook"),
            new VideoObj("3", "user3@example.com", "Hài Hước - Stand Up Comedy", "https://www.youtube.com/embed/example3", "Funny stand up comedy show")
        ];
        displayVideos(mockVideos);
    } else {
        $.ajax({
            url: appConst.baseUrl.concat("/top-videos"),
            type: "GET",
            dataType: "json"
        }).done(function(rs) {
            const videos = rs.data.map(videoInfo => 
                new VideoObj(videoInfo.id, videoInfo.userShared, videoInfo.title, videoInfo.embedLink, videoInfo.desc)
            );
            displayVideos(videos);
        }).fail(function(err) {
            showMessage(err.responseJSON.error.message, 'error')
        });
    }
}

function displayVideos(videos) {
    // Clear all video lists
    $("#list-video-popular").empty();
    $("#list-video-funny").empty();

    // Sort videos by upvotes for popular tab
    const sortedByPopularity = [...videos].sort((a, b) => b.upvotes - a.upvotes);

    // Display popular videos (top 5)
    sortedByPopularity.forEach(video => {
        const videoHtml = bindingDataWhenLoad(video, loadTemplate());
        $("#list-video-popular").append(videoHtml);
    });
}

/**
 * Initialize theme from localStorage or system preference
 */
function initTheme() {
    const savedTheme = localStorage.getItem('theme');
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const currentTheme = savedTheme || (prefersDark ? 'dark' : 'light');
    
    // Set initial theme
    document.documentElement.setAttribute('data-theme', currentTheme);
    
    // Update icon based on current theme
    const icon = document.querySelector('.theme-toggle i');
    if (currentTheme === 'dark') {
        icon.classList.remove('fa-moon');
        icon.classList.add('fa-sun');
    } else {
        icon.classList.remove('fa-sun');
        icon.classList.add('fa-moon');
    }
}

/**
 * Toggle between light and dark theme
 */
function toggleTheme() {
    const currentTheme = document.documentElement.getAttribute('data-theme');
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
    
    // Update theme
    document.documentElement.setAttribute('data-theme', newTheme);
    localStorage.setItem('theme', newTheme);
    
    // Update icon
    const icon = document.querySelector('.theme-toggle i');
    if (newTheme === 'dark') {
        icon.classList.remove('fa-moon');
        icon.classList.add('fa-sun');
    } else {
        icon.classList.remove('fa-sun');
        icon.classList.add('fa-moon');
    }
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

// Handle description toggle
$(document).on('click', '.description-toggle', function() {
    const descriptionSection = $(this).closest('.video-description-container').find('.video-description');
    const toggleIcon = $(this).find('i');
    
    descriptionSection.toggleClass('show');
    $(this).toggleClass('active');
    
    if (descriptionSection.hasClass('show')) {
        toggleIcon.removeClass('fa-chevron-down').addClass('fa-chevron-up');
    } else {
        toggleIcon.removeClass('fa-chevron-up').addClass('fa-chevron-down');
    }
});

// Add event listener for when new videos are added to the DOM
$(document).on('DOMNodeInserted', function(e) {
    if ($(e.target).hasClass('youtube-player')) {
        const videoId = $(e.target).data('video-id');
        if (videoId && !youtubePlayers[videoId]) {
            createYouTubePlayer(videoId, e.target);
        }
    }
});

