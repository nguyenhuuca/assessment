/**
 * Authentication Module
 * Handles all authentication related functionality
 */
const Auth = {
    /**
     * Initialize AJAX headers with JWT token
     */
    initAjaxHeaders() {
        const headers = {};
        const jwt = localStorage.getItem("jwt");
        const guestToken = localStorage.getItem("guestToken");
        
        if (jwt) {
            headers['Authorization'] = jwt;
        }
        
        if (guestToken) {
            headers['X-Guest-Token'] = guestToken;
        }
        
        $.ajaxSetup({
            headers: headers
        });
    },

    /**
     * Check user session by calling /user/me API
     */
    checkUserSession() {
        const jwt = localStorage.getItem("jwt");
        if (!jwt) return;

        $.ajax({
            url: appConst.baseUrl.concat("/user/me"),
            type: "GET",
            dataType: "json"
        }).fail((err) => {
            if (err.status === 401 || err.status === 403) {
                this.logout();
            }
        });
    },

    /**
     * User storage management
     */
    UserStorage: {
        getUsers() {
            return JSON.parse(localStorage.getItem('users') || '{}');
        },

        saveUser(user) {
            const users = this.getUsers();
            users[user.email] = {
                mfaEnabled: user.mfaEnabled || false
            };
            localStorage.setItem('users', JSON.stringify(users));
        },

        getUserMfaStatus(email) {
            const users = this.getUsers();
            return users[email]?.mfaEnabled || false;
        }
    },

    /**
     * Login/Registration handling
     */
    LoginManager: {
        pendingLoginData: null,

        joinSystem() {
            $("#loginSpinner").show();
            const userObj = {
                email: $("#usr").val()
            };

            $.ajax({
                url: appConst.baseUrl.concat("/user/join"),
                type: "POST",
                data: JSON.stringify(userObj),
                contentType: "application/json",
                dataType: "json"
            }).done((rs) => {
                this.handleLoginResponse(rs.data);
            }).fail((err) => {
                showMessage(err.responseJSON.error.message, "error");
                $("#loginSpinner").hide();
            });
        },

        handleLoginResponse(data) {
            this.pendingLoginData = data;
            let mfaEnabled;

            if (data.action === STATUS.MFA_REQUIRED) {
                mfaEnabled = true;
            } else if (data.action === STATUS.INVITED_SEND) {
                showMessage("We've sent you an email. Please check your inbox", "success");
                $("#loginSpinner").hide();
                return;
            }

            if (mfaEnabled) {
                data.user.mfaEnabled = mfaEnabled;
                localStorage.setItem('user', JSON.stringify(data.user));
                Auth.UserStorage.saveUser(data.user);
                $('#mfaVerificationModal').modal('show');
                $("#loginSpinner").hide();
            } else {
                this.processLoginSuccess(data);
            }
        },

        processLoginSuccess(data) {
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
            
            localStorage.setItem('jwt', data.jwt);
            localStorage.setItem('user', JSON.stringify(data.user));
            
            Auth.initAjaxHeaders();
        }
    },

    /**
     * MFA (Multi-Factor Authentication) handling
     */
    MFA: {
        verifyLoginMFA() {
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

            $.ajax({
                url: appConst.baseUrl.concat("/user/mfa/verify"),
                type: "POST",
                data: JSON.stringify({ 
                    otp: code,
                    username: Auth.LoginManager.pendingLoginData.user.email,
                    sessionToken: Auth.LoginManager.pendingLoginData.sessionToken
                }),
                contentType: "application/json",
                dataType: "json"
            }).done((rs) => {
                Auth.LoginManager.processLoginSuccess(rs.data);
                $('#mfaVerificationModal').modal('hide');
            }).fail((err) => {
                errorElement.textContent = err.responseJSON.error.message;
                errorElement.style.display = 'block';
            }).always(() => {
                spinner.classList.add('d-none');
            });
        },

        init() {
            const setupMfaBtn = document.getElementById('setupMfaBtn');
            const mfaSetupSection = document.getElementById('mfaSetupSection');
            const mfaDisableSection = document.getElementById('mfaDisableSection');
            const verifyMfaBtn = document.getElementById('verifyMfaBtn');
            const disableMfaBtn = document.getElementById('disableMfaBtn');
            const mfaStatus = document.getElementById('mfaStatus');
            const profileMessage = document.getElementById('profileMessage');

            const user = JSON.parse(localStorage.getItem('user') || '{}');
            if (user.mfaEnabled) {
                mfaStatus.textContent = 'Enabled';
                mfaStatus.classList.add('enabled');
                setupMfaBtn.style.display = 'none';
                mfaDisableSection.classList.add('active');
            }

            this.setupEventListeners(setupMfaBtn, mfaSetupSection, mfaDisableSection, 
                                   verifyMfaBtn, disableMfaBtn, mfaStatus, profileMessage, user);
        },

        setupEventListeners(setupMfaBtn, mfaSetupSection, mfaDisableSection, 
                          verifyMfaBtn, disableMfaBtn, mfaStatus, profileMessage, user) {
            setupMfaBtn.addEventListener('click', () => this.handleSetupMFA(user, mfaSetupSection, profileMessage));
            verifyMfaBtn.addEventListener('click', () => this.handleVerifyMFA(user, profileMessage));
            disableMfaBtn.addEventListener('click', () => this.handleDisableMFA(user, profileMessage));
        },

        handleSetupMFA(user, mfaSetupSection, profileMessage) {
            $.ajax({
                url: appConst.baseUrl.concat("/user/mfa/setup").concat("?username=").concat(user.email),
                type: "GET",
                dataType: "json"
            }).done((rs) => {
                user.secret = rs.data.secret;
                localStorage.setItem('user', JSON.stringify(user));
                document.getElementById('qrCode').innerHTML = 
                    `<img src="data:data:image/png;base64,${rs.data.qrCode}" alt="MFA QR Code">`;
                mfaSetupSection.classList.add('active');
            }).fail((err) => {
                profileMessage.textContent = err.responseJSON.error.message;
                profileMessage.className = 'profile-message error';
                profileMessage.style.display = 'block';
            });
        },

        handleVerifyMFA(user, profileMessage) {
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

            $.ajax({
                url: appConst.baseUrl.concat("/user/mfa/enable"),
                type: "POST",
                data: JSON.stringify({ otp: code, username: user.email, secret: user.secret }),
                contentType: "application/json",
                dataType: "json"
            }).done(() => {
                this.enableMFA();
            }).fail((err) => {
                profileMessage.textContent = err.responseJSON.error.message;
                profileMessage.className = 'profile-message error';
                profileMessage.style.display = 'block';
            }).always(() => {
                spinner.classList.add('d-none');
            });
        },

        handleDisableMFA(user, profileMessage) {
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

            $.ajax({
                url: appConst.baseUrl.concat("/user/mfa/disable"),
                type: "POST",
                data: JSON.stringify({ otp: code, username: user.email  }),
                contentType: "application/json",
                dataType: "json"
            }).done(() => {
                this.disableMFA();
            }).fail((err) => {
                profileMessage.textContent = err.responseJSON.error.message;
                profileMessage.className = 'profile-message error';
                profileMessage.style.display = 'block';
            }).always(() => {
                spinner.classList.add('d-none');
            });
        },

        enableMFA() {
            const user = JSON.parse(localStorage.getItem('user') || '{}');
            user.mfaEnabled = true;
            localStorage.setItem('user', JSON.stringify(user));
            Auth.UserStorage.saveUser(user);
            this.updateMfaSetupUI();
            this.showSuccessMessage("MFA has been enabled successfully!");
        },

        disableMFA() {
            const mfaStatus = document.getElementById('mfaStatus');
            const setupMfaBtn = document.getElementById('setupMfaBtn');
            const mfaSetupSection = document.getElementById('mfaSetupSection');
            const mfaDisableSection = document.getElementById('mfaDisableSection');

            mfaStatus.textContent = 'Disabled';
            mfaStatus.classList.remove('enabled');
            setupMfaBtn.style.display = 'block';
            mfaSetupSection.classList.remove('active');
            mfaDisableSection.classList.remove('active');

            document.getElementById('disableVerificationCode').value = '';

            const user = JSON.parse(localStorage.getItem('user') || '{}');
            user.mfaEnabled = false;
            localStorage.setItem('user', JSON.stringify(user));
            Auth.UserStorage.saveUser(user);

            this.showSuccessMessage("MFA has been disabled successfully!");
        },

        updateMfaSetupUI() {
            const user = JSON.parse(localStorage.getItem('user') || '{}');
            const mfaStatus = document.getElementById('mfaStatus');
            const setupMfaBtn = document.getElementById('setupMfaBtn');
            const mfaSetupSection = document.getElementById('mfaSetupSection');
            const mfaDisableSection = document.getElementById('mfaDisableSection');

            if (user.mfaEnabled) {
                mfaStatus.textContent = 'Enabled';
                mfaStatus.classList.add('enabled');
                setupMfaBtn.style.display = 'none';
                mfaSetupSection.classList.remove('active');
                mfaDisableSection.classList.add('active');
            } else {
                mfaStatus.textContent = 'Disabled';
                mfaStatus.classList.remove('enabled');
                setupMfaBtn.style.display = 'block';
                mfaSetupSection.classList.remove('active');
                mfaDisableSection.classList.remove('active');
            }
        },

        showSuccessMessage(message) {
            const profileMessage = document.getElementById('profileMessage');
            profileMessage.textContent = message;
            profileMessage.className = 'profile-message success';
            profileMessage.style.display = 'block';
        }
    },

    /**
     * Magic Link handling
     */
    MagicLink: {
        verifyToken(token) {
            if (!token) return;

            $.ajax({
                url: appConst.baseUrl.concat("/user/verify-magic?token=").concat(token),
                type: "GET",
                dataType: "json"
            }).done((rs) => {
                if (rs.data.action === STATUS.MFA_REQUIRED) {
                    Auth.LoginManager.handleLoginResponse(rs.data);
                    window.history.replaceState({}, document.title, "/");
                } else {
                    Auth.LoginManager.handleLoginResponse(rs.data);
                    window.location.href = "https://funnyapp.canh-labs.com";
                }
            }).fail((err) => {
                showMessage(err.responseJSON.error.message, "error");
            });
        }
    },

    /**
     * Profile management
     */
    Profile: {
        updateUserInfo() {
            const user = JSON.parse(localStorage.getItem('user') || '{}');
            document.getElementById('userEmail').textContent = user.email || '';
            document.getElementById('memberSince').textContent = new Date().toLocaleDateString();
            document.getElementById('lastLogin').textContent = new Date().toLocaleDateString();
        }
    },

    /**
     * Logout handling
     */
    logout() {
        localStorage.removeItem("jwt");
        localStorage.removeItem("user");
        initState();
        $("#loginBtn").show();
        $("#profileBtn").hide();
    },

    /**
     * Initialize authentication state
     */
    initState() {
        $("#loginSpinner").hide();
        $("#shareBtn").hide();
        $("#logoutBtn").hide();
        $("#profileBtn").hide();
        $("#messageInfo").hide();
        $("#mainMsg").hide();
        $("#grUser").show();

        if (localStorage.getItem("jwt")) {
            const data = {
                jwt: localStorage.getItem("jwt"),
                user: JSON.parse(localStorage.getItem("user"))
            };
            this.LoginManager.processLoginSuccess(data);
            // Check session after initializing state
            this.checkUserSession();
        }
    }
};

// Initialize when document is ready
$(document).ready(function() {
    // Auth.initState();
    Auth.MFA.init();
    
    // Add event listener for MFA verification button
    document.getElementById('verifyLoginMfaBtn').addEventListener('click', () => Auth.MFA.verifyLoginMFA());
    
    // Clear pending login data when modal is closed
    $('#mfaVerificationModal').on('hidden.bs.modal', function () {
        Auth.LoginManager.pendingLoginData = null;
        document.getElementById('loginVerificationCode').value = '';
        document.getElementById('mfaVerificationError').style.display = 'none';
    });

    // Add event listener to clear message when modal is closed
    $('#profileModal').on('hidden.bs.modal', function () {
        const profileMessage = document.getElementById('profileMessage');
        profileMessage.style.display = 'none';
        profileMessage.textContent = '';
    });

    // Update UI when profile modal is opened
    $('#profileModal').on('show.bs.modal', function () {
        Auth.MFA.updateMfaSetupUI();
        Auth.Profile.updateUserInfo();
    });

    // Check for magic link token
    const queryString = window.location.search;
    const params = new URLSearchParams(queryString);
    const token = params.get('token');

    if (token) {
        Auth.MagicLink.verifyToken(token);
    }
}); 