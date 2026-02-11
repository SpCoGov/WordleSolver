package top.spco.wordlesolver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Web server for Wordle Simulation Viewer - same functionality as WordleSimViewerApp.
 */
public class WordleWebServer {

    private static final Gson GSON = new GsonBuilder().create();
    private static final int PORT = 8765;
    private static File defaultAnswersFile;

    public static void main(String[] args) throws Exception {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        defaultAnswersFile = projectRoot.resolve("src/main/resources/answers").toFile();
        if (!defaultAnswersFile.exists()) {
            defaultAnswersFile = projectRoot.resolve("build/resources/main/answers").toFile();
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        server.createContext("/api/", new ApiHandler());
        server.createContext("/", new StaticHandler());
        server.setExecutor(executor);
        server.start();
        System.out.println("Wordle Simulation Viewer: http://localhost:" + PORT + "/");
    }

    static class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                addCors(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            addCors(exchange);
            String path = exchange.getRequestURI().getPath();
            try {
                if (path.equals("/api/answers") && "GET".equals(exchange.getRequestMethod())) {
                    handleGetAnswers(exchange);
                    return;
                }
                if (path.equals("/api/simulate/random") && "POST".equals(exchange.getRequestMethod())) {
                    handleSimulateRandom(exchange);
                    return;
                }
                if (path.equals("/api/simulate/list") && "POST".equals(exchange.getRequestMethod())) {
                    handleSimulateList(exchange);
                    return;
                }
                if (path.equals("/api/simulate/single") && "POST".equals(exchange.getRequestMethod())) {
                    handleSimulateSingle(exchange);
                    return;
                }
                if (path.equals("/api/manual/refresh") && "POST".equals(exchange.getRequestMethod())) {
                    handleManualRefresh(exchange);
                    return;
                }
                sendJson(exchange, 404, Map.of("error", "Not found"));
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, Map.of("error", e.getMessage()));
            }
        }

