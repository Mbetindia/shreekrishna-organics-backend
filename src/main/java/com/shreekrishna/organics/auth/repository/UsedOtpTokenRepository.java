
package com.shreekrishna.organics.auth.repository;

import com.shreekrishna.organics.auth.entity.UsedOtpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsedOtpTokenRepository
        extends JpaRepository<UsedOtpToken, Long> {

    boolean existsByTokenHash(String tokenHash);
}
