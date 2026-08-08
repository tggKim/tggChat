package com.tgg.chat.domain.file.controller;

import com.tgg.chat.common.messaging.event.UserMetadataEvent;
import com.tgg.chat.common.messaging.redis.RedisPublisher;
import com.tgg.chat.common.security.principal.AuthenticatedUser;
import com.tgg.chat.domain.file.dto.internal.FindUserImageResult;
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
            @RequestPart MultipartFile userProfileImage
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
}
