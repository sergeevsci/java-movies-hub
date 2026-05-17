package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

class MoviesHandler extends BaseHttpHandler {

    private final MoviesStore moviesStore;

    public MoviesHandler(MoviesStore moviesStore) {
        this.moviesStore = moviesStore;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod().toUpperCase();

        switch (method) {
            case "GET" -> {
                sendJson(ex, 200, gson.toJson(moviesStore.getAll()));
            }
            case "POST" -> {
                // Проверка Content-Type
                String contentType = ex.getRequestHeaders().getFirst("Content-Type");
                if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
                    sendError(ex, 415, "Unsupported Media Type");
                    return;
                }

                try (InputStream is = ex.getRequestBody()) {
                    String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    Movie movie = gson.fromJson(body, Movie.class);

                    // Вызов метода валидации
                    List<String> details = validateMovie(movie);

                    if (!details.isEmpty()) {
                        // Если есть ошибки
                        ErrorResponse errorObj = new ErrorResponse("Ошибка валидации", details);
                        sendJson(ex, 422, gson.toJson(errorObj));
                        return;
                    }

                    // Если всё ок — сохраняем
                    int id = moviesStore.add(movie);
                    movie.setId(id);

                    sendJson(ex, 201, gson.toJson(movie));

                } catch (Exception e) {
                    sendError(ex, 400, "Некорректный формат JSON");
                }
            }
            default -> sendError(ex, 405, "Метод " + method + " не поддерживается");
        }
    }

    private List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();

        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            errors.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > 100) {
            errors.add("длина названия не должна превышать 100 символов");
        }

        // вычисляем максимальный разрешенный год (текущий + 1)
        int maxYear = LocalDate.now().getYear() + 1;
        if (movie.getYear() < 1888 || movie.getYear() > maxYear) {
            errors.add("год должен быть между 1888 и " + maxYear);
        }

        return errors;
    }
}
