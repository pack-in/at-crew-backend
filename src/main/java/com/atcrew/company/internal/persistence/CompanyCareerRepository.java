package com.atcrew.company.internal.persistence;

import com.atcrew.company.internal.domain.CompanyCareer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyCareerRepository extends JpaRepository<CompanyCareer, String> {

    List<CompanyCareer> findByCompanyIdOrderByStartDateDesc(String companyId);

    long countByCompanyId(String companyId);
}
