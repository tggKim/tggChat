package com.tgg.chat.domain.friend.repository;

import com.tgg.chat.domain.friend.dto.query.UserFriendRowDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserFriendMapper {

    public List<UserFriendRowDto> findFriendListByOwnerId(Long ownerId);
    
    public boolean existsByOwnerIdAndFriendId(Long ownerId, Long friendId);

    public int countActiveFriendsByIds(Long ownerId, List<Long> friendIds);

}
