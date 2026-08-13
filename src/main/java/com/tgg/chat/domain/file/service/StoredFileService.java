package com.tgg.chat.domain.file.service;

import com.tgg.chat.common.messaging.event.*;
import com.tgg.chat.common.security.jwt.JwtUtils;
import com.tgg.chat.domain.chat.entity.ChatMessage;
import com.tgg.chat.domain.chat.entity.ChatRoom;
import com.tgg.chat.domain.chat.entity.ChatRoomUser;
import com.tgg.chat.domain.chat.enums.ChatMessageType;
import com.tgg.chat.domain.chat.enums.ChatRoomType;
import com.tgg.chat.domain.chat.enums.ChatRoomUserStatus;
import com.tgg.chat.domain.chat.repository.ChatMessageRepository;
import com.tgg.chat.domain.chat.repository.ChatRoomUserRepository;
import com.tgg.chat.domain.file.dto.internal.FindMessageFileResult;
import com.tgg.chat.domain.file.dto.internal.FindUserImageResult;
import com.tgg.chat.domain.file.dto.internal.SaveMessageFileResult;
import com.tgg.chat.domain.file.entity.StoredFile;
import com.tgg.chat.domain.file.enums.FileCategory;
import com.tgg.chat.domain.file.enums.StoredFileVariant;
import com.tgg.chat.domain.file.repository.StoredFileRepository;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Service
public class StoredFileService {
    private final UserRepository userRepository;
    private final StoredFileRepository storedFileRepository;
    private final ChatRoomUserRepository chatRoomUserRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final JwtUtils jwtUtils;

    private final Path fileRootPath;

    private static final long MAX_TOTAL_FILE_SIZE = 3L * 1024 * 1024 * 1024;

    private static final Set<String> ALLOWED_IMAGE_FORMATS = Set.of(
            "jpeg",
            "png",
            "gif",
            "webp"
    );

    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    private static final Set<String> VIDEO_CONTENT_TYPES = Set.of(
            "video/mp4",
            "application/mp4",
            "video/x-m4v",
            "video/quicktime",
            "application/quicktime",
            "video/webm"
    );

    public StoredFileService(
            @Value("${file_root_path}") String fileRootPath,
            StoredFileRepository storedFileRepository,
            UserRepository userRepository,
            ChatRoomUserRepository chatRoomUserRepository,
            ChatMessageRepository chatMessageRepository,
            JwtUtils jwtUtils
    ) {
        this.storedFileRepository = storedFileRepository;
        this.fileRootPath = Path.of(fileRootPath);
        this.userRepository = userRepository;
        this.chatRoomUserRepository = chatRoomUserRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.jwtUtils = jwtUtils;
    }

