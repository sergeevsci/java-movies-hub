package ru.practicum.moviehub.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;

import java.net.URI;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        HttpResponse<String> response = sendGet("/movies");

        assertEquals(200, response.statusCode());
        assertJsonContentType(response);

        List<Movie> movies = parseMoviesList(response.body());
        assertTrue(movies.isEmpty(), "Если фильмов нет, сервер должен вернуть пустой список");
    }

    @Test
    void getMovies_whenHasMovies_returnsPreviouslyAddedMovies() throws Exception {
        sendCreateMovie("Матрица", 1999);
        sendCreateMovie("Шрек", 2001);

        HttpResponse<String> response = sendGet("/movies");

        assertEquals(200, response.statusCode());
        assertJsonContentType(response);

        List<Movie> movies = parseMoviesList(response.body());
        assertEquals(2, movies.size());
        assertTrue(movies.stream().anyMatch(m -> "Матрица".equals(m.getTitle()) && m.getYear() == 1999));
        assertTrue(movies.stream().anyMatch(m -> "Шрек".equals(m.getTitle()) && m.getYear() == 2001));
    }

    @Test
    void addMovie_whenValid_returnsCreatedMovieWithId() throws Exception {
        HttpResponse<String> postResponse = sendCreateMovie("Невероятная жизнь Уолтера Митти", 2013);

        assertEquals(201, postResponse.statusCode(), "POST /movies с валидными данными должен вернуть 201");
        assertJsonContentType(postResponse);

        Movie responseMovie = gson.fromJson(postResponse.body(), Movie.class);
        assertNotNull(responseMovie, "Сервер вернул пустое тело");
        assertEquals("Невероятная жизнь Уолтера Митти", responseMovie.getTitle(), "Название фильма не совпадает");
        assertEquals(2013, responseMovie.getYear(), "Год фильма не совпадает");
        assertTrue(responseMovie.getId() > 0, "Сервер должен был присвоить фильму валидный ID > 0");
    }

    @Test
    void addMovie_whenTitleEmpty_returns422WithDetails() throws Exception {
        HttpResponse<String> response = sendPostJson("{\"title\":\"   \",\"year\":2000}");

        assertEquals(422, response.statusCode());
        assertJsonContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertNotNull(error);
        assertEquals("Ошибка валидации", error.getError());
        assertNotNull(error.getDetails());
        assertFalse(error.getDetails().isEmpty());
    }

    @Test
    void addMovie_whenTitleTooLong_returns422() throws Exception {
        String longTitle = "A".repeat(101);
        HttpResponse<String> response = sendPostJson("{\"title\":\"" + longTitle + "\",\"year\":2000}");

        assertEquals(422, response.statusCode());
        assertJsonContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertNotNull(error);
        assertEquals("Ошибка валидации", error.getError());
        assertNotNull(error.getDetails());
        assertFalse(error.getDetails().isEmpty());
    }

    @Test
    void addMovie_whenYearInvalid_returns422() throws Exception {
        int invalidYear = LocalDate.now().getYear() + 2;
        HttpResponse<String> response = sendPostJson("{\"title\":\"Фильм\",\"year\":" + invalidYear + "}");

        assertEquals(422, response.statusCode());
        assertJsonContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertNotNull(error);
        assertEquals("Ошибка валидации", error.getError());
        assertNotNull(error.getDetails());
        assertFalse(error.getDetails().isEmpty());
    }

    @Test
    void addMovie_whenWrongContentType_returns415WithErrorObject() throws Exception {
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("not json", StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(postRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, response.statusCode());
        assertJsonContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertNotNull(error);
        assertNotNull(error.getError());
    }

    @Test
    void addMovie_whenMalformedJson_returns400WithErrorObject() throws Exception {
        HttpResponse<String> response = sendPostJson("{\"title\":\"Матрица\",\"year\":1999");

        assertEquals(400, response.statusCode());
        assertJsonContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertNotNull(error);
        assertNotNull(error.getError());
    }

    @Test
    void getMovieById_whenExists_returnsMovieAndStatus200() throws Exception {
        HttpResponse<String> postResponse = sendCreateMovie("Матрица", 1999);
        Movie createdMovie = gson.fromJson(postResponse.body(), Movie.class);
        int savedId = createdMovie.getId();
        HttpResponse<String> getResponse = sendGet("/movies/" + savedId);

        assertEquals(200, getResponse.statusCode(), "GET /movies/{id} должен вернуть статус 200");
        assertJsonContentType(getResponse);

        Movie fetchedMovie = gson.fromJson(getResponse.body(), Movie.class);
        assertNotNull(fetchedMovie, "Тело ответа не должно быть пустым");
        assertEquals(savedId, fetchedMovie.getId(), "ID полученного фильма должен совпадать");
        assertEquals("Матрица", fetchedMovie.getTitle(), "Название фильма должно совпадать");
        assertEquals(1999, fetchedMovie.getYear(), "Год фильма должен совпадать");
    }

    @Test
    void getMovieById_whenNotExists_returns404NotFound() throws Exception {
        HttpResponse<String> getResponse = sendGet("/movies/9999");

        assertEquals(404, getResponse.statusCode(), "Если фильма нет, должен вернуться статус 404");
        assertJsonContentType(getResponse);

        ErrorResponse error = gson.fromJson(getResponse.body(), ErrorResponse.class);
        assertNotNull(error);
        assertNotNull(error.getError());
    }

    @Test
    void getMovieById_whenIdNotNumber_returns400() throws Exception {
        HttpResponse<String> response = sendGet("/movies/not-a-number");

        assertEquals(400, response.statusCode());
        assertJsonContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertNotNull(error);
        assertNotNull(error.getError());
    }

    @Test
    void deleteMovie_whenExists_returns204AndThen404OnRepeatedDelete() throws Exception {
        HttpResponse<String> postResponse = sendCreateMovie("Матрица", 1999);
        Movie createdMovie = gson.fromJson(postResponse.body(), Movie.class);
        int savedId = createdMovie.getId();

        HttpResponse<String> deleteResponse = sendDelete("/movies/" + savedId);
        assertEquals(204, deleteResponse.statusCode(), "Успешное удаление должно возвращать статус 204");
        assertJsonContentType(deleteResponse);

        HttpResponse<String> repeatedDeleteResponse = sendDelete("/movies/" + savedId);
        assertEquals(404, repeatedDeleteResponse.statusCode(), "Повторное удаление должно вернуть 404 Not Found");
        assertJsonContentType(repeatedDeleteResponse);

        ErrorResponse error = gson.fromJson(repeatedDeleteResponse.body(), ErrorResponse.class);
        assertNotNull(error);
        assertNotNull(error.getError());
    }

    @Test
    void deleteMovie_whenIdNotNumber_returns400() throws Exception {
        HttpResponse<String> response = sendDelete("/movies/wrong-id");

        assertEquals(400, response.statusCode());
        assertJsonContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertNotNull(error);
        assertNotNull(error.getError());
    }

    @Test
    void getMovies_withYearFilter_returnsOnlyMatchingMovies() throws Exception {
        sendCreateMovie("Шрек", 2001);
        sendCreateMovie("Че там еще было", 2010);

        HttpResponse<String> response = sendGet("/movies?year=2001");

        assertEquals(200, response.statusCode(), "Запрос с корректным фильтром должен вернуть 200");
        assertJsonContentType(response);

        List<Movie> filteredMovies = parseMoviesList(response.body());

        assertEquals(1, filteredMovies.size(), "Должен вернуться только один фильм");
        assertEquals("Шрек", filteredMovies.get(0).getTitle(), "Должен вернуться фильм 'Шрек'");
        assertEquals(2001, filteredMovies.get(0).getYear(), "Год должен быть 2001");
    }

    @Test
    void getMovies_withYearFilter_whenNoMatches_returnsEmptyList() throws Exception {
        sendCreateMovie("Матрица", 1999);

        HttpResponse<String> response = sendGet("/movies?year=2007");

        assertEquals(200, response.statusCode());
        assertJsonContentType(response);

        List<Movie> filteredMovies = parseMoviesList(response.body());
        assertTrue(filteredMovies.isEmpty());
    }

    @Test
    void getMovies_withBadYearFilter_returns400BadRequest() throws Exception {
        HttpResponse<String> response = sendGet("/movies?year=invalid_year");

        assertEquals(400, response.statusCode(), "Некорректный год должен возвращать 400");
        assertJsonContentType(response);

        ErrorResponse errorObj = gson.fromJson(response.body(), ErrorResponse.class);

        assertNotNull(errorObj, "Тело ответа не должно быть пустым");
        assertEquals("Некорректный параметр запроса — 'year'", errorObj.getError(),
                "Сообщение об ошибке не совпадает с ТЗ");
    }

    @Test
    void unsupportedMethod_returns405MethodNotAllowed() throws Exception {
        HttpRequest putRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .PUT(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(putRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(405, response.statusCode());
        assertJsonContentType(response);

        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertNotNull(error);
        assertNotNull(error.getError());
    }

    private HttpResponse<String> sendGet(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendDelete(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .DELETE()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendPostJson(String json) throws Exception {
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return client.send(postRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendCreateMovie(String title, int year) throws Exception {
        Movie movie = new Movie(title, year);
        return sendPostJson(gson.toJson(movie));
    }

    private List<Movie> parseMoviesList(String jsonBody) {
        return gson.fromJson(jsonBody, new TypeToken<List<Movie>>() {
        }.getType());
    }

    private void assertJsonContentType(HttpResponse<String> response) {
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType,
                "Content-Type должен быть application/json; charset=UTF-8");
    }
}
