package com.canhlabs.assessment.web;

import com.canhlabs.assessment.share.AppConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping(AppConstant.API.BASE_URL +"/thread")
@RestController
@Slf4j
public class ThreadController {
    @GetMapping("/name")
    public String getThreadName() {
        return Thread.currentThread().toString();
    }
    @GetMapping("/load")
    public void doSomething() throws InterruptedException {
        log.info("hey, I'm doing something");
        Thread.sleep(1000);
    }
}
