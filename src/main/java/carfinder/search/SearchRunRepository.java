package carfinder.search;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchRunRepository extends JpaRepository<SearchRun, UUID> {

	List<SearchRun> findByStatusIn(Collection<RunStatus> statuses);
}
