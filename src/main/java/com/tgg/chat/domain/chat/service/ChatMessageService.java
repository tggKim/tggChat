package com.tgg.chat.domain.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.tgg.chat.common.messaging.event.ChatRoomListEvent;
import com.tgg.chat.domain.chat.dto.internal.SaveChatMessageResult;
import com.tgg.chat.domain.chat.dto.query.ChatMessageListRowDto;
import com.tgg.chat.domain.chat.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tgg.chat.common.messaging.event.ChatEvent;
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
    private final ChatRoomUserRepository chatRoomUserRepository;
    
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageMapper chatMessageMapper;
    
    @Transactional
    public SaveChatMessageResult saveMessage(
    		Long userId,
    		Long chatRoomId,
    		ChatMessageRequest message
    ) {
        // 유저가 채팅방에 속한 유저인지 검증
        ChatRoomUser findChatRoomUser = chatRoomUserRepository.findByChatRoomIdAndUserIdWithChatRoomAndUser(chatRoomId, userId)
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

        // ChatRoom 추출
        ChatRoom findChatRoom = findChatRoomUser.getChatRoom();

        // 메시지 db에 저장
        ChatMessage chatMessage = ChatMessage.of(findChatRoom, user, message.getContent(), ChatMessageType.TEXT);
        ChatMessage savedChatMessage = chatMessageRepository.save(chatMessage);

        List<ChatEvent> chatEvents = new ArrayList<>();
        List<Long> eventUserIds;
        List<ChatRoomListEvent> chatRoomListEvents = new ArrayList<>();
        // 1대1 채팅방은 상대방이 LEFT 상태이면 ACTIVE 로 복귀, 삭제된 유저이면 제외
        if(findChatRoom.getChatRoomType() == ChatRoomType.DIRECT) {
            List<ChatRoomUser> chatRoomUsers = chatRoomUserRepository.findByChatRoomIdWithUser(chatRoomId);
            Optional<ChatRoomUser> opponentOptional = chatRoomUsers.stream()
                    .filter(chatRoomUser -> !userId.equals(chatRoomUser.getUser().getUserId()))
                    .findFirst();

            // 상대 유저가 delete 된 상태라면 거치치 않는다
            if (opponentOptional.isPresent()) {
                ChatRoomUser opponent = opponentOptional.get();

                if (opponent.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT) {
                    opponent.joinChatRoom(savedChatMessage.getChatMessageId());

                    chatRoomListEvents.add(ChatRoomListEvent.roomAdded(
                            chatRoomId,
                            ChatRoomType.DIRECT,
                            opponent.getUser().getUserId(),
                            user.getUsername(),
                            2L,
                            user.getProfileImageKey() == null ? List.of() : List.of(user.getProfileImageKey())
                    ));
                }
            }

            eventUserIds = chatRoomUsers.stream().map(chatRoomUser -> chatRoomUser.getUser().getUserId()).toList();
        } else {
            eventUserIds = chatRoomUserRepository.findActiveUserIds(chatRoomId);
        }

        ChatEvent chatEvent = ChatEvent.of(
                chatRoomId,
                userId,
                user.getUsername(),
                null,
                null,
                message.getContent(),
                savedChatMessage.getChatMessageId(),
                ChatMessageType.TEXT,
                savedChatMessage.getCreatedAt(),
                (long) eventUserIds.size(),
                eventUserIds
        );

        chatEvents.add(chatEvent);

        return SaveChatMessageResult.of(chatEvents, chatRoomListEvents);
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
