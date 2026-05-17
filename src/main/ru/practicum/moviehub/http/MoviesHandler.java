package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.util.List;

class MoviesHandler extends BaseHttpHandler {

    private final MoviesStore moviesStore;

    public MoviesHandler(MoviesStore moviesStore) {
        this.moviesStore = moviesStore;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();

        if (method.equalsIgnoreCase("GET")) {
            // список всех фильмов
            List<Movie> movies = moviesStore.getAll();

            // 2. Gson превращает List<Movie> в JSON-строку вида: [{"title":"...", "year":...}]
            // Если список пуст, Gson сам сгенерирует правильную строку "[]"
            String jsonResponse = gson.toJson(movies);

            sendJson(ex, 200, jsonResponse);
        } else {
            sendError(ex, 405, "Метод не поддерживается");
        }
    }
}
