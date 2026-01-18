package com.tgg.chat.domain.chat.room.service;

import com.tgg.chat.domain.chat.room.dto.request.CreateDirectChatRoomRequestDto;
import com.tgg.chat.domain.chat.room.dto.response.CreateDirectChatRoomResponseDto;
import com.tgg.chat.domain.chat.room.entity.ChatRoom;
import com.tgg.chat.domain.chat.room.enums.ChatRoomType;
import com.tgg.chat.domain.chat.room.repository.ChatRoomMapper;
import com.tgg.chat.domain.chat.room.repository.ChatRoomRepository;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMapper chatRoomMapper;

    @Transactional
    public CreateDirectChatRoomResponseDto createDirectChatRoom(Long userId, CreateDirectChatRoomRequestDto requestDto) {

        Long friendUserId = requestDto.getFriendId();

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

        // 응답 DTO 생성
        CreateDirectChatRoomResponseDto responseDto = CreateDirectChatRoomResponseDto.of(savedChatRoom.getChatRoomId());

        return responseDto;

    }

}
