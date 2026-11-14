package carfinder.source.marketplace;

import carfinder.source.SourceException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.json.JsonMapper;

/**
 * Streams the response bodies of a HAR file that can hold a site's ads: POSTs to its graphql endpoint and HTML documents
 * under one path. The file is read token by token and never held whole; request and response headers, cookies and post
 * data are skipped unread, and only bodies the filter wants are decoded.
 */
public final class HarReader {

	/** Which bodies a site's ads live in, and the PT message when the file has none. */
	public record Filter(String graphqlUrl, String documentPrefix, String noAdsPt) {
	}

	public static final Filter MARKETPLACE = new Filter("https://www.facebook.com/api/graphql/",
			"https://www.facebook.com/marketplace/", MarketplaceFeedParser.NO_ADS);

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private HarReader() {
	}

	/** Lazy: each body is read when the stream asks for it. Close the stream to close the parser. */
	public static Stream<String> bodies(InputStream har, Filter filter) {
		JsonParser p = MAPPER.createParser(har);
		Iterator<String> it = new Iterator<>() {
			private boolean started;
			private boolean done;
			private String next;

			@Override
			public boolean hasNext() {
				if (next == null && !done) {
					next = advance();
				}
				return next != null;
			}

			@Override
			public String next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}
				String n = next;
				next = null;
				return n;
			}

			private String advance() {
				try {
					if (!started) {
						started = true;
						if (!toEntries(p)) {
							throw new SourceException(filter.noAdsPt());
						}
					}
					while (p.nextToken() == JsonToken.START_OBJECT) {
						String body = entry(p, filter);
						if (body != null) {
							return body;
						}
					}
					done = true;
					return null;
				}
				catch (JacksonException e) {
					// not JSON, or cut short; the parser's message may quote file content, so it is dropped
					throw new SourceException(filter.noAdsPt());
				}
			}
		};
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED | Spliterator.NONNULL),
				false).onClose(p::close);
	}

	/** Moves to the first token inside log.entries. */
	private static boolean toEntries(JsonParser p) {
		return p.nextToken() == JsonToken.START_OBJECT && toProperty(p, "log")
				&& p.currentToken() == JsonToken.START_OBJECT && toProperty(p, "entries")
				&& p.currentToken() == JsonToken.START_ARRAY;
	}

	/** Moves to the value of the named property of the current object, skipping the others unread. */
	private static boolean toProperty(JsonParser p, String name) {
		while (p.nextToken() == JsonToken.PROPERTY_NAME) {
			String n = p.currentName();
			p.nextToken();
			if (n.equals(name)) {
				return true;
			}
			p.skipChildren();
		}
		return false;
	}

	/** One entry, from its START_OBJECT to its END_OBJECT; the wanted body or null. */
	private static String entry(JsonParser p, Filter f) {
		String method = null;
		String url = null;
		String mime = null;
		String text = null;
		String encoding = null;
		while (p.nextToken() == JsonToken.PROPERTY_NAME) {
			String name = p.currentName();
			JsonToken value = p.nextToken();
			if (name.equals("request") && value == JsonToken.START_OBJECT) {
				while (p.nextToken() == JsonToken.PROPERTY_NAME) {
					String k = p.currentName();
					JsonToken t = p.nextToken();
					if (k.equals("method") && t == JsonToken.VALUE_STRING) {
						method = p.getString();
					} else if (k.equals("url") && t == JsonToken.VALUE_STRING) {
						url = p.getString();
					} else {
						p.skipChildren(); // headers, cookies, postData, queryString
					}
				}
			} else if (name.equals("response") && value == JsonToken.START_OBJECT) {
				while (p.nextToken() == JsonToken.PROPERTY_NAME) {
					String k = p.currentName();
					JsonToken t = p.nextToken();
					if (!k.equals("content") || t != JsonToken.START_OBJECT) {
						p.skipChildren(); // headers, cookies
						continue;
					}
					while (p.nextToken() == JsonToken.PROPERTY_NAME) {
						String c = p.currentName();
						JsonToken v = p.nextToken();
						if (v != JsonToken.VALUE_STRING) {
							p.skipChildren();
						} else if (c.equals("mimeType")) {
							mime = p.getString();
						} else if (c.equals("encoding")) {
							encoding = p.getString();
						} else if (c.equals("text") && (url == null || url.startsWith(f.graphqlUrl())
								|| url.startsWith(f.documentPrefix()))) {
							text = p.getString(); // other bodies (scripts, images) are skipped without decoding
						}
					}
				}
			} else {
				p.skipChildren();
			}
		}
		if (text == null || url == null) {
			return null;
		}
		boolean graphql = "POST".equals(method) && url.startsWith(f.graphqlUrl());
		boolean document = url.startsWith(f.documentPrefix()) && mime != null && mime.startsWith("text/html");
		if (!graphql && !document) {
			return null;
		}
		if (!"base64".equals(encoding)) {
			return text;
		}
		try {
			return new String(Base64.getMimeDecoder().decode(text), StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}
}
