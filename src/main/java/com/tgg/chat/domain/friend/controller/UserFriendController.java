package com.tgg.chat.domain.friend.controller;

import com.tgg.chat.domain.friend.dto.request.CreateFriendRequestDto;
import com.tgg.chat.domain.friend.service.UserFriendService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserFriendController {

    private final UserFriendService userFriendService;

    @PostMapping("/friends")
    public ResponseEntity<Void> createUserFriend(Authentication authentication, @RequestBody CreateFriendRequestDto createFriendRequestDto) {

        Claims claims = (Claims)authentication.getPrincipal();

        Long loginUserId = Long.parseLong(claims.getSubject());

        userFriendService.createFriend(loginUserId, createFriendRequestDto);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(null);

    }

}
