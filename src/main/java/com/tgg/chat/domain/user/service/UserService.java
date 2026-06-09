package com.tgg.chat.domain.user.service;

import com.tgg.chat.domain.user.dto.request.UserUpdateRequestDto;
import com.tgg.chat.domain.user.dto.response.UserResponseDto;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tgg.chat.domain.user.dto.request.SignUpRequestDto;
import com.tgg.chat.domain.user.dto.response.SignUpResponseDto;
import com.tgg.chat.domain.user.dto.response.OtherUserResponseDto;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	
	@Transactional
	public SignUpResponseDto signUpUser(SignUpRequestDto signUpRequestDto) {
		// 이메일 중복 검사
		if(userRepository.existsByEmail(signUpRequestDto.getEmail())) {
			throw new ErrorException(ErrorCode.DUPLICATE_EMAIL_ERROR);
		}
		
		// 유저명 중복 검사
		if(userRepository.existsByUsername(signUpRequestDto.getUsername())) {
			throw new ErrorException(ErrorCode.DUPLICATE_USERNAME_ERROR);
		}
		
		String encodedPassword = passwordEncoder.encode(signUpRequestDto.getPassword());
		
		User requestUser = User.of(signUpRequestDto.getEmail(), encodedPassword, signUpRequestDto.getUsername());
		
		User savedUser = userRepository.save(requestUser);
		
		return SignUpResponseDto.of(savedUser);
	}
	
	@Transactional(readOnly = true)
	public OtherUserResponseDto findOtherUser(Long userId) {
		User findUser = findActiveUserById(userId);
		
		return OtherUserResponseDto.of(findUser);
	}

    @Transactional(readOnly = true)
    public UserResponseDto findUser(Long userId) {
        User findUser = findActiveUserById(userId);

        return UserResponseDto.of(findUser);
    }

	@Transactional
	public void updateUser(Long loginUserId, UserUpdateRequestDto userUpdateRequestDto) {
		User findUser = findActiveUserById(loginUserId);

        String newUsername = userUpdateRequestDto.getUsername();
        // 기존 username과 다르면 db에서 중복된 username 있는지 검사
        if(!findUser.getUsername().equals(newUsername) && userRepository.existsByUsername(newUsername)) {
            throw new ErrorException(ErrorCode.DUPLICATE_USERNAME_ERROR);
        }

		findUser.update(newUsername);
	}

	@Transactional
	public void deleteUser(Long userId) {
		User findUser = findActiveUserById(userId);

		findUser.deleteUser();
	}

    private User findActiveUserById(Long userId) {
        User findUser = userRepository.findById(userId).orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));
        if(findUser.getDeleted()) {
            throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }

        return findUser;
    }
}
