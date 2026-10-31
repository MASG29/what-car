package carfinder.web;

import carfinder.search.RunView;
import carfinder.search.SearchService;
import carfinder.web.dto.ErrorDto;
import carfinder.web.dto.RankingDto;
import carfinder.web.dto.SearchStarted;
import carfinder.web.dto.StartSearchRequest;
import jakarta.validation.Valid;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/searches")
public class SearchController {

	private final SearchService service;

	public SearchController(SearchService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public SearchStarted start(@Valid @RequestBody StartSearchRequest req) {
		return new SearchStarted(service.start(req.limits(), req.preset(), req.customWeights(), req.sites()));
	}

	@GetMapping("/{id}")
	public RunView status(@PathVariable UUID id) {
		return service.status(id);
	}

	@GetMapping("/{id}/results")
	public RankingDto results(@PathVariable UUID id) {
		return RankingDto.from(service.results(id));
	}

	/** Only start() throws it here: a run is already going. */
	@ExceptionHandler(IllegalStateException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	ErrorDto busy(IllegalStateException e) {
		return new ErrorDto(e.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ErrorDto invalid(IllegalArgumentException e) {
		return new ErrorDto(e.getMessage());
	}

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	ErrorDto unknown() {
		return new ErrorDto("Pesquisa não encontrada");
	}
}
