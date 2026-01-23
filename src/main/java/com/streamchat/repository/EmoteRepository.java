package com.streamchat.repository;

import com.streamchat.model.entity.Emote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Emote entity operations.
 */
@Repository
public interface EmoteRepository extends JpaRepository<Emote, Long> {

    /**
     * Find all global emotes.
     *
     * @return list of global emotes
     */
    @Query("SELECT e FROM Emote e WHERE e.isGlobal = true")
    List<Emote> findAllGlobal();

    /**
     * Find emotes for a stream (including global).
     *
     * @param streamId the stream ID
     * @return list of emotes
     */
    @Query("SELECT e FROM Emote e WHERE e.stream.id = :streamId OR e.isGlobal = true")
    List<Emote> findByStreamIdOrGlobal(Long streamId);

    /**
     * Find emote by code in stream.
     *
     * @param streamId the stream ID
     * @param code the emote code
     * @return optional emote
     */
    @Query("SELECT e FROM Emote e WHERE (e.stream.id = :streamId OR e.isGlobal = true) " +
            "AND e.code = :code")
    Optional<Emote> findByStreamIdAndCode(Long streamId, String code);

    /**
     * Check if emote code exists in stream.
     *
     * @param streamId the stream ID
     * @param code the emote code
     * @return true if exists
     */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM Emote e " +
            "WHERE (e.stream.id = :streamId OR e.isGlobal = true) AND e.code = :code")
    boolean existsByStreamIdAndCode(Long streamId, String code);
}