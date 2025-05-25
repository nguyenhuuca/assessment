$.ajaxSetup({
    headers:{
        'Authorization': localStorage.getItem("jwt")
    }
})
let appConst = {
    //baseUrl: "http://localhost:8081/v1/assessment",
    baseUrl: "https://canh-labs.com/api/v1/assessment",
    offlineMode: true  // Flag to control offline/online mode
}
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
}

/**
 * Using to share youtubue url
 */
function share() {
    $("#shareSpinner").show();
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
        $("#list-video").prepend(stringHtml);
        $("#shareSpinner").hide();
        $('#shareModal').modal('hide');
    } else {
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
            $("#list-video").prepend(stringHtml);
            $("#shareSpinner").hide();
            $('#shareModal').modal('hide')
        }).fail(function(err) {
            $("#errMsg").text(err.responseJSON.error.message);
            $("#errMsg").show();
            $("#shareSpinner").hide();
        });
    }
}

/**
 * User can login or register incase is new user
 * When call api joinSytem, will return user info and jwt token for old user and new user
 * Need to handle some error
 */
function joinSystem() {
    $("#loginSpinner").show();
    var userObj = {
        email: $("#usr").val(),
        password: $("#pwd").val()
    }

    if (appConst.offlineMode) {
        // Mock login response for offline mode
        const mockData = {
            jwt: "mock-jwt-token",
            user: {
                email: userObj.email
            }
        };
        proceesLoginSuccess(mockData);
    } else {
        $.ajax({
            url: appConst.baseUrl.concat("/join"),
            type: "POST",
            data: JSON.stringify(userObj),
            contentType: "application/json",
            dataType: "json"
        }).done(function(rs) {
            proceesLoginSuccess(rs.data);
        }).fail(function(err) {
            $("#errMsg").text(err.responseJSON.error.message);
            $("#errMsg").show();
            $("#loginSpinner").hide();
        });
    }
}


/**
 * Using to remove jwt token, and user can login again
 * I am using stateless web application, so not need to call to server to logout.
 * Because jwt is Self-contained (Transparent token),
 * In-case We want revoke this token on server, we need add this token to
 * black list on server arter logout.
 */
function logout() {
    console.log("logout system");
    localStorage.removeItem("jwt");
    localStorage.removeItem("user");
    initState();
    $("#loginBtn").show()

}

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
    $("#loginSpinner").hide();
    $("#shareSpinner").hide();
    $("#shareBtn").hide();
    $("#logoutBtn").hide();
    $("#messageInfo").hide();
    $("#errMsg").hide();

    $("#grUser").show()
    $("#grPass").show()
    // check user login befor
    if(localStorage.getItem("jwt")) {
        const data = {
            jwt: localStorage.getItem("jwt"),
            user: JSON.parse(localStorage.getItem("user"))
        }
        proceesLoginSuccess(data);

    }
}

/**
 * Using to return fix template for list video, each video will have item with fix fomat
 * @returns html with paramter by format: {{param}}
 */
