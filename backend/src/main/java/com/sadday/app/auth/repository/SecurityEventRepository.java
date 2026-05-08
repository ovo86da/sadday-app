package com.sadday.app.auth.repository;

import com.sadday.app.auth.entity.SecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID> {

    /** ¿El socio ha iniciado sesión desde este device_id antes? */
    @Query("SELECT COUNT(e) > 0 FROM SecurityEvent e " +
           "WHERE e.socioId = :socioId AND e.deviceId = :deviceId " +
           "AND e.eventType IN ('LOGIN_SUCCESS', 'NEW_DEVICE_LOGIN')")
    boolean existsKnownDevice(@Param("socioId") UUID socioId, @Param("deviceId") String deviceId);

    /** ¿El socio ha iniciado sesión desde este país en los últimos 90 días? */
    @Query("SELECT COUNT(e) > 0 FROM SecurityEvent e " +
           "WHERE e.socioId = :socioId AND e.countryCode = :countryCode " +
           "AND e.eventType IN ('LOGIN_SUCCESS', 'NEW_COUNTRY_LOGIN') " +
           "AND e.createdAt > :since")
    boolean existsKnownCountry(@Param("socioId") UUID socioId,
                               @Param("countryCode") String countryCode,
                               @Param("since") OffsetDateTime since);
}
