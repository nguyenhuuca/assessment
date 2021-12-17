$.ajaxSetup({
    headers:{
        'Authorization': localStorage.getItem("jwt")
    }
})
var appConst = {
    baseUrl: "http://localhost:8081/v1/assessment"
    //baseUrl: "https://canh-labs.com/api/v1/assessment"
}
/**
 * Using to holed video object
 * @param {*} id : id video after share
 * @param {*} userShared: userShare 
 * @param {*} title: title video
 * @param {*} src:  emper link
 * @param {*} desc: description 
 */
function VideoObj(id, userShared, title, src, desc) {
        this.id = id;
        this.userShared = userShared;
        this.title = title;
        this.src = src;
        this.desc = desc;
};

/**
 * Using to share youtubue url
 */
function share() {
    $("#shareSpinner").show();
    var link = $("#urlYoutube").val();
    var shareObj = {
        url: link
    }
    $.ajax({
        url: appConst.baseUrl.concat("/share-links"),
        type: "POST",
        data: JSON.stringify(shareObj),
        contentType: "application/json",
        dataType: "json"
    }).done(function(rs) {
        var videoInfo = rs.data;
        console.log(videoInfo);
        const video = new VideoObj(videoInfo.id, videoInfo.userShared, videoInfo.title, videoInfo.embedLink, videoInfo.desc);
        var stringHtml = bindingDataWhenLoad(video, loadTemplate());
        $("#list-video").prepend(stringHtml);
        $("#shareSpinner").hide();
        $('#shareModal').modal('hide')

    }).fail(function(err) {
        $("#errMsg").text(err.responseJSON.error.message);
        $("#errMsg").show();
        $("#shareSpinner").hide();
    });
   
};

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

};

/**
 * Using to increase vote when click icon voteUp
 * @param {*} element hold the value of voteUp 
 */
function voteUp(element) {
    var id = $(element).attr("id");
    var idItem = "#"+ id.split("_")[0] + "_" + "upCount";
    var val= $(idItem).text();
    val = parseInt(val) + 1;
    $(idItem).text(val);
};

/**
 * Using to decrease vote when click icon voteDown
 * @param {*} element hole the value of voteDown
 */
function voteDown(element) {
    var id = $(element).attr("id");
    var idItem = "#"+ id.split("_")[0] + "_" + "downCount";
    var val = $(idItem).text();
    val = parseInt(val) - 1;
    val = val >= 0 ? val : 0;
    $(idItem).text(val);
};

/**
 * Using to delete video that user have just added
 * @param {*} element 
 */
function deleteVideos(element) {
    var id = $(element).attr("id")[0];
    var idItem = "#"+ id + "_row";
    console.log(id);
    console.log(idItem);
    $(idItem).remove();
    $.ajax({
        url: appConst.baseUrl.concat("/share-links/"+id),
        type: 'DELETE'
    }).done(function(rs) {
      console.log(rs);

    }).fail(function(err) {
        $("#errMsg").text(err.responseJSON.error.message);
        $("#errMsg").show();
        $("#shareSpinner").hide();
    });

};

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
    <div class="row" id = "{{id_row}}">
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
            <div>
                <span id = "{{id_upVote}}" onclick = "voteUp(this)"><i class="app-pointer far fa-thumbs-up fa-2x"></i></span>
                <span id = "{{id_downVote}}" onclick = "voteDown(this)""><i class="app-pointer far fa-thumbs-down fa-2x"></i></span>  
                <div style = "float:right;">
                    <span id = "{{id_del_item}}" onclick = "deleteVideos(this)">
                        <i style = "color:red;" class="app-pointer fas fa-trash-alt"></i>
                    </span>
                </div>
            </div> 
           
           
        </div>
        <div>
            <span id = "{{id_upCount}}" style="float: left; margin-right: 10px;">0</span><span><i class="far fa-thumbs-up"></i></span>
            <span id = {{id_downCount}}>0</span> <span><i class="far fa-thumbs-down"></i></span>
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
    var stringHtml = templateHtml.replace("{{linkYotube}}", videoObj.src);
    stringHtml = stringHtml.replace("{{movi_title}}", videoObj.title);
    stringHtml = stringHtml.replace("{{userName}}", videoObj.userShared);
    stringHtml = stringHtml.replace("{{desc}}", videoObj.desc);
    stringHtml = stringHtml.replace("{{id_upCount}}", videoObj.id +"_upCount");
    stringHtml = stringHtml.replace("{{id_downCount}}", videoObj.id+"_downCount");
    stringHtml = stringHtml.replace("{{id_upVote}}", videoObj.id+"_upVote");
    stringHtml = stringHtml.replace("{{id_del_item}}", videoObj.id + "_del_item")
    stringHtml = stringHtml.replace("{{id_downVote}}", videoObj.id+"_downVote");
    stringHtml = stringHtml.replace("{{id_row}}", videoObj.id + "_row");
    return stringHtml;
}

/**
 * Using to get all link share when page is loaded
 */
function loadData() {
    var data = [];
    $.ajax({
        url: appConst.baseUrl.concat("/share-links"),
        type: "GET",
        contentType: "application/json",
        dataType: "json"
    }).done(function(rs) {
        var videoInfoList = rs.data;
        console.log(videoInfoList);
        videoInfoList.forEach(videoInfo =>{
            const video = new VideoObj(videoInfo.id, videoInfo.userShared, videoInfo.title, videoInfo.embedLink, videoInfo.desc);
            data.push(video);
        });
        
        data.forEach(item => {
            var templateHtml = loadTemplate();
            stringHtml = bindingDataWhenLoad(item, templateHtml); 
            $('#list-video').append(stringHtml);
        });
    }).fail(function(err) {
        $("#errMsg").text(err.responseJSON.error.message);
        $("#errMsg").show();
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

