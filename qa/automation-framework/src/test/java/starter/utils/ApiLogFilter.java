package starter.utils;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

/**
 * Prints every API response in RestAssured's log().all() format:
 * status line, headers, then the pretty-printed JSON body.
 *
 * Only very large bodies are shortened (GET /products embeds every sale and can be
 * hundreds of KB); the complete response is always in the Serenity report.
 *
 * System properties (e.g. mvn clean install -Dapi.log=errors):
 *   api.log           all (default) | errors (only 4xx/5xx) | none
 *   api.log.maxBody   max characters of a pretty body to print (default 20000, 0 = no limit)
 */
public class ApiLogFilter implements Filter {

    private static final String MODE = System.getProperty("api.log", "all").toLowerCase();
    private static final int MAX_BODY = Integer.getInteger("api.log.maxBody", 20000);

    @Override
    public Response filter(FilterableRequestSpecification request,
                           FilterableResponseSpecification responseSpec,
                           FilterContext ctx) {
        Response response = ctx.next(request, responseSpec);

        boolean isError = response.statusCode() >= 400;
        if (MODE.equals("none") || (MODE.equals("errors") && !isError)) {
            return response;
        }

        String body = prettyBody(response);
        if (MAX_BODY > 0 && body.length() > MAX_BODY) {
            body = body.substring(0, MAX_BODY) + System.lineSeparator() + "    ... ["
                    + (body.length() - MAX_BODY) + " more chars, full body in the Serenity report]";
        }

        StringBuilder out = new StringBuilder(response.getStatusLine()).append(System.lineSeparator());
        for (Header h : response.getHeaders()) {
            out.append(h.getName()).append(": ").append(h.getValue()).append(System.lineSeparator());
        }
        out.append(System.lineSeparator()).append(body).append(System.lineSeparator());

        System.out.println(out);
        return response;
    }

    private static String prettyBody(Response response) {
        String raw = response.asString();
        if (raw == null || raw.isBlank()) return "";
        try {
            return response.asPrettyString();
        } catch (RuntimeException e) {
            return raw;
        }
    }
}
