package carfinder.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** suggestion: what the user probably meant ("Ford Fiesta"); left out of the JSON when null. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorDto(String messagePt, String suggestion) {

	public ErrorDto(String messagePt) {
		this(messagePt, null);
	}
}
