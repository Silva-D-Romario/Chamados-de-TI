package br.com.romariosilva.chamados.category.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketCategoryRepository extends JpaRepository<TicketCategory, Long> {

    List<TicketCategory> findAllByActiveTrueOrderByNameAsc();

    List<TicketCategory> findAllByOrderByNameAsc();

    Optional<TicketCategory> findByNameIgnoreCase(String name);

    Optional<TicketCategory> findByNameIgnoreCaseAndActiveTrue(String name);
}