        private void addCors(HttpExchange exchange) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        }

        private void handleGetAnswers(HttpExchange exchange) throws IOException {
            String q = exchange.getRequestURI().getQuery();
            String path = null;
            if (q != null) {
                for (String param : q.split("&")) {
                    if (param.startsWith("path=")) {
                        try { path = URLDecoder.decode(param.substring(5), StandardCharsets.UTF_8); } catch (Exception ignored) {}
                    }
                }
            }
            File f = resolveAnswersFile(path);
            if (f == null || !f.exists()) {
                sendJson(exchange, 400, Map.of("error", "Answers file not found"));
                return;
            }
            int length = 5;
            if (q != null) {
                for (String param : q.split("&")) {
                    if (param.startsWith("length=")) {
                        try { length = Integer.parseInt(param.substring(7)); } catch (NumberFormatException ignored) {}
                        break;
                    }
                }
            }
            List<String> words = loadAnswersInOrder(f, length);
            sendJson(exchange, 200, Map.of("words", words, "total", words.size()));
        }

        @SuppressWarnings("unchecked")
        private void handleSimulateRandom(HttpExchange exchange) throws IOException {
            Map<String, Object> body = readJson(exchange, Map.class);
            int testTimes = intVal(body, "testTimes", 1000);
            int topK = intVal(body, "topK", 20);
            int maxAttempts = intVal(body, "maxAttempts", 6);
            int length = intVal(body, "wordLength", 5);
            int threads = Math.max(1, intVal(body, "threads", Runtime.getRuntime().availableProcessors()));
            boolean hardMode = Boolean.TRUE.equals(body.get("hardMode"));
            String answersPath = (String) body.get("answersPath");
            ScoreParams params = readScoreParams(body);

            File answersFile = resolveAnswersFile(answersPath);
            if (answersFile == null || !answersFile.exists()) {
                sendJson(exchange, 400, Map.of("error", "Answers file not found"));
                return;
            }

            long start = System.currentTimeMillis();
            int wins = 0;
            long attemptsSum = 0;
            List<WordleSimViewerApp.FailureRecord> failures = new ArrayList<>();
            ExecutorService exec = Executors.newFixedThreadPool(threads);
            var completion = new java.util.concurrent.ExecutorCompletionService<WordleSimViewerApp.SimResult>(exec);

            for (int i = 0; i < testTimes; i++) {
                final int idx = i;
                completion.submit(() -> runSingleGame(idx, null, maxAttempts, length, answersFile, topK, params, hardMode));
            }

            try {
                for (int i = 0; i < testTimes; i++) {
                    WordleSimViewerApp.SimResult r = completion.take().get();
                    attemptsSum += r.attempts;
                    if (r.win) wins++;
                    else failures.add(WordleSimViewerApp.FailureRecord.from(r));
                }
            } catch (Exception e) {
                exec.shutdownNow();
                throw new RuntimeException(e);
            }
            exec.shutdown();

            long end = System.currentTimeMillis();
            Map<String, Object> result = new HashMap<>();
            result.put("testTimes", testTimes);
            result.put("completed", testTimes);
            result.put("wins", wins);
            result.put("failures", failures);
            result.put("avgAttempts", testTimes == 0 ? 0 : attemptsSum * 1.0 / testTimes);
            result.put("totalSeconds", (end - start) / 1000.0);
            result.put("threads", threads);
            result.put("hardMode", hardMode);
            sendJson(exchange, 200, result);
        }

        @SuppressWarnings("unchecked")
        private void handleSimulateList(HttpExchange exchange) throws IOException {
            Map<String, Object> body = readJson(exchange, Map.class);
            int topK = intVal(body, "topK", 20);
            int maxAttempts = intVal(body, "maxAttempts", 6);
            int length = intVal(body, "wordLength", 5);
            int threads = Math.max(1, intVal(body, "threads", Runtime.getRuntime().availableProcessors()));
            boolean hardMode = Boolean.TRUE.equals(body.get("hardMode"));
            String answersPath = (String) body.get("answersPath");
            ScoreParams params = readScoreParams(body);

            File answersFile = resolveAnswersFile(answersPath);
            if (answersFile == null || !answersFile.exists()) {
                sendJson(exchange, 400, Map.of("error", "Answers file not found"));
                return;
            }

            List<String> answers = loadAnswersInOrder(answersFile, length);
            if (answers.isEmpty()) {
                sendJson(exchange, 400, Map.of("error", "No answers with length " + length));
                return;
            }

            long start = System.currentTimeMillis();
            int wins = 0;
            long attemptsSum = 0;
            List<WordleSimViewerApp.GameRecord> allGames = new ArrayList<>();
            ExecutorService exec = Executors.newFixedThreadPool(threads);
            var completion = new java.util.concurrent.ExecutorCompletionService<WordleSimViewerApp.SimResult>(exec);

            for (int i = 0; i < answers.size(); i++) {
                final int idx = i;
                final String answer = answers.get(i);
                completion.submit(() -> runSingleGame(idx, answer, maxAttempts, length, answersFile, topK, params, hardMode));
            }

            try {
                for (int i = 0; i < answers.size(); i++) {
                    WordleSimViewerApp.SimResult r = completion.take().get();
                    attemptsSum += r.attempts;
                    if (r.win) wins++;
                    allGames.add(WordleSimViewerApp.GameRecord.from(r));
                }
            } catch (Exception e) {
                exec.shutdownNow();
                throw new RuntimeException(e);
            }
            exec.shutdown();

            long end = System.currentTimeMillis();
            Map<String, Object> result = new HashMap<>();
            result.put("testTimes", answers.size());
            result.put("completed", answers.size());
            result.put("wins", wins);
            result.put("allGames", allGames);
            result.put("avgAttempts", answers.size() == 0 ? 0 : attemptsSum * 1.0 / answers.size());
            result.put("totalSeconds", (end - start) / 1000.0);
            result.put("threads", threads);
            result.put("hardMode", hardMode);
            sendJson(exchange, 200, result);
        }

        @SuppressWarnings("unchecked")
        private void handleSimulateSingle(HttpExchange exchange) throws IOException {
            Map<String, Object> body = readJson(exchange, Map.class);
            String selectedWord = (String) body.get("selectedWord");
            int topK = intVal(body, "topK", 20);
            int maxAttempts = intVal(body, "maxAttempts", 6);
            int length = intVal(body, "wordLength", 5);
            boolean hardMode = Boolean.TRUE.equals(body.get("hardMode"));
            String answersPath = (String) body.get("answersPath");
            ScoreParams params = readScoreParams(body);

            if (selectedWord == null || selectedWord.isBlank()) {
                sendJson(exchange, 400, Map.of("error", "Selected word is empty"));
                return;
            }
            selectedWord = selectedWord.trim().toUpperCase();
            if (selectedWord.length() != length || !selectedWord.chars().allMatch(Character::isLetter)) {
                sendJson(exchange, 400, Map.of("error", "Invalid selected word"));
                return;
            }

            File answersFile = resolveAnswersFile(answersPath);
            if (answersFile == null || !answersFile.exists()) {
                sendJson(exchange, 400, Map.of("error", "Answers file not found"));
                return;
            }

            long start = System.currentTimeMillis();
            WordleSimViewerApp.SimResult result = runSingleGame(0, selectedWord, maxAttempts, length, answersFile, topK, params, hardMode);
            long end = System.currentTimeMillis();

            Map<String, Object> resp = new HashMap<>();
            resp.put("result", result);
            resp.put("totalSeconds", (end - start) / 1000.0);
            sendJson(exchange, 200, resp);
        }

        @SuppressWarnings("unchecked")
        private void handleManualRefresh(HttpExchange exchange) throws IOException {
            Map<String, Object> body = readJson(exchange, Map.class);
            String answersPath = (String) body.get("answersPath");
            int topK = intVal(body, "topK", 200);
            boolean hardMode = Boolean.TRUE.equals(body.get("hardMode"));
            int maxAttempts = intVal(body, "maxAttempts", 6);
            int wordLength = intVal(body, "wordLength", 5);
            ScoreParams params = readScoreParams(body);

            List<Map<String, Object>> guessesList = (List<Map<String, Object>>) body.get("guesses");
            if (guessesList == null) guessesList = List.of();

            File answersFile = resolveAnswersFile(answersPath);
            if (answersFile == null || !answersFile.exists()) {
                sendJson(exchange, 400, Map.of("error", "Answers file not found"));
                return;
            }

            WordleSolver solver = new WordleSolver(answersFile);
            solver.setMaxGuessTime(maxAttempts);
            solver.setHardMode(hardMode);
            applyScoreParams(solver, params);

            for (Map<String, Object> g : guessesList) {
                String word = (String) g.get("word");
                String result = (String) g.get("result");
                if (word != null && result != null && word.length() == wordLength) {
                    try {
                        solver.applyGuess(word.trim().toUpperCase(), result);
                    } catch (Exception e) {
                        sendJson(exchange, 400, Map.of("error", "Invalid guess: " + e.getMessage()));
                        return;
                    }
                }
            }

            Map<String, Integer> scoreMap = solver.printAnswers();
            Set<String> guessed = solver.getGuessedWords();
            if (!guessed.isEmpty()) {
                scoreMap.entrySet().removeIf(e -> guessed.contains(e.getKey().toUpperCase()));
            }

            List<WordleSimViewerApp.CandidateScore> candidates = scoreMap.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(Math.max(1, topK))
                    .map(e -> new WordleSimViewerApp.CandidateScore(e.getKey().toUpperCase(), e.getValue()))
                    .toList();

            String nextGuess = solver.nextGuess();
            WordleSolver.GuessDecision decision = solver.getLastDecision();

            List<WordleSimViewerApp.ProbeWord> probes = new ArrayList<>();
            if ("PROBE_SINGLE_SLOT".equals(decision.strategy) && decision.matchedWords != null) {
                for (String entry : decision.matchedWords) {
                    String word = entry;
                    String newLetters = "";
                    int idx = entry.indexOf('(');
                    if (idx > 0) {
                        word = entry.substring(0, idx);
                        newLetters = entry.substring(idx).replace("newLetters=", "");
                    }
                    probes.add(new WordleSimViewerApp.ProbeWord(word.toUpperCase(), newLetters, decision.strategy));
                }
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("candidates", candidates);
            resp.put("probes", probes);
            resp.put("candidatesCount", scoreMap.size());
            resp.put("probesCount", probes.size());
            resp.put("nextGuess", nextGuess != null ? nextGuess.toUpperCase() : "");
            resp.put("strategy", decision.strategy != null ? decision.strategy : "");
            sendJson(exchange, 200, resp);
        }

        private File resolveAnswersFile(String path) {
            if (path != null && !path.isBlank()) {
                File f = new File(path.trim());
                if (f.isAbsolute() && f.exists()) return f;
                f = new File(System.getProperty("user.dir"), path.trim());
                if (f.exists()) return f;
            }
            return defaultAnswersFile;
        }

        private ScoreParams readScoreParams(Map<String, Object> body) {
            ScoreParams p = new ScoreParams();
            p.charFrequencyWeight = intVal(body, "charFrequencyWeight", 1);
            p.positionTopBonus = intVal(body, "positionTopBonus", 100);
            p.repeatPenaltyBase = intVal(body, "repeatPenaltyBase", 25);
            return p;
        }

        private int intVal(Map<String, Object> m, String key, int def) {
            Object v = m.get(key);
            if (v instanceof Number) return ((Number) v).intValue();
            if (v instanceof String) {
                try { return Integer.parseInt((String) v); } catch (Exception ignored) {}
            }
            return def;
        }

        private void applyScoreParams(WordleSolver solver, ScoreParams p) {
            solver.setCharFrequencyWeight(p.charFrequencyWeight);
            solver.setPositionTopBonus(p.positionTopBonus);
            solver.setRepeatPenaltyBase(p.repeatPenaltyBase);
        }

        private WordleSimViewerApp.SimResult runSingleGame(int index, String fixedAnswer, int maxAttempts, int length,
                                        File answersFile, int topK, ScoreParams scoreParams, boolean hardMode) {
            Wordle wordle = fixedAnswer == null
                    ? Wordle.start(maxAttempts, length, answersFile)
                    : Wordle.start(maxAttempts, fixedAnswer);

            WordleSolver solver = new WordleSolver(answersFile);
            solver.setMaxGuessTime(maxAttempts);
            solver.setHardMode(hardMode);
            applyScoreParams(solver, scoreParams);

            List<WordleSimViewerApp.StepDetail> steps = new ArrayList<>();
            Map<String, Integer> lastScoreMap = null;

            try {
                while (!wordle.isGameOver()) {
                    Map<String, Integer> scoreMap = solver.printAnswers();
                    Set<String> guessed = solver.getGuessedWords();
                    if (!guessed.isEmpty()) {
                        scoreMap.entrySet().removeIf(e -> guessed.contains(e.getKey().toUpperCase()));
                    }
                    lastScoreMap = scoreMap;

                    if (scoreMap.isEmpty()) {
                        WordleSimViewerApp.StepDetail step = new WordleSimViewerApp.StepDetail();
                        step.guess = "(no candidate)";
                        step.result = "NO CANDIDATES";
                        step.topCandidates = List.of();
                        steps.add(step);
                        break;
                    }

                    List<WordleSimViewerApp.CandidateScore> topCandidates = scoreMap.entrySet().stream()
                            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                            .limit(Math.max(1, topK))
                            .map(e -> new WordleSimViewerApp.CandidateScore(e.getKey(), e.getValue()))
                            .toList();

                    String guessWord = solver.nextGuess();
                    if (guessWord == null || guessWord.isEmpty()) {
                        guessWord = topCandidates.get(0).word;
                    }
                    WordleSolver.GuessDecision decision = solver.getLastDecision();

                    String result = wordle.guess(guessWord);

                    WordleSimViewerApp.StepDetail step = new WordleSimViewerApp.StepDetail();
                    step.guess = guessWord;
                    step.result = result;
                    step.topCandidates = topCandidates;
                    step.strategy = decision.strategy;
                    step.strategyReason = decision.strategyReason;
                    step.filterRounds = decision.filterRounds;
                    step.checkedWords = decision.checkedWords;
                    step.targetCoverage = decision.targetCoverage;
                    step.matchedWords = decision.matchedWords != null ? decision.matchedWords : List.of();
                    step.familyCandidates = decision.familyCandidates != null ? decision.familyCandidates : List.of();
                    step.filterPath = decision.filterPath != null ? decision.filterPath : List.of();
                    steps.add(step);

                    if ("GAME OVER".equals(result)) break;
                    solver.applyGuess(guessWord, result);
                }
            } catch (Exception ex) {
                WordleSimViewerApp.StepDetail err = new WordleSimViewerApp.StepDetail();
                err.guess = "(exception)";
                err.result = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                err.topCandidates = (lastScoreMap == null) ? List.of() : lastScoreMap.entrySet().stream()
                        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                        .limit(Math.max(1, topK))
                        .map(e -> new WordleSimViewerApp.CandidateScore(e.getKey(), e.getValue()))
                        .toList();
                steps.add(err);
            }

            WordleSimViewerApp.SimResult r = new WordleSimViewerApp.SimResult();
            r.index = index;
            r.answer = wordle.getAnswer();
            r.attempts = wordle.getAttempts();
            r.win = wordle.isWin();
            r.steps = steps;
            return r;
        }

        private static List<String> loadAnswersInOrder(File f, int length) throws IOException {
            List<String> lines = Files.readAllLines(f.toPath());
            List<String> out = new ArrayList<>();
            for (String line : lines) {
                if (line == null) continue;
                String w = line.trim().toUpperCase();
                if (w.length() == length) out.add(w);
            }
            return out;
        }

        private void sendJson(HttpExchange ex, int code, Object obj) throws IOException {
            String json = GSON.toJson(obj);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            ex.sendResponseHeaders(code, json.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }

        @SuppressWarnings("unchecked")
        private <T> T readJson(HttpExchange ex, Class<T> type) throws IOException {
            try (Reader r = new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8)) {
                return (T) GSON.fromJson(r, type);
            }
        }
    }

    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            path = path.replaceFirst("^/", "");

            InputStream in = WordleWebServer.class.getResourceAsStream("/web/" + path);
            if (in == null) {
                in = WordleWebServer.class.getResourceAsStream("/web/index.html");
            }
            if (in == null) {
                exchange.sendResponseHeaders(404, 0);
                return;
            }

            String contentType = "text/html";
            if (path.endsWith(".css")) contentType = "text/css";
            else if (path.endsWith(".js")) contentType = "application/javascript";

            exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                in.transferTo(out);
            }
        }
    }

    static class ScoreParams {
        int charFrequencyWeight;
        int positionTopBonus;
        int repeatPenaltyBase;
    }
}
