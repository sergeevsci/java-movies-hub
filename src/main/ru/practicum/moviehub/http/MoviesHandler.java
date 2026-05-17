package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

class MoviesHandler extends BaseHttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();

        if (method.equalsIgnoreCase("GET")) {
            sendJson(ex, 200, "[]"); // не вручную указываем пустой массив
        } else {
            ex.sendResponseHeaders(405, 0);
            ex.close();
        }
    }
}
