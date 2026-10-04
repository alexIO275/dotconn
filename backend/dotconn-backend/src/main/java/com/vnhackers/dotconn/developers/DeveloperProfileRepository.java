// Repository Spring Data pentru căutarea și paginarea profilurilor de programatori.
package com.vnhackers.dotconn.developers;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DeveloperProfileRepository
    extends JpaRepository<DeveloperProfile, Long>, JpaSpecificationExecutor<DeveloperProfile> {}
