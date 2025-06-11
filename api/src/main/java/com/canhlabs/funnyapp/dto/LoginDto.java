package com.canhlabs.funnyapp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class LoginDto extends BaseDto {
    private String email;
    private String password;

    public void setEmail(String email) {
        if (StringUtils.isNotEmpty(email)) {
            this.email = email.toLowerCase().trim();
        }
    }

    public String getEmail() {
        if (StringUtils.isNotEmpty(email)) {
            this.email = email.toLowerCase().trim();
        }
        return this.email;
    }


}
