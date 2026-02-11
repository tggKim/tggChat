package com.tgg.chat.domain.chat.service;

import java.util.ArrayList;
import java.util.List;

import com.tgg.chat.domain.chat.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tgg.chat.common.redis.pubsub.ChatEvent;
import com.tgg.chat.common.redis.pubsub.RedisPublisher;
import com.tgg.chat.domain.chat.dto.request.ChatMessageRequest;
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
public class ChatService {
	
    private final RedisPublisher redisPublisher;
    private final ChatRoomUserRepository chatRoomUserRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomUserMapper chatRoomUserMapper;
    private final ChatRoomMapper chatRoomMapper;
    
    @Transactional
    public List<ChatEvent> saveMessage(
    		Long userId, 
    		Long chatRoomId, 
    		ChatMessageRequest message
    ) {

        // 채팅방의 존재여부와, 유저가 채팅방에 속한 유저인지 검증
        ChatRoomUser chatRoomUser = chatRoomUserRepository.findWithAllDetails(chatRoomId, userId)
                .orElseThrow(() -> new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
        
        // 요청한 유저가 채팅방에서 나간 상태면 예외
        if(chatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT) {
            throw new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }
        
        // ChatRoom, User 추출
        ChatRoom chatRoom = chatRoomUser.getChatRoom();
        User user = chatRoomUser.getUser();
        
        // 삭제된 유저인지 검증
        if(user.getDeleted()) {
        	throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }

        List<ChatEvent> chatEvents = new ArrayList<>();
        if(chatRoom.getChatRoomType() == ChatRoomType.DIRECT) {
        	// 채팅방별 ChatMessage의 최대 seq 조회
        	Long seq = chatRoomMapper.getLastSeqLock(chatRoomId);
        	
        	long addNumber = 1L;
        	
        	// 1대1 채팅방의 2명의 유저의 상태가 LEFT면 ACTIVE로 수정
        	List<ChatRoomUser> chatRoomUsers = chatRoomUserRepository.findByChatRoomIdWithUser(chatRoomId);
            for(ChatRoomUser findChatRoomUser : chatRoomUsers) {
                if(findChatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT) {
                	findChatRoomUser.setJoinedAt();
                	findChatRoomUser.setChatRoomUserStatus(ChatRoomUserStatus.ACTIVE);
                	findChatRoomUser.setLastReadSeq(seq); // 현재 보내지는 메시지 부터 읽지 않음 처리가 필요
                    findChatRoomUser.setHistoryStartSeq(seq); // 현재
                    
                    User findUser = findChatRoomUser.getUser();
                    
                    ChatMessage joinChatMessage = ChatMessage.of(chatRoom, findUser, seq + addNumber, findUser.getUsername() + "가 채팅에 참여했습니다.", ChatMessageType.JOIN_TEXT);
                    ChatMessage savedJoinChatMessage = chatMessageRepository.save(joinChatMessage);
                    
                    // 1대1 채팅방 멤버는 2명이므로 2로 고정
                    ChatEvent joinChatEvent = ChatEvent.of(
                            chatRoomId,
                            findChatRoomUser.getUser().getUserId(),
                            findUser.getUsername() + "가 채팅에 참여했습니다.",
                            seq + addNumber,
                            ChatMessageType.JOIN_TEXT,
                            savedJoinChatMessage.getCreatedAt(),
                            2L
                    );
                    
                    chatEvents.add(joinChatEvent);
                    
                    addNumber++;
                }
            }

            ChatMessage chatMessage = ChatMessage.of(chatRoom, user, seq + addNumber, message.getContent(), ChatMessageType.TEXT);
            ChatMessage savedChatMessage = chatMessageRepository.save(chatMessage);
            
            ChatEvent chatEvent = ChatEvent.of(
                    chatRoomId,
                    userId,
                    message.getContent(),
                    seq + addNumber,
                    ChatMessageType.TEXT,
                    savedChatMessage.getCreatedAt(),
                    2L
            );
            
            chatEvents.add(chatEvent);
        	
        	// chatRoom 의 lastSeq, lastMessagePreview, lastMessageAt 수정
        	chatRoomMapper.updateLastSeq(seq + addNumber, message.getContent(), savedChatMessage.getCreatedAt() ,chatRoomId);
        } else {
        	// 채팅방별 ChatMessage의 최대 seq 조회
        	Long seq = chatRoomMapper.getLastSeqLock(chatRoomId);
        	
        	// 메시지 db에 저장
        	ChatMessage chatMessage = ChatMessage.of(chatRoom, user, seq + 1, message.getContent(), ChatMessageType.TEXT);
            ChatMessage savedChatMessage = chatMessageRepository.save(chatMessage);

        	// 채팅방에 참여중인 인원들 수 조회
            Long memberCount = chatRoomUserMapper.getMemberCount(chatRoomId);

            ChatEvent chatEvent = ChatEvent.of(
                    chatRoomId,
                    userId,
                    message.getContent(),
                    seq + 1,
                    ChatMessageType.TEXT,
                    savedChatMessage.getCreatedAt(),
                    memberCount
            );
            
            chatEvents.add(chatEvent);
            
        	// chatRoom 의 lastSeq 증가
        	chatRoomMapper.updateLastSeq(seq + 1, message.getContent(), savedChatMessage.getCreatedAt(), chatRoomId);
        }

        return chatEvents;

    }
    
    public void sendMessage(
    		List<ChatEvent> chatEvents
    ) {
    	chatEvents.forEach(chatEvent -> {
    		redisPublisher.publishChatEvent(chatEvent);
    	});
    }
    
}
