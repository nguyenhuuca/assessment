

/**
 * Authentication related functions
 */
// Setup AJAX headers with JWT token
$.ajaxSetup({
    headers:{
        'Authorization': localStorage.getItem("jwt")
    }
});

let pendingLoginData = null;

/**
 * Get all users from localStorage
 * @returns {Object} Object containing all users with their MFA status
 */
function getUsers() {
    return JSON.parse(localStorage.getItem('users') || '{}');
}

/**
 * Save user to localStorage
 * @param {Object} user - User object to save
 */
function saveUser(user) {
    const users = getUsers();
    users[user.email] = {
        mfaEnabled: user.mfaEnabled || false
    };
    localStorage.setItem('users', JSON.stringify(users));
}

/**
 * Get user's MFA status from localStorage
 * @param {string} email - User's email
 * @returns {boolean} MFA status
 */
function getUserMfaStatus(email) {
    const users = getUsers();
    return users[email]?.mfaEnabled || false;
}

/**
 * User can login or register in case is new user
 * When call api joinSystem, will return user info and jwt token for old user and new user
 */
function joinSystem() {
    $("#loginSpinner").show();
    var userObj = {
        email: $("#usr").val()
    }

    if (appConst.offlineMode) {
        // Check if user exists in users list
        const mfaEnabled = getUserMfaStatus(userObj.email);
        const mockData = {
            jwt: "mock-jwt-token",
            user: {
                email: userObj.email,
                mfaEnabled: mfaEnabled
            }
        };
        handleLoginResponse(mockData);
    } else {
        $.ajax({
            url: appConst.baseUrl.concat("/user/join"),
            type: "POST",
            data: JSON.stringify(userObj),
            contentType: "application/json",
            dataType: "json"
        }).done(function(rs) {
            handleLoginResponse(rs.data);
        }).fail(function(err) {
            showMessage(err.responseJSON.error.message, "error")
            $("#loginSpinner").hide();
        });
    }
}

/**
 * Handle login response and check if MFA verification is needed
 * @param {Object} data - Login response data
 */
function handleLoginResponse(data) {
    // Store the login data temporarily
    pendingLoginData = data;
    let mfaEnabled;

    // Check MFA status from users list
    if(appConst.offlineMode) {
        mfaEnabled = getUserMfaStatus(data.user.email);
    } else if(data.action === STATUS.MFA_REQUIRED) {
        mfaEnabled = true
    } else if (data.action === STATUS.INVITED_SEND) {
        showMessage("We've sent you an email. Please check your inbox", "success")
        $("#loginSpinner").hide();
    }

    // Update user's MFA status
    data.user.mfaEnabled = mfaEnabled;

    if (mfaEnabled) {
        // User has MFA enabled, show verification modal
        $('#mfaVerificationModal').modal('show');
        $("#loginSpinner").hide();
    } else {
        // No MFA required, process login directly
        processLoginSuccess(data);
    }
}

/**
 * Verify MFA code during login
 */
