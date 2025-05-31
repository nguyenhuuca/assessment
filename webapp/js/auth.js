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

/**
 * Initialize MFA setup
 */
function initMFA() {
    const setupMfaBtn = document.getElementById('setupMfaBtn');
    const mfaSetupSection = document.getElementById('mfaSetupSection');
    const mfaDisableSection = document.getElementById('mfaDisableSection');
    const verifyMfaBtn = document.getElementById('verifyMfaBtn');
    const disableMfaBtn = document.getElementById('disableMfaBtn');
    const mfaStatus = document.getElementById('mfaStatus');

    // Check if MFA is already enabled
    const user = JSON.parse(localStorage.getItem('user') || '{}');
    if (user.mfaEnabled) {
        mfaStatus.textContent = 'Enabled';
        mfaStatus.classList.add('enabled');
        setupMfaBtn.style.display = 'none';
        mfaDisableSection.classList.add('active');
    }

    // Setup MFA button click handler
    setupMfaBtn.addEventListener('click', function() {
        if (appConst.offlineMode) {
            // Mock QR code for offline mode
            const mockQRCode = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';
            document.getElementById('qrCode').innerHTML = `<img src="${mockQRCode}" alt="MFA QR Code">`;
        } else {
            // Call API to get QR code
            $.ajax({
                url: appConst.baseUrl.concat("/mfa/setup"),
                type: "GET",
                dataType: "json"
            }).done(function(rs) {
                document.getElementById('qrCode').innerHTML = `<img src="${rs.data.qrCode}" alt="MFA QR Code">`;
            }).fail(function(err) {
                $("#errMsg").text(err.responseJSON.error.message);
                $("#errMsg").show();
            });
        }
        mfaSetupSection.classList.add('active');
    });

    // Verify MFA button click handler
    verifyMfaBtn.addEventListener('click', function() {
        const code = document.getElementById('verificationCode').value;
        if (!code || code.length !== 6) {
            $("#errMsg").text("Please enter a valid 6-digit code");
            $("#errMsg").show();
            return;
        }

        const spinner = document.getElementById('verifySpinner');
        spinner.classList.remove('d-none');

        if (appConst.offlineMode) {
            // Mock verification for offline mode
            setTimeout(() => {
                enableMFA();
                spinner.classList.add('d-none');
            }, 1000);
        } else {
            // Call API to verify code
            $.ajax({
                url: appConst.baseUrl.concat("/mfa/verify"),
                type: "POST",
                data: JSON.stringify({ code: code }),
                contentType: "application/json",
                dataType: "json"
            }).done(function(rs) {
                enableMFA();
            }).fail(function(err) {
                $("#errMsg").text(err.responseJSON.error.message);
                $("#errMsg").show();
            }).always(function() {
                spinner.classList.add('d-none');
            });
        }
    });

    // Disable MFA button click handler
    disableMfaBtn.addEventListener('click', function() {
        const code = document.getElementById('disableVerificationCode').value;
        if (!code || code.length !== 6) {
            $("#errMsg").text("Please enter a valid 6-digit code");
            $("#errMsg").show();
            return;
        }

        const spinner = document.getElementById('disableSpinner');
        spinner.classList.remove('d-none');

        if (appConst.offlineMode) {
            // Mock disable for offline mode
            setTimeout(() => {
                disableMFA();
                spinner.classList.add('d-none');
            }, 1000);
        } else {
            // Call API to disable MFA
            $.ajax({
                url: appConst.baseUrl.concat("/mfa/disable"),
                type: "POST",
                data: JSON.stringify({ code: code }),
                contentType: "application/json",
                dataType: "json"
            }).done(function(rs) {
                disableMFA();
            }).fail(function(err) {
                $("#errMsg").text(err.responseJSON.error.message);
                $("#errMsg").show();
            }).always(function() {
                spinner.classList.add('d-none');
            });
        }
    });
}

/**
 * Enable MFA for the user
 */
function enableMFA() {
    const mfaStatus = document.getElementById('mfaStatus');
    const setupMfaBtn = document.getElementById('setupMfaBtn');
    const mfaSetupSection = document.getElementById('mfaSetupSection');
    const mfaDisableSection = document.getElementById('mfaDisableSection');

    // Update UI
    mfaStatus.textContent = 'Enabled';
    mfaStatus.classList.add('enabled');
    setupMfaBtn.style.display = 'none';
    mfaSetupSection.classList.remove('active');
    mfaDisableSection.classList.add('active');

    // Update user data
    const user = JSON.parse(localStorage.getItem('user') || '{}');
    user.mfaEnabled = true;
    localStorage.setItem('user', JSON.stringify(user));

    // Show success message
    $("#errMsg").text("MFA has been enabled successfully!");
    $("#errMsg").show();
}

/**
 * Disable MFA for the user
 */
function disableMFA() {
    const mfaStatus = document.getElementById('mfaStatus');
    const setupMfaBtn = document.getElementById('setupMfaBtn');
    const mfaSetupSection = document.getElementById('mfaSetupSection');
    const mfaDisableSection = document.getElementById('mfaDisableSection');

    // Update UI
    mfaStatus.textContent = 'Disabled';
    mfaStatus.classList.remove('enabled');
    setupMfaBtn.style.display = 'block';
    mfaSetupSection.classList.remove('active');
    mfaDisableSection.classList.remove('active');

    // Clear verification code
    document.getElementById('disableVerificationCode').value = '';

    // Update user data
    const user = JSON.parse(localStorage.getItem('user') || '{}');
    user.mfaEnabled = false;
    localStorage.setItem('user', JSON.stringify(user));

    // Show success message
    $("#errMsg").text("MFA has been disabled successfully!");
    $("#errMsg").show();
}

// Initialize MFA when document is ready
$(document).ready(function() {
    initMFA();
}); 