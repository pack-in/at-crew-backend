package com.atcrew.company.internal.persistence;

import com.atcrew.company.internal.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, String> {

    Optional<Company> findByMemberId(String memberId);

    boolean existsByMemberId(String memberId);
}
