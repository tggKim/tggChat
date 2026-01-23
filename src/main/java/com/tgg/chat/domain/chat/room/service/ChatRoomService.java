package com.tgg.chat.domain.chat.room.service;

import com.tgg.chat.domain.chat.room.dto.request.CreateDirectChatRoomRequestDto;
import com.tgg.chat.domain.chat.room.dto.request.CreateGroupChatRoomRequestDto;
import com.tgg.chat.domain.chat.room.dto.response.CreateDirectChatRoomResponseDto;
import com.tgg.chat.domain.chat.room.dto.response.CreateGroupChatRoomResponseDto;
import com.tgg.chat.domain.chat.room.entity.ChatRoom;
import com.tgg.chat.domain.chat.room.entity.ChatRoomUser;
import com.tgg.chat.domain.chat.room.enums.ChatRoomType;
import com.tgg.chat.domain.chat.room.enums.ChatRoomUserRole;
import com.tgg.chat.domain.chat.room.enums.ChatRoomUserStatus;
import com.tgg.chat.domain.chat.room.repository.ChatRoomMapper;
import com.tgg.chat.domain.chat.room.repository.ChatRoomRepository;
import com.tgg.chat.domain.chat.room.repository.ChatRoomUserMapper;
import com.tgg.chat.domain.chat.room.repository.ChatRoomUserRepository;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserMapper;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMapper chatRoomMapper;

    private final ChatRoomUserRepository chatRoomUserRepository;
    private final ChatRoomUserMapper chatRoomUserMapper;

    @Transactional
    public CreateDirectChatRoomResponseDto createDirectChatRoom(Long userId, CreateDirectChatRoomRequestDto requestDto) {

        Long friendUserId = requestDto.getFriendId();

        // 자신과 1대1 채팅방을 만들 수 없음
        if (userId.equals(friendUserId)) {
            throw new ErrorException(ErrorCode.CANNOT_CREATE_CHAT_ROOM_WITH_SELF);
        }

        // 1대1 채팅방은 유저간에 유일해야 하므로 유니크 제약 조건에 걸릴 수 있도록 아래처럼 계산이 필요
        Long maxUseId = Math.max(userId, friendUserId);
        Long minUserId = Math.min(userId, friendUserId);
        
        // 각 유저 조회
        User user1 = userRepository.findById(maxUseId).orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));
        User user2 = userRepository.findById(minUserId).orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));
        if(user1.getDeleted() || user2.getDeleted()) {
            throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }
        
        // 1대1 채팅방 이미 존재시 예외
        boolean directChatRoomExists = chatRoomMapper.existsDirectChatRoom(user1.getUserId(), user2.getUserId());
        if(directChatRoomExists) {
            throw new ErrorException(ErrorCode.DIRECT_CHAT_ROOM_ALREADY_EXISTS);
        }

        // 채팅방 생성
        ChatRoom chatRoom = ChatRoom.of(ChatRoomType.DIRECT, user1, user2);
        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);

        // ChatRoomUser 생성한다, 1대1 채팅방은 두 유저의 권한이 모두 MEMBER 이다.
        ChatRoomUser chatRoomUser1 = ChatRoomUser.of(user1, savedChatRoom, ChatRoomUserRole.MEMBER, ChatRoomUserStatus.ACTIVE);
        ChatRoomUser chatRoomUser2 = ChatRoomUser.of(user2, savedChatRoom, ChatRoomUserRole.MEMBER, ChatRoomUserStatus.ACTIVE);
        chatRoomUserRepository.save(chatRoomUser1);
        chatRoomUserRepository.save(chatRoomUser2);

        // 응답 DTO 생성
        CreateDirectChatRoomResponseDto responseDto = CreateDirectChatRoomResponseDto.of(savedChatRoom.getChatRoomId());

        return responseDto;

    }
    
    @Transactional
    public CreateGroupChatRoomResponseDto createGroupChatRoom(Long userId, CreateGroupChatRoomRequestDto requestDto) {
    	
    	// dto에서 필드 추출
    	List<Long> friendIds = requestDto.getFriendIds();
    	String chatRoomName = requestDto.getChatRoomName();
    	
    	// 자기 자신과 단체 채팅방을 만들 수 없다.
    	if(friendIds.contains(userId)) {
    		throw new ErrorException(ErrorCode.CANNOT_CREATE_CHAT_ROOM_WITH_SELF);
    	}
    	
    	// 중복을 제거한 리스트 생성
    	Set<Long> set = new HashSet<>(friendIds);
        set.add(userId);
        List<Long> memberIds = new ArrayList<>(set);
    	
        // 최소 인원 체크
        if (memberIds.size() < 2) {
            throw new ErrorException(ErrorCode.CHAT_ROOM_MEMBER_REQUIRED);
        }
    	
    	// 채팅방 생성 요청받은 유저들이 존재하는지 확인하고 검증
    	int existsCount = userMapper.countActiveUsersByIds(memberIds);
    	if(existsCount != memberIds.size()) {
    		throw new ErrorException(ErrorCode.USER_NOT_FOUND);
    	}
    	
    	// 채팅방 생성
    	ChatRoom chatRoom = ChatRoom.of(ChatRoomType.GROUP, chatRoomName);
    	ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);
    	
    	// ChatRoomUser들 생성, 로그인 유저는 방장 권한을 가진다.
    	List<ChatRoomUser> chatRoomUsers = memberIds.stream()
    			.map(id -> {
		    		User user = userRepository.getReferenceById(id);
		    		ChatRoomUserRole chatRoomUserRole = id.equals(userId) ? ChatRoomUserRole.OWNER : ChatRoomUserRole.MEMBER;
		    		return ChatRoomUser.of(user, savedChatRoom, chatRoomUserRole, ChatRoomUserStatus.ACTIVE);
    			})
    			.collect(Collectors.toList());
    	chatRoomUserRepository.saveAll(chatRoomUsers);
    	
    	// 응답 DTO 생성
    	return CreateGroupChatRoomResponseDto.of(savedChatRoom.getChatRoomId());
    	
    }

}
