package com.tgg.chat.domain.friend.repository;

import com.tgg.chat.domain.friend.entity.UserFriend;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserFriendMapper {

    public List<UserFriend> findOwnerId(Long ownerId);

}
