package com.tgg.chat.domain.friend.controller;

import com.tgg.chat.domain.friend.dto.request.CreateFriendRequestDto;
import com.tgg.chat.domain.friend.dto.response.FriendListResponseDto;
import com.tgg.chat.domain.friend.service.UserFriendService;
import com.tgg.chat.domain.user.dto.response.SignUpResponseDto;
import com.tgg.chat.exception.ErrorResponse;

import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Friend API", description = "친구 관련 API")
@RestController
@RequiredArgsConstructor
public class UserFriendController {

    private final UserFriendService userFriendService;

    @PostMapping("/friends")
	@SecurityRequirement(name = "JWT Auth")
	@Operation(
		summary = "친구 추가",
		description =  "유저를 친구 목록에 추가합니다."
	)
	@ApiResponses({
		@ApiResponse(
				responseCode = "200", 
				description = "친구 추가 성공",
				content = @Content(
					mediaType = "application/json"
				)
		),
		@ApiResponse(
				responseCode = "400", 
				description = "자기 자신을 친구로 추가할 수 없습니다.",
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
    public ResponseEntity<Void> createUserFriend(Authentication authentication, @RequestBody CreateFriendRequestDto createFriendRequestDto) {

    	// Authentication 에서 로그인한 유저의 userId 추출
        Claims claims = (Claims)authentication.getPrincipal();
        Long loginUserId = Long.parseLong(claims.getSubject());

        // 친구 추가
        userFriendService.createFriend(loginUserId, createFriendRequestDto);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(null);

    }

    @GetMapping("/friends")
	@SecurityRequirement(name = "JWT Auth")
	@Operation(
		summary = "친구 목록 조회",
		description =  "유저를 친구 목록을 조회합니다."
	)
	@ApiResponses({
		@ApiResponse(
				responseCode = "200", 
				description = "친구 목록 조회 성공",
				content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = FriendListResponseDto.class)
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
    public ResponseEntity<List<FriendListResponseDto>> findFriendList(Authentication authentication) {

    	// Authentication 에서 로그인한 유저의 userId 추출
        Claims claims = (Claims)authentication.getPrincipal();
        Long loginUserId = Long.parseLong(claims.getSubject());

        // 친구 목록 조회
        List<FriendListResponseDto> friendList = userFriendService.findFriendListByOwnerId(loginUserId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(friendList);

    }
    
}
