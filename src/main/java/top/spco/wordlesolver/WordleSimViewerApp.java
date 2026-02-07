package top.spco.wordlesolver;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WordleSimViewerApp extends Application {

    private final TextField tfTestTimes = new TextField("10000");
    private final TextField tfTopK = new TextField("50");
    private final TextField tfMaxAttempts = new TextField("6");
    private final TextField tfWordLength = new TextField("5");
    private final TextField tfAnswersPath = new TextField("/Users/spco/IdeaProjects/WordleSolver/src/main/resources/answers");

    private final Button btnRun = new Button("Run");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label lbStats = new Label("Ready.");

    private final TableView<FailureRecord> tvFailures = new TableView<>();
    private final TextArea taDetails = new TextArea();

    @Override
    public void start(Stage stage) {
        GridPane controls = new GridPane();
        controls.setHgap(10);
        controls.setVgap(8);

        int r = 0;
        controls.addRow(r++, new Label("Test Times"), tfTestTimes, new Label("TopK"), tfTopK);
        controls.addRow(r++, new Label("Max Attempts"), tfMaxAttempts, new Label("Word Length"), tfWordLength);
        controls.addRow(r++, new Label("Answers File"), tfAnswersPath);

        HBox runBar = new HBox(10, btnRun, progressBar, lbStats);
        runBar.setPadding(new Insets(8, 0, 0, 0));
        HBox.setHgrow(progressBar, Priority.ALWAYS);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        VBox leftTop = new VBox(10, controls, runBar);
        leftTop.setPadding(new Insets(12));

        // Failures table
        setupFailuresTable();
        VBox left = new VBox(10, leftTop, new Label("Failed Games"), tvFailures);
        VBox.setVgrow(tvFailures, Priority.ALWAYS);

        // Details
        taDetails.setEditable(false);
        taDetails.setWrapText(false);

        VBox right = new VBox(10, new Label("Failure Details"), taDetails);
        right.setPadding(new Insets(12));
        VBox.setVgrow(taDetails, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(left, right);
        splitPane.setDividerPositions(0.55);

        btnRun.setOnAction(e -> runSimulations());

        Scene scene = new Scene(splitPane, 1200, 720);
        stage.setTitle("Wordle Simulation Viewer");
        stage.setScene(scene);
        stage.show();
    }

    private void setupFailuresTable() {
        TableColumn<FailureRecord, Number> colIndex = new TableColumn<>("#");
        colIndex.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().index));

        TableColumn<FailureRecord, String> colAnswer = new TableColumn<>("Answer");
        colAnswer.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().answer));

        TableColumn<FailureRecord, Number> colAttempts = new TableColumn<>("Attempts");
        colAttempts.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().attempts));

        TableColumn<FailureRecord, String> colLastGuess = new TableColumn<>("Last Guess");
        colLastGuess.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().lastGuess));

        tvFailures.getColumns().addAll(colIndex, colAnswer, colAttempts, colLastGuess);
        tvFailures.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        tvFailures.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                taDetails.clear();
                return;
            }
            taDetails.setText(formatFailure(selected));
        });
    }

    private void runSimulations() {
        int testTimes = parseInt(tfTestTimes.getText(), 1000);
        int topK = parseInt(tfTopK.getText(), 20);
        int maxAttempts = parseInt(tfMaxAttempts.getText(), 6);
        int length = parseInt(tfWordLength.getText(), 5);
        File answersFile = new File(tfAnswersPath.getText().trim());

        btnRun.setDisable(true);

        var failuresList = FXCollections.<FailureRecord>observableArrayList();
        tvFailures.setItems(failuresList);

        taDetails.clear();
        lbStats.setText("Running...");
        progressBar.setProgress(0);

        Task<SimSummary> task = new Task<>() {
            @Override
            protected SimSummary call() {
                long start = System.currentTimeMillis();

                int wins = 0;
                int completed = 0;
                long attemptsSum = 0;

                for (int i = 0; i < testTimes; i++) {
                    SimResult result = runSingleGame(i, maxAttempts, length, answersFile, topK);

                    completed++;
                    attemptsSum += result.attempts;

                    if (result.win) {
                        wins++;
                    } else {
                        FailureRecord fr = FailureRecord.from(result);

                        Platform.runLater(() -> failuresList.add(fr));
                    }

                    if (i % 5 == 0) {
                        updateProgress(i + 1, testTimes);
                        updateMessage(buildStats(completed, wins, failuresList.size(), attemptsSum));
                    }
                }

                long end = System.currentTimeMillis();

                SimSummary summary = new SimSummary();
                summary.testTimes = testTimes;
                summary.completed = completed;
                summary.wins = wins;
                summary.failures = new ArrayList<>(failuresList); // 复制一份结果
                summary.avgAttempts = completed == 0 ? 0 : (attemptsSum * 1.0 / completed);
                summary.totalSeconds = (end - start) / 1000.0;
                return summary;
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());
        lbStats.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> {
            progressBar.progressProperty().unbind();
            lbStats.textProperty().unbind();

            SimSummary summary = task.getValue();
            lbStats.setText(String.format(
                    "Done. Time=%.2fs, Completed=%d, Wins=%d, WinRate=%.2f%%, AvgAttempts=%.3f, Failures=%d",
                    summary.totalSeconds,
                    summary.completed,
                    summary.wins,
                    summary.completed == 0 ? 0 : (summary.wins * 100.0 / summary.completed),
                    summary.avgAttempts,
                    failuresList.size()
            ));
            btnRun.setDisable(false);
        });

        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            lbStats.textProperty().unbind();
            Throwable ex = task.getException();
            lbStats.setText("Failed: " + (ex == null ? "unknown error" : ex.getMessage()));
            btnRun.setDisable(false);
        });

        Thread t = new Thread(task, "wordle-sim-task");
        t.setDaemon(true);
        t.start();
    }

    private static String buildStats(int completed, int wins, int failures, long attemptsSum) {
        double winRate = completed == 0 ? 0 : (wins * 100.0 / completed);
        double avg = completed == 0 ? 0 : (attemptsSum * 1.0 / completed);
        return String.format("Completed=%d, Wins=%d (%.2f%%), Failures=%d, AvgAttempts=%.3f",
                completed, wins, winRate, failures, avg);
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private SimResult runSingleGame(int index, int maxAttempts, int length, File answersFile, int topK) {
        Wordle wordle = Wordle.start(maxAttempts, length, answersFile);

        WordleRegex solver = new WordleRegex();
        solver.setWordList(answersFile);

        List<StepDetail> steps = new ArrayList<>();
        Map<String, Integer> lastScoreMap = null;

        try {
            while (!wordle.isGameOver()) {
                Map<String, Integer> scoreMap = solver.answerScoreMap();
                lastScoreMap = scoreMap;

                if (scoreMap.isEmpty()) {
                    StepDetail step = new StepDetail();
                    step.guess = "(no candidate)";
                    step.result = "NO CANDIDATES";
                    step.topCandidates = List.of();
                    steps.add(step);
                    break;
                }

                List<CandidateScore> topCandidates = scoreMap.entrySet().stream()
                        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                        .limit(Math.max(1, topK))
                        .map(e -> new CandidateScore(e.getKey(), e.getValue()))
                        .collect(Collectors.toList());

                // 选择当前最优候选作为猜测
                String guessWord = topCandidates.get(0).word;

                String result = wordle.guess(guessWord);

                StepDetail step = new StepDetail();
                step.guess = guessWord;
                step.result = result;
                step.topCandidates = topCandidates;
                steps.add(step);

                if ("GAME OVER".equals(result)) {
                    break;
                }
                solver.guess(result);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            StepDetail err = new StepDetail();
            err.guess = "(exception)";
            err.result = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            err.topCandidates = (lastScoreMap == null) ? List.of() : lastScoreMap.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(Math.max(1, topK))
                    .map(e -> new CandidateScore(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
            steps.add(err);
        }

        SimResult r = new SimResult();
        r.index = index;
        r.answer = wordle.getAnswer();
        r.attempts = wordle.getAttempts();
        r.win = wordle.isWin();
        r.steps = steps;
        return r;
    }

    private String formatFailure(FailureRecord fr) {
        StringBuilder sb = new StringBuilder();
        sb.append("Game #").append(fr.index).append("\n");
        sb.append("Answer: ").append(fr.answer).append("\n");
        sb.append("Attempts: ").append(fr.attempts).append("\n\n");

        int i = 1;
        for (StepDetail step : fr.steps) {
            sb.append("Step ").append(i++).append("\n");
            sb.append("  Guess : ").append(step.guess).append("\n");
            sb.append("  Result: ").append(step.result).append("\n");
            sb.append("  Top Candidates:\n");
            int j = 1;
            for (CandidateScore cs : step.topCandidates) {
                sb.append(String.format("    %2d) %-10s  %d%n", j++, cs.word, cs.score));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ====== Data models ======
    static class CandidateScore {
        final String word;
        final int score;

        CandidateScore(String word, int score) {
            this.word = word;
            this.score = score;
        }
    }

    static class StepDetail {
        String guess;
        String result;
        List<CandidateScore> topCandidates = List.of();
    }

    static class SimResult {
        int index;
        String answer;
        int attempts;
        boolean win;
        List<StepDetail> steps;
    }

    static class FailureRecord {
        int index;
        String answer;
        int attempts;
        String lastGuess;
        List<StepDetail> steps;

        static FailureRecord from(SimResult r) {
            FailureRecord fr = new FailureRecord();
            fr.index = r.index;
            fr.answer = r.answer;
            fr.attempts = r.attempts;
            fr.steps = r.steps;
            fr.lastGuess = (r.steps == null || r.steps.isEmpty()) ? "" : r.steps.get(r.steps.size() - 1).guess;
            return fr;
        }
    }

    static class SimSummary {
        int testTimes;
        int completed;
        int wins;
        double avgAttempts;
        double totalSeconds;
        List<FailureRecord> failures = List.of();
    }

    public static void main(String[] args) {
        launch(args);
    }
}