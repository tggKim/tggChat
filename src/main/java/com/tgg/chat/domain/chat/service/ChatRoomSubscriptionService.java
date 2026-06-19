package com.tgg.chat.domain.chat.service;

import com.tgg.chat.domain.chat.repository.ChatRoomUserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatRoomSubscriptionService {
    private final ChatRoomUserRepository chatRoomUserRepository;

    public boolean validateCanSubscribe(Long userId, Long chatRoomId) {
        return chatRoomUserRepository.existsActiveMember(chatRoomId, userId);
    }
}
