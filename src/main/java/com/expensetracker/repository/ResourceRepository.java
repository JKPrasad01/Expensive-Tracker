package com.expensetracker.repository;

import com.expensetracker.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ResourceRepository extends JpaRepository<Resource,Long> {

    boolean existsByResourceKeyEqualsIgnoreCase(String resourceKey);

    boolean existsByResourceNameEqualsIgnoreCase(String resourceName);

    List<Resource> findByResourceKeyIn(Set<String> resourceKeys);

    Optional<Resource> findByResourceKeyEqualsIgnoreCase(String resourceKey);


    List<Resource> findByParentIsNullOrderByDisplayOrderAsc();

    @Query("select r from Resource r left join fetch r.parent")
    List<Resource> findAllWithParent();

}
