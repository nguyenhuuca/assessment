package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.utils.AppConstant;
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
        return "thread name: " + Thread.currentThread().getName() +
                ", is virtual: " + Thread.currentThread().isVirtual();
    }
    @GetMapping("/load")
    public void doSomething() throws InterruptedException {
        log.info("hey, I'm doing something");
        Thread.sleep(1000);
    }
}
