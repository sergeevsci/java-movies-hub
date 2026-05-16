package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8";

    protected void sendJson(HttpExchange ex, int status, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", CT_JSON);

        // Преобразуем строку в байты с использованием StandardCharsets.UTF_8
        byte[] response = json.getBytes(StandardCharsets.UTF_8);

        // Отправляем стартовую строку с переданным статус-кодом и длиной тела
        ex.sendResponseHeaders(status, response.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(response);
        }

        ex.close();
    }

    protected void sendNoContent(HttpExchange ex) throws java.io.IOException {
        // Код 204 означает "No Content". Вторым аргументом передаем -1
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(204, -1);
        ex.close();
    }
}

class MoviesHandler extends BaseHttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();

        if (method.equalsIgnoreCase("GET")) {
            sendJson(ex, 200, "[]");
        } else {
            ex.sendResponseHeaders(405, 0);
            ex.close();
        }
    }
}
