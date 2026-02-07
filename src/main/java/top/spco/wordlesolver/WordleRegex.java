package top.spco.wordlesolver;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WordleRegex {
    private final int length;
    private File wordList;
    private final HashMap<Integer, HashSet<Character>> cannotBe = new HashMap<>();
    private final HashSet<Character> hasWords = new HashSet<>();
    private final HashMap<Integer, Character> mustBe = new HashMap<>();
    private WordListData data;

    public WordleRegex(int length) {
        this.length = length;
    }

    public WordleRegex() {
        this(5);
    }

    public HashSet<String> answers() {
        HashSet<String> matchingWords = new HashSet<>();
        if (wordList != null) {
            Pattern pattern = Pattern.compile(toRegex(), Pattern.CASE_INSENSITIVE);
            for (String line : toWordList(wordList)) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    matchingWords.add(line);
                }
            }
            return matchingWords;
        }
        return matchingWords;
    }

    public List<String> printAnswers(boolean withScore, boolean returnOnly) {
        HashSet<String> answers = answers();
        genData();
        List<String> finalAnswers = new ArrayList<>(answers);
        // 对所有单词排序，优先含有最多高出现率字符的单词，同时考虑重复字母的惩罚
        finalAnswers.sort((a, b) -> Integer.compare(calculateWordScore(b), calculateWordScore(a)));

        if (!returnOnly) {
            if (withScore) {
                // 打印出排序后的单词和分数
                finalAnswers.forEach(s -> System.out.println(s.toLowerCase() + "\t" + calculateWordScore(s)));
            } else {
                // 仅打印出排序后的单词
                finalAnswers.forEach(s -> System.out.println(s.toLowerCase()));
            }
        }

        return finalAnswers;
    }

    public Map<String, Integer> answerScoreMap() {
        HashSet<String> answers = answers();
        genData();
        List<String> sortedAnswers = new ArrayList<>(answers);
        sortedAnswers.sort((a, b) -> Integer.compare(calculateWordScore(b), calculateWordScore(a)));
        Map<String, Integer> finalAnswers = new LinkedHashMap<>();
        System.out.println(answers.size());
        for (String answer : sortedAnswers) {
            finalAnswers.put(answer.toLowerCase(), calculateWordScore(answer));
        }

        return finalAnswers;
    }

    public List<String> printAnswers() {
        return printAnswers(false);
    }

    public List<String> printAnswers(boolean withScore) {
        return printAnswers(withScore, false);
    }

    private static boolean hasDuplicateLetters(String word) {
        Set<Character> seen = new HashSet<>();
        for (char c : word.toUpperCase().toCharArray()) {
            if (!seen.add(c)) { // 如果添加失败，说明有重复字母
                return true;
            }
        }
        return false;
    }

    private int calculateWordScore(String word) {
        int score = 0;
        String upperWord = word.toUpperCase();
        StringBuilder sb = new StringBuilder(word).append("评分细则：");

        // 记录重复字母的出现次数
        HashMap<Character, Integer> charRepeatCount = new HashMap<>();
        // 累加字符总频率
        for (char c : upperWord.toCharArray()) {
            int times = data.charOccurrences.getOrDefault(c, 0);
            sb.append("+").append(times).append("(字母").append(c).append("的出现频率为").append(times).append(")");
            score += times;

            // 记录重复字母的数量
            charRepeatCount.put(c, charRepeatCount.getOrDefault(c, 0) + 1);
        }

        // 对重复字母进行减分，避免过度加权
        for (Map.Entry<Character, Integer> entry : charRepeatCount.entrySet()) {
            int repeatCount = entry.getValue();

            // 设定一个重复字母惩罚系数（例如，重复出现超过2次时，每次出现减分）
            if (repeatCount > 1) {
                score -= (int) Math.pow(0, repeatCount);
            }
        }

        // 累加每个位置高频字母的分数
        for (int i = 0; i < upperWord.length(); i++) {
            char c = upperWord.charAt(i);
            Map<Character, Integer> positionMap = data.positionCharOccurrences.getOrDefault(i, new HashMap<>());
            int maxFrequency = positionMap.values().stream().max(Integer::compare).orElse(0);
            int charFrequency = positionMap.getOrDefault(c, 0);
            if (charFrequency == maxFrequency && charFrequency > 0) {
                score += 100; // 高权重值
                sb.append("  +100 (位置 ").append(i)
                        .append(" 的字母 ").append(c)
                        .append(" 是该位置的高频字母，出现频率为 ").append(charFrequency).append(")\n");
            }
        }
        sb.append("=").append(score);
        return score;
    }

    public String toRegex() {   
        if (hasWords.isEmpty() && mustBe.isEmpty() && cannotBe.isEmpty()) {
            return "^[a-zA-Z]{" + length + "}$";
        }

        StringBuilder regex = new StringBuilder("\\b");
        for (char c : this.hasWords) {
            regex.append("(?=\\w*").append(c).append(")");
        }
        for (int i = 0; i < length; i++) {
            if (mustBe.containsKey(i)) {
                regex.append(mustBe.get(i));
                continue;
            }
            regex.append("[^");
            if (cannotBe.containsKey(i)) {
                HashSet<Character> set = cannotBe.get(i);
                for (char c : set) {
                    regex.append(c);
                }
            }
            regex.append("]");
        }
        regex.append("\\b");
        return regex.toString();
    }

    public void setWordList(File wordList) {
        this.wordList = wordList;
    }

    public WordleRegex setLetter(int letterPos, char c) {
        if (isValidPos(letterPos)) {
            mustBe.put(letterPos - 1, c);
        }
        return this;
    }

    public WordleRegex setLetter(int letterPos, String cs) {
        return setLetter(letterPos, stringToChars(cs, true)[0]);
    }

    public WordleRegex notHave(char... cs) {
        for (int i = 1; i <= this.length; i++) {
            cannotBe(i, cs);
        }
        return this;
    }

    public WordleRegex notHave(String cs) {
        return notHave(stringToChars(cs, false));
    }

    public WordleRegex cannotBe(int letterPos, char... cs) {
        if (!isValidPos(letterPos)) {
            return this;
        }
        HashSet<Character> set;
        if (cannotBe.containsKey(letterPos - 1)) {
            set = cannotBe.get(letterPos - 1);
        } else {
            set = new HashSet<>();
        }
        for (char c : cs) {
            set.add(c);
        }
        cannotBe.put(letterPos - 1, set);
        return this;
    }

    public WordleRegex cannotBe(int letterPos, String chars) {
        return cannotBe(letterPos, stringToChars(chars, false));
    }

    public WordleRegex hasWord(char... cs) {
        for (char c : cs) {
            hasWords.add(c);
        }
        return this;
    }

    public WordleRegex hasWord(String cs) {
        return hasWord(stringToChars(cs, false));
    }

    public WordleRegex yellowBlock(int letterPos, char c) {
        cannotBe(letterPos, c);
        hasWord(c);
        return this;
    }

    public WordleRegex yellowBlock(int letterPos, String cs) {
        char[] chars = stringToChars(cs, false);
        for (char c : chars) {
            yellowBlock(letterPos, c);
        }
        return this;
    }

    public WordleRegex yellowBlock(String word) {
        if (word == null) {
            return this;
        }
        if (word.length() > this.length) {
            throw new IllegalArgumentException("word length is too long");
        }
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c == '_' || c == 'x' || c == ' ') {
                continue;
            }
            yellowBlock(i + 1, word.charAt(i));
        }
        return this;
    }

    public WordleRegex mustBe(String word) {
        if (word == null) {
            return this;
        }
        if (word.length() > this.length) {
            throw new IllegalArgumentException("word length is too long");
        }
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c == '_' || c == 'x' || c == ' ') {
                continue;
            }
            mustBe.put(i, c);
        }
        return this;
    }

    public void genData() {
        this.data = new WordListData(length, answers());
    }

    private char[] stringToChars(String str, boolean singleOnly) {
        char[] chars = str.toUpperCase().toCharArray();
        if (singleOnly && chars.length > 1) {
            throw new IllegalArgumentException("Only one single character allowed");
        }
        return chars;
    }

    private boolean isValidPos(int letterPos) {
        return letterPos >= 1 && letterPos <= this.length;
    }

    public static Set<String> toWordList(File file) {
        HashSet<String> wordList = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.toUpperCase();
                wordList.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return wordList;
    }

    public WordleRegex guess(String guessResult) {
        LetterColor inColor = null;
        HashMap<Character, LetterColor> appearedLetters = new HashMap<>();
        int pos = 0;
        for (char c : guessResult.toCharArray()) {
            if (Character.isUpperCase(c)) {
                pos++;
                if (pos > this.length) {
                    throw new IllegalArgumentException("Word length is too long.");
                }
                if (inColor == null) {
                    throw new IllegalArgumentException("Color of undefined letters.");
                }
                switch (inColor) {
                    case BLACK -> {
                        if (appearedLetters.containsKey(c) && appearedLetters.get(c) == LetterColor.YELLOW) {
                            cannotBe(pos, c);
                            continue;
                        }
                        notHave(c);
                        appearedLetters.put(c, LetterColor.BLACK);
                    }
                    case GREEN -> {
                        setLetter(pos, c);
                        appearedLetters.put(c, LetterColor.GREEN);
                    }
                    case YELLOW -> {
                        yellowBlock(pos, c);
                        appearedLetters.put(c, LetterColor.YELLOW);
                    }
                }
            } else {
                inColor = LetterColor.toLetterColor(String.valueOf(c));
                if (inColor == null) {
                    throw new IllegalArgumentException("Unknown color: " + c + ".");
                }
            }

        }
        if (pos != this.length) {
            throw new IllegalArgumentException("The guess does not match the word length.");
        }
        return this;
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
            return positionCharOccurrences.getOrDefault(index, new HashMap<>()).getOrDefault(Character.toUpperCase(c), 0);
        }

        public int getLength() {
            return length;
        }
    }

    public enum LetterColor {
        YELLOW,
        BLACK,
        GREEN;

        public static LetterColor toLetterColor(String code) {
            for (LetterColor letterColor : LetterColor.values()) {
                if (code.equals(String.valueOf(letterColor.name().charAt(0)).toLowerCase())) {
                    return letterColor;
                }
            }
            return null;
        }
    }
}
