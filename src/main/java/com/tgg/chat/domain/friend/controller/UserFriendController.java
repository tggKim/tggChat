package com.tgg.chat.domain.friend.controller;

import com.tgg.chat.common.security.principal.AuthenticatedUser;
import com.tgg.chat.domain.friend.dto.request.CreateFriendRequestDto;
import com.tgg.chat.domain.friend.dto.response.FriendListResponseDto;
import com.tgg.chat.domain.friend.dto.response.SearchFriendResponseDto;
import com.tgg.chat.domain.friend.service.UserFriendService;
import com.tgg.chat.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Friend API", description = "친구 관련 API")
@RestController
@RequiredArgsConstructor
public class UserFriendController {

    private final UserFriendService userFriendService;

    @GetMapping("/friends/search")
    @SecurityRequirement(name = "JWT Auth")
    @Operation(
            summary = "친구 추가 대상 검색",
            description = "이름이 일치하는 유저 중 친구로 추가할 수 있는 유저를 조회합니다."
    )
    public ResponseEntity<List<SearchFriendResponseDto>> searchFriends(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(name = "username", required = false) String username
    ) {
        List<SearchFriendResponseDto> responseDtos = userFriendService.searchFriends(authenticatedUser.getUserId(), username);

        return ResponseEntity.ok(responseDtos);
    }

    @PostMapping("/friends")
	@SecurityRequirement(name = "JWT Auth")
	@Operation(
		summary = "친구 추가",
		description =  "유저를 친구 목록에 추가합니다."
	)
	@ApiResponses({
		@ApiResponse(
				responseCode = "200", 
				description = "친구 추가 성공"
		),
        @ApiResponse(
                responseCode = "400",
                description = "요청값 검증 실패 또는 자기 자신 친구 추가",
                content = @Content(
                        mediaType = "application/json",
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
				description = "존재하지 않는 유저",
				content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = ErrorResponse.class)
				)
		),
		@ApiResponse(
				responseCode = "409", 
				description = "이미 친구로 등록된 유저",
				content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = ErrorResponse.class)
				)
		)
	})
    public ResponseEntity<Void> createUserFriend(@AuthenticationPrincipal AuthenticatedUser authenticatedUser, @Valid @RequestBody CreateFriendRequestDto createFriendRequestDto) {
        userFriendService.createFriend(authenticatedUser.getUserId(), createFriendRequestDto);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(null);
    }

    @GetMapping("/friends")
	@SecurityRequirement(name = "JWT Auth")
	@Operation(
		summary = "친구 목록 조회",
		description =  "로그인한 유저의 친구 목록을 조회합니다."
	)
	@ApiResponses({
		@ApiResponse(
				responseCode = "200", 
				description = "친구 목록 조회 성공",
				content = @Content(
					mediaType = "application/json",
                    array = @ArraySchema(
                            schema = @Schema(implementation = FriendListResponseDto.class)
                    )
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
				description = "존재하지 않는 유저",
				content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = ErrorResponse.class)
				)
		)
	})
    public ResponseEntity<List<FriendListResponseDto>> findFriendList(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        List<FriendListResponseDto> friendList = userFriendService.findFriendListByOwnerId(authenticatedUser.getUserId());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(friendList);
    }
    
}
