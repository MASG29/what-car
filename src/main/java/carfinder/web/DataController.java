package carfinder.web;

import carfinder.data.DataStore;
import carfinder.data.ImportBatchView;
import carfinder.search.SearchService;
import carfinder.web.dto.ErrorDto;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Lists imports and deletes stored data. Every delete waits for no search to be running (409 otherwise). */
@RestController
public class DataController {

	private final DataStore data;
	private final SearchService search;

	public DataController(DataStore data, SearchService search) {
		this.data = data;
		this.search = search;
	}

	@GetMapping("/api/imports")
	public List<ImportBatchView> imports() {
		return data.imports();
	}

	@DeleteMapping("/api/imports/{id}")
	public Map<String, Integer> deleteImport(@PathVariable long id) {
		return Map.of("deleted", search.whileIdle(() -> data.deleteImport(id)));
	}

	@DeleteMapping("/api/searches")
	public Map<String, Integer> deleteSearches() {
		return Map.of("deleted", search.whileIdle(data::deleteSearches));
	}

	@DeleteMapping("/api/data")
	public Map<String, Integer> deleteAll() {
		return Map.of("deleted", search.whileIdle(data::deleteAll));
	}

	@ExceptionHandler(IllegalStateException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	ErrorDto busy(IllegalStateException e) {
		return new ErrorDto(e.getMessage());
	}

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	ErrorDto unknown(NoSuchElementException e) {
		return new ErrorDto(e.getMessage());
	}
}
