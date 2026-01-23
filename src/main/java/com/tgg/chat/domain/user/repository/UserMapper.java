package com.tgg.chat.domain.user.repository;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.tgg.chat.domain.user.entity.User;

@Mapper
public interface UserMapper {
	
	User findByEmail(String email);
	
	User findById(Long id);
	
	User findByUsername(String username);

	boolean existsByEmail(String email);
	
	boolean existsByUsername(String username);
	
	int countActiveUsersByIds(List<Long> userIds);
	
}
