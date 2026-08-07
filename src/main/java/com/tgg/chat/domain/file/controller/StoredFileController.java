package com.tgg.chat.domain.file.controller;

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
        storedFileService.saveUserProfile(authenticatedUser.getUserId(), userProfileImage);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(null);
    }

    @GetMapping(value = "/profile-images/{fileKey}/thumbnail")
    public ResponseEntity<FileSystemResource> findUserThumbnail(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable String fileKey
    ) {
        FileSystemResource fileSystemResource = storedFileService.findUserThumbnail(authenticatedUser.getUserId(), fileKey);

        return ResponseEntity
                .status(HttpStatus.OK)
                .contentType(MediaType.IMAGE_JPEG)
                .body(fileSystemResource);
    }

    @GetMapping("/profile-images/{fileKey}/image")
    public ResponseEntity<FileSystemResource> findUserImage(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable String fileKey
    ) {
        FindUserImageResult findUserImageResult = storedFileService.findUserImage(authenticatedUser.getUserId(), fileKey);

        FileSystemResource fileSystemResource = findUserImageResult.getFileSystemResource();
        String contentType = findUserImageResult.getContentType();

        MediaType mediaType = MediaType.parseMediaType(contentType);

        ContentDisposition contentDisposition = ContentDisposition
                .inline()
                .filename(findUserImageResult.getOriginalFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity
                .status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(mediaType)
                .body(fileSystemResource);
    }
}
