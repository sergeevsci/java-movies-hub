package ru.practicum.moviehub.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;

import java.net.URI;
import com.google.gson.Gson;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static final int PORT = 8080;
    private static MoviesServer server;
    private static HttpClient client;
    private static final Gson gson = new Gson();

    @BeforeAll
    static void beforeAll() {
        server = new MoviesServer(PORT);
        server.start();

        // Создаем HTTP-клиент с таймаутом
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @BeforeEach
    void beforeEach() {
        server.getMoviesStore().clear();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        // Собираем URI из константы BASE и нужного эндпоинта
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    @Test
    void addMovie_whenValid_returnsCreatedMovieWithId() throws Exception {
        Movie inputMovie = new Movie("Невероятная жизнь Уолтера Митти", 2013);
        String requestBodyJson = gson.toJson(inputMovie);

        // Строим POST-запрос
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> postResponse =
                client.send(postRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // проверяем код 201
        assertEquals(201, postResponse.statusCode(), "POST /movies с валидными данными должен вернуть 201");

        // Десериализуем ответ сервера обратно в объект Movie
        Movie responseMovie = gson.fromJson(postResponse.body(), Movie.class);

        // проверяем поля через assertEquals
        java.util.Objects.requireNonNull(responseMovie, "Сервер вернул пустое тело");
        assertEquals("Невероятная жизнь Уолтера Митти", responseMovie.getTitle(), "Название фильма не совпадает");
        assertEquals(2013, responseMovie.getYear(), "Год фильма не совпадает");
        assertTrue(responseMovie.getId() > 0, "Сервер должен был присвоить фильму валидный ID > 0");
    }

    @Test
    void getMovieById_whenExists_returnsMovieAndStatus200() throws Exception {
        // Создаем фильм, который мы будем искать
        Movie newMovie = new Movie("Матрица", 1999);
        String createJson = gson.toJson(newMovie);

        // Отправляем POST-запрос, чтобы сохранить его на сервере
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> postResponse =
                client.send(postRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(201, postResponse.statusCode(), "Фильм должен успешно создаться");

        // Десериализуем ответ, чтобы узнать, какой ID присвоил сервер
        Movie createdMovie = gson.fromJson(postResponse.body(), Movie.class);
        int savedId = createdMovie.getId();

        // Отправляем GET-запрос на эндпоинт /movies/{id}
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + savedId)) // Динамический URI с ID
                .GET()
                .build();

        HttpResponse<String> getResponse =
                client.send(getRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Проверяем статус 200 OK
        assertEquals(200, getResponse.statusCode(), "GET /movies/{id} должен вернуть статус 200");

        // Десериализуем полученный фильм и проверяем корректность полей
        Movie fetchedMovie = gson.fromJson(getResponse.body(), Movie.class);

        java.util.Objects.requireNonNull(fetchedMovie, "Тело ответа не должно быть пустым");
        assertEquals(savedId, fetchedMovie.getId(), "ID полученного фильма должен совпадать");
        assertEquals("Матрица", fetchedMovie.getTitle(), "Название фильма должно совпадать");
        assertEquals(1999, fetchedMovie.getYear(), "Год фильма должен совпадать");
    }

    @Test
    void getMovieById_whenNotExists_returns404NotFound() throws Exception {
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/9999"))
                .GET()
                .build();

        HttpResponse<String> getResponse =
                client.send(getRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Проверяем 404
        assertEquals(404, getResponse.statusCode(), "Если фильма нет, должен вернуться статус 404");

        assertTrue(getResponse.body().contains("\"error\":"), "Ответ должен содержать описание ошибки");
    }

    @Test
    void deleteMovie_whenExists_returns204AndThen404OnRepeatedDelete() throws Exception {
        Movie newMovie = new Movie("Матрица", 1999);
        String createJson = gson.toJson(newMovie);

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> postResponse =
                client.send(postRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        Movie createdMovie = gson.fromJson(postResponse.body(), Movie.class);
        int savedId = createdMovie.getId();

        // Отправляем DELETE запрос на удаление созданного фильма
        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + savedId))
                .DELETE()
                .build();

        HttpResponse<String> deleteResponse =
                client.send(deleteRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Проверяем, что первый вызов вернул код 204 No Content
        assertEquals(204, deleteResponse.statusCode(), "Успешное удаление должно возвращать статус 204");

        // Отправляем такой же DELETE запрос еще разок
        HttpResponse<String> repeatedDeleteResponse =
                client.send(deleteRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Теперь сервер обязан ответить 404, так как фильма уже нет
        assertEquals(404, repeatedDeleteResponse.statusCode(), "Повторное удаление должно вернуть 404 Not Found");
    }

    @Test
    void getMovies_withYearFilter_returnsOnlyMatchingMovies() throws Exception {
        Movie m1 = new Movie("Шрекс", 2001);
        client.send(HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(m1), StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.discarding());

        Movie m2 = new Movie("Че там еще было", 2010);
        client.send(HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(m2), StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.discarding());

        // Делаем GET-запрос с фильтром
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2001"))
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(getRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, response.statusCode(), "Запрос с корректным фильтром должен вернуть 200");

        Movie[] filteredMovies = gson.fromJson(response.body(), Movie[].class);

        assertEquals(1, filteredMovies.length, "Должен вернуться только один фильм");
        assertEquals("Шрекс", filteredMovies[0].getTitle(), "Должен вернуться фильм 'Шрекс'");
        assertEquals(2001, filteredMovies[0].getYear(), "Год должен быть 2001");
    }

    @Test
    void getMovies_withBadYearFilter_returns400BadRequest() throws Exception {
        // Отправляем запрос с заведомо некорректным годом
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=invalid_year"))
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(getRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, response.statusCode(), "Некорректный год должен возвращать 400");

        ErrorResponse errorObj = gson.fromJson(response.body(), ErrorResponse.class);

        java.util.Objects.requireNonNull(errorObj, "Тело ответа не должно быть пустым");
        assertEquals("Некорректный параметр запроса — 'year'", errorObj.getError(),
                "Сообщение об ошибке не совпадает с ТЗ");
    }
}