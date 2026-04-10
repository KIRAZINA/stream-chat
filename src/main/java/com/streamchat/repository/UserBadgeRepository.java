package com.streamchat.repository;

import com.streamchat.model.entity.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for user badge operations.
 */
@Repository
public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END " +
            "FROM UserBadge b " +
            "WHERE b.user.id = :userId " +
            "AND b.badgeType = :badgeType " +
            "AND (b.stream.id = :streamId OR b.stream IS NULL)")
    boolean hasBadge(@Param("userId") Long userId,
                     @Param("streamId") Long streamId,
                     @Param("badgeType") String badgeType);

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END " +
            "FROM UserBadge b " +
            "WHERE b.user.id = :userId " +
            "AND b.badgeType = :badgeType " +
            "AND (b.stream.id = :streamId OR b.stream IS NULL) " +
            "AND b.grantedAt <= :grantedBefore")
    boolean hasBadgeGrantedBefore(@Param("userId") Long userId,
                                  @Param("streamId") Long streamId,
                                  @Param("badgeType") String badgeType,
                                  @Param("grantedBefore") LocalDateTime grantedBefore);

    @Query("SELECT DISTINCT b.badgeType " +
            "FROM UserBadge b " +
            "WHERE b.user.id = :userId " +
            "AND (b.stream.id = :streamId OR b.stream IS NULL)")
    List<String> findBadgeTypesByUserIdAndStreamIdOrGlobal(@Param("userId") Long userId,
                                                           @Param("streamId") Long streamId);
}
