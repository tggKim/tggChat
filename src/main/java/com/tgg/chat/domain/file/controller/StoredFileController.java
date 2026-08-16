package com.tgg.chat.domain.file.controller;

import com.tgg.chat.common.messaging.event.UserMetadataEvent;
import com.tgg.chat.common.messaging.redis.RedisPublisher;
import com.tgg.chat.common.security.principal.AuthenticatedUser;
import com.tgg.chat.domain.file.dto.internal.FindMessageFileResult;
import com.tgg.chat.domain.file.dto.internal.FindUserImageResult;
import com.tgg.chat.domain.file.dto.internal.SaveMessageFileResult;
import com.tgg.chat.domain.file.enums.FileCategory;
import com.tgg.chat.domain.file.enums.StoredFileVariant;
import com.tgg.chat.domain.file.service.StoredFileService;
import com.tgg.chat.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Tag(name = "Stored File API", description = "파일 저장 및 조회 API")
@RestController
@RequiredArgsConstructor
public class StoredFileController {
    private final StoredFileService storedFileService;
    private final RedisPublisher redisPublisher;

    @PutMapping(value = "/me/profile-image")
    @SecurityRequirement(name = "JWT Auth")
    @Operation(
            summary = "프로필 이미지 변경",
            description = "로그인한 사용자의 프로필 이미지 원본과 썸네일을 저장하고 기존 이미지를 교체합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "프로필 이미지 변경 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = """
                SF001: 지원하지 않는 이미지 형식인 경우
                SF006: 프로필 이미지가 없거나 비어 있는 경우
                """,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않거나 삭제된 사용자",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "프로필 이미지 또는 썸네일 저장 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Void> saveUserProfile(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestPart(required = false) MultipartFile userProfileImage
    ) {
        UserMetadataEvent userMetadataEvent = storedFileService.saveUserProfile(authenticatedUser.getUserId(), userProfileImage);

        redisPublisher.publishUserMetadataEvent(userMetadataEvent);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(null);
    }

    @GetMapping(value = "/profile-images/{fileKey}/thumbnail")
    @Operation(
            summary = "프로필 이미지 썸네일 조회",
            description = "프로필 이미지 키로 JPEG 형식의 썸네일 이미지를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "프로필 이미지 썸네일 조회 성공",
                    content = @Content(
                            mediaType = MediaType.IMAGE_JPEG_VALUE,
                            schema = @Schema(type = "string", format = "binary")
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "파일 정보가 없거나 실제 썸네일 파일이 존재하지 않음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<FileSystemResource> findUserThumbnail(
            @PathVariable String fileKey
    ) {
        FileSystemResource fileSystemResource = storedFileService.findUserThumbnail(fileKey);

        return ResponseEntity
                .status(HttpStatus.OK)
                .contentType(MediaType.IMAGE_JPEG)
                //.cacheControl(
                //        CacheControl.maxAge(Duration.ofDays(365))
                //                .cachePublic()
                //                .immutable()
                //)
                .body(fileSystemResource);
    }

    @GetMapping("/profile-images/{fileKey}/image")
    @Operation(
            summary = "프로필 원본 이미지 조회",
            description = "프로필 이미지 키로 JPG, PNG, GIF 또는 WebP 형식의 원본 이미지를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "프로필 원본 이미지 조회 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.IMAGE_JPEG_VALUE,
                                    schema = @Schema(type = "string", format = "binary")
                            ),
                            @Content(
                                    mediaType = MediaType.IMAGE_PNG_VALUE,
                                    schema = @Schema(type = "string", format = "binary")
                            ),
                            @Content(
                                    mediaType = "image/gif",
                                    schema = @Schema(type = "string", format = "binary")
                            ),
                            @Content(
                                    mediaType = "image/webp",
                                    schema = @Schema(type = "string", format = "binary")
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "파일 정보가 없거나 실제 원본 이미지 파일이 존재하지 않음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<FileSystemResource> findUserImage(
        @PathVariable String fileKey
    ) {
        FindUserImageResult findUserImageResult = storedFileService.findUserImage(fileKey);

        FileSystemResource fileSystemResource = findUserImageResult.getFileSystemResource();
        String contentType = findUserImageResult.getContentType();

        MediaType mediaType = MediaType.parseMediaType(contentType);

        return ResponseEntity
                .status(HttpStatus.OK)
                .contentType(mediaType)
                .body(fileSystemResource);
    }

    @PostMapping(value = "/chatRooms/{chatRoomId}/files")
    @SecurityRequirement(name = "JWT Auth")
    @Operation(
            summary = "채팅방 파일 메시지 전송",
            description = """
                채팅방에 여러 파일을 첨부한 파일 메시지를 전송합니다.
                한 번에 1개 이상 30개 이하의 파일을 전송할 수 있으며,
                전체 파일 크기는 최대 3GB까지 허용합니다.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "파일 메시지 전송 성공",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = """
                        SF001: 지원하지 않는 이미지 형식인 경우
                        SF003: 파일이 없거나 비어 있는 파일이 포함된 경우
                        SF004: 파일 개수가 30개를 초과한 경우
                        """,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증에 실패한 경우",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = """
                        CR010: 요청자가 채팅방에 속하지 않았거나
                        채팅방에서 나간 상태인 경우
                        """,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                        U003: 요청 사용자가 삭제된 사용자인 경우
                        """,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "413",
                    description = """
                        SF005: 전체 파일 크기가 3GB를 초과한 경우
                        """,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "파일 저장 또는 메시지 이벤트 처리 중 서버 오류가 발생한 경우",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Void> saveMessageFile(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long chatRoomId,
            @RequestPart(required = false) List<MultipartFile> files
    ) {
        SaveMessageFileResult result = storedFileService.saveMessageFile(authenticatedUser.getUserId(), chatRoomId, files);

        redisPublisher.publishChatRoomListEvents(result.getChatRoomListEvents());
        redisPublisher.publishChatEvent(result.getChatEvent());

        return ResponseEntity.ok().build();
    }

    @GetMapping("/media/messages/{chatMessageId}/files/{fileOrder}")
    public ResponseEntity<FileSystemResource> findMessageFile(
            @CookieValue(value = "mediaToken", required = false) String mediaToken,
            @PathVariable Long chatMessageId,
            @PathVariable Integer fileOrder,
            @RequestParam StoredFileVariant storedFileVariant
    ) {
        FindMessageFileResult findMessageFileResult= storedFileService.findMessageFile(chatMessageId, fileOrder, storedFileVariant, mediaToken);

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity
                .status(HttpStatus.OK)
                .contentLength(findMessageFileResult.getFileSize());

//        if(storedFileVariant == StoredFileVariant.THUMBNAIL) {
//            responseBuilder.cacheControl(
//                    CacheControl
//                            .maxAge(Duration.ofMinutes(10)).cachePrivate()
//            )
//            .header(HttpHeaders.VARY, HttpHeaders.COOKIE);
//        }

        if (findMessageFileResult.getFileCategory() == FileCategory.FILE) {
            responseBuilder
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment()
                                    .filename(findMessageFileResult.getOriginalFileName(), StandardCharsets.UTF_8)
                                    .build()
                                    .toString()
                    );
        } else {
            responseBuilder.contentType(
                    MediaType.parseMediaType(findMessageFileResult.getContentType())
            );

            if (storedFileVariant == StoredFileVariant.ORIGINAL) {
                responseBuilder.header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(findMessageFileResult.getOriginalFileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                );
            }
        }

        return responseBuilder.body(findMessageFileResult.getFileSystemResource());
    }
}
