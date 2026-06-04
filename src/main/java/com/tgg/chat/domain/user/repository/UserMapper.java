package com.tgg.chat.domain.user.repository;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.tgg.chat.domain.chat.dto.query.UserIdUsernameQueryDto;
import com.tgg.chat.domain.user.entity.User;

@Mapper
public interface UserMapper {
	User findById(Long userId);
	
	List<UserIdUsernameQueryDto> getUserNames(List<Long> userIds);
}
