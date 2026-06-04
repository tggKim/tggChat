package com.tgg.chat.domain.user.repository;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.tgg.chat.domain.chat.dto.query.UserIdUsernameQueryDto;

@Mapper
public interface UserMapper {
	List<UserIdUsernameQueryDto> getUserNames(List<Long> userIds);
}
