package ru.practicum.moviehub;

import ru.practicum.moviehub.http.MoviesServer;

public class MovieHubApp {

    private static final int PORT = 8080;

    public static void main(String[] args) {
        final MoviesServer server = new MoviesServer(PORT);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();

    }

}