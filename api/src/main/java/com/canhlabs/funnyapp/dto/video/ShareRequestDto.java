package com.canhlabs.funnyapp.dto.video;
import com.canhlabs.funnyapp.dto.BaseDto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ShareRequestDto  extends  BaseDto{
    String url;
    boolean isPrivate;
    String description;
    String title;
}
