package com.cdlab.cdlabchat.common;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class CommonRepository {

    private final EntityManager entityManager;

    public CommonRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public int ping() {
        Object result = entityManager.createNativeQuery("SELECT 1").getSingleResult();
        return ((Number) result).intValue();
    }
}
