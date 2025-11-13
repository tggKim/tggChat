package com.tgg.chat.domain.user.controller;

import org.springframework.web.bind.annotation.RestController;

import com.tgg.chat.domain.user.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	
}
