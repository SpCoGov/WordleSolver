package top.spco.wordlesolver;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

public class WordleScorer {
    private int charFrequencyWeight = 1;
    private int positionTopBonus = 100;
    private int repeatPenaltyBase = 25;

    public Map<String, Integer> scoreMap(WordleConstraint constraint) {
        return scoreMap(constraint.answers(), constraint.getLength());
    }

    public List<String> rankCandidates(WordleConstraint constraint) {
        return rankCandidates(constraint.answers(), constraint.getLength());
    }

    public Map<String, Integer> scoreMap(Set<String> candidates, int length) {
        if (candidates == null || candidates.isEmpty()) {
            return new LinkedHashMap<>();
        }
        WordListData data = new WordListData(length, candidates);
        List<String> sorted = new ArrayList<>(candidates);
        sorted.sort((a, b) -> Integer.compare(calculateWordScore(b, data), calculateWordScore(a, data)));
        Map<String, Integer> finalAnswers = new LinkedHashMap<>();
        for (String answer : sorted) {
            finalAnswers.put(answer.toLowerCase(), calculateWordScore(answer, data));
        }
        return finalAnswers;
    }

    public List<String> rankCandidates(Set<String> candidates, int length) {
        if (candidates == null || candidates.isEmpty()) {
            return new ArrayList<>();
        }
        WordListData data = new WordListData(length, candidates);
        List<String> finalAnswers = new ArrayList<>(candidates);
        finalAnswers.sort((a, b) -> Integer.compare(calculateWordScore(b, data), calculateWordScore(a, data)));
        return finalAnswers;
    }

    private int calculateWordScore(String word, WordListData data) {
        int score = 0;
        String upperWord = word.toUpperCase();

        // 记录重复字母的出现次数
        HashMap<Character, Integer> charRepeatCount = new HashMap<>();
        // 累加字符总频率
        for (char c : upperWord.toCharArray()) {
            int times = data.charOccurrences.getOrDefault(c, 0);
            score += times * charFrequencyWeight;
            charRepeatCount.put(c, charRepeatCount.getOrDefault(c, 0) + 1);
        }

        // 对重复字母进行减分，避免过度加权
        for (Map.Entry<Character, Integer> entry : charRepeatCount.entrySet()) {
            int repeatCount = entry.getValue();
            if (repeatCount > 1) {
                score -= (int) Math.pow(repeatPenaltyBase, repeatCount);
            }
        }

        // 累加每个位置高频字母的分数
        for (int i = 0; i < upperWord.length(); i++) {
            char c = upperWord.charAt(i);
            Map<Character, Integer> positionMap = data.positionCharOccurrences.getOrDefault(i, new HashMap<>());
            int maxFrequency = positionMap.values().stream().max(Integer::compare).orElse(0);
            int charFrequency = positionMap.getOrDefault(c, 0);
            if (charFrequency == maxFrequency && charFrequency > 0) {
                score += positionTopBonus;
            }
        }
        return score;
    }

    public WordleScorer setCharFrequencyWeight(int charFrequencyWeight) {
        this.charFrequencyWeight = charFrequencyWeight;
        return this;
    }

    public WordleScorer setPositionTopBonus(int positionTopBonus) {
        this.positionTopBonus = positionTopBonus;
        return this;
    }

    public WordleScorer setRepeatPenaltyBase(int repeatPenaltyBase) {
        this.repeatPenaltyBase = repeatPenaltyBase;
        return this;
    }

    public int getCharFrequencyWeight() {
        return charFrequencyWeight;
    }

    public int getPositionTopBonus() {
        return positionTopBonus;
    }

    public int getRepeatPenaltyBase() {
        return repeatPenaltyBase;
    }

    public static class WordListData implements Serializable {
        @Serial
        private static final long serialVersionUID = 7167948794795625313L;
        private final Set<String> wordList;
        private final int length;
        private final HashMap<Character, Integer> charOccurrences = new HashMap<>();
        private final HashMap<Integer, HashMap<Character, Integer>> positionCharOccurrences = new HashMap<>();

        public WordListData(int length, Set<String> wordList) {
            this.length = length;
            this.wordList = wordList;
            countCharOccurrence();
            calculatePositionCharOccurrences();
        }

        private void countCharOccurrence() {
            for (String line : wordList) {
                if (line.length() != this.length) {
                    continue;
                }
                for (char c : line.toCharArray()) {
                    charOccurrences.put(c, charOccurrences.getOrDefault(c, 0) + 1);
                }
            }
        }

        private void calculatePositionCharOccurrences() {
            for (String line : wordList) {
                line = line.toUpperCase();
                for (int i = 0; i < line.length(); i++) {
                    if (line.length() != this.length) {
                        continue;
                    }
                    if (positionCharOccurrences.size() <= i) {
                        positionCharOccurrences.put(i, new HashMap<>());
                    }
                    Map<Character, Integer> positionMap = positionCharOccurrences.get(i);
                    char c = line.charAt(i);
                    positionMap.put(c, positionMap.getOrDefault(c, 0) + 1);
                }
            }
        }

        public int getCharOccurrence(char c) {
            return charOccurrences.getOrDefault(Character.toUpperCase(c), 0);
        }

        public Set<Character> appearedChars() {
            return charOccurrences.keySet();
        }

        public Set<Character> positionAppearedChars(int index) {
            if (index < 0 || index >= this.length) {
                throw new IllegalArgumentException("index is out of bounds");
            }
            return positionCharOccurrences.getOrDefault(index, new HashMap<>()).keySet();
        }

        public int getPositionCharOccurrence(int index, char c) {
            if (index < 0 || index >= this.length) {
                throw new IllegalArgumentException("index is out of range");
            }
            return positionCharOccurrences.getOrDefault(index, new HashMap<>())
                    .getOrDefault(Character.toUpperCase(c), 0);
        }

        public int getLength() {
            return length;
        }
    }
}