    @Transactional
    public UserMetadataEvent saveUserProfile(Long userId, MultipartFile userProfileImage) {
        User findUser = userRepository.findById(userId).orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));
        if(findUser.getDeleted()) {
            throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }

        if(userProfileImage == null || userProfileImage.isEmpty()) {
            throw new ErrorException(ErrorCode.PROFILE_FILE_REQUIRED);
        }

        // 기존 저장된 파일들이 있다면 삭제하기 위해 미리 key 추출
        String previousProfileImageKey = findUser.getProfileImageKey();

        // 새로운 key를 생성 후 유저의 정보 업데이트
        String newProfileImageKey = "user:" + userId + ":" + UUID.randomUUID();
        findUser.updateProfileImageKey(newProfileImageKey);

        // 요청을 받은 파일의 타입을 검사하고 알아낸다, 이미지의 첫번째 프레임을 썸네일로 사용한다
        String imageFormat;
        BufferedImage firstFrame;
        try(
                InputStream inputStream = userProfileImage.getInputStream();
                ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream)
        ) {
            // 파일을 처리할 수 있는 ImageReader가 있는지 검증
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if(!readers.hasNext()) {
                throw new ErrorException(ErrorCode.UNSUPPORTED_IMAGE_FORMAT);
            }

            ImageReader reader = readers.next();

            try {
                reader.setInput(imageInputStream);
                imageFormat = reader.getFormatName().toLowerCase(Locale.ROOT);

                // 파일의 포맷을 가져온뒤 jpeg, png, gif, webp 중 하나인지 검증
                if (!ALLOWED_IMAGE_FORMATS.contains(imageFormat)) {
                    throw new ErrorException(ErrorCode.UNSUPPORTED_IMAGE_FORMAT);
                }

                // GIF와 WebP가 애니메이션이어도 첫 프레임만 읽는다.
                firstFrame = reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String originalExtension = imageFormat.equals("jpeg") ? ".jpg" : "." + imageFormat;
        String originalContentType = "image/" + imageFormat;

        String imageName = UUID.randomUUID() + originalExtension;
        String thumbnailImageName = UUID.randomUUID() + ".jpg";
        Path imagePath = fileRootPath.resolve(imageName);
        Path thumbnailImagePath = fileRootPath.resolve(thumbnailImageName);
        long thumbnailFileSize;
        try {
            userProfileImage.transferTo(imagePath);

            Thumbnails.of(firstFrame)
                    .size(320, 320)
                    .keepAspectRatio(true)
                    .outputFormat("jpg")
                    .outputQuality(0.9)
                    .toFile(thumbnailImagePath.toFile());

            thumbnailFileSize = Files.size(thumbnailImagePath);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(imagePath);
            } catch (IOException cleanupException) {
                log.warn("프로필 이미지 저장 실패 후 원본 파일 정리 실패: {}", imagePath, cleanupException);
            }

            try {
                Files.deleteIfExists(thumbnailImagePath);
            } catch (IOException cleanupException) {
                log.warn("프로필 이미지 저장 실패 후 썸네일 파일 정리 실패: {}", thumbnailImagePath, cleanupException);
            }

            throw new RuntimeException(e);
        }

        // 원본 이미지에 대한 StoredFile 저장
        storedFileRepository.save(
                StoredFile.of(
                        newProfileImageKey,
                        imageName,
                        userProfileImage.getOriginalFilename(),
                        originalContentType,
                        userProfileImage.getSize(),
                        1,
                        StoredFileVariant.ORIGINAL,
                        FileCategory.IMAGE
                )
        );

        // 썸네일 이미지에 대한 StoredFile 저장
        storedFileRepository.save(
                StoredFile.of(
                        newProfileImageKey,
                        thumbnailImageName,
                        userProfileImage.getOriginalFilename(),
                        "image/jpeg",
                        thumbnailFileSize,
                        1,
                        StoredFileVariant.THUMBNAIL,
                        FileCategory.IMAGE
                )
        );

        // 수신자 조회 실패 시 기존 파일이 먼저 삭제되지 않도록 삭제 전에 조회
        List<Long> eventUserIds = userRepository.findAllInteractingUserIds(userId);
        
        // 기존의 파일이 있었다면 실제파일과 StoredFile 모두 삭제
        if(previousProfileImageKey != null) {
            List<StoredFile> previousStoredFiles = storedFileRepository.findAllByFileKey(previousProfileImageKey);

            previousStoredFiles.forEach(
                    previousStoredFile -> {
                        Path deletePath = fileRootPath.resolve(previousStoredFile.getStoredFileName());
                        try {
                            Files.deleteIfExists(deletePath);
                        } catch (IOException e) {
                            // 기존파일 삭제시 에러가 난다고 해서 새로운 파일 저장에 영향을 주면 안된다.
                            log.warn("기존 프로필 파일 삭제 실패: {}", deletePath, e);
                        }
                    }
            );

            storedFileRepository.deleteAll(previousStoredFiles);
        }
        
        return UserMetadataEvent.userProfileImageUpdated(userId, newProfileImageKey, eventUserIds);
    }

    @Transactional(readOnly = true)
    public FileSystemResource findUserThumbnail(String fileKey) {
        StoredFile findStoredFile = storedFileRepository.findByFileKeyAndStoredFileVariant(fileKey, StoredFileVariant.THUMBNAIL).orElseThrow(() -> new ErrorException(ErrorCode.STORED_FILE_NOT_FOUND));
        String savedFileName = findStoredFile.getStoredFileName();

        Path thumbnailImagePath = fileRootPath.resolve(savedFileName);
        if(!Files.isRegularFile(thumbnailImagePath)) {
            throw new ErrorException(ErrorCode.STORED_FILE_NOT_FOUND);
        }

        return new FileSystemResource(thumbnailImagePath);
    }

    @Transactional(readOnly = true)
    public FindUserImageResult findUserImage(String fileKey) {
        StoredFile findStoredFile = storedFileRepository.findByFileKeyAndStoredFileVariant(fileKey, StoredFileVariant.ORIGINAL).orElseThrow(() -> new ErrorException(ErrorCode.STORED_FILE_NOT_FOUND));
        String savedFileName = findStoredFile.getStoredFileName();

        Path imagePath = fileRootPath.resolve(savedFileName);
        if(!Files.isRegularFile(imagePath)) {
            throw new ErrorException(ErrorCode.STORED_FILE_NOT_FOUND);
        }

        FileSystemResource fileSystemResource = new FileSystemResource(imagePath);

        return FindUserImageResult.of(fileSystemResource, findStoredFile.getContentType());
    }

    @Transactional
    public SaveMessageFileResult saveMessageFile(Long userId, Long chatRoomId, List<MultipartFile> files) {
        // 유저가 채팅방에 속한 유저인지 검증
        ChatRoomUser findChatRoomUser = chatRoomUserRepository.findByChatRoomIdAndUserIdWithChatRoomAndUser(chatRoomId, userId)
                .orElseThrow(() -> new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));

        // 요청한 유저가 채팅방에서 나간 상태면 예외
        if (findChatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT) {
            throw new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }

        // User 추출 후 삭제된 유저인지 검증
        User findUser = findChatRoomUser.getUser();
        if (findUser.getDeleted()) {
            throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }

        // 비어있지 않은 유효한 파일을 한번에 1개 이상 30개 이하 전송 가능
        if(files == null
                || files.isEmpty()
                || files.stream().anyMatch(file -> file == null || file.isEmpty())) {
            throw new ErrorException(ErrorCode.CHAT_FILE_REQUIRED);
        }
        if(files.size() > 30) {
            throw new ErrorException(ErrorCode.CHAT_FILE_COUNT_LIMIT_EXCEEDED);
        }

        // 총 파일의 크키는 3GB 이하
        long totalSize = files.stream().mapToLong(file -> file.getSize()).sum();
        if(totalSize > MAX_TOTAL_FILE_SIZE) {
            throw new ErrorException(ErrorCode.CHAT_FILE_TOTAL_SIZE_LIMIT_EXCEEDED);
        }

        // ChatMessage 저장
        ChatMessage savedChatMessage = chatMessageRepository.save(
                ChatMessage.of(
                        findChatRoomUser.getChatRoom(),
                        findUser,
                        "파일 " + files.size() + "개",
                        ChatMessageType.FILE
                )
        );

        List<Path> createdFilePaths = new ArrayList<>();
        try {
            Tika tika = new Tika();

            int fileOrder = 1;
            String fileKey = "chat-message:" + savedChatMessage.getChatMessageId();
            List<StoredFile> storedFiles = new ArrayList<>();
            for(MultipartFile file : files) {
                // 파일의 타입을 추론
                String detectedContentType;
                try(InputStream inputStream = file.getInputStream()) {
                    detectedContentType = tika.detect(inputStream);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                FileCategory fileCategory;
                if(IMAGE_CONTENT_TYPES.contains(detectedContentType)) {
                    fileCategory = FileCategory.IMAGE;
                } else if(VIDEO_CONTENT_TYPES.contains(detectedContentType)) {
                    fileCategory = FileCategory.VIDEO;
                } else {
                    fileCategory = FileCategory.FILE;
                }

                // tika로 추론한 타입에 따라 이미지, 동영상, 기타 파일처리로 분류한다
                if(fileCategory == FileCategory.IMAGE) {
                    // 요청을 받은 파일의 타입을 검사하고 알아낸다, 이미지의 첫번째 프레임을 썸네일로 사용한다
                    String imageFormat;
                    BufferedImage firstFrame;
                    try(
                            InputStream inputStream = file.getInputStream();
                            ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream)
                    ) {
                        // 파일을 처리할 수 있는 ImageReader가 있는지 검증
                        Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
                        if(!readers.hasNext()) {
                            throw new ErrorException(ErrorCode.UNSUPPORTED_IMAGE_FORMAT);
                        }

                        ImageReader reader = readers.next();

                        try {
                            reader.setInput(imageInputStream);
                            imageFormat = reader.getFormatName().toLowerCase(Locale.ROOT);

                            // 파일의 포맷을 가져온뒤 jpeg, png, gif, webp 중 하나인지 검증
                            if (!ALLOWED_IMAGE_FORMATS.contains(imageFormat)) {
                                throw new ErrorException(ErrorCode.UNSUPPORTED_IMAGE_FORMAT);
                            }

                            // GIF와 WebP가 애니메이션이어도 첫 프레임만 읽는다.
                            firstFrame = reader.read(0);
                        } finally {
                            reader.dispose();
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    String originalExtension = imageFormat.equals("jpeg") ? ".jpg" : "." + imageFormat;
                    String originalContentType = "image/" + imageFormat;

                    String imageName = UUID.randomUUID() + originalExtension;
                    String thumbnailImageName = UUID.randomUUID() + ".jpg";
                    Path imagePath = fileRootPath.resolve(imageName);
                    Path thumbnailImagePath = fileRootPath.resolve(thumbnailImageName);
                    createdFilePaths.add(imagePath);
                    createdFilePaths.add(thumbnailImagePath);
                    long thumbnailFileSize;
                    try {
                        file.transferTo(imagePath);

                        Thumbnails.of(firstFrame)
                                .size(320, 320)
                                .keepAspectRatio(true)
                                .outputFormat("jpg")
                                .outputQuality(0.9)
                                .toFile(thumbnailImagePath.toFile());

                        thumbnailFileSize = Files.size(thumbnailImagePath);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    // 원본 이미지에 대한 StoredFile 생성
                    storedFiles.add(
                            StoredFile.of(
                                    fileKey,
                                    imageName,
                                    file.getOriginalFilename(),
                                    originalContentType,
                                    file.getSize(),
                                    fileOrder,
                                    StoredFileVariant.ORIGINAL,
                                    FileCategory.IMAGE
                            )
                    );

                    // 썸네일 이미지에 대한 StoredFile 생성
                    storedFiles.add(
                            StoredFile.of(
                                    fileKey,
                                    thumbnailImageName,
                                    file.getOriginalFilename(),
                                    "image/jpeg",
                                    thumbnailFileSize,
                                    fileOrder,
                                    StoredFileVariant.THUMBNAIL,
                                    FileCategory.IMAGE
                            )
                    );
                } else if (fileCategory == FileCategory.VIDEO) {
                    String originalExtension;
                    String originalContentType;
                    if (detectedContentType.equals("video/mp4") || detectedContentType.equals("application/mp4") || detectedContentType.equals("video/x-m4v")) {
                        originalExtension = ".mp4";
                        originalContentType = "video/mp4";
                    } else if (detectedContentType.equals("video/quicktime") || detectedContentType.equals("application/quicktime")) {
                        originalExtension = ".mov";
                        originalContentType = "video/quicktime";
                    } else {
                        originalExtension = ".webm";
                        originalContentType = "video/webm";
                    }

                    String videoName = UUID.randomUUID() + originalExtension;
                    String videoThumbnailName = UUID.randomUUID() + ".jpg";
                    Path videoPath = fileRootPath.resolve(videoName);
                    Path videoThumbNailPath = fileRootPath.resolve(videoThumbnailName);
                    createdFilePaths.add(videoPath);
                    createdFilePaths.add(videoThumbNailPath);
                    long thumbnailFileSize;
                    try {
                        file.transferTo(videoPath);

                        FFmpegBuilder builder = new FFmpegBuilder()
                                .setInput(videoPath)
                                .done()
                                .overrideOutputFiles(true)
                                .addOutput(videoThumbNailPath)
                                .setFrames(1)
                                .disableAudio()
                                .setVideoCodec("mjpeg")
                                .setVideoFilter(
                                        "scale=320:320:force_original_aspect_ratio=decrease"
                                )
                                .setFormat("image2")
                                .done();

                        FFmpeg ffmpeg = new FFmpeg("ffmpeg");
                        ffmpeg.run(builder);

                        thumbnailFileSize = Files.size(videoThumbNailPath);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    storedFiles.add(
                            StoredFile.of(
                                    fileKey,
                                    videoName,
                                    file.getOriginalFilename(),
                                    originalContentType,
                                    file.getSize(),
                                    fileOrder,
                                    StoredFileVariant.ORIGINAL,
                                    FileCategory.VIDEO
                            )
                    );

                    storedFiles.add(
                            StoredFile.of(
                                    fileKey,
                                    videoThumbnailName,
                                    file.getOriginalFilename(),
                                    "image/jpeg",
                                    thumbnailFileSize,
                                    fileOrder,
                                    StoredFileVariant.THUMBNAIL,
                                    FileCategory.VIDEO
                            )
                    );
                } else {
                    String fileName = UUID.randomUUID().toString();
                    Path filePath = fileRootPath.resolve(fileName);
                    createdFilePaths.add(filePath);

                    try {
                        file.transferTo(filePath);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    storedFiles.add(
                            StoredFile.of(
                                    fileKey,
                                    fileName,
                                    file.getOriginalFilename(),
                                    detectedContentType,
                                    file.getSize(),
                                    fileOrder,
                                    StoredFileVariant.ORIGINAL,
                                    FileCategory.FILE
                            )
                    );
                }

                fileOrder++;
            }

            storedFileRepository.saveAll(storedFiles);

            List<ChatEventFile> chatEventFiles = storedFiles.stream()
                    .filter(file -> file.getStoredFileVariant() == StoredFileVariant.ORIGINAL)
                    .map(file -> {
                        return ChatEventFile.of(
                                file.getFileOrder(),
                                file.getFileCategory(),
                                file.getOriginalFileName(),
                                file.getContentType(),
                                file.getFileSize()
                        );
                    })
                    .toList();

            List<Long> eventUserIds;
            List<ChatRoomListEvent> chatRoomListEvents = new ArrayList<>();
            ChatRoom findChatRoom = findChatRoomUser.getChatRoom();
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

                        List<ChatRoomPreviewUser> chatRoomPreviewUsers = List.of(
                                ChatRoomPreviewUser.of(
                                        findUser.getUserId(),
                                        findUser.getUsername(),
                                        findUser.getProfileImageKey()
                                )
                        );

                        chatRoomListEvents.add(ChatRoomListEvent.roomAdded(
                                chatRoomId,
                                ChatRoomType.DIRECT,
                                opponent.getUser().getUserId(),
                                findChatRoom.getRoomName(),
                                opponent.getCustomRoomName(),
                                opponent.getChatRoomUserRole(),
                                2L,
                                chatRoomPreviewUsers,
                                savedChatMessage.getContent(),
                                savedChatMessage.getChatMessageId(),
                                savedChatMessage.getCreatedAt(),
                                savedChatMessage.getChatMessageId(),
                                1L
                        ));

                        eventUserIds = List.of(userId);
                    } else {
                        eventUserIds = chatRoomUsers.stream().map(chatRoomUser -> chatRoomUser.getUser().getUserId()).toList();
                    }
                } else {
                    eventUserIds = chatRoomUsers.stream().map(chatRoomUser -> chatRoomUser.getUser().getUserId()).toList();
                }
            } else {
                eventUserIds = chatRoomUserRepository.findActiveUserIds(chatRoomId);
            }

            ChatEvent chatEvent = ChatEvent.messageSent(
                findChatRoomUser.getChatRoom().getChatRoomId(),
                userId,
                findUser.getUsername(),
                findUser.getProfileImageKey(),
                chatEventFiles,
                savedChatMessage.getContent(),
                savedChatMessage.getChatMessageId(),
                savedChatMessage.getChatMessageType(),
                savedChatMessage.getCreatedAt(),
                eventUserIds
            );

            return SaveMessageFileResult.of(
                    chatRoomListEvents,
                    chatEvent
            );
        } catch (Exception e) {
            for(Path createdFilePath : createdFilePaths) {
                try {
                    Files.deleteIfExists(createdFilePath);
                } catch (IOException cleanupException) {
                    log.warn(
                            "채팅 파일 저장 실패 후 생성 파일 정리 실패: {}",
                            createdFilePath,
                            cleanupException
                    );
                }
            }

            throw e;
        }
    }

    // 메시지 파일 조회
    public FindMessageFileResult findMessageFile(
            Long chatMessageId,
            Integer fileOrder,
            StoredFileVariant storedFileVariant,
            String mediaToken
    ) {
        // 미디어 토큰 파싱 시도하고 미디어 토큰인지 확인
        Claims claims = jwtUtils.parseClaims(mediaToken);
        if(!jwtUtils.isMediaToken(claims)) {
            throw new ErrorException(ErrorCode.JWT_INVALID_MEDIA_TOKEN);
        }

        // 유저의 삭제 여부 검증
        Long userId = Long.parseLong(claims.getSubject());
        User findUser = userRepository.findById(userId).orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));
        if(findUser.getDeleted()) {
            throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }

        // 메시지의 존재 여부 검증
        ChatMessage findChatMessage = chatMessageRepository.findByChatMessageIdWithChatRoom(chatMessageId).orElseThrow(() -> new ErrorException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));

        // 요청 유저가 메시지의 채팅방에 참여중인지 검증
        Long findChatRoomId = findChatMessage.getChatRoom().getChatRoomId();
        ChatRoomUser findChatRoomUser = chatRoomUserRepository.findByChatRoomIdAndUserId(findChatRoomId, userId).orElseThrow(() -> new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
        if(findChatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT) {
            throw new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }

        // 요청 유저가 볼 수 있는 메시지 범위인지 검증
        if(findChatRoomUser.getVisibleStartMessageId() > findChatMessage.getChatMessageId()) {
            throw new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }

        // StoredFile 조회
        String fileKey = "chat-message:" + findChatMessage.getChatMessageId();
        StoredFile findStoredFile = storedFileRepository.findByFileKeyAndFileOrderAndStoredFileVariant(fileKey, fileOrder, storedFileVariant).orElseThrow(() -> new ErrorException(ErrorCode.STORED_FILE_NOT_FOUND));

        // 실제 로컬 저장소에 존재하는 파일인지 검증
        Path filePath = fileRootPath.resolve(findStoredFile.getStoredFileName());
        if(!Files.isRegularFile(filePath)) {
            throw new ErrorException(ErrorCode.STORED_FILE_NOT_FOUND);
        }

        FileSystemResource fileSystemResource = new FileSystemResource(filePath);

        return FindMessageFileResult.of(
                fileSystemResource,
                findStoredFile.getContentType(),
                findStoredFile.getOriginalFileName(),
                findStoredFile.getFileSize(),
                findStoredFile.getFileCategory()
        );
    }
}
