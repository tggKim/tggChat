package com.tgg.chat.domain.user.service;

import org.springframework.stereotype.Service;

import com.tgg.chat.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	
}
