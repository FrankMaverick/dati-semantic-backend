package it.gov.innovazione.ndc.alerter.data;

import it.gov.innovazione.ndc.alerter.entities.Event;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
interface EventRepository extends NameableRepository<Event, String> {

    boolean existsByNameAndOccurredAt(String name, Instant occurredAt);
}