package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import ru.practicum.moviehub.api.ErrorResponse;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8";
    protected final Gson gson = new Gson();

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

    protected void sendNoContent(HttpExchange ex) throws IOException {
        // Код 204 "No Content". Вторым аргументом передаем -1
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(204, -1);
        ex.close();
    }

    protected void sendError(HttpExchange ex, int status, String errorMessage) throws IOException {
        ErrorResponse errorObj = new ErrorResponse(errorMessage);

        // Gson объект в JSON-строку: {"error":"Текст ошибки"}
        String jsonError = gson.toJson(errorObj);

        sendJson(ex, status, jsonError);
    }
}
