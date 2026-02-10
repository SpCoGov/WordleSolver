package top.spco.wordlesolver;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class WordleSolver {
    private final WordleConstraint constraint = new WordleConstraint();
    private final WordleScorer scorer = new WordleScorer();
    private final Map<String, String> guessResult = new HashMap<>();
    private final Set<String> guessedWords = new HashSet<>();
    private final Set<Character> probedLetters = new HashSet<>();
    private int guessTime = 0;
    private int maxGuessTime = 6;
    private boolean hardMode = false;
    private int entropyMaxGuesses = 300;
    private int entropyMaxCandidates = 2000;
    private int minProbeDistinctDiscriminativeLetters = 2;
    private Set<String> allWordsCache = Set.of();
    private GuessDecision lastDecision = GuessDecision.none();

    public WordleSolver(File wordList) {
        constraint.setWordList(wordList);
        allWordsCache = constraint.allWords();
    }

    public Map<String, Integer> printAnswers() {
        return scorer.scoreMap(constraint);
    }

    public void guessResult(String result) {
        applyGuess("", result);
    }

    public void applyGuess(String guessWord, String result) {
        if (guessTime >= maxGuessTime) {
            throw new IllegalStateException("Guess timed out.");
        }
        guessTime++;
        if (guessWord != null && !guessWord.isBlank()) {
            String normalizedWord = guessWord.toUpperCase();
            guessedWords.add(normalizedWord);
            guessResult.put(normalizedWord, result);
            if ("PROBE_SINGLE_SLOT".equals(lastDecision.strategy) && normalizedWord.equals(lastDecision.chosenWord)) {
                for (char c : lastDecision.newProbeLetters) {
                    probedLetters.add(c);
                }
            }
        }
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

    public WordleSolver setMinProbeDistinctDiscriminativeLetters(int minProbeDistinctDiscriminativeLetters) {
        this.minProbeDistinctDiscriminativeLetters = Math.max(1, minProbeDistinctDiscriminativeLetters);
        return this;
    }

    public WordleSolver setCharFrequencyWeight(int charFrequencyWeight) {
        scorer.setCharFrequencyWeight(charFrequencyWeight);
        return this;
    }

    public WordleSolver setPositionTopBonus(int positionTopBonus) {
        scorer.setPositionTopBonus(positionTopBonus);
        return this;
    }

    public WordleSolver setRepeatPenaltyBase(int repeatPenaltyBase) {
        scorer.setRepeatPenaltyBase(repeatPenaltyBase);
        return this;
    }

    public String nextGuess() {
        Set<String> candidates = constraint.answers();
        if (!guessedWords.isEmpty()) {
            candidates.removeIf(w -> guessedWords.contains(w.toUpperCase()));
        }
        if (candidates.isEmpty()) {
            lastDecision = GuessDecision.noCandidate("NO_CANDIDATE");
            return null;
        }
        if (candidates.size() <= 2) {
            List<String> rankedSmallSet = scorer.rankCandidates(candidates, constraint.getLength());
            if (rankedSmallSet.isEmpty()) {
                lastDecision = GuessDecision.noCandidate("NO_CANDIDATE_AFTER_FILTER");
                return null;
            }
            String chosen = rankedSmallSet.getFirst();
            lastDecision = GuessDecision.candidate(chosen, toSortedUpperWords(candidates), "CANDIDATES_LE_2");
            return chosen;
        }
        String fallbackReason = "PROBE_SKIPPED";
        if (hardMode) {
            fallbackReason = "HARD_MODE";
        } else if (getRemainingAttempts() < 2) {
            fallbackReason = "REMAINING_ATTEMPTS_LT_2";
        } else if (candidates.size() > entropyMaxCandidates) {
            fallbackReason = "CANDIDATES_GT_LIMIT";
        } else {
            SingleSlotFamily family = detectSingleSlotFamily(candidates);
            if (family != null) {
                ProbeSelection selection = chooseProbeGuessByCoverage(family);
                if (selection != null) {
                    lastDecision = GuessDecision.probe(
                            selection.guess,
                            selection.filterRounds,
                            selection.checkedWords,
                            selection.targetCoverage,
                            selection.matchedWords,
                            toSortedUpperWords(family.candidates),
                            selection.filterPath,
                            selection.newProbeLetters,
                            "PROBE_SELECTED"
                    );
                    return selection.guess;
                }
                fallbackReason = "NO_PROBE_MATCH_AFTER_RULES";
            } else {
                fallbackReason = "NOT_SINGLE_SLOT_FAMILY";
            }
        }
        List<String> ranked = scorer.rankCandidates(candidates, constraint.getLength());
        if (ranked.isEmpty()) {
            lastDecision = GuessDecision.noCandidate("NO_CANDIDATE_AFTER_RANK");
            return null;
        }
        String chosen = ranked.getFirst();
        lastDecision = GuessDecision.candidate(chosen, toSortedUpperWords(candidates), fallbackReason);
        return chosen;
    }

    public GuessDecision getLastDecision() {
        return lastDecision;
    }

    public Set<String> getGuessedWords() {
        return new HashSet<>(guessedWords);
    }

    private ProbeSelection chooseProbeGuessByCoverage(SingleSlotFamily family) {
        int lettersCount = family.variableLetters.size();
        if (lettersCount < 2 || allWordsCache.isEmpty()) {
            return null;
        }
        List<String> rankedPool = scorer.rankCandidates(allWordsCache, constraint.getLength());
        if (rankedPool.size() > entropyMaxGuesses) {
            rankedPool = rankedPool.subList(0, entropyMaxGuesses);
        }
        int minCoverage = Math.max(2, minProbeDistinctDiscriminativeLetters);
        int checkedWords = 0;
        int filterRounds = 0;
        List<String> filterPath = new ArrayList<>();
        List<Character> letters = new ArrayList<>(family.variableLetters);
        letters.sort(Character::compareTo);
        for (int targetCoverage = lettersCount; targetCoverage >= minCoverage; targetCoverage--) {
            filterRounds++;
            List<String> matchedWords = new ArrayList<>();
            String chosenWord = null;
            List<Character> chosenNewProbeLetters = List.of();
            String coverageRegex = buildCoverageRegex(letters, targetCoverage);
            Pattern pattern = Pattern.compile(coverageRegex, Pattern.CASE_INSENSITIVE);
            int excludedByFamily = 0;
            int excludedByGuessed = 0;
            int regexMatchedCount = 0;
            int excludedByNewProbeRule = 0;
            int acceptedCount = 0;
            List<String> excludedFamilySample = new ArrayList<>();
            List<String> excludedGuessedSample = new ArrayList<>();
            List<String> excludedProbeSample = new ArrayList<>();
            for (String word : rankedPool) {
                if (family.candidates.contains(word)) {
                    excludedByFamily++;
                    if (excludedFamilySample.size() < 6) {
                        excludedFamilySample.add(word.toUpperCase());
                    }
                    continue;
                }
                if (guessedWords.contains(word.toUpperCase())) {
                    excludedByGuessed++;
                    if (excludedGuessedSample.size() < 6) {
                        excludedGuessedSample.add(word.toUpperCase());
                    }
                    continue;
                }
                checkedWords++;
                if (pattern.matcher(word).matches()) {
                    regexMatchedCount++;
                    List<Character> newProbeLetters = collectNewProbeLetters(word, letters);
                    if (newProbeLetters.size() <= 1) {
                        excludedByNewProbeRule++;
                        if (excludedProbeSample.size() < 6) {
                            excludedProbeSample.add(word.toUpperCase() + "(newLetters=" + newProbeLetters + ")");
                        }
                        continue;
                    }
                    acceptedCount++;
                    if (matchedWords.size() < 12) {
                        matchedWords.add(word.toUpperCase() + "(newLetters=" + newProbeLetters + ")");
                    }
                    if (chosenWord == null) {
                        chosenWord = word;
                        chosenNewProbeLetters = newProbeLetters;
                    }
                }
            }
            filterPath.add(String.format(
                    "coverage>=%d | regex=%s | regexMatched=%d | accepted=%d | " +
                            "excluded{family=%d, guessed=%d, newProbe<=1=%d} | " +
                            "rules=[exclude family candidates; exclude guessed words; exclude newProbeLetters<=1] | " +
                            "sample{accepted=%s, family=%s, guessed=%s, newProbe<=1=%s}",
                    targetCoverage,
                    coverageRegex,
                    regexMatchedCount,
                    acceptedCount,
                    excludedByFamily,
                    excludedByGuessed,
                    excludedByNewProbeRule,
                    matchedWords.isEmpty() ? "[]" : matchedWords,
                    excludedFamilySample.isEmpty() ? "[]" : excludedFamilySample,
                    excludedGuessedSample.isEmpty() ? "[]" : excludedGuessedSample,
                    excludedProbeSample.isEmpty() ? "[]" : excludedProbeSample
            ));
            if (chosenWord != null) {
                return new ProbeSelection(
                        chosenWord,
                        filterRounds,
                        checkedWords,
                        targetCoverage,
                        matchedWords,
                        filterPath,
                        chosenNewProbeLetters
                );
            }
        }
        return null;
    }

    private List<Character> collectNewProbeLetters(String word, List<Character> targetLetters) {
        HashSet<Character> inWord = new HashSet<>();
        for (char c : word.toUpperCase().toCharArray()) {
            inWord.add(c);
        }
        List<Character> newLetters = new ArrayList<>();
        for (char c : targetLetters) {
            if (inWord.contains(c) && !probedLetters.contains(c)) {
                newLetters.add(c);
            }
        }
        return newLetters;
    }

    private String buildCoverageRegex(List<Character> letters, int targetCoverage) {
        List<List<Character>> combos = new ArrayList<>();
        buildCombinations(letters, targetCoverage, 0, new ArrayList<>(), combos);
        StringBuilder regex = new StringBuilder("^(?:");
        for (int i = 0; i < combos.size(); i++) {
            if (i > 0) {
                regex.append("|");
            }
            regex.append("(?:");
            for (char c : combos.get(i)) {
                regex.append("(?=.*").append(Pattern.quote(String.valueOf(c))).append(")");
            }
            regex.append(".*)");
        }
        regex.append(")$");
        return regex.toString();
    }

    private void buildCombinations(List<Character> letters, int choose, int index, List<Character> current, List<List<Character>> out) {
        if (current.size() == choose) {
            out.add(new ArrayList<>(current));
            return;
        }
        int remainingNeeded = choose - current.size();
        for (int i = index; i <= letters.size() - remainingNeeded; i++) {
            current.add(letters.get(i));
            buildCombinations(letters, choose, i + 1, current, out);
            current.removeLast();
        }
    }

    private SingleSlotFamily detectSingleSlotFamily(Set<String> candidates) {
        if (candidates.size() < 2) {
            return null;
        }
        List<String> list = new ArrayList<>(candidates);
        String first = list.getFirst().toUpperCase();
        int length = first.length();
        int variableIndex = -1;

        for (int i = 0; i < length; i++) {
            char base = first.charAt(i);
            boolean same = true;
            for (String word : list) {
                if (word.length() != length) {
                    return null;
                }
                if (Character.toUpperCase(word.charAt(i)) != base) {
                    same = false;
                    break;
                }
            }
            if (!same) {
                if (variableIndex != -1) {
                    return null;
                }
                variableIndex = i;
            }
        }

        if (variableIndex == -1) {
            return null;
        }

        HashSet<Character> variableLetters = new HashSet<>();
        for (String word : list) {
            variableLetters.add(Character.toUpperCase(word.charAt(variableIndex)));
        }
        if (variableLetters.size() < 2) {
            return null;
        }
        return new SingleSlotFamily(variableIndex, variableLetters, candidates);
    }

    private record SingleSlotFamily(int variableIndex, Set<Character> variableLetters, Set<String> candidates) {
    }

    private static List<String> toSortedUpperWords(Set<String> words) {
        List<String> list = new ArrayList<>();
        for (String word : words) {
            list.add(word.toUpperCase());
        }
        list.sort(String::compareTo);
        return list;
    }

    private record ProbeSelection(
            String guess,
            int filterRounds,
            int checkedWords,
            int targetCoverage,
            List<String> matchedWords,
            List<String> filterPath,
            List<Character> newProbeLetters
    ) {
    }

    public static class GuessDecision {
        public final String strategy;
        public final String strategyReason;
        public final String chosenWord;
        public final int filterRounds;
        public final int checkedWords;
        public final int targetCoverage;
        public final List<String> matchedWords;
        public final List<String> familyCandidates;
        public final List<String> filterPath;
        public final List<Character> newProbeLetters;

        private GuessDecision(String strategy, String strategyReason, String chosenWord, int filterRounds, int checkedWords, int targetCoverage,
                              List<String> matchedWords, List<String> familyCandidates, List<String> filterPath,
                              List<Character> newProbeLetters) {
            this.strategy = strategy;
            this.strategyReason = strategyReason;
            this.chosenWord = chosenWord;
            this.filterRounds = filterRounds;
            this.checkedWords = checkedWords;
            this.targetCoverage = targetCoverage;
            this.matchedWords = matchedWords;
            this.familyCandidates = familyCandidates;
            this.filterPath = filterPath;
            this.newProbeLetters = newProbeLetters;
        }

        static GuessDecision none() {
            return new GuessDecision("NONE", "INIT", "", 0, 0, 0, List.of(), List.of(), List.of(), List.of());
        }

        static GuessDecision noCandidate(String reason) {
            return new GuessDecision("NO_CANDIDATE", reason, "", 0, 0, 0, List.of(), List.of(), List.of(), List.of());
        }

        static GuessDecision candidate(String chosenWord, List<String> candidates, String reason) {
            return new GuessDecision("CANDIDATE_RANK", reason, chosenWord.toUpperCase(), 0, 0, 0, List.of(), candidates, List.of(), List.of());
        }

        static GuessDecision probe(String chosenWord, int filterRounds, int checkedWords, int targetCoverage,
                                   List<String> matchedWords, List<String> familyCandidates, List<String> filterPath,
                                   List<Character> newProbeLetters, String reason) {
            return new GuessDecision(
                    "PROBE_SINGLE_SLOT",
                    reason,
                    chosenWord.toUpperCase(),
                    filterRounds,
                    checkedWords,
                    targetCoverage,
                    matchedWords,
                    familyCandidates,
                    filterPath,
                    newProbeLetters
            );
        }
    }
}