function verifyLoginMFA() {
    const code = document.getElementById('loginVerificationCode').value;
    const errorElement = document.getElementById('mfaVerificationError');
    
    if (!code || code.length !== 6) {
        errorElement.textContent = "Please enter a valid 6-digit code";
        errorElement.style.display = 'block';
        return;
    }

    const spinner = document.getElementById('verifyLoginSpinner');
    spinner.classList.remove('d-none');
    errorElement.style.display = 'none';

    if (appConst.offlineMode) {
        // Mock verification for offline mode
        setTimeout(() => {
            processLoginSuccess(pendingLoginData);
            $('#mfaVerificationModal').modal('hide');
            spinner.classList.add('d-none');
        }, 1000);
    } else {
        // Call API to verify code
        $.ajax({
            url: appConst.baseUrl.concat("/user/mfa/verify"),
            type: "POST",
            data: JSON.stringify({ 
                otp: code,
                username: pendingLoginData.user.email,
                sessionToken: pendingLoginData.sessionToken
            }),
            contentType: "application/json",
            dataType: "json"
        }).done(function(rs) {
            processLoginSuccess(rs.data);
            $('#mfaVerificationModal').modal('hide');
        }).fail(function(err) {
            errorElement.textContent = err.responseJSON.error.message;
            errorElement.style.display = 'block';
        }).always(function() {
            spinner.classList.add('d-none');
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
    $("#profileBtn").show();
    $("#messageInfo").text(` Welcome ${data.user.email}`);
    $("#messageInfo").show();
    $("#loginBtn").hide();
    $("#loginSpinner").hide();
    $("#grUser").hide();
    $("#grPass").hide();
    $("#mainMsg").hide();
    
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
    $("#profileBtn").hide();
}

/**
 * Initialize authentication state
 */
function initAuthState() {
    $("#loginSpinner").hide();
    $("#shareBtn").hide();
    $("#logoutBtn").hide();
    $("#profileBtn").hide();
    $("#messageInfo").hide();
    $("#mainMsg").hide();
    $("#grUser").show();

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
    const profileMessage = document.getElementById('profileMessage');

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
                url: appConst.baseUrl.concat("/user/mfa/setup").concat("?username=").concat(user.email),
                type: "GET",
                dataType: "json"
            }).done(function(rs) {
                user.secret=rs.data.secret
                localStorage.setItem('user', JSON.stringify(user));
                document.getElementById('qrCode').innerHTML = `<img src="data:data:image/png;base64,${rs.data.qrCode}" alt="MFA QR Code">`;
            }).fail(function(err) {
                profileMessage.textContent = err.responseJSON.error.message;
                profileMessage.className = 'profile-message error';
                profileMessage.style.display = 'block';
            });
        }
        mfaSetupSection.classList.add('active');
    });

    // Verify MFA button click handler
    verifyMfaBtn.addEventListener('click', function() {
        const code = document.getElementById('verificationCode').value;
        if (!code || code.length !== 6) {
            profileMessage.textContent = "Please enter a valid 6-digit code";
            profileMessage.className = 'profile-message error';
            profileMessage.style.display = 'block';
            return;
        }

        const spinner = document.getElementById('verifySpinner');
        spinner.classList.remove('d-none');
        profileMessage.style.display = 'none';

        if (appConst.offlineMode) {
            // Mock verification for offline mode
            setTimeout(() => {
                enableMFA();
                spinner.classList.add('d-none');
            }, 1000);
        } else {
            // Call API to verify code
            $.ajax({
                url: appConst.baseUrl.concat("/user/mfa/enable"),
                type: "POST",
                data: JSON.stringify({ otp: code,  username: user.email, secret: user.secret }),
                contentType: "application/json",
                dataType: "json"
            }).done(function(rs) {
                enableMFA();
            }).fail(function(err) {
                profileMessage.textContent = err.responseJSON.error.message;
                profileMessage.className = 'profile-message error';
                profileMessage.style.display = 'block';
            }).always(function() {
                spinner.classList.add('d-none');
            });
        }
    });

    // Disable MFA button click handler
    disableMfaBtn.addEventListener('click', function() {
        const code = document.getElementById('disableVerificationCode').value;
        if (!code || code.length !== 6) {
            profileMessage.textContent = "Please enter a valid 6-digit code";
            profileMessage.className = 'profile-message error';
            profileMessage.style.display = 'block';
            return;
        }

        const spinner = document.getElementById('disableSpinner');
        spinner.classList.remove('d-none');
        profileMessage.style.display = 'none';

        if (appConst.offlineMode) {
            // Mock disable for offline mode
            setTimeout(() => {
                disableMFA();
                spinner.classList.add('d-none');
            }, 1000);
        } else {
            // Call API to disable MFA
            $.ajax({
                url: appConst.baseUrl.concat("user/mfa/disable"),
                type: "POST",
                data: JSON.stringify({ code: code }),
                contentType: "application/json",
                dataType: "json"
            }).done(function(rs) {
                disableMFA();
            }).fail(function(err) {
                profileMessage.textContent = err.responseJSON.error.message;
                profileMessage.className = 'profile-message error';
                profileMessage.style.display = 'block';
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
    
    // Save to users list
    saveUser(user);

    // Show success message in profile modal
    const profileMessage = document.getElementById('profileMessage');
    profileMessage.textContent = "MFA has been enabled successfully!";
    profileMessage.className = 'profile-message success';
    profileMessage.style.display = 'block';
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
    
    // Save to users list
    saveUser(user);

    // Show success message in profile modal
    const profileMessage = document.getElementById('profileMessage');
    profileMessage.textContent = "MFA has been disabled successfully!";
    profileMessage.className = 'profile-message success';
    profileMessage.style.display = 'block';
}

// Initialize MFA when document is ready
$(document).ready(function() {
    initMFA();
    
    // Add event listener for MFA verification button
    document.getElementById('verifyLoginMfaBtn').addEventListener('click', verifyLoginMFA);
    
    // Clear pending login data when modal is closed
    $('#mfaVerificationModal').on('hidden.bs.modal', function () {
        pendingLoginData = null;
        document.getElementById('loginVerificationCode').value = '';
        document.getElementById('mfaVerificationError').style.display = 'none';
    });

    // Add event listener to clear message when modal is closed
    $('#profileModal').on('hidden.bs.modal', function () {
        const profileMessage = document.getElementById('profileMessage');
        profileMessage.style.display = 'none';
        profileMessage.textContent = '';
    });
}); 