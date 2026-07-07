package com.tgg.chat.domain.chat.service;

import java.util.ArrayList;
import java.util.List;

import com.tgg.chat.domain.chat.dto.query.ChatMessageListRowDto;
import com.tgg.chat.domain.chat.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tgg.chat.common.messaging.event.ChatEvent;
import com.tgg.chat.common.messaging.redis.RedisPublisher;
import com.tgg.chat.domain.chat.dto.request.ChatMessageRequest;
import com.tgg.chat.domain.chat.dto.response.ChatMessageListResponseDto;
import com.tgg.chat.domain.chat.entity.ChatMessage;
import com.tgg.chat.domain.chat.entity.ChatRoom;
import com.tgg.chat.domain.chat.entity.ChatRoomUser;
import com.tgg.chat.domain.chat.enums.ChatMessageType;
import com.tgg.chat.domain.chat.enums.ChatRoomType;
import com.tgg.chat.domain.chat.enums.ChatRoomUserStatus;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatMessageService {
	
    private final RedisPublisher redisPublisher;
    
    private final ChatRoomUserRepository chatRoomUserRepository;
    
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageMapper chatMessageMapper;

    private final ChatRoomRepository chatRoomRepository;
    
    @Transactional
    public List<ChatEvent> saveMessage(
    		Long userId,
    		Long chatRoomId,
    		ChatMessageRequest message
    ) {
        // 유저가 채팅방에 속한 유저인지 검증
        ChatRoomUser findChatRoomUser = chatRoomUserRepository.findByChatRoomIdAndUserIdWithUser(chatRoomId, userId)
                .orElseThrow(() -> new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
        
        // 요청한 유저가 채팅방에서 나간 상태면 예외
        if(findChatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT) {
            throw new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }
        
        // User 추출 후 삭제된 유저인지 검증
        User user = findChatRoomUser.getUser();
        if(user.getDeleted()) {
        	throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }

        // 채팅방별 ChatMessage의 최대 seq 조회
        ChatRoom lockedChatRoom = chatRoomRepository.findByIdForUpdate(chatRoomId)
                .orElseThrow(() -> new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
        Long seq = lockedChatRoom.getLastMessageSeq();

        List<ChatEvent> chatEvents = new ArrayList<>();
        List<Long> eventUserIds;
        if(lockedChatRoom.getChatRoomType() == ChatRoomType.DIRECT) {
            // 1대1 채팅방은 상대방이 LEFT 상태이면 ACTIVE 로 복귀, 삭제된 유저이면 제외
            List<ChatRoomUser> chatRoomUsers = chatRoomUserRepository.findByChatRoomIdWithUser(chatRoomId);
            for (ChatRoomUser chatRoomUser : chatRoomUsers) {
                if (chatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT) {
                    chatRoomUser.joinChatRoom(seq);
                }
            }

            eventUserIds = chatRoomUsers.stream().map(chatRoomUser -> chatRoomUser.getUser().getUserId()).toList();
        } else {
            eventUserIds = chatRoomUserRepository.findActiveUserIds(chatRoomId);
        }

        // 메시지 db에 저장
        ChatMessage chatMessage = ChatMessage.of(lockedChatRoom, user, seq + 1, message.getContent(), ChatMessageType.TEXT);
        ChatMessage savedChatMessage = chatMessageRepository.save(chatMessage);

        ChatEvent chatEvent = ChatEvent.of(
                chatRoomId,
                userId,
                user.getUsername(),
                null,
                null,
                message.getContent(),
                seq + 1,
                ChatMessageType.TEXT,
                savedChatMessage.getCreatedAt(),
                (long) eventUserIds.size(),
                eventUserIds
        );

        chatEvents.add(chatEvent);

        // chatRoom 의 lastSeq 증가
        lockedChatRoom.updateLastMessage(seq + 1, message.getContent(), savedChatMessage.getCreatedAt());

        return chatEvents;
    }
    
    public void sendMessage(
    		List<ChatEvent> chatEvents
    ) {
    	chatEvents.forEach(chatEvent -> {
    		redisPublisher.publishChatEvent(chatEvent);
    	});
    }
    
    @Transactional(readOnly = true)
    public List<ChatMessageListResponseDto> findChatMessages(Long userId, Long chatRoomId, Long offsetSeq) {
    	if(!chatRoomUserRepository.existsActiveMember(chatRoomId, userId)) {
            throw new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }

        if(offsetSeq == null) {
            offsetSeq = 0L;
        }

        List<ChatMessageListRowDto> chatMessages = chatMessageMapper.findChatMessages(userId, chatRoomId, offsetSeq);
    	return chatMessages.stream().map(ChatMessageListResponseDto::from).toList();
    }

}
