package com.tgg.chat.domain.chat.room.service;

import com.tgg.chat.domain.chat.room.repository.ChatRoomMapper;
import com.tgg.chat.domain.chat.room.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMapper chatRoomMapper;

}
