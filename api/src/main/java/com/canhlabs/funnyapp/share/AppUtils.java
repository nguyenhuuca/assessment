package com.canhlabs.funnyapp.share;

import com.canhlabs.funnyapp.share.dto.UserDetailDto;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.regex.Pattern;

public class AppUtils {
    @SuppressWarnings("squid:S5998")
    static final String EMAIL_REGEX = "^[a-zA-Z0-9_+&*-]+(?:\\."+
            "[a-zA-Z0-9_+&*-]+)*@" +
            "(?:[a-zA-Z0-9-]+\\.)+[a-z" +
            "A-Z]{2,7}$";
    static Pattern patEmail = Pattern.compile(EMAIL_REGEX);
    private  AppUtils() {}
    public  static String getNonNull(Object val) {
        if (val == null) return "";
        return val.toString();
    }

    public static UserDetailDto getCurrentUser() {
        Object detail = null;
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            detail = SecurityContextHolder.getContext().getAuthentication().getDetails();
        }
        return (detail instanceof UserDetailDto) ? (UserDetailDto) detail : null;
    }
    public static boolean isValidEmail(String email) {
        if (email == null)
            return false;
        return patEmail.matcher(email).matches();
    }

}
