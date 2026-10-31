package carfinder.buying;

import java.util.List;

public record Checklist(List<Link> links, List<String> questionsPt) {

	public record Link(String labelPt, String url) {
	}
}
