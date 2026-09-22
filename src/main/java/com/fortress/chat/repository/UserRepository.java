package com.fortress.chat.repository;

import com.fortress.chat.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByUid(String uid);

    Optional<User> findByEmail(String email);

    List<User> findByEmailContainingIgnoreCaseAndUidNotAndIsDeletedFalse(String email, String excludeUid);
}