function loadTemplate() {
    return `
    <div class="row">
        <!-- 16:9 aspect ratio -->
        <div class="col-6">
        <div class="ratio ratio-16x9">
            <iframe src="{{linkYotube}}" allowfullscreen></iframe>
        </div>
        </div>
        <div class="col-6">
        <div style="color:red; font-weight:bold;">{{movi_title}}</div>
        <div>
            <div style = "float:left; width:250px;">
               <div style = "float:left;font-weight: 600;">Shared by:&nbsp;</div> <div>{{userName}}</div>
            </div>
        </div>
        <div class="vote-container">
            <div class="vote-button" id="{{id_upVote}}" onclick="voteUp(this)">
                <i class="far fa-thumbs-up"></i>
                <span class="vote-count" id="{{id_upCount}}">0</span>
            </div>
            <div class="vote-button" id="{{id_downVote}}" onclick="voteDown(this)">
                <i class="far fa-thumbs-down"></i>
                <span class="vote-count" id="{{id_downCount}}">0</span>
            </div>
            <div class="delete-button" id="{{id_delete}}" onclick="deleteVideo(this)">
                <i class="fas fa-trash"></i>
                Delete
            </div>
        </div>
        <div class = "app-title">Description:</div>
        <pre class = "app-wrap-desc">{{desc}}</pre>
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
    let stringHtml = templateHtml.replace("{{linkYotube}}", videoObj.src);
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

// Mock data for videos
const mockVideos = [
    {
        id: "1",
        userShared: "john.doe@example.com",
        title: "Funny Cat Compilation 2024",
        embedLink: "https://www.youtube.com/embed/dQw4w9WgXcQ",
        desc: "A hilarious compilation of cats doing funny things!"
    },
    {
        id: "2",
        userShared: "jane.smith@example.com",
        title: "Best Dog Moments",
        embedLink: "https://www.youtube.com/embed/dQw4w9WgXcQ",
        desc: "The most adorable and funny dog moments caught on camera"
    },
    {
        id: "3",
        userShared: "bob.wilson@example.com",
        title: "Epic Fails 2024",
        embedLink: "https://www.youtube.com/embed/dQw4w9WgXcQ",
        desc: "The most epic fails of 2024 that will make you laugh"
    }
];

/**
 * Using to get all link share when page is loaded
 */
function loadData() {
    let data = [];
    
    if (appConst.offlineMode) {
        // Use mock data in offline mode
        mockVideos.forEach(videoInfo => {
            const video = new VideoObj(
                videoInfo.id,
                videoInfo.userShared,
                videoInfo.title,
                videoInfo.embedLink,
                videoInfo.desc
            );
            data.push(video);
        });
    } else {
        // Use API in online mode
        $.ajax({
            url: appConst.baseUrl.concat("/share-links"),
            type: "GET",
            contentType: "application/json",
            dataType: "json"
        }).done(function(rs) {
            let videoInfoList = rs.data;
            console.log(videoInfoList);
            videoInfoList.forEach(videoInfo => {
                const video = new VideoObj(videoInfo.id, videoInfo.userShared, videoInfo.title, videoInfo.embedLink, videoInfo.desc);
                data.push(video);
            });
            
            data.forEach(item => {
                let templateHtml = loadTemplate();
                let stringHtml = bindingDataWhenLoad(item, templateHtml);
                $('#list-video').append(stringHtml);
            });
        }).fail(function(err) {
            $("#errMsg").text(err.responseJSON.error.message);
            $("#errMsg").show();
        });
        return; // Return early for API call since we'll append data in the done callback
    }
    
    // For offline mode, append data immediately
    data.forEach(item => {
        let templateHtml = loadTemplate();
        let stringHtml = bindingDataWhenLoad(item, templateHtml);
        $('#list-video').append(stringHtml);
    });
}

function proceesLoginSuccess(data) {
    $("#shareBtn").show();
    $("#logoutBtn").show();
    $("#messageInfo").text(` Welcome ${data.user.email}`);
    $("#messageInfo").show();
    $("#loginBtn").hide();
    $("#loginSpinner").hide();
    $("#grUser").hide();
    $("#grPass").hide();
    $("#errMsg").hide();
    // save jwt
    localStorage.setItem('jwt', data.jwt);
    localStorage.setItem('user', JSON.stringify(data.user));
    $.ajaxSetup({
        headers:{
            'Authorization': localStorage.getItem("jwt")
        }
    })
}

/**
 * Initialize theme from localStorage or system preference
 */
function initTheme() {
    const savedTheme = localStorage.getItem('theme');
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    
    if (savedTheme === 'dark' || (!savedTheme && prefersDark)) {
        document.documentElement.setAttribute('data-theme', 'dark');
        document.querySelector('.theme-toggle i').classList.replace('fa-moon', 'fa-sun');
    }
}

/**
 * Toggle between light and dark theme
 */
function toggleTheme() {
    const currentTheme = document.documentElement.getAttribute('data-theme');
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
    
    document.documentElement.setAttribute('data-theme', newTheme);
    localStorage.setItem('theme', newTheme);
    
    const icon = document.querySelector('.theme-toggle i');
    if (newTheme === 'dark') {
        icon.classList.replace('fa-moon', 'fa-sun');
    } else {
        icon.classList.replace('fa-sun', 'fa-moon');
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
            $("#errMsg").text(err.responseJSON.error.message);
            $("#errMsg").show();
        });
    }
}

