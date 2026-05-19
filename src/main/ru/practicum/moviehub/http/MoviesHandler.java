package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Классная идея разделить все на методы и вызывать нужный по требованию, чето сразу я не додумался разбить код.
// И глянул, дальше Spring Boot все будет делать именно так, только автоматически, прикольная тема. как раз дальше будет
// Сразу ясно что и для чего делается

public class MoviesHandler extends BaseHttpHandler {

    // Префикс теперь в константе
    private static final String MOVIES_PREFIX = "/movies/";
    private final MoviesStore moviesStore;

    public MoviesHandler(MoviesStore moviesStore) {
        this.moviesStore = moviesStore;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod().toUpperCase();

        // switch занимается делегированием в нужный метод
        switch (method) {
            case "GET" -> handleGet(ex);
            case "POST" -> handlePost(ex);
            case "DELETE" -> handleDelete(ex);
            default -> sendError(ex, 405, "Метод " + method + " не поддерживается");
        }
    }

    // =========================================================================
    // ЛОГИКА ОБРАБОТКИ GET-ЗАПРОСОВ
    // =========================================================================
    private void handleGet(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getQuery();

        // Разделяем хендлер на базовый /movies (список и фильтрация)
        if (path.equals("/movies") || path.equals("/movies/")) {
            if (query != null) {
                processFilteredGet(ex, query);
            } else {
                sendJson(ex, 200, gson.toJson(moviesStore.getAll()));
            }
            return;
        }

        // Разделяем хендлер на ресурсный /movies/{id}
        if (path.startsWith(MOVIES_PREFIX)) {
            processGetById(ex, path);
            return;
        }

        // Если путь не подошел ни под один паттерн
        sendError(ex, 404, "Ресурс не найден");
    }

    private void processFilteredGet(HttpExchange ex, String query) throws IOException {
        Map<String, String> params = parseQueryParams(query);

        if (params.containsKey("year")) {
            try {
                int targetYear = Integer.parseInt(params.get("year"));
                int maxYear = LocalDate.now().getYear() + 1;

                if (targetYear < 1888 || targetYear > maxYear) {
                    sendError(ex, 400, "Некорректный параметр запроса — 'year'");
                    return;
                }

                List<Movie> filteredMovies = moviesStore.getAll().stream()
                        .filter(movie -> movie.getYear() == targetYear)
                        .toList();

                sendJson(ex, 200, gson.toJson(filteredMovies));
            } catch (NumberFormatException e) {
                sendError(ex, 400, "Некорректный параметр запроса — 'year'");
            }
        } else {
            sendError(ex, 400, "Некорректный параметр запроса — 'year'");
        }
    }

    private void processGetById(HttpExchange ex, String path) throws IOException {
        try {
            int id = extractIdFromPath(path);
            Movie movie = moviesStore.getById(id);

            if (movie != null) {
                sendJson(ex, 200, gson.toJson(movie));
            } else {
                sendError(ex, 404, "Фильм с id " + id + " не найден");
            }
        } catch (NumberFormatException e) {
            sendError(ex, 400, "Некорректный формат ID ресурса");
        }
    }

    // =========================================================================
    // ЛОГИКА ОБРАБОТКИ POST-ЗАПРОСОВ
    // =========================================================================
    private void handlePost(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
            sendError(ex, 415, "Unsupported Media Type");
            return;
        }

        // Оптимизация: читаем и парсим в JSON напрямую из InputStream через Reader
        try (InputStream is = ex.getRequestBody();
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

            Movie movie = gson.fromJson(reader, Movie.class);
            List<String> details = validateMovie(movie);

            if (!details.isEmpty()) {
                ErrorResponse errorObj = new ErrorResponse("Ошибка валидации", details);
                sendJson(ex, 422, gson.toJson(errorObj));
                return;
            }

            int id = moviesStore.add(movie);
            movie.setId(id);

            sendJson(ex, 201, gson.toJson(movie));
        } catch (Exception e) {
            sendError(ex, 400, "Некорректный формат JSON");
        }
    }

    // =========================================================================
    // ЛОГИКА ОБРАБОТКИ DELETE-ЗАПРОСОВ
    // =========================================================================
    private void handleDelete(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        if (path.startsWith(MOVIES_PREFIX)) {
            try {
                int id = extractIdFromPath(path);
                Movie deletedMovie = moviesStore.delete(id);

                if (deletedMovie != null) {
                    sendNoContent(ex);
                } else {
                    sendError(ex, 404, "Фильм с id " + id + " не найден");
                }
            } catch (NumberFormatException e) {
                sendError(ex, 400, "Некорректный формат ID ресурса");
            }
        } else {
            sendError(ex, 400, "Не указан ID фильма для удаления");
        }
    }

    // =========================================================================
    // ВСПОМОГАТЕЛЬНЫЕ УТИЛИТАРНЫЕ МЕТОДЫ
    // =========================================================================

    // Безопасное извлечение ID с обработкой завершающего слэша
    private int extractIdFromPath(String path) throws NumberFormatException {
        String idString = path.substring(MOVIES_PREFIX.length());
        if (idString.endsWith("/")) {
            idString = idString.substring(0, idString.length() - 1);
        }
        return Integer.parseInt(idString);
    }

    // Парсинг параметров по имени
    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null || query.isBlank()) {
            return result;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] idx = pair.split("=", 2);
            if (idx.length == 2) {
                String key = URLDecoder.decode(idx[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(idx[1], StandardCharsets.UTF_8);
                result.put(key, value);
            } else if (idx.length == 1 && !idx[0].isBlank()) {
                String key = URLDecoder.decode(idx[0], StandardCharsets.UTF_8);
                result.put(key, "");
            }
        }
        return result;
    }

    private List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();

        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            errors.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > 100) {
            errors.add("длина названия не должна превышать 100 символов");
        }

        int maxYear = LocalDate.now().getYear() + 1;
        if (movie.getYear() < 1888 || movie.getYear() > maxYear) {
            errors.add("год должен быть между 1888 и " + maxYear);
        }

        return errors;
    }
}
