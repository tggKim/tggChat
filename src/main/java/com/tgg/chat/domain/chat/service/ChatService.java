package com.tgg.chat.domain.chat.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tgg.chat.common.redis.pubsub.ChatEvent;
import com.tgg.chat.common.redis.pubsub.RedisPublisher;
import com.tgg.chat.domain.chat.dto.request.ChatMessageRequest;
import com.tgg.chat.domain.chat.repository.ChatMessageMapper;
import com.tgg.chat.domain.chat.repository.ChatMessageRepository;
import com.tgg.chat.domain.chat.repository.ChatRoomMapper;
import com.tgg.chat.domain.chat.entity.ChatMessage;
import com.tgg.chat.domain.chat.entity.ChatRoom;
import com.tgg.chat.domain.chat.entity.ChatRoomUser;
import com.tgg.chat.domain.chat.enums.ChatMessageType;
import com.tgg.chat.domain.chat.enums.ChatRoomType;
import com.tgg.chat.domain.chat.enums.ChatRoomUserStatus;
import com.tgg.chat.domain.chat.repository.ChatRoomUserRepository;
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
    private final ChatMessageMapper chatMessageMapper;
    private final ChatRoomMapper chatRoomMapper;
    
    @Transactional
    public void sendMessage(
    		Long userId, 
    		Long chatRoomId, 
    		ChatMessageRequest message
    ) {

        // 채팅방의 존재여부와, 유저가 채팅방에 속한 유저인지 검증
        ChatRoomUser chatRoomUser = chatRoomUserRepository.findWithAllDetails(chatRoomId, userId)
                .orElseThrow(() -> new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
        
        // ChatRoom, User 추출
        ChatRoom chatRoom = chatRoomUser.getChatRoom();
        User user = chatRoomUser.getUser();
        
        // 삭제된 유저인지 검증
        if(user.getDeleted()) {
        	throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }
        
        if(chatRoom.getChatRoomType() == ChatRoomType.DIRECT) {
        	
        	// 채팅방별 ChatMessage의 최대 seq 조회
        	//Long seq = chatRoomMapper.getLastSeq(chatRoomId);
        	Long seq = chatRoomMapper.getLastSeqLock(chatRoomId);
        	
        	// chatRoom 의 lastSeq 증가
        	chatRoomMapper.updateLastSeq(seq + 1, chatRoomId);
        	
        	// 메시지 db에 저장
        	ChatMessage chatMessage = ChatMessage.of(chatRoom, user, seq + 1, message.getContent(), ChatMessageType.TEXT);
        	chatMessageRepository.save(chatMessage);
        	
        	// 1대1 채팅방의 2명의 유저의 상태가 LEFT면 ACTIVE로 수정
        	List<ChatRoomUser> chatRoomUsers = chatRoomUserRepository.findByChatRoom(chatRoom);
            chatRoomUsers.forEach(findChatRoomUser -> {
                if(findChatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT) {
                	findChatRoomUser.setJoinedAt();
                	findChatRoomUser.setChatRoomUserStatus(ChatRoomUserStatus.ACTIVE);
                	findChatRoomUser.setLastReadSeq(seq + 1);
                }
            });
        	
        } else {
        	
        	// 단체 채팅방이면 요청한 유저의 상태가 LEFT면 예외
        	if(chatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT) {
        		throw new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
            }
        	
        	// 채팅방별 ChatMessage의 최대 seq 조회
        	//Long seq = chatRoomMapper.getLastSeq(chatRoomId);
        	Long seq = chatRoomMapper.getLastSeqLock(chatRoomId);
        	
        	// chatRoom 의 lastSeq 증가
        	chatRoomMapper.updateLastSeq(seq + 1, chatRoomId);
        	
        	// 메시지 db에 저장
        	ChatMessage chatMessage = ChatMessage.of(chatRoom, user, seq + 1, message.getContent(), ChatMessageType.TEXT);
        	chatMessageRepository.save(chatMessage);
        	
        }

        ChatEvent chatEvent = ChatEvent.of(chatRoomId, userId, message.getContent());
		redisPublisher.publishChatEvent(chatEvent);
		
    }
    
}
