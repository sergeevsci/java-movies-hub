package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MoviesStore {
    private final Map<Integer, Movie> movieStore = new HashMap<>();

    private int currentId = 1;

    public int add(Movie movie) {
        int id = currentId++;
        movieStore.put(id, movie);
        return id;
    }

    public Movie getById(int id) {
        return movieStore.get(id);
    }

    public List<Movie> getAll() {
        return new ArrayList<>(movieStore.values());
    }

    public Movie delete(int id) {
        return movieStore.remove(id);
    }

    public void clear() {
        movieStore.clear();
        currentId = 1; // Сбрасываем счетчик ID, чтобы каждый тест начинался с чистого листа
    }
}