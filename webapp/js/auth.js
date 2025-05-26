/**
 * Authentication related functions
 */

// Setup AJAX headers with JWT token
$.ajaxSetup({
    headers:{
        'Authorization': localStorage.getItem("jwt")
    }
});

/**
 * User can login or register in case is new user
 * When call api joinSystem, will return user info and jwt token for old user and new user
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
        processLoginSuccess(mockData);
    } else {
        $.ajax({
            url: appConst.baseUrl.concat("/join"),
            type: "POST",
            data: JSON.stringify(userObj),
            contentType: "application/json",
            dataType: "json"
        }).done(function(rs) {
            processLoginSuccess(rs.data);
        }).fail(function(err) {
            $("#errMsg").text(err.responseJSON.error.message);
            $("#errMsg").show();
            $("#loginSpinner").hide();
        });
    }
}

/**
 * Process successful login
 * @param {Object} data - Login response data containing jwt and user info
 */
function processLoginSuccess(data) {
    $("#shareBtn").show();
    $("#logoutBtn").show();
    $("#messageInfo").text(` Welcome ${data.user.email}`);
    $("#messageInfo").show();
    $("#loginBtn").hide();
    $("#loginSpinner").hide();
    $("#grUser").hide();
    $("#grPass").hide();
    $("#errMsg").hide();
    
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
 * Using stateless web application, so no need to call server to logout
 * JWT is Self-contained (Transparent token)
 * If we want to revoke this token on server, we need to add it to blacklist
 */
function logout() {
    console.log("logout system");
    localStorage.removeItem("jwt");
    localStorage.removeItem("user");
    initState();
    $("#loginBtn").show();
}

/**
 * Initialize authentication state
 */
function initAuthState() {
    $("#loginSpinner").hide();
    $("#shareBtn").hide();
    $("#logoutBtn").hide();
    $("#messageInfo").hide();
    $("#errMsg").hide();
    $("#grUser").show();
    $("#grPass").show();

    // Check if user was previously logged in
    if(localStorage.getItem("jwt")) {
        const data = {
            jwt: localStorage.getItem("jwt"),
            user: JSON.parse(localStorage.getItem("user"))
        };
        processLoginSuccess(data);
    }
} 