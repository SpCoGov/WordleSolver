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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class WordleSimViewerApp extends Application {

    private static final ScoreParams DEFAULT_PARAMS = defaultScoreParams();
    private final TextField tfTestTimes = new TextField("10000");
    private final TextField tfTopK = new TextField("20");
    private final TextField tfMaxAttempts = new TextField("6");
    private final TextField tfWordLength = new TextField("5");
    private final TextField tfThreads = new TextField(String.valueOf(Math.max(1, Runtime.getRuntime().availableProcessors())));
    private final TextField tfAnswersPath = new TextField("/Users/spco/IdeaProjects/WordleSolver/src/main/resources/answers");
    private final TextField tfCharFrequencyWeight = new TextField(String.valueOf(DEFAULT_PARAMS.charFrequencyWeight));
    private final TextField tfPositionTopBonus = new TextField(String.valueOf(DEFAULT_PARAMS.positionTopBonus));
    private final TextField tfRepeatPenaltyBase = new TextField(String.valueOf(DEFAULT_PARAMS.repeatPenaltyBase));
    private final CheckBox cbHardMode = new CheckBox("Hard Mode");

    private final Button btnRun = new Button("Run");
    private final Button btnStop = new Button("Stop");
    private final Button btnCopyDetails = new Button("Copy Details");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label lbStats = new Label("Ready.");

    private final TableView<FailureRecord> tvFailures = new TableView<>();
    private final TextArea taReport = new TextArea();
    private final WebView wvDetails = new WebView();
    private String currentFailureDetailsText = "";
    private Task<SimSummary> currentTask;
    private java.util.concurrent.ExecutorService currentExecutor;

    private final TextField tfListTopK = new TextField("20");
    private final TextField tfListMaxAttempts = new TextField("6");
    private final TextField tfListWordLength = new TextField("5");
    private final TextField tfListThreads = new TextField(String.valueOf(Math.max(1, Runtime.getRuntime().availableProcessors())));
    private final TextField tfListAnswersPath = new TextField("/Users/spco/IdeaProjects/WordleSolver/src/main/resources/answers");
    private final TextField tfListCharFrequencyWeight = new TextField(String.valueOf(DEFAULT_PARAMS.charFrequencyWeight));
    private final TextField tfListPositionTopBonus = new TextField(String.valueOf(DEFAULT_PARAMS.positionTopBonus));
    private final TextField tfListRepeatPenaltyBase = new TextField(String.valueOf(DEFAULT_PARAMS.repeatPenaltyBase));
    private final CheckBox cbListHardMode = new CheckBox("Hard Mode");
    private final Button btnRunList = new Button("Run");
    private final Button btnStopList = new Button("Stop");
    private final Button btnCopyListDetails = new Button("Copy Details");
    private final ProgressBar progressBarList = new ProgressBar(0);
    private final Label lbStatsList = new Label("Ready.");
    private final TableView<GameRecord> tvAllGames = new TableView<>();
    private final TextArea taListReport = new TextArea();
    private final WebView wvListDetails = new WebView();
    private String currentListDetailsText = "";
    private Task<SimSummary> currentListTask;
    private java.util.concurrent.ExecutorService currentListExecutor;

    @Override
    public void start(Stage stage) {
        TabPane tabPane = new TabPane();
        Tab simTab = new Tab("Simulation", createSimulationPane());
        Tab listSimTab = new Tab("List Simulation", createListSimulationPane());
        Tab manualTab = new Tab("手动猜词", createManualGuessPane());
        tabPane.getTabs().addAll(simTab, listSimTab, manualTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Scene scene = new Scene(tabPane, 1200, 720);
        stage.setTitle("Wordle Simulation Viewer");
        stage.setScene(scene);
        stage.show();
    }

    private SplitPane createSimulationPane() {
        GridPane controls = new GridPane();
        controls.setHgap(10);
        controls.setVgap(8);

        int r = 0;
        controls.addRow(r++, new Label("Test Times"), tfTestTimes, new Label("TopK"), tfTopK);
        controls.addRow(r++, new Label("Max Attempts"), tfMaxAttempts, new Label("Word Length"), tfWordLength);
        controls.addRow(r++, new Label("Threads"), tfThreads, new Label("Answers File"), tfAnswersPath);
        controls.addRow(r++, new Label("CharWeight"), tfCharFrequencyWeight, new Label("PosBonus"), tfPositionTopBonus);
        controls.addRow(r++, new Label("RepeatBase"), tfRepeatPenaltyBase);
        controls.addRow(r++, cbHardMode);

        HBox runBar = new HBox(10, btnRun, btnStop, progressBar, lbStats);
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
        taReport.setEditable(false);
        taReport.setWrapText(false);

        wvDetails.setContextMenuEnabled(true);
        setDetailsPlaceholder("Select a failed game to see details.");
        btnCopyDetails.setOnAction(e -> copyDetailsToClipboard());

        VBox right = new VBox(
                10,
                new Label("Report"),
                taReport,
                new HBox(8, new Label("Failure Details"), btnCopyDetails),
                wvDetails
        );
        right.setPadding(new Insets(12));
        VBox.setVgrow(taReport, Priority.NEVER);
        VBox.setVgrow(wvDetails, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(left, right);
        splitPane.setDividerPositions(0.55);

        btnRun.setOnAction(e -> runSimulations());
        btnStop.setOnAction(e -> stopSimulations());
        btnStop.setDisable(true);

        return splitPane;
    }

    private SplitPane createListSimulationPane() {
        GridPane controls = new GridPane();
        controls.setHgap(10);
        controls.setVgap(8);

        int r = 0;
        controls.addRow(r++, new Label("Mode"), new Label("Simulate Every Answer"), new Label("TopK"), tfListTopK);
        controls.addRow(r++, new Label("Max Attempts"), tfListMaxAttempts, new Label("Word Length"), tfListWordLength);
        controls.addRow(r++, new Label("Threads"), tfListThreads, new Label("Answers File"), tfListAnswersPath);
        controls.addRow(r++, new Label("CharWeight"), tfListCharFrequencyWeight, new Label("PosBonus"), tfListPositionTopBonus);
        controls.addRow(r++, new Label("RepeatBase"), tfListRepeatPenaltyBase);
        controls.addRow(r++, cbListHardMode);

        HBox runBar = new HBox(10, btnRunList, btnStopList, progressBarList, lbStatsList);
        runBar.setPadding(new Insets(8, 0, 0, 0));
        HBox.setHgrow(progressBarList, Priority.ALWAYS);
        progressBarList.setMaxWidth(Double.MAX_VALUE);

        VBox leftTop = new VBox(10, controls, runBar);
        leftTop.setPadding(new Insets(12));

        setupAllGamesTable();
        VBox left = new VBox(10, leftTop, new Label("All Games"), tvAllGames);
        VBox.setVgrow(tvAllGames, Priority.ALWAYS);

        taListReport.setEditable(false);
        taListReport.setWrapText(false);

        wvListDetails.setContextMenuEnabled(true);
        setListDetailsPlaceholder("Select a game to see details.");
        btnCopyListDetails.setOnAction(e -> copyListDetailsToClipboard());

        VBox right = new VBox(
                10,
                new Label("Report"),
                taListReport,
                new HBox(8, new Label("Game Details"), btnCopyListDetails),
                wvListDetails
        );
        right.setPadding(new Insets(12));
        VBox.setVgrow(taListReport, Priority.NEVER);
        VBox.setVgrow(wvListDetails, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(left, right);
        splitPane.setDividerPositions(0.55);

        btnRunList.setOnAction(e -> runListSimulations());
        btnStopList.setOnAction(e -> stopListSimulations());
        btnStopList.setDisable(true);

        return splitPane;
    }

    private SplitPane createManualGuessPane() {
        final int maxAttempts = 6;
        final int wordLength = 5;

        TextField tfManualAnswersPath = new TextField(tfAnswersPath.getText());
        TextField tfManualTopK = new TextField("200");
        Button btnRefresh = new Button("刷新候选");
        Button btnReset = new Button("重置");
        Label lbStatus = new Label("请输入猜测并选择颜色，然后确认。");
        Label lbCandidates = new Label("候选: 0");

        TableView<CandidateScore> tvCandidates = new TableView<>();
        TableColumn<CandidateScore, String> colWord = new TableColumn<>("单词");
        colWord.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().word));
        TableColumn<CandidateScore, Number> colScore = new TableColumn<>("分数");
        colScore.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().score));
        tvCandidates.getColumns().add(colWord);
        tvCandidates.getColumns().add(colScore);
        tvCandidates.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        VBox left = new VBox(8, new Label("候选列表"), lbCandidates, tvCandidates);
        left.setPadding(new Insets(12));
        VBox.setVgrow(tvCandidates, Priority.ALWAYS);

        HBox topRow = new HBox(10,
                new Label("答案库"),
                tfManualAnswersPath,
                new Label("TopK"),
                tfManualTopK,
                btnRefresh,
                btnReset
        );
        HBox.setHgrow(tfManualAnswersPath, Priority.ALWAYS);

        VBox attemptsBox = new VBox(8);
        List<ManualAttemptRow> rows = new ArrayList<>();
        for (int i = 0; i < maxAttempts; i++) {
            ManualAttemptRow row = new ManualAttemptRow(wordLength, i + 1);
            rows.add(row);
            attemptsBox.getChildren().add(row.container);
        }

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            ManualAttemptRow row = rows.get(rowIndex);
            for (int pos = 0; pos < wordLength; pos++) {
                int finalRowIndex = rowIndex;
                int finalPos = pos;
                ComboBox<String> cb = row.colorBoxes.get(pos);
                cb.valueProperty().addListener((obs, old, val) -> {
                    if ("G(绿)".equals(val)) {
                        for (int r = finalRowIndex + 1; r < rows.size(); r++) {
                            rows.get(r).colorBoxes.get(finalPos).getSelectionModel().select("G(绿)");
                        }
                    }
                });
            }
        }

        Button btnConfirm = new Button("确认本次结果");
        VBox right = new VBox(12, topRow, attemptsBox, btnConfirm, lbStatus);
        right.setPadding(new Insets(12));
        VBox.setVgrow(attemptsBox, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(left, right);
        splitPane.setDividerPositions(0.35);

        final int[] currentAttempt = {0};
        final WordleConstraint[] constraintRef = {new WordleConstraint(wordLength)};
        final WordleScorer scorer = new WordleScorer();

        Runnable applyScoreParams = () -> applyScoreParams(scorer, readScoreParams());

        Runnable refreshCandidates = () -> {
            File answersFile = new File(tfManualAnswersPath.getText().trim());
            if (!answersFile.exists()) {
                showAlert("答案库路径不存在: " + answersFile.getAbsolutePath());
                return;
            }
            constraintRef[0].setWordList(answersFile);
            applyScoreParams.run();

            int topK = parseInt(tfManualTopK.getText(), 200);
            Map<String, Integer> scoreMap = scorer.scoreMap(constraintRef[0].answers(), wordLength);
            List<CandidateScore> candidates = scoreMap.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(Math.max(1, topK))
                    .map(e -> new CandidateScore(e.getKey().toUpperCase(), e.getValue()))
                    .collect(Collectors.toList());

            tvCandidates.setItems(FXCollections.observableArrayList(candidates));
            lbCandidates.setText(String.format("候选: %d / 显示: %d", scoreMap.size(), candidates.size()));
        };

        Runnable resetManual = () -> {
            constraintRef[0] = new WordleConstraint(wordLength);
            currentAttempt[0] = 0;
            for (int i = 0; i < rows.size(); i++) {
                ManualAttemptRow row = rows.get(i);
                row.clear();
                row.setEnabled(i == 0);
            }
            btnConfirm.setDisable(false);
            lbStatus.setText("请输入猜测并选择颜色，然后确认。");
            refreshCandidates.run();
        };

        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setEnabled(i == 0);
        }

        tvCandidates.setRowFactory(tv -> {
            TableRow<CandidateScore> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    ManualAttemptRow currentRow = rows.get(currentAttempt[0]);
                    currentRow.tfGuess.setText(row.getItem().word.toUpperCase());
                }
            });
            return row;
        });

        btnConfirm.setOnAction(e -> {
            if (currentAttempt[0] >= maxAttempts) {
                btnConfirm.setDisable(true);
                return;
            }
            ManualAttemptRow row = rows.get(currentAttempt[0]);
            String guess = row.tfGuess.getText().trim().toUpperCase();
            if (guess.length() != wordLength || !guess.chars().allMatch(Character::isLetter)) {
                showAlert("请输入 5 个字母的单词。");
                return;
            }
            String guessResult = buildGuessResult(guess, row.colorBoxes);
            try {
                constraintRef[0].guess(guessResult);
            } catch (Exception ex) {
                showAlert("颜色或结果不合法: " + ex.getMessage());
                return;
            }

            row.setEnabled(false);
            currentAttempt[0]++;
            if (currentAttempt[0] < maxAttempts) {
                rows.get(currentAttempt[0]).setEnabled(true);
                lbStatus.setText("已确认第 " + currentAttempt[0] + " 次，继续下一次。");
            } else {
                lbStatus.setText("已完成 6 次猜测。");
                btnConfirm.setDisable(true);
            }
            refreshCandidates.run();
        });

        btnRefresh.setOnAction(e -> refreshCandidates.run());
        btnReset.setOnAction(e -> resetManual.run());

        refreshCandidates.run();
        return splitPane;
    }

    private static String buildGuessResult(String guess, List<ComboBox<String>> colorBoxes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < guess.length(); i++) {
            String value = colorBoxes.get(i).getValue();
            char code = value == null || value.isEmpty() ? 'b' : Character.toLowerCase(value.charAt(0));
            sb.append(code).append(guess.charAt(i));
        }
        return sb.toString();
    }

    private static TextField createGuessField(int maxLength) {
        TextField tf = new TextField();
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String text = change.getControlNewText();
            if (text.length() > maxLength) {
                return null;
            }
            if (!text.chars().allMatch(Character::isLetter)) {
                return null;
            }
            return change;
        };
        tf.setTextFormatter(new TextFormatter<>(filter));
        tf.textProperty().addListener((obs, old, val) -> {
            if (val != null) {
                String upper = val.toUpperCase();
                if (!upper.equals(val)) {
                    tf.setText(upper);
                }
            }
        });
        tf.setPrefColumnCount(maxLength + 1);
        return tf;
    }

    private static void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
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
        TableColumn<FailureRecord, String> colTag = new TableColumn<>("Tag");
        colTag.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().tag));

        tvFailures.getColumns().add(colIndex);
        tvFailures.getColumns().add(colAnswer);
        tvFailures.getColumns().add(colAttempts);
        tvFailures.getColumns().add(colLastGuess);
        tvFailures.getColumns().add(colTag);
        tvFailures.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        tvFailures.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                setDetailsPlaceholder("Select a failed game to see details.");
                return;
            }
            currentFailureDetailsText = formatFailureText(selected);
            renderFailureRich(selected);
        });
    }

    private void setupAllGamesTable() {
        TableColumn<GameRecord, Number> colIndex = new TableColumn<>("#");
        colIndex.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().index));

        TableColumn<GameRecord, String> colAnswer = new TableColumn<>("Answer");
        colAnswer.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().answer));

        TableColumn<GameRecord, String> colOutcome = new TableColumn<>("Outcome");
        colOutcome.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().win ? "WIN" : "FAIL"));

        TableColumn<GameRecord, Number> colAttempts = new TableColumn<>("Attempts");
        colAttempts.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().attempts));

        TableColumn<GameRecord, String> colLastGuess = new TableColumn<>("Last Guess");
        colLastGuess.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().lastGuess));

        TableColumn<GameRecord, String> colTag = new TableColumn<>("Tag");
        colTag.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().tag));

        tvAllGames.getColumns().add(colIndex);
        tvAllGames.getColumns().add(colAnswer);
        tvAllGames.getColumns().add(colOutcome);
        tvAllGames.getColumns().add(colAttempts);
        tvAllGames.getColumns().add(colLastGuess);
        tvAllGames.getColumns().add(colTag);
        tvAllGames.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        colIndex.setSortType(TableColumn.SortType.ASCENDING);
        tvAllGames.getSortOrder().clear();
        tvAllGames.getSortOrder().add(colIndex);

        tvAllGames.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                setListDetailsPlaceholder("Select a game to see details.");
                return;
            }
            currentListDetailsText = formatSimResultText(selected.toSimResult());
            renderListGameRich(selected.toSimResult());
        });
    }

    private void runSimulations() {
        int testTimes = parseInt(tfTestTimes.getText(), 1000);
        int topK = parseInt(tfTopK.getText(), 20);
        int maxAttempts = parseInt(tfMaxAttempts.getText(), 6);
        int length = parseInt(tfWordLength.getText(), 5);
        int threads = Math.max(1, parseInt(tfThreads.getText(), Runtime.getRuntime().availableProcessors()));
        boolean hardMode = cbHardMode.isSelected();
        File answersFile = new File(tfAnswersPath.getText().trim());

        btnRun.setDisable(true);
        btnStop.setDisable(false);

        var failuresList = FXCollections.<FailureRecord>observableArrayList();
        tvFailures.setItems(failuresList);

        setDetailsPlaceholder("Running...");
        taReport.clear();
        lbStats.setText("Running...");
        progressBar.setProgress(0);

        Task<SimSummary> task = new Task<>() {
            @Override
            protected SimSummary call() {
                long start = System.currentTimeMillis();

                ScoreParams scoreParams = readScoreParams();
                int wins = 0;
                int completed = 0;
                long attemptsSum = 0;

                var executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
                currentExecutor = executor;
                var completion = new java.util.concurrent.ExecutorCompletionService<SimResult>(executor);

                for (int i = 0; i < testTimes; i++) {
                    final int index = i;
                    completion.submit(() -> runSingleGame(index, maxAttempts, length, answersFile, topK, scoreParams, hardMode));
                }

                try {
                    for (int i = 0; i < testTimes; i++) {
                        if (isCancelled()) {
                            break;
                        }
                        SimResult result = completion.take().get();

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
                } catch (Exception ex) {
                    if (!isCancelled()) {
                        throw new RuntimeException(ex);
                    }
                } finally {
                    executor.shutdownNow();
                }

                long end = System.currentTimeMillis();

                SimSummary summary = new SimSummary();
                summary.testTimes = testTimes;
                summary.completed = completed;
                summary.wins = wins;
                summary.failures = new ArrayList<>(failuresList); // 复制一份结果
                summary.avgAttempts = completed == 0 ? 0 : (attemptsSum * 1.0 / completed);
                summary.totalSeconds = (end - start) / 1000.0;
                summary.scoreParams = scoreParams;
                summary.threads = threads;
                summary.hardMode = hardMode;
                summary.cancelled = isCancelled();
                return summary;
            }
        };
        currentTask = task;

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
            taReport.setText(buildReport(summary));
            btnRun.setDisable(false);
            btnStop.setDisable(true);
        });

        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            lbStats.textProperty().unbind();
            Throwable ex = task.getException();
            lbStats.setText("Failed: " + (ex == null ? "unknown error" : ex.getMessage()));
            taReport.setText("Failed: " + (ex == null ? "unknown error" : ex.getMessage()));
            btnRun.setDisable(false);
            btnStop.setDisable(true);
        });
        task.setOnCancelled(e -> {
            progressBar.progressProperty().unbind();
            lbStats.textProperty().unbind();
            SimSummary summary = task.getValue();
            if (summary == null) {
                summary = new SimSummary();
                summary.completed = 0;
                summary.wins = 0;
                summary.avgAttempts = 0;
                summary.totalSeconds = 0;
                summary.scoreParams = readScoreParams();
                summary.threads = Math.max(1, parseInt(tfThreads.getText(), Runtime.getRuntime().availableProcessors()));
                summary.hardMode = cbHardMode.isSelected();
                summary.cancelled = true;
            }
            lbStats.setText("Cancelled.");
            taReport.setText(buildReport(summary));
            btnRun.setDisable(false);
            btnStop.setDisable(true);
        });

        Thread t = new Thread(task, "wordle-sim-task");
        t.setDaemon(true);
        t.start();
    }

    private void runListSimulations() {
        int topK = parseInt(tfListTopK.getText(), 20);
        int maxAttempts = parseInt(tfListMaxAttempts.getText(), 6);
        int length = parseInt(tfListWordLength.getText(), 5);
        int threads = Math.max(1, parseInt(tfListThreads.getText(), Runtime.getRuntime().availableProcessors()));
        boolean hardMode = cbListHardMode.isSelected();
        File answersFile = new File(tfListAnswersPath.getText().trim());
        List<String> answers;
        try {
            answers = loadAnswersInOrder(answersFile, length);
        } catch (RuntimeException ex) {
            setListDetailsPlaceholder("Failed to load answers: " + ex.getMessage());
            lbStatsList.setText("Failed to load answers.");
            return;
        }
        if (answers.isEmpty()) {
            setListDetailsPlaceholder("No answers with the requested word length.");
            lbStatsList.setText("No answers.");
            return;
        }

        btnRunList.setDisable(true);
        btnStopList.setDisable(false);

        var allGamesList = FXCollections.<GameRecord>observableArrayList();
        tvAllGames.setItems(allGamesList);

        setListDetailsPlaceholder("Running...");
        taListReport.clear();
        lbStatsList.setText("Running...");
        progressBarList.setProgress(0);

        Task<SimSummary> task = new Task<>() {
            @Override
            protected SimSummary call() {
                long start = System.currentTimeMillis();

                ScoreParams scoreParams = readListScoreParams();
                int wins = 0;
                int completed = 0;
                long attemptsSum = 0;
                int total = answers.size();

                var executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
                currentListExecutor = executor;
                var completion = new java.util.concurrent.ExecutorCompletionService<SimResult>(executor);

                for (int i = 0; i < total; i++) {
                    final int index = i;
                    final String answer = answers.get(i);
                    completion.submit(() -> runSingleGame(index, answer, maxAttempts, length, answersFile, topK, scoreParams, hardMode));
                }

                try {
                    for (int i = 0; i < total; i++) {
                        if (isCancelled()) {
                            break;
                        }
                        SimResult result = completion.take().get();
                        completed++;
                        attemptsSum += result.attempts;
                        if (result.win) {
                            wins++;
                        }
                        GameRecord record = GameRecord.from(result);
                        Platform.runLater(() -> allGamesList.add(record));
                        if (i % 5 == 0) {
                            updateProgress(i + 1, total);
                            updateMessage(buildStats(completed, wins, completed - wins, attemptsSum));
                        }
                    }
                } catch (Exception ex) {
                    if (!isCancelled()) {
                        throw new RuntimeException(ex);
                    }
                } finally {
                    executor.shutdownNow();
                }

                long end = System.currentTimeMillis();

                SimSummary summary = new SimSummary();
                summary.testTimes = total;
                summary.completed = completed;
                summary.wins = wins;
                summary.avgAttempts = completed == 0 ? 0 : (attemptsSum * 1.0 / completed);
                summary.totalSeconds = (end - start) / 1000.0;
                summary.scoreParams = scoreParams;
                summary.threads = threads;
                summary.hardMode = hardMode;
                summary.cancelled = isCancelled();
                return summary;
            }
        };
        currentListTask = task;

        progressBarList.progressProperty().bind(task.progressProperty());
        lbStatsList.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> {
            progressBarList.progressProperty().unbind();
            lbStatsList.textProperty().unbind();

            SimSummary summary = task.getValue();
            int failures = summary.completed - summary.wins;
            lbStatsList.setText(String.format(
                    "Done. Time=%.2fs, Completed=%d, Wins=%d, WinRate=%.2f%%, AvgAttempts=%.3f, Failures=%d",
                    summary.totalSeconds,
                    summary.completed,
                    summary.wins,
                    summary.completed == 0 ? 0 : (summary.wins * 100.0 / summary.completed),
                    summary.avgAttempts,
                    failures
            ));
            taListReport.setText(buildReport(summary));
            btnRunList.setDisable(false);
            btnStopList.setDisable(true);
        });

        task.setOnFailed(e -> {
            progressBarList.progressProperty().unbind();
            lbStatsList.textProperty().unbind();
            Throwable ex = task.getException();
            lbStatsList.setText("Failed: " + (ex == null ? "unknown error" : ex.getMessage()));
            taListReport.setText("Failed: " + (ex == null ? "unknown error" : ex.getMessage()));
            btnRunList.setDisable(false);
            btnStopList.setDisable(true);
        });

        task.setOnCancelled(e -> {
            progressBarList.progressProperty().unbind();
            lbStatsList.textProperty().unbind();
            SimSummary summary = task.getValue();
            if (summary == null) {
                summary = new SimSummary();
                summary.completed = 0;
                summary.wins = 0;
                summary.avgAttempts = 0;
                summary.totalSeconds = 0;
                summary.scoreParams = readListScoreParams();
                summary.threads = Math.max(1, parseInt(tfListThreads.getText(), Runtime.getRuntime().availableProcessors()));
                summary.hardMode = cbListHardMode.isSelected();
                summary.cancelled = true;
            }
            lbStatsList.setText("Cancelled.");
            taListReport.setText(buildReport(summary));
            btnRunList.setDisable(false);
            btnStopList.setDisable(true);
        });

        Thread t = new Thread(task, "wordle-list-sim-task");
        t.setDaemon(true);
        t.start();
    }

    private void stopSimulations() {
        if (currentTask != null) {
            currentTask.cancel();
        }
        if (currentExecutor != null) {
            currentExecutor.shutdownNow();
        }
        btnStop.setDisable(true);
    }

    private void stopListSimulations() {
        if (currentListTask != null) {
            currentListTask.cancel();
        }
        if (currentListExecutor != null) {
            currentListExecutor.shutdownNow();
        }
        btnStopList.setDisable(true);
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

    private SimResult runSingleGame(int index, int maxAttempts, int length, File answersFile, int topK, ScoreParams scoreParams, boolean hardMode) {
        return runSingleGame(index, null, maxAttempts, length, answersFile, topK, scoreParams, hardMode);
    }

    private SimResult runSingleGame(int index, String fixedAnswer, int maxAttempts, int length, File answersFile, int topK, ScoreParams scoreParams, boolean hardMode) {
        Wordle wordle = fixedAnswer == null
                ? Wordle.start(maxAttempts, length, answersFile)
                : Wordle.start(maxAttempts, fixedAnswer);

        WordleSolver solver = new WordleSolver(answersFile);
        solver.setMaxGuessTime(maxAttempts);
        solver.setHardMode(hardMode);
        applyScoreParams(solver, scoreParams);

        List<StepDetail> steps = new ArrayList<>();
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

                String guessWord = solver.nextGuess();
                if (guessWord == null || guessWord.isEmpty()) {
                    guessWord = topCandidates.get(0).word;
                }
                WordleSolver.GuessDecision decision = solver.getLastDecision();

                String result = wordle.guess(guessWord);

                StepDetail step = new StepDetail();
                step.guess = guessWord;
                step.result = result;
                step.topCandidates = topCandidates;
                step.strategy = decision.strategy;
                step.strategyReason = decision.strategyReason;
                step.filterRounds = decision.filterRounds;
                step.checkedWords = decision.checkedWords;
                step.targetCoverage = decision.targetCoverage;
                step.matchedWords = decision.matchedWords;
                step.familyCandidates = decision.familyCandidates;
                step.filterPath = decision.filterPath;
                steps.add(step);

                if ("GAME OVER".equals(result)) {
                    break;
                }
                solver.applyGuess(guessWord, result);
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

    private static List<String> loadAnswersInOrder(File answersFile, int length) {
        try {
            List<String> lines = Files.readAllLines(answersFile.toPath());
            List<String> answers = new ArrayList<>();
            for (String line : lines) {
                if (line == null) {
                    continue;
                }
                String word = line.trim().toUpperCase();
                if (word.length() == length) {
                    answers.add(word);
                }
            }
            return answers;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read answers file: " + answersFile.getAbsolutePath(), e);
        }
    }

    private void setDetailsPlaceholder(String message) {
        wvDetails.getEngine().loadContent(
                "<html><body style='margin:0;padding:10px;background:#0b1220;color:#9aa4b2;"
                        + "font-family:Menlo,Consolas,monospace;font-size:12px;'>"
                        + escapeHtml(message)
                        + "</body></html>"
        );
        currentFailureDetailsText = message;
    }

    private void setListDetailsPlaceholder(String message) {
        wvListDetails.getEngine().loadContent(
                "<html><body style='margin:0;padding:10px;background:#0b1220;color:#9aa4b2;"
                        + "font-family:Menlo,Consolas,monospace;font-size:12px;'>"
                        + escapeHtml(message)
                        + "</body></html>"
        );
        currentListDetailsText = message;
    }

    private void renderFailureRich(FailureRecord fr) {
        wvDetails.getEngine().loadContent(formatFailureHtml(fr));
    }

    private void copyDetailsToClipboard() {
        ClipboardContent content = new ClipboardContent();
        content.putString(currentFailureDetailsText == null ? "" : currentFailureDetailsText);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void copyListDetailsToClipboard() {
        ClipboardContent content = new ClipboardContent();
        content.putString(currentListDetailsText == null ? "" : currentListDetailsText);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private String formatFailureHtml(FailureRecord fr) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><style>")
                .append("body{margin:0;padding:10px;background:#0b1220;color:#f8fafc;font-family:Menlo,Consolas,monospace;font-size:12px;line-height:1.5;}")
                .append(".title{color:#70b7ff;font-weight:700;margin-top:12px;}")
                .append(".meta{color:#d9e2f2;}")
                .append(".k{color:#9cdcfe;}")
                .append(".v{color:#ffd166;}")
                .append(".n{color:#4ec9b0;}")
                .append(".muted{color:#c5cbd6;}")
                .append(".word{background:#233047;color:#ffd166;padding:1px 6px;border-radius:10px;font-weight:700;}")
                .append(".chip{display:inline-block;background:#1a2336;color:#e6edf7;border:1px solid #32415f;padding:2px 6px;border-radius:10px;margin:1px 4px 1px 0;}")
                .append(".cov{display:inline-block;background:#1f6feb;color:#fff;padding:1px 8px;border-radius:10px;font-weight:700;}")
                .append(".res{display:inline-block;min-width:18px;text-align:center;padding:2px 6px;margin-right:4px;border-radius:6px;font-weight:700;}")
                .append(".res-g{background:#2ea043;color:#ffffff;}")
                .append(".res-y{background:#d29922;color:#101010;}")
                .append(".res-b{background:#4b5563;color:#f8fafc;}")
                .append(".regex{display:block;background:#111927;color:#7ee787;border:1px solid #2f3f60;border-radius:6px;padding:6px;white-space:pre-wrap;word-break:break-all;margin:4px 0 6px 0;}")
                .append(".path-card{border:1px solid #24324c;background:#10182a;border-radius:8px;padding:8px;margin:6px 0;}")
                .append(".cand{color:#ffffff;white-space:pre;}")
                .append("</style></head><body>");
        sb.append("<div class='title'>Game #").append(fr.index).append("</div>");
        sb.append("<div class='meta'>Answer: <span class='v'>").append(escapeHtml(fr.answer)).append("</span></div>");
        sb.append("<div class='meta'>Attempts: ").append(fr.attempts).append("</div>");
        sb.append("<div class='title'>Game Board</div>");
        int boardStep = 1;
        for (StepDetail step : fr.steps) {
            sb.append("<div><span class='k'>#").append(boardStep++).append("</span> ");
            sb.append("<span class='word'>").append(escapeHtml(step.guess)).append("</span> ");
            sb.append(buildResultHtml(step.result));
            sb.append("</div>");
        }

        int i = 1;
        for (StepDetail step : fr.steps) {
            sb.append("<div class='title'>Step ").append(i++).append("</div>");
            sb.append("<div><span class='k'>Guess:</span> <span class='v'>").append(escapeHtml(step.guess)).append("</span></div>");
            sb.append("<div><span class='k'>Result:</span> ").append(buildResultHtml(step.result)).append("</div>");
            if (step.strategy != null && !step.strategy.isEmpty()) {
                sb.append("<div><span class='k'>Strategy:</span> ").append(escapeHtml(step.strategy)).append("</div>");
            }
            if (step.strategyReason != null && !step.strategyReason.isEmpty()) {
                sb.append("<div><span class='k'>StrategyReason:</span> ").append(escapeHtml(step.strategyReason)).append("</div>");
            }
            if (step.filterRounds > 0 || step.checkedWords > 0) {
                sb.append("<div><span class='n'>FilterRounds:</span> ").append(step.filterRounds)
                        .append(", <span class='n'>CheckedWords:</span> ").append(step.checkedWords).append("</div>");
            }
            if (step.targetCoverage > 0) {
                sb.append("<div><span class='n'>TargetCoverage:</span> ").append(step.targetCoverage).append("</div>");
            }
            if (!step.matchedWords.isEmpty()) {
                sb.append("<div><span class='k'>MatchedWords:</span> ")
                        .append(buildMatchedWordsHtml(step.matchedWords, 12)).append("</div>");
            }
            if (!step.filterPath.isEmpty()) {
                sb.append("<div><span class='k'>FilterPath(n->2):</span></div>");
                for (String pathLine : step.filterPath) {
                    sb.append(buildFilterPathHtml(pathLine));
                }
            }
            sb.append("<div><span class='k'>Top Candidates:</span></div>");
            int j = 1;
            for (CandidateScore cs : step.topCandidates) {
                sb.append("<div class='cand'>")
                        .append(String.format("%2d) ", j++))
                        .append("<span class='word'>").append(escapeHtml(cs.word)).append("</span>")
                        .append("  ")
                        .append(cs.score)
                        .append("</div>");
            }
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private void renderListGameRich(SimResult result) {
        wvListDetails.getEngine().loadContent(formatSimResultHtml(result));
    }

    private String formatSimResultHtml(SimResult result) {
        return formatFailureHtml(FailureRecord.from(result));
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String buildMatchedWordsHtml(List<String> words, int maxDisplay) {
        if (words == null || words.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        int end = Math.min(maxDisplay, words.size());
        for (int i = 0; i < end; i++) {
            String entry = words.get(i);
            String word = entry;
            int idx = entry.indexOf('(');
            if (idx > 0) {
                word = entry.substring(0, idx);
            }
            sb.append("<span class='chip'><span class='word'>")
                    .append(escapeHtml(word))
                    .append("</span> ")
                    .append(escapeHtml(entry.substring(Math.max(0, idx))))
                    .append("</span>");
        }
        if (words.size() > end) {
            sb.append("<span class='chip'>... +").append(words.size() - end).append("</span>");
        }
        return sb.toString();
    }

    private static String buildResultHtml(String result) {
        if (result == null) {
            return "<span class='v'></span>";
        }
        if ("GAME OVER".equals(result) || "NO CANDIDATES".equals(result)) {
            return "<span class='v'>" + escapeHtml(result) + "</span>";
        }
        List<ResultCell> cells = parseResultCells(result);
        if (cells.isEmpty()) {
            return "<span class='v'>" + escapeHtml(result) + "</span>";
        }
        StringBuilder sb = new StringBuilder();
        for (ResultCell cell : cells) {
            sb.append("<span class='res res-").append(cell.colorCode).append("'>")
                    .append(escapeHtml(String.valueOf(cell.letter)))
                    .append("</span>");
        }
        return sb.toString();
    }

    private static List<ResultCell> parseResultCells(String result) {
        List<ResultCell> cells = new ArrayList<>();
        char currentColor = 0;
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            if (c == 'g' || c == 'y' || c == 'b') {
                currentColor = c;
                continue;
            }
            if (Character.isLetter(c) && Character.isUpperCase(c) && currentColor != 0) {
                cells.add(new ResultCell(c, currentColor));
            }
        }
        return cells;
    }

    private static class ResultCell {
        final char letter;
        final char colorCode;

        ResultCell(char letter, char colorCode) {
            this.letter = letter;
            this.colorCode = colorCode;
        }
    }

    private static String buildFilterPathHtml(String pathLine) {
        String[] parts = pathLine.split(" \\| ");
        String coverage = "";
        String regex = "";
        String regexMatched = "";
        String accepted = "";
        String excluded = "";
        String rules = "";
        String sample = "";
        for (String part : parts) {
            if (part.startsWith("coverage>=")) coverage = part;
            else if (part.startsWith("regex=")) regex = part.substring("regex=".length());
            else if (part.startsWith("regexMatched=")) regexMatched = part;
            else if (part.startsWith("accepted=")) accepted = part;
            else if (part.startsWith("excluded{")) excluded = part;
            else if (part.startsWith("rules=")) rules = part;
            else if (part.startsWith("sample{")) sample = part;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='path-card'>");
        sb.append("<div><span class='cov'>").append(escapeHtml(coverage)).append("</span> ");
        if (!regexMatched.isEmpty())
            sb.append("<span class='chip'>").append(escapeHtml(regexMatched)).append("</span>");
        if (!accepted.isEmpty()) sb.append("<span class='chip'>").append(escapeHtml(accepted)).append("</span>");
        if (!excluded.isEmpty()) sb.append("<span class='chip'>").append(escapeHtml(excluded)).append("</span>");
        sb.append("</div>");
        if (!regex.isEmpty()) {
            sb.append("<div class='regex'>").append(escapeHtml(regex)).append("</div>");
        }
        if (!rules.isEmpty()) {
            sb.append("<div class='muted'>").append(escapeHtml(rules)).append("</div>");
        }
        if (!sample.isEmpty()) {
            sb.append("<div class='muted'>").append(escapeHtml(sample)).append("</div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private String formatFailureText(FailureRecord fr) {
        StringBuilder sb = new StringBuilder();
        sb.append("Game #").append(fr.index).append("\n");
        sb.append("Answer: ").append(fr.answer).append("\n");
        sb.append("Attempts: ").append(fr.attempts).append("\n\n");
        sb.append("Game Board\n");
        int boardStep = 1;
        for (StepDetail step : fr.steps) {
            sb.append("  #").append(boardStep++).append(" ")
                    .append(step.guess).append(" -> ").append(step.result).append("\n");
        }
        sb.append("\n");

        int i = 1;
        for (StepDetail step : fr.steps) {
            sb.append("Step ").append(i++).append("\n");
            sb.append("  Guess : ").append(step.guess).append("\n");
            sb.append("  Result: ").append(step.result).append("\n");
            if (step.strategy != null && !step.strategy.isEmpty()) {
                sb.append("  Strategy: ").append(step.strategy).append("\n");
            }
            if (step.strategyReason != null && !step.strategyReason.isEmpty()) {
                sb.append("  StrategyReason: ").append(step.strategyReason).append("\n");
            }
            if (step.filterRounds > 0 || step.checkedWords > 0) {
                sb.append("  FilterRounds: ").append(step.filterRounds)
                        .append(", CheckedWords: ").append(step.checkedWords).append("\n");
            }
            if (step.targetCoverage > 0) {
                sb.append("  TargetCoverage: ").append(step.targetCoverage).append("\n");
            }
            if (!step.matchedWords.isEmpty()) {
                sb.append("  MatchedWords: ").append(formatWordList(step.matchedWords, 12)).append("\n");
            }
            if (!step.filterPath.isEmpty()) {
                sb.append("  FilterPath(n->2):\n");
                for (String pathLine : step.filterPath) {
                    sb.append("    - ").append(pathLine).append("\n");
                }
            }
            sb.append("  Top Candidates:\n");
            int j = 1;
            for (CandidateScore cs : step.topCandidates) {
                sb.append(String.format("    %2d) %-10s  %d%n", j++, cs.word, cs.score));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatSimResultText(SimResult result) {
        return formatFailureText(FailureRecord.from(result));
    }

    private static ScoreParams defaultScoreParams() {
        WordleScorer scorer = new WordleScorer();
        ScoreParams params = new ScoreParams();
        params.charFrequencyWeight = scorer.getCharFrequencyWeight();
        params.positionTopBonus = scorer.getPositionTopBonus();
        params.repeatPenaltyBase = scorer.getRepeatPenaltyBase();
        return params;
    }

    private static void applyScoreParams(WordleScorer scorer, ScoreParams params) {
        scorer.setCharFrequencyWeight(params.charFrequencyWeight);
        scorer.setPositionTopBonus(params.positionTopBonus);
        scorer.setRepeatPenaltyBase(params.repeatPenaltyBase);
    }

    private static void applyScoreParams(WordleSolver solver, ScoreParams params) {
        solver.setCharFrequencyWeight(params.charFrequencyWeight);
        solver.setPositionTopBonus(params.positionTopBonus);
        solver.setRepeatPenaltyBase(params.repeatPenaltyBase);
    }

    private static String buildReport(SimSummary summary) {
        double winRate = summary.completed == 0 ? 0 : (summary.wins * 100.0 / summary.completed);
        StringBuilder sb = new StringBuilder();
        sb.append("Report\n");
        sb.append(String.format("Completed: %d%n", summary.completed));
        sb.append(String.format("Wins: %d%n", summary.wins));
        sb.append(String.format("Failures: %d%n", summary.completed - summary.wins));
        sb.append(String.format("WinRate: %.2f%%%n", winRate));
        sb.append(String.format("AvgAttempts: %.3f%n", summary.avgAttempts));
        sb.append(String.format("Time: %.2fs%n", summary.totalSeconds));
        sb.append(String.format("Threads: %d%n", summary.threads));
        sb.append(String.format("HardMode: %s%n", summary.hardMode));
        sb.append(String.format("Cancelled: %s%n", summary.cancelled));
        if (summary.scoreParams != null) {
            sb.append("\nScore Params\n");
            sb.append(String.format("charFrequencyWeight: %d%n", summary.scoreParams.charFrequencyWeight));
            sb.append(String.format("positionTopBonus: %d%n", summary.scoreParams.positionTopBonus));
            sb.append(String.format("repeatPenaltyBase: %d%n", summary.scoreParams.repeatPenaltyBase));
        }
        return sb.toString();
    }

    private ScoreParams readScoreParams() {
        ScoreParams params = new ScoreParams();
        params.charFrequencyWeight = parseInt(tfCharFrequencyWeight.getText(), DEFAULT_PARAMS.charFrequencyWeight);
        params.positionTopBonus = parseInt(tfPositionTopBonus.getText(), DEFAULT_PARAMS.positionTopBonus);
        params.repeatPenaltyBase = parseInt(tfRepeatPenaltyBase.getText(), DEFAULT_PARAMS.repeatPenaltyBase);
        return params;
    }

    private ScoreParams readListScoreParams() {
        ScoreParams params = new ScoreParams();
        params.charFrequencyWeight = parseInt(tfListCharFrequencyWeight.getText(), DEFAULT_PARAMS.charFrequencyWeight);
        params.positionTopBonus = parseInt(tfListPositionTopBonus.getText(), DEFAULT_PARAMS.positionTopBonus);
        params.repeatPenaltyBase = parseInt(tfListRepeatPenaltyBase.getText(), DEFAULT_PARAMS.repeatPenaltyBase);
        return params;
    }

    private static String formatWordList(List<String> words, int maxDisplay) {
        if (words == null || words.isEmpty()) {
            return "[]";
        }
        int end = Math.min(maxDisplay, words.size());
        String base = String.join(", ", words.subList(0, end));
        if (words.size() > end) {
            return "[" + base + ", ... +" + (words.size() - end) + "]";
        }
        return "[" + base + "]";
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

    static class ManualAttemptRow {
        final HBox container;
        final TextField tfGuess;
        final List<ComboBox<String>> colorBoxes = new ArrayList<>();

        ManualAttemptRow(int wordLength, int index) {
            Label lbIndex = new Label("第 " + index + " 次");
            this.tfGuess = createGuessField(wordLength);
            this.tfGuess.setPromptText("五个字母");
            HBox colors = new HBox(6);
            for (int i = 0; i < wordLength; i++) {
                ComboBox<String> cb = new ComboBox<>(FXCollections.observableArrayList("B(灰)", "Y(黄)", "G(绿)"));
                cb.getSelectionModel().select(0);
                cb.setPrefWidth(70);
                colorBoxes.add(cb);
                colors.getChildren().add(cb);
            }
            this.container = new HBox(8, lbIndex, tfGuess, colors);
        }

        void setEnabled(boolean enabled) {
            tfGuess.setDisable(!enabled);
            for (ComboBox<String> cb : colorBoxes) {
                cb.setDisable(!enabled);
            }
        }

        void clear() {
            tfGuess.clear();
            for (ComboBox<String> cb : colorBoxes) {
                cb.getSelectionModel().select(0);
            }
        }
    }

    static class StepDetail {
        String guess;
        String result;
        List<CandidateScore> topCandidates = List.of();
        String strategy = "";
        String strategyReason = "";
        int filterRounds;
        int checkedWords;
        int targetCoverage;
        List<String> matchedWords = List.of();
        List<String> familyCandidates = List.of();
        List<String> filterPath = List.of();
    }

    static class SimResult {
        int index;
        String answer;
        int attempts;
        boolean win;
        List<StepDetail> steps;
    }

    static class GameRecord {
        int index;
        String answer;
        boolean win;
        int attempts;
        String lastGuess;
        String tag;
        List<StepDetail> steps;

        static GameRecord from(SimResult r) {
            GameRecord gr = new GameRecord();
            gr.index = r.index;
            gr.answer = r.answer;
            gr.win = r.win;
            gr.attempts = r.attempts;
            gr.steps = r.steps;
            gr.lastGuess = (r.steps == null || r.steps.isEmpty()) ? "" : r.steps.get(r.steps.size() - 1).guess;
            boolean hasProbe = r.steps != null && r.steps.stream().anyMatch(s -> "PROBE_SINGLE_SLOT".equals(s.strategy));
            gr.tag = hasProbe ? "PROBE" : "-";
            return gr;
        }

        SimResult toSimResult() {
            SimResult r = new SimResult();
            r.index = index;
            r.answer = answer;
            r.win = win;
            r.attempts = attempts;
            r.steps = steps;
            return r;
        }
    }

    static class FailureRecord {
        int index;
        String answer;
        int attempts;
        String lastGuess;
        String tag;
        List<StepDetail> steps;

        static FailureRecord from(SimResult r) {
            FailureRecord fr = new FailureRecord();
            fr.index = r.index;
            fr.answer = r.answer;
            fr.attempts = r.attempts;
            fr.steps = r.steps;
            fr.lastGuess = (r.steps == null || r.steps.isEmpty()) ? "" : r.steps.get(r.steps.size() - 1).guess;
            boolean hasProbe = r.steps != null && r.steps.stream().anyMatch(s -> "PROBE_SINGLE_SLOT".equals(s.strategy));
            fr.tag = hasProbe ? "PROBE" : "-";
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
        ScoreParams scoreParams;
        int threads;
        boolean hardMode;
        boolean cancelled;
    }

    static class ScoreParams {
        int charFrequencyWeight;
        int positionTopBonus;
        int repeatPenaltyBase;
    }

    public static void main(String[] args) {
        launch(args);
    }
}