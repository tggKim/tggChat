package com.tgg.chat.domain.friend.service;

import com.tgg.chat.domain.friend.dto.request.CreateFriendRequestDto;
import com.tgg.chat.domain.friend.entity.UserFriend;
import com.tgg.chat.domain.friend.repository.UserFriendMapper;
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
    private final UserFriendMapper userFriendMapper;

    @Transactional
    public void createFriend(Long ownerId, CreateFriendRequestDto createFriendRequestDto) {

        // 현재 로그인한 유저와, 친구로 추가하고자 하는 유저 엔티티 조회
        User owner = userRepository.findById(ownerId).orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));
        User friend = userRepository.findById(createFriendRequestDto.getFriendId()).orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));

        // 이미 친구로 등록이 되었는지 확인
        boolean isAlreadyFriend = userFriendMapper.existsByOwnerIdAndFriendId(owner.getUserId(), friend.getUserId());
        if(isAlreadyFriend) {
        	throw new ErrorException(ErrorCode.ALREADY_FRIEND);
        }
        
        // UserFriend 엔티티 생성
        UserFriend userFriend = UserFriend.of(owner, friend);

        userFriendRepository.save(userFriend);

    }

}
