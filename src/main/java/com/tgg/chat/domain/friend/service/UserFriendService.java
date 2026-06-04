package com.tgg.chat.domain.friend.service;

import com.tgg.chat.domain.friend.dto.query.UserFriendRowDto;
import com.tgg.chat.domain.friend.dto.request.CreateFriendRequestDto;
import com.tgg.chat.domain.friend.dto.response.FriendListResponseDto;
import com.tgg.chat.domain.friend.entity.UserFriend;
import com.tgg.chat.domain.friend.repository.UserFriendMapper;
import com.tgg.chat.domain.friend.repository.UserFriendRepository;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserFriendService {

    private final UserRepository userRepository;
    private final UserFriendRepository userFriendRepository;
    private final UserFriendMapper userFriendMapper;

    @Transactional
    public void createFriend(Long loginUserId, CreateFriendRequestDto createFriendRequestDto) {
        // 현재 로그인한 유저와, 친구로 추가하고자 하는 유저 엔티티 조회
        User owner = userRepository.findById(loginUserId)
                .orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));
        if(owner.getDeleted()) {
            throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }

        User friend = userRepository.findByUsername(createFriendRequestDto.getUsername())
                .orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));
        if(friend.getDeleted()) {
        	throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }
        
        // 자기 자신은 친구로 추가할 수 없으므로 검증
        Long ownerId = owner.getUserId();
        Long friendId = friend.getUserId();
        if(ownerId.equals(friendId)) {
        	throw new ErrorException(ErrorCode.SELF_FRIEND_NOT_ALLOWED);
        }
        
        // 이미 친구로 등록이 되었는지 확인
        boolean isAlreadyFriend = userFriendRepository.existsByOwner_UserIdAndFriend_UserId(ownerId, friendId);
        if(isAlreadyFriend) {
        	throw new ErrorException(ErrorCode.ALREADY_FRIEND);
        }
        
        // UserFriend 엔티티 생성
        UserFriend userFriend = UserFriend.of(owner, friend);

        userFriendRepository.save(userFriend);
    }
    
    @Transactional(readOnly = true)
    public List<FriendListResponseDto> findFriendListByOwnerId(Long ownerId) {
    	// 유저의 존재여부를 검증
		User findUser = userRepository.findById(ownerId).orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));
		if(findUser.getDeleted()) {
			throw new ErrorException(ErrorCode.USER_NOT_FOUND);
		}
    	
		// 친구목록 조회
		List<UserFriendRowDto> friendList = userFriendMapper.findFriendListByOwnerId(ownerId);
		
		// 응답 DTO로 변환
		return friendList.stream().map(userFriendRowDto -> {
                    Long friendId = userFriendRowDto.getFriendId();
                    String friendUsername = userFriendRowDto.getFriendUsername();
                    return FriendListResponseDto.of(friendId, friendUsername);
                })
                .collect(Collectors.toList());
    }

}
