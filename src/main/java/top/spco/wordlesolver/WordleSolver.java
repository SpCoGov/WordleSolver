package top.spco.wordlesolver;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class WordleSolver {
    private final WordleRegex regex = new WordleRegex();
    private final Map<String, String> guessResult = new HashMap<>();
    private int guessTime = 0;
    private int maxGuessTime = 6;

    public WordleSolver(File wordList) {
        regex.setWordList(wordList);
    }

    public Map<String, Integer> printAnswers() {

        return regex.answerScoreMap();

    }

    public void guessResult(String result) {
        if (guessTime >= maxGuessTime) {
            throw new IllegalStateException("Guess timed out.");
        }
        guessTime++;
        guessResult.put(result, result);
    }

    public void setMaxGuessTime(int maxGuessTime) {
        this.maxGuessTime = maxGuessTime;
    }
}
