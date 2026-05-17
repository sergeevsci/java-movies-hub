package ru.practicum.moviehub.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;

import java.net.URI;
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
    com.google.gson.Gson testGson = new com.google.gson.Gson();

    @BeforeAll
    static void beforeAll() {
        server = new MoviesServer(PORT);
        server.start();

        // Создаем HTTP-клиент с таймаутом
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
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
        Movie inputMovie = new Movie("Inception", 2010);
        String requestBodyJson = testGson.toJson(inputMovie);

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
        Movie responseMovie = testGson.fromJson(postResponse.body(), Movie.class);

        // проверяем поля через assertEquals
        java.util.Objects.requireNonNull(responseMovie, "Сервер вернул пустое тело");
        assertEquals("Inception", responseMovie.getTitle(), "Название фильма не совпадает");
        assertEquals(2010, responseMovie.getYear(), "Год фильма не совпадает");
        assertTrue(responseMovie.getId() > 0, "Сервер должен был присвоить фильму валидный ID > 0");
    }
}