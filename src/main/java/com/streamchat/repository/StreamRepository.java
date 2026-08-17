package com.streamchat.repository;

import com.streamchat.model.entity.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Stream entity operations.
 */
@Repository
public interface StreamRepository extends JpaRepository<Stream, Long> {

    /**
     * Find stream by unique stream key.
     *
     * @param streamKey the stream key
     * @return optional stream
     */
    Optional<Stream> findByStreamKey(String streamKey);

    /**
     * Find all live streams.
     *
     * @return list of live streams
     */
    List<Stream> findByIsLiveTrue();

    /**
     * Check if stream exists by key.
     *
     * @param streamKey the stream key
     * @return true if exists
     */
    boolean existsByStreamKey(String streamKey);
}