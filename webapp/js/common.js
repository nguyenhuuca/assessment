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

