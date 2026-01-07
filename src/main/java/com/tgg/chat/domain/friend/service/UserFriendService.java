package com.tgg.chat.domain.friend.service;

import com.tgg.chat.domain.friend.dto.request.CreateFriendRequestDto;
import com.tgg.chat.domain.friend.entity.UserFriend;
import com.tgg.chat.domain.friend.repository.UserFriendRepository;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserFriendService {

    private final UserRepository userRepository;
    private final UserFriendRepository userFriendRepository;

    @Transactional
    public void createFriend(CreateFriendRequestDto createFriendRequestDto, Long ownerId) {

        // 현재 로그인한 유저와, 친구로 추가하고자 하는 유저 엔티티 조회
        User owner = userRepository.findById(ownerId).orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));
        User friend = userRepository.findById(createFriendRequestDto.getFriendId()).orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));

        // UserFriend 엔티티 생성
        UserFriend userFriend = UserFriend.of(owner, friend);

        userFriendRepository.save(userFriend);

    }

}
