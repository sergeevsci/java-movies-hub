package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MoviesServer {
    private final HttpServer server;
    private final MoviesStore moviesStore;

    public MoviesServer(MoviesStore moviesStore, int port) {
        this.moviesStore = moviesStore;
        try {
            // создайте сервер
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать HTTP-сервер", e);
        }
    }

    public void start() {
        // запустите сервер
        server.start();
        System.out.println("Сервер запущен");
    }

    public void stop() {
        // остановите сервер
        server.stop(0);
        System.out.println("Сервер остановлен");
    }
}