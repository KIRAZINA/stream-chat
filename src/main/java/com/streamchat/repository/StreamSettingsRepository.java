package com.streamchat.repository;

import com.streamchat.model.entity.StreamSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for StreamSettings entity operations.
 */
@Repository
public interface StreamSettingsRepository extends JpaRepository<StreamSettings, Long> {

    /**
     * Find settings by stream ID.
     *
     * @param streamId the stream ID
     * @return optional settings
     */
    @Query("SELECT s FROM StreamSettings s WHERE s.stream.id = :streamId")
    Optional<StreamSettings> findByStreamId(Long streamId);
}