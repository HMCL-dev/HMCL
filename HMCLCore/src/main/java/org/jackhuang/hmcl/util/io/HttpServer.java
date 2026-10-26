// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.io;

import com.google.gson.JsonParseException;
import fi.iki.elonen.NanoHTTPD;
import org.jackhuang.hmcl.util.function.ExceptionalFunction;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class HttpServer extends NanoHTTPD {
    private int traceId = 0;
    protected final List<Route> routes = new ArrayList<>();

    public HttpServer(int port) {
        super(port);
    }

    public HttpServer(String hostname, int port) {
        super(hostname, port);
    }

    public String getRootUrl() {
        return "http://localhost:" + getListeningPort();
    }

    protected void addRoute(Method method, Pattern path, ExceptionalFunction<Request, Response, ?> server) {
        routes.add(new DefaultRoute(method, path, server));
    }

    protected static Response ok(Object response) {
        LOG.info(String.format("Response %s", JsonUtils.GSON.toJson(response)));
        return newFixedLengthResponse(Response.Status.OK, "text/json", JsonUtils.GSON.toJson(response));
    }

    protected static Response notFound() {
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "404 not found");
    }

    protected static Response noContent() {
        return newFixedLengthResponse(Response.Status.NO_CONTENT, MIME_HTML, "");
    }

    protected static Response badRequest() {
        return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_HTML, "400 bad request");
    }

    protected static Response internalError() {
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_HTML, "500 internal error");
    }

    @Override
    public Response serve(IHTTPSession session) {
        int currentId = traceId++;
        LOG.info(String.format("[%d] %s --> %s", currentId, session.getMethod().name(),
                session.getUri() + Optional.ofNullable(session.getQueryParameterString()).map(s -> "?" + s).orElse("")));

        Response response = null;
        for (Route route : routes) {
            if (route.method != session.getMethod()) continue;

            Matcher pathMatcher = route.pathPattern.matcher(session.getUri());
            if (!pathMatcher.find()) continue;

            response = route.serve(new Request(pathMatcher, mapOf(NetworkUtils.parseQuery(session.getQueryParameterString())), session));
            break;
        }

        if (response == null) response = notFound();
        LOG.info(String.format("[%d] %s <--", currentId, response.getStatus()));
        return response;
    }

    public static abstract class Route {
        Method method;
        Pattern pathPattern;

        public Route(Method method, Pattern pathPattern) {
            this.method = method;
            this.pathPattern = pathPattern;
        }

        public Method getMethod() {
            return method;
        }

        public Pattern getPathPattern() {
            return pathPattern;
        }

        public abstract Response serve(Request request);
    }

    public static class DefaultRoute extends Route {
        private final ExceptionalFunction<Request, Response, ?> server;

        public DefaultRoute(Method method, Pattern pathPattern, ExceptionalFunction<Request, Response, ?> server) {
            super(method, pathPattern);
            this.server = server;
        }

        @Override
        public Response serve(Request request) {
            try {
                return server.apply(request);
            } catch (JsonParseException e) {
                return badRequest();
            } catch (Exception e) {
                LOG.error("Error handling " + request.getSession().getUri(), e);
                return internalError();
            }
        }
    }

    public static class Request {
        Matcher pathVariables;
        Map<String, String> query;
        NanoHTTPD.IHTTPSession session;

        public Request(Matcher pathVariables, Map<String, String> query, NanoHTTPD.IHTTPSession session) {
            this.pathVariables = pathVariables;
            this.query = query;
            this.session = session;
        }

        public Matcher getPathVariables() {
            return pathVariables;
        }

        public Map<String, String> getQuery() {
            return query;
        }

        public NanoHTTPD.IHTTPSession getSession() {
            return session;
        }
    }
}
