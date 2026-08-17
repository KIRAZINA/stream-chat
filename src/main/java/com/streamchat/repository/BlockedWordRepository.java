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
}