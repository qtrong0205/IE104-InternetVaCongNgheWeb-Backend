package com.nhom6.motofix.repository;

import com.nhom6.motofix.entity.BookingMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BookingMediaRepository extends JpaRepository<BookingMedia, UUID> {
}
