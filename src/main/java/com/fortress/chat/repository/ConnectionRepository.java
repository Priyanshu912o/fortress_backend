package com.fortress.chat.repository;

import com.fortress.chat.entity.Connection;
import com.fortress.chat.entity.enums.ConnectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConnectionRepository extends JpaRepository<Connection, String> {

    /** Find existing connection in either direction */
    @Query("SELECT c FROM Connection c WHERE " +
           "(c.fromUid = :uid1 AND c.toUid = :uid2) OR " +
           "(c.fromUid = :uid2 AND c.toUid = :uid1)")
    Optional<Connection> findBetweenUsers(@Param("uid1") String uid1, @Param("uid2") String uid2);

    /** All accepted connections for a user (either direction) */
    @Query("SELECT c FROM Connection c WHERE c.status = 'ACCEPTED' AND " +
           "(c.fromUid = :uid OR c.toUid = :uid) ORDER BY c.updatedAt DESC")
    List<Connection> findAllAcceptedForUser(@Param("uid") String uid);

    /** Incoming pending requests */
    List<Connection> findByToUidAndStatusOrderByCreatedAtDesc(String toUid, ConnectionStatus status);

    /** Outgoing sent requests */
    List<Connection> findByFromUidAndStatusOrderByCreatedAtDesc(String fromUid, ConnectionStatus status);

    /** Check if an accepted connection exists between two users */
    @Query("SELECT COUNT(c) > 0 FROM Connection c WHERE c.status = 'ACCEPTED' AND " +
           "((c.fromUid = :uid1 AND c.toUid = :uid2) OR (c.fromUid = :uid2 AND c.toUid = :uid1))")
    boolean existsAcceptedBetween(@Param("uid1") String uid1, @Param("uid2") String uid2);

    /** Find accepted connections with specific UIDs (for group chat validation) */
    @Query("SELECT c FROM Connection c WHERE c.status = 'ACCEPTED' AND " +
           "((c.fromUid = :uid AND c.toUid IN :otherUids) OR " +
           "(c.toUid = :uid AND c.fromUid IN :otherUids))")
    List<Connection> findAcceptedConnectionsWithUsers(@Param("uid") String uid, @Param("otherUids") List<String> otherUids);

    /** Delete all connections involving a user */
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Connection c WHERE c.fromUid = :uid OR c.toUid = :uid")
    void deleteAllForUser(@Param("uid") String uid);
}
