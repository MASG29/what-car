package carfinder.data;

import carfinder.listing.Site;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Imports and the user's "delete" actions. Saved runs lose deleted ads through the database's cascades. */
@Service
public class DataStore {

	private final JdbcTemplate jdbc;
	private final Clock clock;

	public DataStore(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Transactional
	public long recordImport(Site site, int read, int added, Collection<Long> listingIds) {
		Long id = jdbc.queryForObject(
				"INSERT INTO import_batch (site, imported_at, read, added) VALUES (?, ?, ?, ?) RETURNING id",
				Long.class, site.name(), Timestamp.from(clock.instant()), read, added);
		jdbc.batchUpdate("INSERT INTO import_batch_listing (batch_id, listing_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
				listingIds.stream().map(l -> new Object[] {id, l}).toList());
		return id;
	}

	public List<ImportBatchView> imports() {
		return jdbc.query("""
				SELECT b.id, b.site, b.imported_at, b.read, b.added,
				       (SELECT count(*) FROM import_batch_listing x WHERE x.batch_id = b.id) AS listings
				FROM import_batch b ORDER BY b.imported_at DESC, b.id DESC""",
				(rs, i) -> new ImportBatchView(rs.getLong("id"), Site.valueOf(rs.getString("site")),
						rs.getTimestamp("imported_at").toInstant(), rs.getInt("read"), rs.getInt("added"),
						rs.getInt("listings")));
	}

	/** Deletes the import and the ads no other import holds; returns how many ads went. */
	@Transactional
	public int deleteImport(long id) {
		if (jdbc.queryForObject("SELECT count(*) FROM import_batch WHERE id = ?", Integer.class, id) == 0) {
			throw new NoSuchElementException("Importação não encontrada");
		}
		int deleted = jdbc.update("""
				DELETE FROM listing WHERE id IN (
				  SELECT x.listing_id FROM import_batch_listing x WHERE x.batch_id = ?
				  AND NOT EXISTS (SELECT 1 FROM import_batch_listing o WHERE o.listing_id = x.listing_id AND o.batch_id <> ?))""",
				id, id);
		jdbc.update("DELETE FROM import_batch WHERE id = ?", id);
		return deleted;
	}

	@Transactional
	public int deleteSearches() {
		return jdbc.update("DELETE FROM search_run");
	}

	/** Every saved search, import and ad; returns how many ads went. */
	@Transactional
	public int deleteAll() {
		jdbc.update("DELETE FROM search_run");
		jdbc.update("DELETE FROM import_batch");
		return jdbc.update("DELETE FROM listing");
	}
}
