package com.tgg.chat.domain.chat.room.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatRoomController {

    @GetMapping("/chatRooms")
    public String deploy_test() {
        return "deploy success";
    }

}
