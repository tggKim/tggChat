package com.tgg.chat.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.tgg.chat.domain.chat.dto.request.ChatMessageRequest;
import com.tgg.chat.domain.chat.entity.ChatMessage;
import com.tgg.chat.domain.chat.entity.ChatRoom;
import com.tgg.chat.domain.chat.entity.ChatRoomUser;
import com.tgg.chat.domain.chat.enums.ChatRoomType;
import com.tgg.chat.domain.chat.enums.ChatRoomUserRole;
import com.tgg.chat.domain.chat.enums.ChatRoomUserStatus;
import com.tgg.chat.domain.chat.repository.ChatMessageRepository;
import com.tgg.chat.domain.chat.repository.ChatRoomRepository;
import com.tgg.chat.domain.chat.repository.ChatRoomUserRepository;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

@SpringBootTest(properties = {
        "SECRET=your_test_secret_key_value_here_at_least_32_chars",
        "spring.jpa.hibernate.ddl-auto=create"
})
@ActiveProfiles("local")
class ChatServiceLockTest {

    @Autowired
    private ChatService chatService;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRoomUserRepository chatRoomUserRepository;
    
    @Autowired
    private DataSource dataSource;
    
    private void printHikariStatus() {
        HikariDataSource conPool = (HikariDataSource) dataSource;
        HikariPoolMXBean poolProxy = conPool.getHikariPoolMXBean();

        System.out.printf("[Hikari Status] Active: %d, Idle: %d, ThreadsAwaiting: %d%n",
                poolProxy.getActiveConnections(),    // 현재 DB와 연결되어 일을 하고 있는 커넥션
                poolProxy.getIdleConnections(),      // 대기 중인 유휴 커넥션
                poolProxy.getThreadsAwaitingConnection()); // 커넥션이 없어서 줄 서서 기다리는 스레드 수
    }

    private Long user1Id;
    private Long user2Id;
    private Long chatRoomId;

    @BeforeEach
    void setUp() {
        User user1 = User.of("scie1", "123456", "user1");
        User user2 = User.of("scie2", "123456", "user2");

        userRepository.save(user1);
        userRepository.save(user2);

        ChatRoom chatRoom = ChatRoom.of(ChatRoomType.GROUP, "testChatRoom");
        chatRoomRepository.save(chatRoom);

        ChatRoomUser chatRoomUser1 = ChatRoomUser.of(user1, chatRoom, ChatRoomUserRole.MEMBER, ChatRoomUserStatus.ACTIVE);
        ChatRoomUser chatRoomUser2 = ChatRoomUser.of(user2, chatRoom, ChatRoomUserRole.MEMBER, ChatRoomUserStatus.ACTIVE);
        chatRoomUserRepository.save(chatRoomUser1);
        chatRoomUserRepository.save(chatRoomUser2);

        this.user1Id = user1.getUserId();
        this.user2Id = user2.getUserId();
        this.chatRoomId = chatRoom.getChatRoomId();
    }

    @Test
    @DisplayName("동시에 100개의 메시지를 보낼 때 seq 중복 발생 확인 (락 미사용)")
    void sendMessage_Concurrency_Test() throws InterruptedException {
//        // given
//        int totalRequests = 1000;
//        int threadPoolSize = 100; // 딱 2개의 스레드만 사용
//
//        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
//        CountDownLatch latch = new CountDownLatch(totalRequests);
//
//        AtomicInteger successCount = new AtomicInteger(0);
//        AtomicInteger failCount = new AtomicInteger(0);
//
//        ChatMessageRequest request = new ChatMessageRequest();
//        ReflectionTestUtils.setField(request, "content", "리플렉션으로 넣은 메시지");
//
//        // 모니터링 스레드 시작
//        Thread monitor = new Thread(() -> {
//            while (!Thread.currentThread().isInterrupted()) {
//                printHikariStatus();
//                try { Thread.sleep(100); } catch (InterruptedException e) { break; }
//            }
//        });
//        monitor.start();
//
//        // when
//        for (int i = 0; i < totalRequests; i++) {
//            final Long currentUserId = (i % 2 == 0) ? user1Id : user2Id; // 홀짝 번갈아가며 유저 할당
//
//            executorService.submit(() -> {
//                try {
//                    // 실제 비즈니스 로직 호출
//                    chatService.sendMessage(currentUserId, chatRoomId, request);
//                    successCount.incrementAndGet();
//                } catch (DataIntegrityViolationException e) {
//                    // DB 유니크 제약 조건(uk_chat_message_room_seq) 위반 시 발생
//                    failCount.incrementAndGet();
//                } catch (Exception e) {
//                    System.err.println("기타 에러: " + e.getMessage());
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        latch.await(); // 100개 작업이 모두 끝날 때까지 대기
//        executorService.shutdown();
//        monitor.interrupt();
//
//        // then
//        List<ChatMessage> allMessages = chatMessageRepository.findAll();
//
//        System.out.println("=== 테스트 결과 ===");
//        System.out.println("성공 횟수: " + successCount.get());
//        System.out.println("실패 횟수 (UK 위반): " + failCount.get());
//        System.out.println("DB 저장 데이터 수: " + allMessages.size());
//
//        // 락이 없으면 중복된 seq를 읽어 insert하려다 실패함
//        // 따라서 실패 횟수는 0보다 커야 하고, 성공 횟수는 100보다 작아야 함
//        assertThat(failCount.get()).isGreaterThan(0);
//        assertThat(successCount.get()).isLessThan(totalRequests);
//        assertThat(allMessages.size()).isEqualTo(successCount.get());
    }
}
