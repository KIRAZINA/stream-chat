package com.streamchat.repository;

import com.streamchat.model.entity.BlockedWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for BlockedWord entity operations.
 */
@Repository
public interface BlockedWordRepository extends JpaRepository<BlockedWord, Long> {

    /**
     * Find all global blocked words.
     *
     * @return list of global blocked words
     */
    @Query("SELECT w FROM BlockedWord w WHERE w.isGlobal = true")
    List<BlockedWord> findAllGlobal();

    /**
     * Find blocked words for a stream (including global).
     *
     * @param streamId the stream ID
     * @return list of blocked words
     */
    @Query("SELECT w FROM BlockedWord w WHERE w.stream.id = :streamId OR w.isGlobal = true")
    List<BlockedWord> findByStreamIdOrGlobal(Long streamId);

    /**
     * Find stream-specific blocked words only.
     *
     * @param streamId the stream ID
     * @return list of blocked words
     */
    @Query("SELECT w FROM BlockedWord w WHERE w.stream.id = :streamId")
    List<BlockedWord> findByStreamId(Long streamId);
}