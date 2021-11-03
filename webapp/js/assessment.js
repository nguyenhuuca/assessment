
/**
 * Using to holed video objct
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
    console.log("update db");
    var url = $("#urlYoutube").val();
    console.log(url);

    const video1 = new VideoObj(1, "nguyenhuuca", "Test share", "https://youtube.com/embed/tMui4IVW0BM", 'This is new video');
    var stringHtml = bindingDataWhenLoad(video1, loadTemplate());
    $("#list-video").prepend(stringHtml);


};

/**
 * User can login or register incase is new user
 * When call api joinSytem, will return user info and jwt token for old user and new user
 * Need to handle some error
 */
function joinSystem() {
    console.log("join system");

    $("#shareBtn").show();
    $("#logoutBtn").show();
    $("#wcJoin").show();

    $("#loginBtn").hide()
    $("#grUser").hide()
    $("#grPass").hide()

};

/**
 * Using to remove jwt token, and user can login again
 */
function logout() {
    console.log("logout system");
    init();
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
    $(idItem).text(val);
};

/**
 * When load page, need to init state for some element on page
 */
function init() {
    $("#shareBtn").hide();
    $("#logoutBtn").hide();
    $("#wcJoin").hide();

    $("#grUser").show()
    $("#grPass").show()
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
            <div style = "float:left; width:200px;">Shared by:{{userName}}</div>
            <div>
            <span id = "{{id_upVote}}" onclick = "voteUp(this)"><i class="far fa-thumbs-up fa-2x"></i></span>
            <span id = "{{id_downVote}}" onclick = "voteDown(this)""><i class="far fa-thumbs-down fa-2x"></i></span>  
            </div> 
            
        </div>
        <div>
            <span id = "{{id_upCount}}" style="float: left; margin-right: 10px;">123</span><span><i class="far fa-thumbs-up"></i></span>
            <span id = {{id_downCount}}>100</span> <span><i class="far fa-thumbs-down"></i></span>
        </div>
        <div>Description:</div>
        <p>{{desc}}</p>
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
    stringHtml = stringHtml.replace("{{id_downVote}}", videoObj.id+"_downVote");
    return stringHtml;
}

/**
 * 
 */
function mockData() {
    var data = [];
    const video1 = new VideoObj(1, "nguyenhuuca", "Test share", "https://www.youtube.com/embed/h_GqRV-SZmU", 'This is new video');
    const video2 = new VideoObj(2, "canh", "Test share", "https://www.youtube.com/embed/h_GqRV-SZmU", 'This is new video');
    const video3 = new VideoObj(3, "canh", "Test share", "https://www.youtube.com/embed/h_GqRV-SZmU", 'This is new video');
    data.push(video1);
    data.push(video2);
    data.push(video3);

    data.forEach(item => {
      var templateHtml = loadTemplate();
      stringHtml = bindingDataWhenLoad(item, templateHtml); 
      $('#list-video').append(stringHtml);
    });
}
