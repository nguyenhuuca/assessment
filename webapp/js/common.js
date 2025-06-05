let appConst = {
    // baseUrl: "http://localhost:8081/v1/funny-app",
    baseUrl: "https://canh-labs.com/api/v1/funny-app",
    offlineMode: false  // Flag to control offline/online mode
}
// enum-like constant
window.STATUS = Object.freeze({
    MFA_REQUIRED: 'MFA_REQUIRED',
    INVITED_SEND: 'INVITED_SEND',
});

function showMessage(message, type = 'error') {
    const $msg = $("#mainMsg");

    $msg.removeClass("error-msg success-msg");

    if (type === 'error') {
        $msg.addClass("error-msg");
    } else if (type === 'success') {
        $msg.addClass("success-msg");
    }

    $msg.text(message).show();
}

$(document).ready(function() {
    // Get full query string, e.g. "?=abcsxs" or "?token=abcsxs"
    let queryString = window.location.search; // returns "?=abcsxs"

    // incase  param key=value like ?token=abc the using URLSearchParams:
    let params = new URLSearchParams(queryString);

    // incase token is param 'token'
    let token = params.get('token');

    // incase URL is ?=abcsxs (no key), have to pasre it:
    if (!token) {
        // queryString = "?=abcsxs"
        // remove "?" và "="
        token = queryString.replace(/^(\?|=)/g, '');
    }

    console.log("Token from URL:", token);

    if(token) {
        // call other api
        // $.post('/api/verify-token', { token: token }, function(response) { ... })
    } else {
        console.error("Token not found in URL!");
    }
});

