package top.spco.wordlesolver;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WordleSolver {
    private final WordleConstraint constraint = new WordleConstraint();
    private final WordleScorer scorer = new WordleScorer();
    private final Map<String, String> guessResult = new HashMap<>();
    private int guessTime = 0;
    private int maxGuessTime = 6;
    private boolean hardMode = true;
    private int entropyMaxGuesses = 300;
    private int entropyMaxCandidates = 2000;
    private Set<String> allWordsCache = Set.of();

    public WordleSolver(File wordList) {
        constraint.setWordList(wordList);
        allWordsCache = constraint.allWords();
    }

    public Map<String, Integer> printAnswers() {
        return scorer.scoreMap(constraint);
    }

    public void guessResult(String result) {
        if (guessTime >= maxGuessTime) {
            throw new IllegalStateException("Guess timed out.");
        }
        guessTime++;
        guessResult.put(result, result);
        constraint.guess(result);
    }

    public void setMaxGuessTime(int maxGuessTime) {
        this.maxGuessTime = maxGuessTime;
    }

    public int getMaxGuessTime() {
        return maxGuessTime;
    }

    public int getGuessTime() {
        return guessTime;
    }

    public int getRemainingAttempts() {
        return Math.max(0, maxGuessTime - guessTime);
    }

    public WordleSolver setHardMode(boolean hardMode) {
        this.hardMode = hardMode;
        return this;
    }

    public boolean isHardMode() {
        return hardMode;
    }

    public WordleSolver setEntropyMaxGuesses(int entropyMaxGuesses) {
        this.entropyMaxGuesses = entropyMaxGuesses;
        return this;
    }

    public WordleSolver setEntropyMaxCandidates(int entropyMaxCandidates) {
        this.entropyMaxCandidates = entropyMaxCandidates;
        return this;
    }

    public String nextGuess() {
        Set<String> candidates = constraint.answers();
        if (candidates.isEmpty()) {
            return null;
        }
        if (getRemainingAttempts() >= 2) {
            String entropyGuess = chooseEntropyGuess(candidates);
            if (entropyGuess != null) {
                return entropyGuess;
            }
        }
        List<String> ranked = scorer.rankCandidates(candidates, constraint.getLength());
        return ranked.isEmpty() ? null : ranked.getFirst();
    }

    private String chooseEntropyGuess(Set<String> candidates) {
        if (candidates.size() > entropyMaxCandidates) {
            return null;
        }
        Set<String> guessPool = hardMode ? candidates : allWordsCache;
        if (guessPool.isEmpty()) {
            return null;
        }

        int length = constraint.getLength();
        List<String> guessList;
        if (guessPool.size() > entropyMaxGuesses) {
            guessList = scorer.rankCandidates(guessPool, length);
            guessList = guessList.subList(0, Math.min(entropyMaxGuesses, guessList.size()));
        } else {
            guessList = new ArrayList<>(guessPool);
        }

        double bestExpected = Double.POSITIVE_INFINITY;
        String bestGuess = null;
        for (String guess : guessList) {
            double expected = expectedRemaining(candidates, guess);
            if (expected < bestExpected) {
                bestExpected = expected;
                bestGuess = guess;
            } else if (expected == bestExpected && bestGuess != null && guess.compareTo(bestGuess) < 0) {
                bestGuess = guess;
            }
        }
        return bestGuess;
    }

    private double expectedRemaining(Set<String> candidates, String guess) {
        HashMap<String, Integer> buckets = new HashMap<>();
        for (String answer : candidates) {
            String pattern = feedbackPattern(guess, answer);
            buckets.put(pattern, buckets.getOrDefault(pattern, 0) + 1);
        }
        double total = candidates.size();
        double sum = 0;
        for (int count : buckets.values()) {
            sum += (double) count * count;
        }
        return sum / total;
    }

    private String feedbackPattern(String guess, String answer) {
        String g = guess.toUpperCase();
        String a = answer.toUpperCase();
        int len = g.length();
        char[] pattern = new char[len];
        HashMap<Character, Integer> counter = new HashMap<>();

        for (int i = 0; i < len; i++) {
            char c = a.charAt(i);
            counter.put(c, counter.getOrDefault(c, 0) + 1);
        }

        for (int i = 0; i < len; i++) {
            if (g.charAt(i) == a.charAt(i)) {
                pattern[i] = 'G';
                char c = g.charAt(i);
                counter.put(c, counter.get(c) - 1);
            } else {
                pattern[i] = 0;
            }
        }

        for (int i = 0; i < len; i++) {
            if (pattern[i] == 'G') {
                continue;
            }
            char c = g.charAt(i);
            int count = counter.getOrDefault(c, 0);
            if (count > 0) {
                pattern[i] = 'Y';
                counter.put(c, count - 1);
            } else {
                pattern[i] = 'B';
            }
        }
        return new String(pattern);
    }
}
