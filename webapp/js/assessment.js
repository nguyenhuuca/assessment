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
                <div class="video-swipe-item" id="video-items-{{containerId}}" style="position:relative;">
                    <div class="video-main">
                        {{videoTags}}
                        <div class="video-loading-spinner"><div class="spinner-border text-light" role="status"></div></div>
                        <div class="video-overlay-controls">
                            <button class="overlay-button play-pause-btn" onclick="VideoActions.togglePlayPause('{{containerId}}')"><i class="fas fa-pause"></i></button>
                            <button class="overlay-button mute-unmute-btn" onclick="VideoActions.toggleMute('{{containerId}}')"><i class="fas fa-volume-up"></i></button>
                        </div>
                        <div class="video-progress-container">
                            <div class="video-progress-bar"></div>
                        </div>
                        <div class="video-info-overlay">
                            <h4 class="video-title-overlay">{{videoTitle}}</h4>
                        </div>
                    </div>
                    <div class="video-actions-vertical">
                        <div class="vote-action-group">
                            <button class="vote-button up" id="{{id_upVote}}" onclick="VideoActions.voteUp(this)"><i class="fas fa-thumbs-up"></i></button>
                            <span class="vote-count" id="{{id_upCount}}">0</span>
                        </div>
                        <div class="vote-action-group">
                            <button class="vote-button down" id="{{id_downVote}}" onclick="VideoActions.voteDown(this)"><i class="fas fa-thumbs-down"></i></button>
                            <span class="vote-count" id="{{id_downCount}}">0</span>
                        </div>
                        <div class="action-group">
                            <button class="action-button comment" onclick="VideoActions.showComments('{{containerId}}')"><i class="fas fa-comment"></i></button>
                            <span class="action-count">0</span>
                        </div>
                        <div class="action-group">
                            <button class="action-button share" onclick="VideoActions.shareVideo('{{containerId}}')"><i class="fas fa-share"></i></button>
                            <span class="action-count">Share</span>
                        </div>
                        {{deleteButton}}
                    </div>
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

    // Render 5 consecutive videos around currentIndex (centered if possible)
    bindData(videosArr, containerId, currentIndex = 0) {
        let template = this.load();
        let videoTags = '';
        const total = videosArr.length;
        let start = Math.max(0, Math.min(currentIndex - 2, total - 5));
        let end = Math.min(total, start + 5);
        for (let i = start; i < end; i++) {
            const v = videosArr[i];
            if (!v) continue;
            const isCurrent = i === currentIndex;
            // Poster logic
            let poster = '';
            if (v.poster) {
                poster = v.poster;
            } else if (v.src && v.src.includes('youtube.com')) {
                const match = v.src.match(/[?&]v=([^&#]+)/);
                if (match) {
                    poster = `https://img.youtube.com/vi/${match[1]}/mqdefault.jpg`;
                }
            } else {
                poster = `./icons/poster.jpeg`;
            }
            videoTags += `<video id="${containerId}-video-${i}" src="${v.src}" poster="${poster}" ${isCurrent ? ' autoplay' : ''} playsinline preload="auto" style="width:100%;height:100%;position:absolute;top:0;left:0;${isCurrent ? '' : 'display:none;'}"></video>`;
        }
        // Upvote/downvote count and delete button for current video
        const v = videosArr[currentIndex];
        const currentUser = JSON.parse(localStorage.getItem('user') || '{}');
        const deleteButton = v && v.isPrivate && currentUser.email === v.userShared
            ? `<div class="delete-button" id="${v.id}_delete" onclick="VideoService.deleteVideo(this)"><i class="fas fa-trash"></i><span>Delete</span></div>`
            : '';
        return template
            .replace("{{videoTags}}", videoTags)
            .replace("{{id_upVote}}", v ? v.id + '_upVote' : '')
            .replace("{{id_downVote}}", v ? v.id + '_downVote' : '')
            .replace("{{id_upCount}}", v ? v.id + '_upCount' : '')
            .replace("{{id_downCount}}", v ? v.id + '_downCount' : '')
            .replace("{{deleteButton}}", deleteButton)
            .replace("{{videoTitle}}", v ? v.title : '')
            .replace(/{{containerId}}/g, containerId);
    }
};

/**
 * Video Actions Module
 * Handles user interactions with videos
 */
const VideoActions = {
    voteStates: {},
    mutedState: {},

    togglePlayPause(containerId) {
        const idx = this.currentVideoIndex[containerId];
        const video = document.getElementById(`${containerId}-video-${idx}`);
        if (!video) return;

        if (video.paused) {
            video.play();
        } else {
            video.pause();
        }
    },

    toggleMute(containerId) {
        const idx = this.currentVideoIndex[containerId];
        const video = document.getElementById(`${containerId}-video-${idx}`);
        const videoContainer = document.getElementById(`video-items-${containerId}`);
        if (!video || !videoContainer) return;
        
        const btn = videoContainer.querySelector('.mute-unmute-btn i');
        if (!btn) return;

        video.muted = !video.muted;
        this.mutedState[containerId] = video.muted;

        if (video.muted) {
            btn.classList.remove('fa-volume-up');
            btn.classList.add('fa-volume-mute');
        } else {
            btn.classList.remove('fa-volume-mute');
            btn.classList.add('fa-volume-up');
        }
    },

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
        const videos = this.videos[containerId];
        const idx = this.currentVideoIndex[containerId];
        if (!videos || typeof idx !== 'number') return;
        const container = document.getElementById(`video-items-${containerId}`);
        if (container) {
            const playPauseBtn = container.querySelector('.play-pause-btn i');
            // Pause and reset ALL existing <video> in the container before rendering new ones
            const oldVideos = container.querySelectorAll('video');
            oldVideos.forEach(vid => {
                vid.pause();
                vid.currentTime = 0;
            });
            
            // Lấy trạng thái muted từ biến toàn cục, mặc định true
            let prevMuted = (typeof VideoActions.mutedState[containerId] === 'boolean') ? VideoActions.mutedState[containerId] : true;
            
            // Only update the video-main content, not the entire container
            const videoMain = container.querySelector('.video-main');
            if (videoMain) {
                // Remove only old video elements, not the controls.
                const oldVideosInMain = videoMain.querySelectorAll('video');
                oldVideosInMain.forEach(v => v.remove());

                let videoTags = '';
                const total = videos.length;
                let start = Math.max(0, Math.min(idx - 2, total - 5));
                let end = Math.min(total, start + 5);
                
                for (let i = start; i < end; i++) {
                    const v = videos[i];
                    if (!v) continue;
                    const isCurrent = i === idx;
                    // Poster logic
                    let poster = '';
                    if (v.poster) {
                        poster = v.poster;
                    } else if (v.src && v.src.includes('youtube.com')) {
                        const match = v.src.match(/[?&]v=([^&#]+)/);
                        if (match) {
                            poster = `https://img.youtube.com/vi/${match[1]}/mqdefault.jpg`;
                        }
                    } else {
                        poster = './icons/poster.jpeg';
                    }
                    videoTags += `<video id="${containerId}-video-${i}" src="${v.src}" poster="${poster}" ${isCurrent ? ' autoplay' : ''} playsinline preload="auto" style="width:100%;height:100%;position:absolute;top:0px;left:0;${isCurrent ? '' : 'display:none;'}"></video>`;
                }
                videoMain.insertAdjacentHTML('afterbegin', videoTags);
            }
            
            // Update action buttons for current video
            const v = videos[idx];

            const titleOverlay = container.querySelector('.video-title-overlay');
            if (titleOverlay && v) {
                titleOverlay.textContent = v.title;
            }

            const currentUser = JSON.parse(localStorage.getItem('user') || '{}');
            const deleteButton = v && v.isPrivate && currentUser.email === v.userShared
                ? `<div class="delete-button" id="${v.id}_delete" onclick="VideoService.deleteVideo(this)"><i class="fas fa-trash"></i><span>Delete</span></div>`
                : '';
            
            // Update vote button IDs
            const upVoteBtn = container.querySelector('.vote-button.up');
            const downVoteBtn = container.querySelector('.vote-button.down');
            const upCountSpan = container.querySelector('.vote-count');
            const downCountSpan = container.querySelectorAll('.vote-count')[1];
            
            if (upVoteBtn && v) upVoteBtn.id = v.id + '_upVote';
            if (downVoteBtn && v) downVoteBtn.id = v.id + '_downVote';
            if (upCountSpan && v) upCountSpan.id = v.id + '_upCount';
            if (downCountSpan && v) downCountSpan.id = v.id + '_downCount';
            
            // Update delete button
            const existingDeleteBtn = container.querySelector('.delete-button');
            if (existingDeleteBtn) {
                existingDeleteBtn.remove();
            }
            if (deleteButton) {
                const actionsContainer = container.querySelector('.video-actions-vertical');
                if (actionsContainer) {
                    actionsContainer.insertAdjacentHTML('beforeend', deleteButton);
                }
            }
            
            // Play-pause icon overlay
            let playIcon = container.querySelector('.video-play-icon');
            if (!playIcon) {
                playIcon = document.createElement('div');
                playIcon.className = 'video-play-icon';
                playIcon.innerHTML = '<i class="fas fa-play"></i>';
                container.querySelector('.video-main').appendChild(playIcon);
            }
            
            // Hiệu ứng fade: set active cho video hiện tại
            const allVideos = container.querySelectorAll('video');
            allVideos.forEach(vid => vid.classList.remove('active'));
            const currentVid = document.getElementById(`${containerId}-video-${idx}`);
            if (currentVid) {
                currentVid.classList.add('active');
                currentVid.muted = prevMuted;

                const loadingSpinner = container.querySelector('.video-loading-spinner');
                const showLoader = () => { if (loadingSpinner) loadingSpinner.style.display = 'flex'; };
                const hideLoader = () => { if (loadingSpinner) loadingSpinner.style.display = 'none'; };

                currentVid.addEventListener('loadstart', showLoader);
                currentVid.addEventListener('waiting', showLoader);
                currentVid.addEventListener('stalled', showLoader);
                currentVid.addEventListener('canplay', hideLoader);
                currentVid.addEventListener('playing', hideLoader);
                currentVid.addEventListener('error', hideLoader);

                const progressContainer = container.querySelector('.video-progress-container');
                const progressBar = container.querySelector('.video-progress-bar');
                
                if (progressContainer && progressBar) {
                    currentVid.addEventListener('timeupdate', () => {
                        if (currentVid.duration) {
                            const progressPercent = (currentVid.currentTime / currentVid.duration) * 100;
                            progressBar.style.width = `${progressPercent}%`;
                        }
                    });
            
                    progressContainer.addEventListener('click', (e) => {
                        if (currentVid.duration) {
                            const rect = progressContainer.getBoundingClientRect();
                            const clickX = e.clientX - rect.left;
                            const width = progressContainer.clientWidth;
                            currentVid.currentTime = (clickX / width) * currentVid.duration;
                        }
                    });
                }

                currentVid.addEventListener('ended', function() {
                    VideoActions.swipeRight(containerId);
                });
                // Lắng nghe sự kiện volumechange để cập nhật trạng thái mute
                currentVid.addEventListener('volumechange', function() {
                    VideoActions.mutedState[containerId] = currentVid.muted;
                    const muteBtn = container.querySelector('.mute-unmute-btn i');
                    if (muteBtn) {
                        if (currentVid.muted) {
                            muteBtn.classList.remove('fa-volume-up');
                            muteBtn.classList.add('fa-volume-mute');
                        } else {
                            muteBtn.classList.remove('fa-volume-mute');
                            muteBtn.classList.add('fa-volume-up');
                        }
                    }
                });
                // Khi play, pause tất cả video khác trong container
                currentVid.addEventListener('play', function() {
                    hideLoader();
                    if (playIcon) playIcon.style.display = 'none';
                    const playPauseBtn = container.querySelector('.play-pause-btn i');
                    if (playPauseBtn) {
                        playPauseBtn.classList.remove('fa-play');
                        playPauseBtn.classList.add('fa-pause');
                    }
                    allVideos.forEach(vid => {
                        if (vid !== currentVid) {
                            vid.pause();
                            vid.currentTime = 0;
                        }
                    });
                });
                currentVid.addEventListener('pause', function() {
                    hideLoader();
                    if (playIcon) playIcon.style.display = 'flex';
                    const playPauseBtn = container.querySelector('.play-pause-btn i');
                    if (playPauseBtn) {
                        playPauseBtn.classList.remove('fa-pause');
                        playPauseBtn.classList.add('fa-play');
                    }
                });
                
                currentVid.parentElement.onclick = (e) => {
                    if (e.target.closest('.overlay-button')) return;
                    this.togglePlayPause(containerId);
                };
                
                currentVid.play().catch(() => {
                    hideLoader();
                    if (playIcon) playIcon.style.display = 'flex';
                });

                // init state
                const muteBtn = container.querySelector('.mute-unmute-btn i');
                if (muteBtn) {
                    if (currentVid.muted) {
                        muteBtn.classList.remove('fa-volume-up');
                        muteBtn.classList.add('fa-volume-mute');
                    } else {
                        muteBtn.classList.remove('fa-volume-mute');
                        muteBtn.classList.add('fa-volume-up');
                    }
                }
                if (currentVid.paused) {
                    if (playIcon) playIcon.style.display = 'flex';
                    if (playPauseBtn) {
                        playPauseBtn.classList.remove('fa-pause');
                        playPauseBtn.classList.add('fa-play');
                    }
                } else {
                    if (playIcon) playIcon.style.display = 'none';
                    if (playPauseBtn) {
                        playPauseBtn.classList.remove('fa-play');
                        playPauseBtn.classList.add('fa-pause');
                    }
                }
            }
        }
    },

    initSwipe(videos, containerId) {
        this.videos[containerId] = videos;
        this.currentVideoIndex[containerId] = 0;
        this.updateVideo(containerId);
    },

    showComments(containerId) {
        // TODO: Implement comments modal
        alert('Comments feature coming soon!');
    },

    shareVideo(containerId) {
        // TODO: Implement share functionality
        const currentVideo = this.videos[containerId][this.currentVideoIndex[containerId]];
        if (currentVideo) {
            // Copy video URL to clipboard or open share modal
            navigator.clipboard.writeText(currentVideo.src).then(() => {
                alert('Video URL copied to clipboard!');
            }).catch(() => {
                alert('Share feature coming soon!');
            });
        }
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
            url: appConst.baseUrl.concat("/video-stream/list"),
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
        const allVideos = [...regularVideos, ...funnyVideos];

        // Display first video and preload next 4
        if (allVideos.length > 0) {
            const videoHtml = VideoTemplate.bindData(allVideos, 'popular-videos', 0);
            $("#list-video-popular").html(videoHtml);
            // Add auto-next event
            const videoEl = document.getElementById('popular-videos-video-0');
            if (videoEl) {
                videoEl.addEventListener('ended', function() {
                    VideoActions.swipeRight('popular-videos');
                });
            }
        }
        VideoActions.initSwipe(allVideos, 'popular-videos');
        addSwipeEvents('popular-videos');
        addKeyboardEvents('popular-videos');
    },

    displayPrivateVideos(videos) {
        $("#list-video-private").empty();
        const sortedByPopularity = [...videos].sort((a, b) => b.upvotes - a.upvotes);
        if (sortedByPopularity.length > 0) {
            const videoHtml = VideoTemplate.bindData(sortedByPopularity, 'private-videos', 0);
            $("#list-video-private").html(videoHtml);
            // Add auto-next event
            const videoEl = document.getElementById('private-videos-video-0');
            if (videoEl) {
                videoEl.addEventListener('ended', function() {
                    VideoActions.swipeRight('private-videos');
                });
            }
        }
        VideoActions.initSwipe(sortedByPopularity, 'private-videos');
        addSwipeEvents('private-videos');
        addKeyboardEvents('private-videos');
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

// Add this function to support swipe gesture
function addSwipeEvents(containerId) {
    let touchStartX = 0;
    let touchEndX = 0;
    const threshold = 50; // px

    // Use event delegation in case container is re-rendered
    document.addEventListener('touchstart', function(e) {
        const container = document.getElementById(`video-items-${containerId}`);
        if (!container || !container.contains(e.target)) return;
        touchStartX = e.changedTouches[0].screenX;
    }, false);

    document.addEventListener('touchend', function(e) {
        const container = document.getElementById(`video-items-${containerId}`);
        if (!container || !container.contains(e.target)) return;
        touchEndX = e.changedTouches[0].screenX;
        const diff = touchEndX - touchStartX;
        if (diff > threshold) {
            VideoActions.swipeLeft(containerId);
        } else if (diff < -threshold) {
            VideoActions.swipeRight(containerId);
        }
    }, false);
}

// Add keyboard navigation for desktop
function addKeyboardEvents(containerId) {
    document.addEventListener('keydown', function(e) {
        // Only handle arrow keys when video container is visible
        const container = document.getElementById(`video-items-${containerId}`);
        if (!container || !container.offsetParent) return;
        
        switch(e.key) {
            case 'ArrowLeft':
                e.preventDefault();
                VideoActions.swipeLeft(containerId);
                showNavigationFeedback('left');
                break;
            case 'ArrowRight':
                e.preventDefault();
                VideoActions.swipeRight(containerId);
                showNavigationFeedback('right');
                break;
        }
    });
}

// Show visual feedback for keyboard navigation
function showNavigationFeedback(direction) {
    // Remove existing feedback
    const existingFeedback = document.querySelector('.nav-feedback');
    if (existingFeedback) {
        existingFeedback.remove();
    }
    
    // Create feedback element
    const feedback = document.createElement('div');
    feedback.className = 'nav-feedback';
    feedback.innerHTML = `<i class="fas fa-arrow-${direction}"></i>`;
    feedback.style.cssText = `
        position: fixed;
        top: 50%;
        ${direction === 'left' ? 'left: 20px' : 'right: 20px'};
        transform: translateY(-50%);
        background: rgba(0, 123, 255, 0.9);
        color: white;
        padding: 15px;
        border-radius: 50%;
        font-size: 1.5rem;
        z-index: 9999;
        animation: navFeedback 0.5s ease-out;
    `;
    
    document.body.appendChild(feedback);
    
    // Remove after animation
    setTimeout(() => {
        if (feedback.parentNode) {
            feedback.remove();
        }
    }, 500);
}
